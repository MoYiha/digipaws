package neth.iecal.curbox.blockers.viewblocker

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.RECEIVER_EXPORTED
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import neth.iecal.curbox.blockers.BaseBlocker
import neth.iecal.curbox.data.models.ViewBlockerConfig
import neth.iecal.curbox.data.models.ViewBlockerRule
import neth.iecal.curbox.services.BaseBlockingService

class ViewBlocker : BaseBlocker() {

    companion object {
        const val INTENT_ACTION_REFRESH_VIEW_BLOCKER = "neth.iecal.curbox.refresh.viewblocker"
        private const val MAX_OVERLAY_COUNT = 100
        private const val BACK_COOLDOWN_MS = 600L

        private const val TARGET_EVENTS_MASK =
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
            AccessibilityEvent.TYPE_VIEW_SCROLLED or
            AccessibilityEvent.TYPE_VIEW_SELECTED

        /**
         * Built-in rules shipped with the app and shown as toggles in the UI.
         * Converted to [ViewBlockerFilterRule] at runtime via [ViewBlockerRule.toFilterRule].
         * [requirePresent] / [requireAbsent] use [NodeMatcher] syntax: `"type:value;type2:value2"`.
         */
        val DEFAULT_RULES = listOf(
            // ── Instagram ──
            ViewBlockerRule("ig_stories_tray", "com.instagram.android", "Hide Stories",
                path = "androidx.viewpager.widget.ViewPager[0]>android.widget.FrameLayout[0]>androidx.recyclerview.widget.RecyclerView[0]>android.widget.LinearLayout[0]",
                requirePresent = listOf("viewId:com.instagram.android:id/title_logo")),
            ViewBlockerRule("ig_search_suggestions", "com.instagram.android", "Hide Explore Grid",
                path = "androidx.viewpager.widget.ViewPager[0]>android.widget.FrameLayout[3]>androidx.recyclerview.widget.RecyclerView[0]>android.widget.FrameLayout[*]"),
            ViewBlockerRule("ig_feed_1", "com.instagram.android", "Hide main feed",
                path = "androidx.viewpager.widget.ViewPager[0]>android.widget.FrameLayout[0]>androidx.recyclerview.widget.RecyclerView[0]>android.view.ViewGroup[*]"),
            ViewBlockerRule("ig_feed_2", "com.instagram.android", "Hide main feed but let me use the following tab",
                path = "androidx.viewpager.widget.ViewPager[0]>android.widget.FrameLayout[0]>androidx.recyclerview.widget.RecyclerView[0]>android.view.ViewGroup[*]",
                requirePresent = listOf("viewId:com.instagram.android:id/title_logo")),
            ViewBlockerRule("ig_reel_interactive_reels", "com.instagram.android", "Hide interactive buttons (like, share, comment) in the reels tab",
                viewId = "com.instagram.android:id/clips_ufi_component"),

            // ── YouTube ──
            ViewBlockerRule("yt_video_thingies", "com.google.android.youtube", "Hide everything (recommendations, comments, description etc) except the video",
                viewId = "com.google.android.youtube:id/watch_list"),
            ViewBlockerRule("yt_video_everything_except_results", "com.google.android.youtube", "Hide feed and only let me access search results",
                viewId = "com.google.android.youtube:id/results",
                requirePresent = listOf("descres:accessibility_feed_filter_bar_content_description")),

            // ── X / Twitter ──
            ViewBlockerRule("x_feed_but_allow_following", "com.twitter.android", "Hide 'For You' and only let me access the Following tab",
                viewId = "android:id/list",
                requirePresent = listOf("descres:guide_tab_title_for_you;isSelected:true")),

            // ── LinkedIn ──
            ViewBlockerRule("li_feed_item", "com.linkedin.android", "Hide feed item",
                viewId = "com.linkedin.android:id/feed_item_update_card"),
            ViewBlockerRule("li_notifications", "com.linkedin.android", "Hide notifications",
                viewId = "com.linkedin.android:id/tab_notifications"),

            // ── WhatsApp ──
            ViewBlockerRule("wa_ai_search", "com.whatsapp", "Hide AI suggestions in search",
                viewId = "com.whatsapp:id/search_meta_ai_input_send_button"),
            ViewBlockerRule("wa_ai_button", "com.whatsapp", "Hide AI button",
                viewId = "com.whatsapp:id/extended_mini_fab"),
            ViewBlockerRule("wa_rec_channels", "com.whatsapp", "Hide recommended channels",
                viewId = "com.whatsapp:id/newsletter_directory_row_container"),
            ViewBlockerRule("wa_updates_button", "com.whatsapp", "Hide Updates button",
                path = "android.view.ViewGroup[0]>android.widget.FrameLayout[1]"),
            ViewBlockerRule("wa_channels_path", "com.whatsapp", "Hide channels",
                path = "androidx.viewpager.widget.ViewPager[0]>androidx.recyclerview.widget.RecyclerView[0]>android.widget.RelativeLayout[*]")
        )
    }

