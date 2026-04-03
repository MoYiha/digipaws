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

        private const val TARGET_EVENTS_MASK =
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
            AccessibilityEvent.TYPE_VIEW_SCROLLED

        val DEFAULT_RULES = listOf(
            ViewBlockerRule("ig_stories_tray", "com.instagram.android", "Hide Stories", desc = "reels tray container"),
            ViewBlockerRule("ig_reels_button", "com.instagram.android", "Hide Reels button", viewId = "com.instagram.android:id/clips_tab"),
            ViewBlockerRule("ig_search_suggestions", "com.instagram.android", "Hide search suggestions", path = "androidx.viewpager.widget.ViewPager[0]>android.widget.FrameLayout[3]>androidx.recyclerview.widget.RecyclerView[0]>android.widget.FrameLayout[*]"),
            ViewBlockerRule("ig_feed_1", "com.instagram.android", "Hide feed 1/2", path = "androidx.viewpager.widget.ViewPager[0]>android.widget.FrameLayout[0]>androidx.recyclerview.widget.RecyclerView[0]>android.view.ViewGroup[*]"),
            ViewBlockerRule("ig_feed_2", "com.instagram.android", "Hide feed 2/2", path = "androidx.viewpager.widget.ViewPager[0]>android.widget.FrameLayout[0]>androidx.recyclerview.widget.RecyclerView[0]>android.widget.FrameLayout[*]"),
            ViewBlockerRule("ig_main_screen", "com.instagram.android", "Hide main screen", viewId = "android:id/list"),
            ViewBlockerRule("li_feed_item", "com.linkedin.android", "Hide feed item", viewId = "com.linkedin.android:id/feed_item_update_card"),
            ViewBlockerRule("li_notifications", "com.linkedin.android", "Hide notifications", viewId = "com.linkedin.android:id/tab_notifications"),
            ViewBlockerRule("wa_ai_search", "com.whatsapp", "Hide AI suggestions in search", viewId = "com.whatsapp:id/search_meta_ai_input_send_button"),
            ViewBlockerRule("wa_ai_button", "com.whatsapp", "Hide AI button", viewId = "com.whatsapp:id/extended_mini_fab"),
            ViewBlockerRule("wa_rec_channels", "com.whatsapp", "Hide recommended channels", viewId = "com.whatsapp:id/newsletter_directory_row_container"),
            ViewBlockerRule("wa_updates_button", "com.whatsapp", "Hide Updates button", path = "android.view.ViewGroup[0]>android.widget.FrameLayout[1]"),
            ViewBlockerRule("wa_channels_path", "com.whatsapp", "Hide channels", path = "androidx.viewpager.widget.ViewPager[0]>androidx.recyclerview.widget.RecyclerView[0]>android.widget.RelativeLayout[*]")
        )

        private fun defaultRuleToRuleString(rule: ViewBlockerRule): String {
            val sb = StringBuilder(rule.packageName)
            rule.viewId?.let { sb.append("##viewId=").append(it) }
            rule.desc?.let { sb.append("##desc=").append(it) }
            rule.path?.let { sb.append("##path=").append(it) }
            rule.className?.let { sb.append("##className=").append(it) }
            rule.text?.let { sb.append("##text=").append(it) }
            if (rule.color != Color.WHITE) {
                sb.append("##color=#").append(Integer.toHexString(rule.color))
            }
            if (!rule.blockTouches) {
                sb.append("##blockTouches=false")
            }
            sb.append("##comment=").append(rule.label)
            return sb.toString()
        }
    }

    private lateinit var service: BaseBlockingService
    private lateinit var windowManager: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var config = ViewBlockerConfig()
    private var settingsJob: Job? = null
    private var isDarkMode = false

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
        val recursiveRules: List<ViewBlockerFilterRule>
    ) {
        val isEmpty get() = pathRules.isEmpty() && viewIdRules.isEmpty() &&
                viewIdDescRules.isEmpty() && recursiveRules.isEmpty()
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

        val ruleStrings = mutableListOf<String>()
        config.rules.filter { it.isEnabled }.forEach { rule ->
            ruleStrings.add(defaultRuleToRuleString(rule))
        }
        ruleStrings.addAll(config.customRules)

        val allRules = ViewBlockerRuleParser.parseRules(ruleStrings).map {
            it.copy(enabled = true)
        }

        val newMap = HashMap<String, PackageRules>(allRules.size)
        val grouped = allRules.groupBy { it.packageName }
        for ((pkg, rules) in grouped) {
            val pathRules = ArrayList<ViewBlockerFilterRule>()
            val viewIdRules = ArrayList<ViewBlockerFilterRule>()
            val viewIdDescRules = ArrayList<ViewBlockerFilterRule>()
            val recursiveRules = ArrayList<ViewBlockerFilterRule>()

            for (rule in rules) {
                when {
                    rule.parsedPath != null -> pathRules.add(rule)
                    rule.needsViewIdWithDescLookup -> viewIdDescRules.add(rule)
                    rule.needsViewIdLookup -> viewIdRules.add(rule)
                    rule.isRecursiveRule -> recursiveRules.add(rule)
                }
            }
            newMap[pkg] = PackageRules(pathRules, viewIdRules, viewIdDescRules, recursiveRules)
        }
        rulesByPackage = newMap
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun setupReceivers() {
        val filter = IntentFilter().apply {
            addAction(INTENT_ACTION_REFRESH_VIEW_BLOCKER)
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
                collectOverlayActions(root, rootRules, actions)
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
    ) {
        if (!root.isVisibleToUser) return

        for (rule in pkgRules.pathRules) {
            val parsed = rule.parsedPath ?: continue
            val targets = matchPathsParsed(root, parsed)
            for ((index, target) in targets.withIndex()) {
                try {
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
            val matches = root.findAccessibilityNodeInfosByViewId(rule.targetViewId!!)
            if (matches != null) {
                for (match in matches) {
                    try {
                        if (match.isVisibleToUser) {
                            collectTargetActions(match, rule, -1, actions)
                        }
                    } finally {
                        @Suppress("DEPRECATION")
                        match.recycle()
                    }
                }
            }
        }

        for (rule in pkgRules.viewIdDescRules) {
            val matches = root.findAccessibilityNodeInfosByViewId(rule.targetViewId!!)
            if (matches != null) {
                for (match in matches) {
                    try {
                        if (match.isVisibleToUser) {
                            collectViewIdDescActions(match, rule, actions)
                        }
                    } finally {
                        @Suppress("DEPRECATION")
                        match.recycle()
                    }
                }
            }
        }

        if (pkgRules.recursiveRules.isNotEmpty()) {
            collectRecursiveActions(root, pkgRules.recursiveRules, actions)
        }
    }

    private fun collectRecursiveActions(
        node: AccessibilityNodeInfo,
        rules: List<ViewBlockerFilterRule>,
        actions: MutableList<OverlayAction>
    ) {
        if (!node.isVisibleToUser) return

        for (rule in rules) {
            if (isTargetView(node, rule)) {
                addBoundsAction(node, rule, -1, actions)
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                collectRecursiveActions(child, rules, actions)
            } finally {
                @Suppress("DEPRECATION")
                child.recycle()
            }
        }
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
