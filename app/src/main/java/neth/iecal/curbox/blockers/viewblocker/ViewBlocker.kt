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
import kotlin.collections.iterator

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
            return sb.toString()        }
    }

    private lateinit var service: BaseBlockingService
    private lateinit var windowManager: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var config = ViewBlockerConfig()
    private var parsedRules = listOf<ViewBlockerFilterRule>()
    private var settingsJob: Job? = null
    private var isDarkMode = false

    private val blockedElements = HashMap<String, BlockedElement>()
    private val activeRuleKeys = mutableSetOf<String>()
    private var lastPackage = ""

    var elementPicker: ElementPicker? = null
        private set

    private data class BlockedElement(val view: View, val bounds: Rect)

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
            parsedRules = emptyList()
            handler.post { forceClearAllOverlays() }
            return
        }

        val ruleStrings = mutableListOf<String>()

        config.rules.filter { it.isEnabled }.forEach { rule ->
            ruleStrings.add(defaultRuleToRuleString(rule))
        }

        ruleStrings.addAll(config.customRules)

        parsedRules = ViewBlockerRuleParser.parseRules(ruleStrings).map {
            it.copy(enabled = true)
        }
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
        if (!config.isActive || parsedRules.isEmpty()) return
        if (elementPicker?.isActive == true) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName == service.packageName || packageName == "com.android.systemui") return

        if (!hasMatchingRule(packageName)) {
            if (lastPackage != packageName && blockedElements.isNotEmpty()) {
                handler.post { forceClearAllOverlays() }
            }
            lastPackage = packageName
            return
        }

        lastPackage = packageName
        handler.post { processEvent() }
    }

    private fun hasMatchingRule(packageName: String): Boolean {
        return parsedRules.any { it.enabled && it.matchesPackage(packageName) }
    }

    private fun processEvent() {
        try {
            val root = service.rootInActiveWindow ?: return
            try {
                val rootPkg = root.packageName ?: return
                if (!hasMatchingRule(rootPkg.toString())) {
                    forceClearAllOverlays()
                    return
                }

                activeRuleKeys.clear()
                processRootNode(root)

                val toRemove = blockedElements.keys.filter { it !in activeRuleKeys }
                for (key in toRemove) {
                    val element = blockedElements.remove(key)
                    if (element != null) {
                        try { windowManager.removeView(element.view) } catch (_: Exception) {}
                    }
                }
            } finally {
                @Suppress("DEPRECATION")
                root.recycle()
            }
        } catch (e: Exception) {
            Log.e("ViewBlocker", "Error processing event", e)
        }
    }

    private fun processRootNode(root: AccessibilityNodeInfo) {
        val packageName = root.packageName ?: return
        for (rule in parsedRules) {
            if (rule.enabled && rule.matchesPackage(packageName)) {
                applyRule(rule, root)
            }
        }
    }

    private fun applyRule(rule: ViewBlockerFilterRule, root: AccessibilityNodeInfo) {
        if (!root.isVisibleToUser) return

        if (!rule.targetPath.isNullOrEmpty()) {
            val targets = matchPaths(root, rule.targetPath)
            for ((index, target) in targets.withIndex()) {
                try {
                    processTargetView(target, rule, index)
                } finally {
                    if (target != root) {
                        @Suppress("DEPRECATION")
                        target.recycle()
                    }
                }
            }
            return
        }

        if (!rule.targetViewId.isNullOrEmpty()) {
            val matches = root.findAccessibilityNodeInfosByViewId(rule.targetViewId)
            if (matches != null) {
                for (match in matches) {
                    try {
                        if (match.isVisibleToUser) {
                            processTargetView(match, rule)
                        }
                    } finally {
                        @Suppress("DEPRECATION")
                        match.recycle()
                    }
                }
            }
            return
        }

        applyRuleRecursive(rule, root)
    }

    private fun applyRuleRecursive(rule: ViewBlockerFilterRule, node: AccessibilityNodeInfo) {
        if (!node.isVisibleToUser) return

        if (isTargetView(node, rule)) {
            processTargetView(node, rule)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                applyRuleRecursive(rule, child)
            } finally {
                @Suppress("DEPRECATION")
                child.recycle()
            }
        }
    }

    private fun isTargetView(node: AccessibilityNodeInfo, rule: ViewBlockerFilterRule): Boolean {
        if (!rule.targetViewId.isNullOrEmpty()) {
            val viewId = node.viewIdResourceName
            return viewId != null && viewId == rule.targetViewId
        }

        if (!rule.targetPath.isNullOrEmpty()) return false

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

    private fun matchPaths(root: AccessibilityNodeInfo, path: String): List<AccessibilityNodeInfo> {
        val empty = emptyList<AccessibilityNodeInfo>()
        if (path.isEmpty()) return empty

        val segments = path.split(">")
        var currentNodes = mutableListOf(root)

        for (segment in segments) {
            val bracketStart = segment.indexOf('[')
            val className: String
            var isWildcard = false
            var index = 0

            if (bracketStart >= 0) {
                className = segment.substring(0, bracketStart)
                val indexStr = segment.substring(bracketStart + 1, segment.indexOf(']'))
                if (indexStr == "*") {
                    isWildcard = true
                } else {
                    index = indexStr.toIntOrNull() ?: return empty
                }
            } else {
                className = segment
            }

            val nextNodes = mutableListOf<AccessibilityNodeInfo>()

            for (current in currentNodes) {
                if (isWildcard) {
                    for (i in 0 until current.childCount) {
                        val child = current.getChild(i) ?: continue
                        val childClass = child.className
                        if (childClass != null && childClass.toString() == className) {
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
                        if (childClass != null && childClass.toString() == className) {
                            if (matchCount == index) {
                                match = child
                                break
                            }
                            matchCount++
                        }
                        @Suppress("DEPRECATION")
                        child.recycle()
                    }
                    if (match != null) nextNodes.add(match)
                }

                if (current != root) {
                    @Suppress("DEPRECATION")
                    current.recycle()
                }
            }

            currentNodes = nextNodes
            if (currentNodes.isEmpty()) return empty
        }

        return currentNodes
    }

    private fun processTargetView(node: AccessibilityNodeInfo, rule: ViewBlockerFilterRule, matchIndex: Int = -1) {
        if (!rule.targetViewId.isNullOrEmpty() && rule.contentDescriptions.isNotEmpty()) {
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                try {
                    if (subtreeContainsContentDescription(child, rule.contentDescriptions)) {
                        val bounds = Rect()
                        child.getBoundsInScreen(bounds)
                        if (!bounds.isEmpty) {
                            addOverlay(bounds, rule, matchIndex)
                        }
                    }
                } finally {
                    @Suppress("DEPRECATION")
                    child.recycle()
                }
            }
            return
        }

        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (!bounds.isEmpty) {
            addOverlay(bounds, rule, matchIndex)
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

    private fun getRuleKey(rule: ViewBlockerFilterRule, matchIndex: Int): String {
        val base = "${rule.packageName}::${rule.ruleString}"
        return if (matchIndex >= 0) "$base::$matchIndex" else base
    }

    private fun addOverlay(area: Rect, rule: ViewBlockerFilterRule, matchIndex: Int = -1) {
        val ruleKey = getRuleKey(rule, matchIndex)
        activeRuleKeys.add(ruleKey)

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