    lateinit var service: BaseBlockingService
    private lateinit var windowManager: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var config = ViewBlockerConfig()
    private var settingsJob: Job? = null
    private var isDarkMode = false
    private var lastBackTime = 0L

    @Volatile private var rulesByPackage = HashMap<String, PackageRules>()

    private val blockedElements = HashMap<String, BlockedElement>(32)
    private val activeRuleKeys = HashSet<String>(32)
    private var lastPackage = ""

    private val boundsRect = Rect()

    var elementPicker: ElementPicker? = null
        private set

    private data class BlockedElement(val view: View, val bounds: Rect)

    private class PackageRules(
        val pathRules: List<ViewBlockerFilterRule>,
        val viewIdRules: List<ViewBlockerFilterRule>,
        val viewIdDescRules: List<ViewBlockerFilterRule>,
        val recursiveRules: List<ViewBlockerFilterRule>,
        val layoutRules: List<ViewBlockerFilterRule>
    ) {
        val isEmpty get() = pathRules.isEmpty() && viewIdRules.isEmpty() &&
                viewIdDescRules.isEmpty() && recursiveRules.isEmpty() && layoutRules.isEmpty()
    }

    private data class OverlayAction(
        val key: String,
        val bounds: Rect,
        val rule: ViewBlockerFilterRule
    )

    fun setupBlocker(service: BaseBlockingService) {
        this.service = service
        windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        updateDarkMode()

        settingsJob?.cancel()
        settingsJob = CoroutineScope(Dispatchers.IO).launch {
            service.dataStoreManager.settings.collectLatest { settings ->
                config = settings.viewBlockerConfig
                rebuildParsedRules()
            }
        }
    }

    private fun rebuildParsedRules() {
        if (!config.isActive) {
            rulesByPackage = HashMap()
            handler.post { forceClearAllOverlays() }
            return
        }

        // Convert enabled built-in rules directly to filter rules (no string round-trip).
        val builtInFilterRules = config.rules
            .filter { it.isEnabled }
            .map { it.toFilterRule() }

        val customFilterRules = ViewBlockerRuleParser.parseRules(config.customRules)

        val allRules = (builtInFilterRules + customFilterRules).map { it.copy(enabled = true) }

        // Group rules by package, then categorise by matching strategy so the hot path in
        // collectOverlayActions can pick the most efficient lookup method per rule type.
        val newMap = HashMap<String, PackageRules>(allRules.size)
        for ((pkg, rules) in allRules.groupBy { it.packageName }) {
            val pathRules        = ArrayList<ViewBlockerFilterRule>()
            val viewIdRules      = ArrayList<ViewBlockerFilterRule>()
            val viewIdDescRules  = ArrayList<ViewBlockerFilterRule>()
            val recursiveRules   = ArrayList<ViewBlockerFilterRule>()
            val layoutRules      = ArrayList<ViewBlockerFilterRule>()

            for (rule in rules) {
                when {
                    rule.isLayoutRule              -> layoutRules.add(rule)
                    rule.parsedPath != null        -> pathRules.add(rule)
                    rule.needsViewIdWithDescLookup -> viewIdDescRules.add(rule)
                    rule.needsViewIdLookup         -> viewIdRules.add(rule)
                    rule.isRecursiveRule           -> recursiveRules.add(rule)
                }
            }
            newMap[pkg] = PackageRules(pathRules, viewIdRules, viewIdDescRules, recursiveRules, layoutRules)
        }
        rulesByPackage = newMap
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun setupReceivers() {
        val filter = IntentFilter().apply {
            addAction(INTENT_ACTION_REFRESH_VIEW_BLOCKER)
            addAction(Intent.ACTION_LOCALE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            service.registerReceiver(refreshReceiver, filter, RECEIVER_EXPORTED)
        } else {
            service.registerReceiver(refreshReceiver, filter)
        }
    }

    fun removeReceivers() {
        service.unregisterReceiver(refreshReceiver)
        settingsJob?.cancel()
        elementPicker?.hide()
        handler.post { forceClearAllOverlays() }
    }

    fun setupElementPicker() {
        elementPicker = ElementPicker(service, windowManager, object : ElementPicker.Listener {
            override fun onRuleChosen(ruleString: String) {
                CoroutineScope(Dispatchers.IO).launch {
                    val updated = config.customRules.toMutableList()
                    updated.add(ruleString)
                    service.dataStoreManager.updateViewBlockerConfig(
                        config.copy(customRules = updated)
                    )
                }
            }

            override fun onRuleUndone(ruleString: String) {
                CoroutineScope(Dispatchers.IO).launch {
                    val updated = config.customRules.toMutableList()
                    updated.remove(ruleString)
                    service.dataStoreManager.updateViewBlockerConfig(
                        config.copy(customRules = updated)
                    )
                }
            }

            override fun onPickerDismissed() {
                service.sendBroadcast(
                    Intent(ElementPickerNotification.ACTION_STOP_PICKER).apply {
                        setPackage(service.packageName)
                    }
                )
            }
        })
    }

    fun doViewBlockerCheck(event: AccessibilityEvent?) {
        if (event == null || (event.eventType and TARGET_EVENTS_MASK) == 0 || event.packageName == "com.android.systemui") return
        if (!config.isActive) return
        if (elementPicker?.isActive == true) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName == service.packageName) return

        val pkgRules = rulesByPackage[packageName]
        if (pkgRules == null || pkgRules.isEmpty) {
            if (lastPackage != packageName && blockedElements.isNotEmpty()) {
                handler.post { forceClearAllOverlays() }
            }
            lastPackage = packageName
            return
        }

        lastPackage = packageName
        processEvent()
    }

    private fun processEvent() {
        try {
            val root = service.rootInActiveWindow ?: return
            try {
                val rootPkg = root.packageName?.toString() ?: return
                val rootRules = rulesByPackage[rootPkg]
                if (rootRules == null || rootRules.isEmpty) {
                    handler.post { forceClearAllOverlays() }
                    return
                }

                val actions = ArrayList<OverlayAction>(16)
                val needsBack = collectOverlayActions(root, rootRules, actions)

                if (needsBack) {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastBackTime > BACK_COOLDOWN_MS) {
                        lastBackTime = now
                        handler.post { service.pressBack() }
                    }
                }
                applyOverlayActionsBatch(actions)
            } finally {
                @Suppress("DEPRECATION")
                root.recycle()
            }
        } catch (e: Exception) {
            Log.e("ViewBlocker", "Error processing event", e)
        }
    }

    private fun collectOverlayActions(
        root: AccessibilityNodeInfo,
        pkgRules: PackageRules,
        actions: MutableList<OverlayAction>
    ): Boolean {
        if (!root.isVisibleToUser) return false

        var triggerBack = false

        for (rule in pkgRules.layoutRules) {
            if (!passesRequireAbsent(root, rule)) continue
            if (!passesRequirePresent(root, rule)) continue
            processLayoutRule(root, rule, actions)
        }

        for (rule in pkgRules.pathRules) {
            if (!passesRequireAbsent(root, rule)) continue
            if (!passesRequirePresent(root, rule)) continue
            val parsed = rule.parsedPath ?: continue
            val targets = matchPathsParsed(root, parsed)
            val ruleHitCount = countHitsForRule(rule, actions)
            for ((index, target) in targets.withIndex()) {
                try {
                    if (rule.maxPerScreen > 0 && ruleHitCount + index >= rule.maxPerScreen) break
                    if (!passesNodeFilters(target, rule)) continue
                    if (!passesChildMatch(target, rule)) continue
                    if (rule.action == RuleAction.BACK) { triggerBack = true; continue }
                    collectTargetActions(target, rule, index, actions)
                } finally {
                    if (target != root) {
                        @Suppress("DEPRECATION")
                        target.recycle()
                    }
                }
            }
        }

        for (rule in pkgRules.viewIdRules) {
            if (!passesRequireAbsent(root, rule)) continue
            if (!passesRequirePresent(root, rule)) continue
            val matches = root.findAccessibilityNodeInfosByViewId(rule.targetViewId!!)
            if (matches != null) {
                var hitCount = 0
                for (match in matches) {
                    try {
                        if (!match.isVisibleToUser) continue
                        if (!passesNodeFilters(match, rule)) continue
                        if (!passesChildMatch(match, rule)) continue
                        if (rule.maxPerScreen > 0 && hitCount >= rule.maxPerScreen) break
                        if (rule.action == RuleAction.BACK) { triggerBack = true; continue }
                        collectTargetActions(match, rule, -1, actions)
                        hitCount++
                    } finally {
                        @Suppress("DEPRECATION")
                        match.recycle()
                    }
                }
            }
        }

        for (rule in pkgRules.viewIdDescRules) {
            if (!passesRequireAbsent(root, rule)) continue
            if (!passesRequirePresent(root, rule)) continue
            val matches = root.findAccessibilityNodeInfosByViewId(rule.targetViewId!!)
            if (matches != null) {
                for (match in matches) {
                    try {
                        if (!match.isVisibleToUser) continue
                        if (!passesChildMatch(match, rule)) continue
                        if (rule.action == RuleAction.BACK) { triggerBack = true; continue }
                        collectViewIdDescActions(match, rule, actions)
                    } finally {
                        @Suppress("DEPRECATION")
                        match.recycle()
                    }
                }
            }
        }

        if (pkgRules.recursiveRules.isNotEmpty()) {
            if (collectRecursiveActions(root, pkgRules.recursiveRules, actions)) {
                triggerBack = true
            }
        }

        return triggerBack
    }

    // ── Require-absent condition ──

    private fun passesRequireAbsent(root: AccessibilityNodeInfo, rule: ViewBlockerFilterRule): Boolean {
        if (rule.requireAbsent.isEmpty()) return true
        for (matcher in rule.requireAbsent) {
            if (findNodeByMatcher(root, matcher, rule.packageName)) {
                return false
            }
        }
        return true
    }

    private fun passesRequirePresent(root: AccessibilityNodeInfo, rule: ViewBlockerFilterRule): Boolean {
        if (rule.requirePresent.isEmpty()) return true
        for (matcher in rule.requirePresent) {
            if (!findNodeByMatcher(root, matcher, rule.packageName)) {
                return false
            }
        }
        return true
    }

    private fun doesNodeMatch(node: AccessibilityNodeInfo, matcher: NodeMatcher, packageName: String): Boolean {
        return matcher.criteria.all { (type, value) ->
            when (type) {
                MatchType.VIEW_ID -> node.viewIdResourceName == value
                MatchType.TEXT -> node.text?.toString() == value
                MatchType.DESC -> node.contentDescription?.toString() == value
                MatchType.CLASS_NAME -> node.className?.toString() == value
                MatchType.TEXT_CONTAINS -> node.text?.toString()?.contains(value, ignoreCase = true) == true
                MatchType.DESC_CONTAINS -> node.contentDescription?.toString()?.contains(value, ignoreCase = true) == true
                MatchType.DESC_RES -> {
                    val actualDesc = node.contentDescription?.toString() ?: return@all false
                    getAppString(packageName, value) == actualDesc
                }
                MatchType.IS_SELECTED -> node.isSelected == value.toBoolean()
                MatchType.IS_CHECKED -> node.isChecked == value.toBoolean()
                MatchType.IS_FOCUSED -> node.isFocused == value.toBoolean()
                MatchType.IS_ENABLED -> node.isEnabled == value.toBoolean()
                MatchType.IS_CLICKABLE -> node.isClickable == value.toBoolean()
                MatchType.PATH -> true
            }
        }
    }

    fun findNodeByMatcher(root: AccessibilityNodeInfo, matcher: NodeMatcher, packageName: String): Boolean {
        if (matcher.criteria.isEmpty()) return false
        val firstCriterion = matcher.criteria.first()

        val foundNodes = mutableListOf<AccessibilityNodeInfo>()

        when (firstCriterion.first) {
            MatchType.VIEW_ID -> {
                root.findAccessibilityNodeInfosByViewId(firstCriterion.second)?.let { foundNodes.addAll(it) }
            }
            MatchType.TEXT, MatchType.DESC, MatchType.TEXT_CONTAINS, MatchType.DESC_CONTAINS -> {
                root.findAccessibilityNodeInfosByText(firstCriterion.second)?.let { foundNodes.addAll(it) }
            }
            MatchType.DESC_RES -> {
                val resolved = getAppString(packageName, firstCriterion.second)
                if (resolved != null) {
                    root.findAccessibilityNodeInfosByText(resolved)?.let { foundNodes.addAll(it) }
                }
            }
            MatchType.PATH -> {
                val segments = ViewBlockerRuleParser.parsePath(firstCriterion.second)
                foundNodes.addAll(matchPathsParsed(root, segments))
            }
            MatchType.CLASS_NAME, MatchType.IS_SELECTED, MatchType.IS_CHECKED, MatchType.IS_FOCUSED, MatchType.IS_ENABLED, MatchType.IS_CLICKABLE -> {
                return findNodeRecursive(root) { doesNodeMatch(it, matcher, packageName) }
            }
        }

        if (foundNodes.isEmpty()) return false

        var exists = false
        for (node in foundNodes) {
            try {
                if (!exists && doesNodeMatch(node, matcher, packageName)) {
                    exists = true
                }
            } finally {
                @Suppress("DEPRECATION")
                node.recycle()
            }
        }
        return exists
    }
    private fun findNodeRecursive(node: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): Boolean {
        if (predicate(node)) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                if (findNodeRecursive(child, predicate)) return true
            } finally {
                @Suppress("DEPRECATION")
                child.recycle()
            }
        }
        return false
    }

    // Cache of resolved app strings (package:resName → resolved string value).
    // Used by the DESC_RES MatchType to look up a string resource from another app by name.
    private val appStringCache = HashMap<String, String>()

    /** Resolve a string resource name from a foreign app's package, with in-memory caching. */
    private fun getAppString(packageName: String, resName: String): String? {
        val key = "$packageName:$resName"
        appStringCache[key]?.let { return it }
        try {
            val resources = service.packageManager.getResourcesForApplication(packageName)
            val resId = resources.getIdentifier(resName, "string", packageName)
            if (resId != 0) {
                val str = resources.getString(resId)
                appStringCache[key] = str
                return str
            }
        } catch (_: Exception) {}
        return null
    }

    // ── Node-level filters ──


    private fun passesNodeFilters(node: AccessibilityNodeInfo, rule: ViewBlockerFilterRule): Boolean {
        rule.clickableFilter?.let { required ->
            if (node.isClickable != required) return false
        }
        rule.textContains?.let { needle ->
            val text = node.text?.toString() ?: return false
            if (!text.contains(needle, ignoreCase = true)) return false
        }
        rule.descContains?.let { needle ->
            val desc = node.contentDescription?.toString() ?: return false
            if (!desc.contains(needle, ignoreCase = true)) return false
        }
        rule.textRegex?.let { regex ->
            val text = node.text?.toString() ?: return false
            if (!regex.containsMatchIn(text)) return false
        }
        rule.descRegex?.let { regex ->
            val desc = node.contentDescription?.toString() ?: return false
            if (!regex.containsMatchIn(desc)) return false
        }
        return true
    }

    // ── Child subtree matching ──

    private fun passesChildMatch(node: AccessibilityNodeInfo, rule: ViewBlockerFilterRule): Boolean {
        if (rule.matchChildren.isEmpty()) return true
        return rule.matchChildren.all { matcher -> findNodeByMatcher(node, matcher, rule.packageName) }
    }

    // ── Layout blocking with exclusions ──

    private fun processLayoutRule(
        root: AccessibilityNodeInfo,
        rule: ViewBlockerFilterRule,
        actions: MutableList<OverlayAction>
    ) {
        val layoutMatcher = rule.blockLayoutMatcher ?: return
        val layoutNode = resolveMatcherToNode(root, layoutMatcher, rule.packageName) ?: return

        try {
            if (!layoutNode.isVisibleToUser) return
            if (!passesChildMatch(layoutNode, rule)) return

            val layoutBounds = Rect()
            layoutNode.getBoundsInScreen(layoutBounds)
            if (layoutBounds.isEmpty) return

            if (rule.excludeFromLayoutMatchers.isEmpty()) {
                actions.add(OverlayAction(rule.baseKey, layoutBounds, rule))
                return
            }

            val exclusionRects = mutableListOf<Rect>()
            for (exMatcher in rule.excludeFromLayoutMatchers) {
                val exNode = resolveMatcherToNode(root, exMatcher, rule.packageName) ?: continue
                try {
                    if (exNode.isVisibleToUser) {
                        val exBounds = Rect()
                        exNode.getBoundsInScreen(exBounds)
                        if (!exBounds.isEmpty && layoutBounds.contains(exBounds)) {
                            exclusionRects.add(exBounds)
                        }
                    }
                } finally {
                    if (exNode != root && exNode != layoutNode) {
                        @Suppress("DEPRECATION") exNode.recycle()
                    }
                }
            }

            if (exclusionRects.isEmpty()) {
                actions.add(OverlayAction(rule.baseKey, layoutBounds, rule))
                return
            }

            val slices = sliceAroundExclusions(layoutBounds, exclusionRects)
            for ((i, slice) in slices.withIndex()) {
                actions.add(OverlayAction("${rule.baseKey}::layout_$i", slice, rule))
            }
        } finally {
            if (layoutNode != root) {
                @Suppress("DEPRECATION") layoutNode.recycle()
            }
        }
    }

    fun resolveMatcherToNode(root: AccessibilityNodeInfo, matcher: NodeMatcher, packageName: String): AccessibilityNodeInfo? {
        val viewIdCriterion = matcher.criteria.firstOrNull { it.first == MatchType.VIEW_ID }
        if (viewIdCriterion != null) {
            val found = root.findAccessibilityNodeInfosByViewId(viewIdCriterion.second)
            return found?.firstOrNull { doesNodeMatch(it, matcher, packageName) }
        }
        return findFirstNodeRecursive(root) { doesNodeMatch(it, matcher, packageName) }
    }
    @Suppress("DEPRECATION")
    private fun findFirstNodeRecursive(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (predicate(node)) return AccessibilityNodeInfo.obtain(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findFirstNodeRecursive(child, predicate)
            child.recycle()
            if (result != null) return result
        }
        return null
    }

    private fun sliceAroundExclusions(layout: Rect, exclusions: List<Rect>): List<Rect> {
        val sortedExclusions = exclusions.sortedBy { it.top }
        val result = mutableListOf<Rect>()
        var coverTop = layout.top

        for (ex in sortedExclusions) {
            if (ex.top > coverTop) {
                result.add(Rect(layout.left, coverTop, layout.right, ex.top))
            }

            if (ex.left > layout.left) {
                result.add(Rect(layout.left, maxOf(coverTop, ex.top), ex.left, ex.bottom))
            }
            if (ex.right < layout.right) {
                result.add(Rect(ex.right, maxOf(coverTop, ex.top), layout.right, ex.bottom))
            }

            coverTop = maxOf(coverTop, ex.bottom)
        }

        if (coverTop < layout.bottom) {
            result.add(Rect(layout.left, coverTop, layout.right, layout.bottom))
        }

        return result.filter { !it.isEmpty }
    }

    // ── Recursive node scan ──

    private fun collectRecursiveActions(
        node: AccessibilityNodeInfo,
        rules: List<ViewBlockerFilterRule>,
        actions: MutableList<OverlayAction>
    ): Boolean {
        if (!node.isVisibleToUser) return false
        var triggerBack = false

        for (rule in rules) {
            if (isTargetView(node, rule) && passesNodeFilters(node, rule) && passesChildMatch(node, rule)) {
                if (rule.maxPerScreen > 0 && countHitsForRule(rule, actions) >= rule.maxPerScreen) continue
                if (rule.action == RuleAction.BACK) { triggerBack = true; continue }
                addBoundsAction(node, rule, -1, actions)
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                if (collectRecursiveActions(child, rules, actions)) {
                    triggerBack = true
                }
            } finally {
                @Suppress("DEPRECATION")
                child.recycle()
            }
        }
        return triggerBack
    }

    private fun isTargetView(node: AccessibilityNodeInfo, rule: ViewBlockerFilterRule): Boolean {
        if (rule.contentDescriptions.isNotEmpty()) {
            val desc = node.contentDescription
            if (desc != null && rule.contentDescriptions.contains(desc.toString())) {
                return true
            }
        }

        if (!rule.targetClassName.isNullOrEmpty()) {
            val className = node.className
            if (className == null || className.toString() != rule.targetClassName) return false
            if (!rule.targetText.isNullOrEmpty()) {
                val text = node.text
                return text != null && text.toString() == rule.targetText
            }
            return true
        }

        if (!rule.targetText.isNullOrEmpty()) {
            val text = node.text
            return text != null && text.toString() == rule.targetText
        }

        if (rule.textContains != null || rule.descContains != null ||
            rule.textRegex != null || rule.descRegex != null) {
            return true
        }

        return false
    }

    private fun matchPathsParsed(root: AccessibilityNodeInfo, segments: List<PathSegment>): List<AccessibilityNodeInfo> {
        if (segments.isEmpty()) return emptyList()

        var currentNodes = mutableListOf(root)

        for (seg in segments) {
            val nextNodes = mutableListOf<AccessibilityNodeInfo>()

            for (current in currentNodes) {
                if (seg.isWildcard) {
                    for (i in 0 until current.childCount) {
                        val child = current.getChild(i) ?: continue
                        val childClass = child.className
                        if (childClass != null && childClass.toString() == seg.className) {
                            nextNodes.add(child)
                        } else {
                            @Suppress("DEPRECATION")
                            child.recycle()
                        }
                    }
                } else {
                    var match: AccessibilityNodeInfo? = null
                    var matchCount = 0
                    for (i in 0 until current.childCount) {
                        val child = current.getChild(i) ?: continue
                        val childClass = child.className
                        if (childClass != null && childClass.toString() == seg.className) {
                            if (matchCount == seg.index) {
                                match = child
                                break
                            }
                            matchCount++
                            @Suppress("DEPRECATION")
                            child.recycle()
                        } else {
                            @Suppress("DEPRECATION")
                            child.recycle()
                        }
                    }
                    if (match != null) nextNodes.add(match)
                }

                if (current != root) {
                    @Suppress("DEPRECATION")
                    current.recycle()
                }
            }

            currentNodes = nextNodes
            if (currentNodes.isEmpty()) return emptyList()
        }

        return currentNodes
    }

    private fun collectViewIdDescActions(
        node: AccessibilityNodeInfo,
        rule: ViewBlockerFilterRule,
        actions: MutableList<OverlayAction>
    ) {
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                if (subtreeContainsContentDescription(child, rule.contentDescriptions)) {
                    addBoundsAction(child, rule, -1, actions)
                }
            } finally {
                @Suppress("DEPRECATION")
                child.recycle()
            }
        }
    }

    private fun collectTargetActions(
        node: AccessibilityNodeInfo,
        rule: ViewBlockerFilterRule,
        matchIndex: Int,
        actions: MutableList<OverlayAction>
    ) {
        addBoundsAction(node, rule, matchIndex, actions)
    }

    private fun addBoundsAction(
        node: AccessibilityNodeInfo,
        rule: ViewBlockerFilterRule,
        matchIndex: Int,
        actions: MutableList<OverlayAction>
    ) {
        node.getBoundsInScreen(boundsRect)
        if (!boundsRect.isEmpty) {
            val key = if (matchIndex >= 0) "${rule.baseKey}::$matchIndex" else rule.baseKey
            actions.add(OverlayAction(key, Rect(boundsRect), rule))
        }
    }

    private fun subtreeContainsContentDescription(node: AccessibilityNodeInfo, targets: Set<String>): Boolean {
        val desc = node.contentDescription
        if (desc != null && targets.contains(desc.toString())) return true

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                if (subtreeContainsContentDescription(child, targets)) return true
            } finally {
                @Suppress("DEPRECATION")
                child.recycle()
            }
        }
        return false
    }

    private fun countHitsForRule(rule: ViewBlockerFilterRule, actions: List<OverlayAction>): Int {
        var count = 0
        for (a in actions) {
            if (a.rule.baseKey == rule.baseKey) count++
        }
        return count
    }

    // ── Overlay application ──

    private fun applyOverlayActionsBatch(actions: List<OverlayAction>) {
        handler.post {
            try {
                activeRuleKeys.clear()
                for (action in actions) {
                    activeRuleKeys.add(action.key)
                }

                for (action in actions) {
                    applyOverlay(action.key, action.bounds, action.rule)
                }

                val iter = blockedElements.iterator()
                while (iter.hasNext()) {
                    val entry = iter.next()
                    if (entry.key !in activeRuleKeys) {
                        try { windowManager.removeView(entry.value.view) } catch (_: Exception) {}
                        iter.remove()
                    }
                }
            } catch (e: Exception) {
                Log.e("ViewBlocker", "Error applying overlays", e)
            }
        }
    }

    private fun applyOverlay(ruleKey: String, area: Rect, rule: ViewBlockerFilterRule) {
        val existing = blockedElements[ruleKey]
        if (existing != null) {
            if (existing.bounds == area) return
            try {
                val lp = createLayoutParams(area, rule.blockTouches)
                windowManager.updateViewLayout(existing.view, lp)
                existing.bounds.set(area)
            } catch (e: Exception) {
                Log.e("ViewBlocker", "Failed to update overlay", e)
            }
            return
        }

        if (blockedElements.size >= MAX_OVERLAY_COUNT) {
            forceClearAllOverlays()
        }

        try {
            val blocker = View(service)
            var overlayColor = rule.color
            if (overlayColor == Color.WHITE && isDarkMode && !rule.ruleString.contains("color=")) {
                overlayColor = Color.BLACK
            }
            blocker.setBackgroundColor(overlayColor)

            val lp = createLayoutParams(area, rule.blockTouches)
            windowManager.addView(blocker, lp)
            blockedElements[ruleKey] = BlockedElement(blocker, Rect(area))
        } catch (e: Exception) {
            Log.e("ViewBlocker", "Failed to add overlay", e)
        }
    }

    private fun createLayoutParams(bounds: Rect, blockTouches: Boolean): WindowManager.LayoutParams {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (!blockTouches) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        return WindowManager.LayoutParams(
            bounds.width(), bounds.height(), bounds.left, bounds.top,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    private fun forceClearAllOverlays() {
        for ((_, element) in blockedElements) {
            try {
                if (element.view.parent != null) {
                    windowManager.removeView(element.view)
                }
            } catch (_: Exception) {}
        }
        blockedElements.clear()
    }

    private fun updateDarkMode() {
        isDarkMode = (service.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == INTENT_ACTION_REFRESH_VIEW_BLOCKER) {
                forceClearAllOverlays()
                setupBlocker(service)
            }
        }
    }
}
