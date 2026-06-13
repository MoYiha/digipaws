package neth.iecal.curbox.blockers.viewblocker

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import neth.iecal.curbox.R
import neth.iecal.curbox.databinding.ElementPickerConfirmDialogBinding
import neth.iecal.curbox.databinding.ElementPickerControlBarBinding
import neth.iecal.curbox.databinding.ElementPickerHighlightBinding
import neth.iecal.curbox.databinding.ElementPickerTouchInterceptorBinding
import neth.iecal.curbox.databinding.ElementPickerUndoBarBinding

class ElementPicker(
    private val service: AccessibilityService,
    private val windowManager: WindowManager,
    private val listener: Listener
) {
    interface Listener {
        fun onRuleChosen(ruleString: String)
        fun onRuleUndone(ruleString: String)
        fun onPickerDismissed()
    }

    private val ui = Handler(Looper.getMainLooper())

    private var touchInterceptorBinding: ElementPickerTouchInterceptorBinding? = null
    private val touchInterceptor: View? get() = touchInterceptorBinding?.root

    private var highlightViewBinding: ElementPickerHighlightBinding? = null
    private val highlightView: View? get() = highlightViewBinding?.root

    private var controlBarBinding: ElementPickerControlBarBinding? = null
    private val controlBar: View? get() = controlBarBinding?.root

    private val nodesAtPoint = mutableListOf<AccessibilityNodeInfo>()
    private var currentNodeIndex = 0
    private var currentPackageName = ""
    private var currentRootNode: AccessibilityNodeInfo? = null
    var isActive = false
        private set
    private var isAtBottom = true
    private var lastAppliedRule: String? = null
    private var undoBarBinding: ElementPickerUndoBarBinding? = null
    private val undoBar: View? get() = undoBarBinding?.root
    private var undoAutoHideRunnable: Runnable? = null

    fun show() {
        if (isActive) return
        isActive = true
        ui.post {
            try {
                createTouchInterceptor()
                createControlBar()
                createHighlightView()
            } catch (e: Exception) {
                Log.e(TAG, "Error showing picker", e)
                hide()
            }
        }
    }

    fun hide() {
        isActive = false
        ui.post {
            removeUndoBar()
            removeSafely(touchInterceptor)
            removeSafely(highlightView)
            removeSafely(controlBar)
            touchInterceptorBinding = null
            highlightViewBinding = null
            controlBarBinding = null
            lastAppliedRule = null
            recycleNodes()
        }
    }

    private fun recycleNodes() {
        for (node in nodesAtPoint) {
            try { @Suppress("DEPRECATION") node.recycle() } catch (_: Exception) {}
        }
        nodesAtPoint.clear()
        currentNodeIndex = 0
        currentRootNode?.let {
            try { @Suppress("DEPRECATION") it.recycle() } catch (_: Exception) {}
        }
        currentRootNode = null
    }

    private fun removeSafely(view: View?) {
        if (view != null) {
            try {
                if (view.parent != null) windowManager.removeView(view)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing view", e)
            }
        }
    }

    private fun createTouchInterceptor() {
        touchInterceptorBinding = ElementPickerTouchInterceptorBinding.inflate(LayoutInflater.from(service))

        touchInterceptorBinding!!.root.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                handleTap(event.rawX, event.rawY)
            }
            true
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        windowManager.addView(touchInterceptorBinding!!.root, params)
    }

    private fun createHighlightView() {
        highlightViewBinding = ElementPickerHighlightBinding.inflate(LayoutInflater.from(service))

        val params = WindowManager.LayoutParams(
            0, 0, 0, 0,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        windowManager.addView(highlightViewBinding!!.root, params)
    }

    private fun createControlBar() {
        controlBarBinding = ElementPickerControlBarBinding.inflate(LayoutInflater.from(service))
        controlBarBinding!!.deeperBtn.setOnClickListener { cycleDeeper() }
        controlBarBinding!!.shallowerBtn.setOnClickListener { cycleShallower() }
        controlBarBinding!!.blockBtn.setOnClickListener { confirmBlock() }
        controlBarBinding!!.blockAllBtn.setOnClickListener { confirmBlockAll() }
        controlBarBinding!!.moveBtn.setOnClickListener { toggleControlBarPosition() }
        controlBarBinding!!.cancelBtn.setOnClickListener { dismissPicker() }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            0, 0,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = (if (isAtBottom) Gravity.BOTTOM else Gravity.TOP) or Gravity.START
        }

        windowManager.addView(controlBar, params)
    }

    private fun dismissPicker() {
        hide()
        listener.onPickerDismissed()
    }

    private fun toggleControlBarPosition() {
        controlBar?.let {
            isAtBottom = !isAtBottom
            val params = it.layoutParams as WindowManager.LayoutParams
            params.gravity = (if (isAtBottom) Gravity.BOTTOM else Gravity.TOP) or Gravity.START
            windowManager.updateViewLayout(it, params)
        }
    }

    private fun handleTap(x: Float, y: Float) {
        val root = service.rootInActiveWindow ?: return
        try {
            currentPackageName = root.packageName?.toString() ?: ""
            recycleNodes()
            @Suppress("DEPRECATION")
            currentRootNode = AccessibilityNodeInfo.obtain(root)
            collectNodesAtPoint(root, x.toInt(), y.toInt(), nodesAtPoint)

            if (nodesAtPoint.isEmpty()) {
                updateInfo(service.getString(R.string.picker_no_element_found))
                hideHighlight()
                return
            }

            currentNodeIndex = nodesAtPoint.size - 1
            highlightCurrentNode()
        } catch (e: Exception) {
            Log.e(TAG, "Error handling tap", e)
        } finally {
            @Suppress("DEPRECATION")
            root.recycle()
        }
    }

    private fun collectNodesAtPoint(node: AccessibilityNodeInfo, x: Int, y: Int, result: MutableList<AccessibilityNodeInfo>) {
        if (!node.isVisibleToUser) return
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        if (bounds.contains(x, y)) {
            @Suppress("DEPRECATION")
            result.add(AccessibilityNodeInfo.obtain(node))
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                collectNodesAtPoint(child, x, y, result)
            } finally {
                @Suppress("DEPRECATION")
                child.recycle()
            }
        }
    }

    private fun cycleDeeper() {
        if (nodesAtPoint.isEmpty()) return
        if (currentNodeIndex < nodesAtPoint.size - 1) {
            currentNodeIndex++
            highlightCurrentNode()
        } else {
            Toast.makeText(service, "Already at deepest level", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cycleShallower() {
        if (nodesAtPoint.isEmpty()) return
        if (currentNodeIndex > 0) {
            currentNodeIndex--
            highlightCurrentNode()
        } else {
            Toast.makeText(service, "Already at shallowest level", Toast.LENGTH_SHORT).show()
        }
    }

    private fun highlightCurrentNode() {
        if (nodesAtPoint.isEmpty() || currentNodeIndex >= nodesAtPoint.size) {
            hideHighlight()
            return
        }

        val node = nodesAtPoint[currentNodeIndex]
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        highlightView?.let { hv ->
            if (!bounds.isEmpty) {
                val params = hv.layoutParams as WindowManager.LayoutParams
                params.x = bounds.left
                params.y = bounds.top
                params.width = bounds.width()
                params.height = bounds.height()
                try {
                    windowManager.updateViewLayout(hv, params)
                    hv.visibility = View.VISIBLE
                    hv.invalidate()
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating highlight", e)
                }
            }
        }

        val description = describeNode(node)
        val depth = "(${currentNodeIndex + 1}/${nodesAtPoint.size})"
        updateInfo("$depth $description")
    }

    private fun hideHighlight() {
        highlightView?.visibility = View.GONE
    }

    private fun updateInfo(text: String) {
        controlBarBinding?.infoText?.text = text
    }

    private fun confirmBlock() {
        if (nodesAtPoint.isEmpty() || currentNodeIndex >= nodesAtPoint.size) {
            Toast.makeText(service, service.getString(R.string.picker_no_element_selected), Toast.LENGTH_SHORT).show()
            return
        }
        val node = nodesAtPoint[currentNodeIndex]
        val selectorDesc = getSelectorDescription(node, currentRootNode)
        showConfirmationOverlay(node, selectorDesc, false)
    }

    private fun confirmBlockAll() {
        if (nodesAtPoint.isEmpty() || currentNodeIndex >= nodesAtPoint.size) {
            Toast.makeText(service, service.getString(R.string.picker_no_element_selected), Toast.LENGTH_SHORT).show()
            return
        }
        val node = nodesAtPoint[currentNodeIndex]
        showConfirmationOverlay(node, "All similar elements", true)
    }

    private enum class MatchStrategy {
        AUTO,
        VIEW_ID,
        DESC,
        TEXT,
        CLASS_NAME,
        PATH
    }

    private data class NodeSnapshot(
        val viewId: String?,
        val desc: String?,
        val text: String?,
        val className: String?,
        val parentViewId: String?,
        val path: String?,
        val wildcardPath: String?
    )

    private data class RuleOptions(
        val pressBack: Boolean = false,
        val blockTouches: Boolean = true,
        val matchByText: Boolean = false,
        val matchByDesc: Boolean = false,
        val requireAbsent: Boolean = false,
        val absentViewId: String = "",
        val blockLayout: Boolean = false,
        val matchChildren: Boolean = false,
        val childrenText: String = "",
        val clickableOnly: Boolean = false,
        val matchStrategy: MatchStrategy = MatchStrategy.AUTO
    )

    private fun showConfirmationOverlay(
        node: AccessibilityNodeInfo,
        selectorDesc: String,
        isBlockAll: Boolean
    ) {
        val snapshot = NodeSnapshot(
            viewId = node.viewIdResourceName?.toString(),
            desc = node.contentDescription?.toString(),
            text = node.text?.toString(),
            className = node.className?.toString(),
            parentViewId = node.parent?.viewIdResourceName?.toString(),
            path = generatePath(node, currentRootNode),
            wildcardPath = generatePathWithWildcard(node, currentRootNode)
        )
        val dialogBinding = ElementPickerConfirmDialogBinding.inflate(LayoutInflater.from(service))
        dialogBinding.titleText.text = if (isBlockAll) "Block All Similar?" else "Block This Element"

        val infoText = StringBuilder()
        if (!snapshot.viewId.isNullOrEmpty()) infoText.append("ID: ").append(snapshot.viewId).append("\n")
        if (!snapshot.className.isNullOrEmpty()) infoText.append("Class: ").append(snapshot.className).append("\n")
        if (!snapshot.text.isNullOrEmpty()) infoText.append("Text: ").append(truncate(snapshot.text, 30)).append("\n")
        if (!snapshot.desc.isNullOrEmpty()) infoText.append("Desc: ").append(truncate(snapshot.desc, 30)).append("\n")
        
        dialogBinding.selectorInfoText.text = infoText.toString()
        dialogBinding.selectorInfoText.visibility = if (infoText.isEmpty()) View.GONE else View.VISIBLE

        var isTogglingChip = false
        fun addChip(container: android.widget.LinearLayout, label: String, appendText: String, color: Int = android.graphics.Color.parseColor("#444444")) {
            val dp = { px: Int -> (px * service.resources.displayMetrics.density).toInt() }
            val normalBg = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#2A2A2A"))
                cornerRadius = dp(16).toFloat()
            }
            val activeBg = android.graphics.drawable.GradientDrawable().apply {
                setColor(color)
                cornerRadius = dp(16).toFloat()
            }
            val btn = android.widget.Button(service).apply {
                text = label
                isAllCaps = false
                textSize = 11f
                minHeight = dp(32)
                minimumHeight = dp(32)
                setPadding(dp(12), dp(4), dp(12), dp(4))
                background = normalBg
                setTextColor(android.graphics.Color.parseColor("#CCCCCC"))
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, dp(6), 0) }
                tag = false // track toggle state
                setOnClickListener {
                    val active = !(tag as Boolean)
                    tag = active
                    val currentText = dialogBinding.ruleQueryInput.text.toString()
                    if (active) {
                        background = activeBg
                        setTextColor(android.graphics.Color.WHITE)
                        if (!currentText.contains(appendText)) {
                            dialogBinding.ruleQueryInput.append(" $appendText")
                        }
                    } else {
                        background = android.graphics.drawable.GradientDrawable().apply {
                            setColor(android.graphics.Color.parseColor("#2A2A2A"))
                            cornerRadius = dp(16).toFloat()
                        }
                        setTextColor(android.graphics.Color.parseColor("#CCCCCC"))
                        val newText = currentText.replace(" $appendText", "").replace(appendText, "")
                        dialogBinding.ruleQueryInput.setText(newText.trim())
                    }
                }
            }
            container.addView(btn)
        }

        // -- Properties from the tapped element --
        val viewIdStr = snapshot.viewId
        val textStr = snapshot.text
        val descStr = snapshot.desc
        val classStr = snapshot.className
        val pathStr = snapshot.path

        if (!viewIdStr.isNullOrEmpty()) {
            val safe = if (viewIdStr.contains(" ")) "\"$viewIdStr\"" else viewIdStr
            addChip(dialogBinding.propertyChipsContainer, "ID: ${viewIdStr.substringAfterLast("/")}", "id:$safe", android.graphics.Color.parseColor("#1565C0"))
        }
        if (!textStr.isNullOrEmpty()) {
            val safe = if (textStr.contains(" ")) "\"$textStr\"" else textStr
            addChip(dialogBinding.propertyChipsContainer, "Text: ${truncate(textStr, 20)}", "text:$safe", android.graphics.Color.parseColor("#2E7D32"))
        }
        if (!descStr.isNullOrEmpty()) {
            val safe = if (descStr.contains(" ")) "\"$descStr\"" else descStr
            addChip(dialogBinding.propertyChipsContainer, "Desc: ${truncate(descStr, 20)}", "desc:$safe", android.graphics.Color.parseColor("#4527A0"))
        }
        if (!classStr.isNullOrEmpty()) {
            val shortClass = classStr.substringAfterLast(".")
            val safe = if (classStr.contains(" ")) "\"$classStr\"" else classStr
            addChip(dialogBinding.propertyChipsContainer, "Class: $shortClass", "class:$safe", android.graphics.Color.parseColor("#37474F"))
        }
        if (!pathStr.isNullOrEmpty()) {
            val safe = if (pathStr.contains(" ")) "\"$pathStr\"" else pathStr
            addChip(dialogBinding.propertyChipsContainer, "Path", "path:$safe", android.graphics.Color.parseColor("#455A64"))
        }

        // -- Action: how the blocked element is handled --
        addChip(dialogBinding.actionChipsContainer, "← Back Press", "action:back", android.graphics.Color.parseColor("#8E24AA"))
        addChip(dialogBinding.actionChipsContainer, "■ Overlay", "action:overlay", android.graphics.Color.parseColor("#3949AB"))
        addChip(dialogBinding.actionChipsContainer, "○ Transparent", "color:#00000000 blocktouches:true", android.graphics.Color.parseColor("#00897B"))
        addChip(dialogBinding.actionChipsContainer, "☞ Allow Clicks", "blocktouches:false", android.graphics.Color.parseColor("#00838F"))

        // -- Advanced modifiers --
        addChip(dialogBinding.modifierChipsContainer, "Limit: 1 per screen", "max:1", android.graphics.Color.parseColor("#5D4037"))
        addChip(dialogBinding.modifierChipsContainer, "Only if clickable", "clickable:true", android.graphics.Color.parseColor("#455A64"))
        if (!textStr.isNullOrEmpty()) {
            val safe = if (textStr.contains(" ")) "\"$textStr\"" else textStr
            addChip(dialogBinding.modifierChipsContainer, "Text contains", "textcontains:$safe", android.graphics.Color.parseColor("#546E7A"))
        }
        if (!descStr.isNullOrEmpty()) {
            val safe = if (descStr.contains(" ")) "\"$descStr\"" else descStr
            addChip(dialogBinding.modifierChipsContainer, "Desc contains", "desccontains:$safe", android.graphics.Color.parseColor("#546E7A"))
        }

        // Generate initial rule using simple options
        val initialOptions = RuleOptions(matchStrategy = MatchStrategy.AUTO)
        val initialRule = if (isBlockAll) {
            generateRuleForAll(snapshot, currentPackageName, null, initialOptions)
        } else {
            generateRule(snapshot, currentPackageName, null, initialOptions)
        }
        
        dialogBinding.ruleQueryInput.setText(initialRule)

        dialogBinding.btnHelp.setOnClickListener {
            // Provide a quick Toast help snippet or could be a Dialog
            Toast.makeText(service, "Edit the query freely. Keys: pkg:, id:, class:, text:, action:, +... Modifiers: +text:\"req\"", Toast.LENGTH_LONG).show()
        }

        dialogBinding.cancelBtn.setOnClickListener { removeSafely(dialogBinding.root) }

        dialogBinding.confirmBtn.setOnClickListener {
            var rawRule = dialogBinding.ruleQueryInput.text.toString().trim()
            val comment = dialogBinding.commentInput.text.toString().trim()
            
            if (comment.isNotEmpty() && !rawRule.contains("comment:")) {
                val safeComment = if (comment.contains(" ")) "\"$comment\"" else comment
                rawRule += " comment:$safeComment"
            }
            
            listener.onRuleChosen(rawRule)
            lastAppliedRule = rawRule
            removeSafely(dialogBinding.root)
            hideHighlight()
            recycleNodes()
            showUndoBar(comment.ifEmpty { selectorDesc })
        }

        dialogBinding.root.setOnClickListener { removeSafely(dialogBinding.root) }

        val overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        windowManager.addView(dialogBinding.root, overlayParams)
    }

    private fun showUndoBar(ruleDescription: String) {
        removeUndoBar()

        undoBarBinding = ElementPickerUndoBarBinding.inflate(LayoutInflater.from(service))
        undoBarBinding!!.message.text = "Blocked: $ruleDescription"

        undoBarBinding!!.undoBtn.apply {
            setOnClickListener {
                lastAppliedRule?.let { rule ->
                    listener.onRuleUndone(rule)
                    lastAppliedRule = null
                }
                removeUndoBar()
                updateInfo("Tap an element to select it")
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            0, 0,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = (if (isAtBottom) Gravity.TOP else Gravity.BOTTOM) or Gravity.START
        }

        windowManager.addView(undoBarBinding!!.root, params)

        undoAutoHideRunnable = Runnable {
            removeUndoBar()
            lastAppliedRule = null
        }
        ui.postDelayed(undoAutoHideRunnable!!, 8000)
    }

    private fun removeUndoBar() {
        undoAutoHideRunnable?.let { ui.removeCallbacks(it) }
        undoAutoHideRunnable = null
        undoBar?.let { removeSafely(it) }
        undoBarBinding = null
    }

    // ── Rule Generation ───────────────────────────────────────────

    private fun StringBuilder.appendSafe(key: String, value: String?) {
        if (value.isNullOrEmpty()) return
        val safeValue = if (value.contains(" ")) "\"$value\"" else value
        this.append(" $key:$safeValue")
    }

    private fun appendOptions(rule: StringBuilder, snapshot: NodeSnapshot, options: RuleOptions) {
        if (options.pressBack) {
            rule.append(" action:back")
        }
        if (!options.blockTouches) {
            rule.append(" blockTouches:false")
        }
        if (options.matchByText) {
            val text = snapshot.text
            if (!text.isNullOrEmpty()) {
                rule.appendSafe("textContains", text)
            }
        }
        if (options.matchByDesc) {
            val desc = snapshot.desc
            if (!desc.isNullOrEmpty()) {
                rule.appendSafe("descContains", desc)
            }
        }
        if (options.requireAbsent && options.absentViewId.isNotEmpty()) {
            val value = if (options.absentViewId.contains(":")) {
                "viewId:${options.absentViewId}"
            } else {
                "text:${options.absentViewId}"
            }
            rule.appendSafe("requireAbsent", value)
        }
        if (options.blockLayout) {
            val parentViewId = snapshot.parentViewId
            if (!parentViewId.isNullOrEmpty()) {
                rule.appendSafe("blockLayout", "viewId:$parentViewId")
            }
        }
        if (options.matchChildren && options.childrenText.isNotEmpty()) {
            rule.appendSafe("matchChildren", "text:${options.childrenText}")
        }
        if (options.clickableOnly) {
            rule.append(" clickable:true")
        }
    }

    private fun applyMainStrategy(
        rule: StringBuilder,
        snapshot: NodeSnapshot,
        options: RuleOptions,
        isBlockAll: Boolean
    ) {
        val viewId = snapshot.viewId
        val desc = snapshot.desc
        val text = snapshot.text
        val className = snapshot.className

        val path = if (isBlockAll) snapshot.wildcardPath else snapshot.path

        val appendViewId = { rule.appendSafe("id", viewId) }
        val appendDesc = { if (!options.matchByDesc) rule.appendSafe("desc", desc) }
        val appendText = { if (!options.matchByText) rule.appendSafe("text", text) }
        val appendClass = { rule.appendSafe("class", className) }
        val appendPath = { rule.appendSafe("path", path) }

        when (options.matchStrategy) {
            MatchStrategy.VIEW_ID -> appendViewId()
            MatchStrategy.DESC -> appendDesc()
            MatchStrategy.TEXT -> appendText()
            MatchStrategy.CLASS_NAME -> appendClass()
            MatchStrategy.PATH -> appendPath()
            MatchStrategy.AUTO -> {
                if (isBlockAll) {
                    if (path != null) {
                        appendPath()
                    } else if (!desc.isNullOrEmpty() && !options.matchByDesc) {
                        appendDesc()
                    } else if (!className.isNullOrEmpty()) {
                        appendClass()
                    } else if (!viewId.isNullOrEmpty()) {
                        appendViewId()
                    }
                } else {
                    if (!viewId.isNullOrEmpty()) {
                        appendViewId()
                    } else if (!desc.isNullOrEmpty() && !options.matchByDesc) {
                        appendDesc()
                    } else if (path != null) {
                        appendPath()
                    } else if (!text.isNullOrEmpty() && !options.matchByText) {
                        appendText()
                    } else if (!className.isNullOrEmpty()) {
                        appendClass()
                    }
                }
            }
        }
    }

    private fun generateRule(
        snapshot: NodeSnapshot,
        packageName: String,
        comment: String?,
        options: RuleOptions
    ): String {
        val rule = StringBuilder("pkg:$packageName")
        applyMainStrategy(rule, snapshot, options, false)
        appendOptions(rule, snapshot, options)

        if (!comment.isNullOrEmpty()) {
            rule.appendSafe("comment", comment)
        }
        return rule.toString()
    }

    private fun generateRuleForAll(
        snapshot: NodeSnapshot,
        packageName: String,
        comment: String?,
        options: RuleOptions
    ): String {
        val rule = StringBuilder("pkg:$packageName")
        applyMainStrategy(rule, snapshot, options, true)
        appendOptions(rule, snapshot, options)

        if (!comment.isNullOrEmpty()) {
            rule.appendSafe("comment", comment)
        }
        return rule.toString()
    }

    private fun generatePath(target: AccessibilityNodeInfo, rootNode: AccessibilityNodeInfo?): String? {
        if (rootNode == null) return null
        val ancestors = mutableListOf<AccessibilityNodeInfo>()
        val indices = mutableListOf<Int>()

        @Suppress("DEPRECATION")
        var current: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(target)
        while (current != null) {
            if (current == rootNode) {
                @Suppress("DEPRECATION") current.recycle()
                break
            }
            val parent = current.parent
            if (parent == null) {
                @Suppress("DEPRECATION") current.recycle()
                break
            }

            var indexAmongSameClass = 0
            val currentClass = current.className
            if (currentClass != null) {
                for (i in 0 until parent.childCount) {
                    val sibling = parent.getChild(i) ?: continue
                    if (sibling == current) { @Suppress("DEPRECATION") sibling.recycle(); break }
                    val siblingClass = sibling.className
                    if (siblingClass != null && siblingClass.toString() == currentClass.toString()) {
                        indexAmongSameClass++
                    }
                    @Suppress("DEPRECATION") sibling.recycle()
                }
            }

            ancestors.add(current)
            indices.add(indexAmongSameClass)
            current = parent
        }

        if (ancestors.isEmpty()) return null

        val path = StringBuilder()
        for (i in ancestors.size - 1 downTo 0) {
            if (path.isNotEmpty()) path.append(">")
            val cls = ancestors[i].className
            path.append(cls?.toString() ?: "Unknown")
            path.append("[").append(indices[i]).append("]")
            @Suppress("DEPRECATION") ancestors[i].recycle()
        }
        return path.toString()
    }

    private fun generatePathWithWildcard(target: AccessibilityNodeInfo, rootNode: AccessibilityNodeInfo?): String? {
        if (rootNode == null) return null
        val ancestors = mutableListOf<AccessibilityNodeInfo>()
        val indices = mutableListOf<Int>()

        @Suppress("DEPRECATION")
        var current: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(target)
        while (current != null) {
            if (current == rootNode) { @Suppress("DEPRECATION") current.recycle(); break }
            val parent = current.parent
            if (parent == null) { @Suppress("DEPRECATION") current.recycle(); break }

            var indexAmongSameClass = 0
            val currentClass = current.className
            if (currentClass != null) {
                for (i in 0 until parent.childCount) {
                    val sibling = parent.getChild(i) ?: continue
                    if (sibling == current) { @Suppress("DEPRECATION") sibling.recycle(); break }
                    val siblingClass = sibling.className
                    if (siblingClass != null && siblingClass.toString() == currentClass.toString()) {
                        indexAmongSameClass++
                    }
                    @Suppress("DEPRECATION") sibling.recycle()
                }
            }
            ancestors.add(current)
            indices.add(indexAmongSameClass)
            current = parent
        }

        if (ancestors.isEmpty()) return null

        val path = StringBuilder()
        for (i in ancestors.size - 1 downTo 0) {
            if (path.isNotEmpty()) path.append(">")
            val cls = ancestors[i].className
            path.append(cls?.toString() ?: "Unknown")
            if (i == 0) path.append("[*]") else path.append("[").append(indices[i]).append("]")
            @Suppress("DEPRECATION") ancestors[i].recycle()
        }
        return path.toString()
    }

    private fun describeNode(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        val className = node.className
        if (className != null) {
            val fullName = className.toString()
            val lastDot = fullName.lastIndexOf('.')
            sb.append(if (lastDot >= 0) fullName.substring(lastDot + 1) else fullName)
        }
        val viewId = node.viewIdResourceName
        if (!viewId.isNullOrEmpty()) {
            val slashIndex = viewId.indexOf('/')
            val shortId = if (slashIndex >= 0) viewId.substring(slashIndex + 1) else viewId
            sb.append(" #$shortId")
        }
        val desc = node.contentDescription
        if (!desc.isNullOrEmpty()) {
            sb.append(" \"${truncate(desc.toString(), 30)}\"")
        }
        val text = node.text
        if (!text.isNullOrEmpty()) {
            sb.append(" [${truncate(text.toString(), 30)}]")
        }
        return sb.toString().trim()
    }

    private fun getSelectorDescription(node: AccessibilityNodeInfo, rootNode: AccessibilityNodeInfo?): String {
        val viewId = node.viewIdResourceName
        if (!viewId.isNullOrEmpty()) return "View ID: $viewId"

        val desc = node.contentDescription
        if (!desc.isNullOrEmpty()) return "Desc: ${truncate(desc.toString(), 50)}"

        val path = generatePath(node, rootNode)
        if (path != null) return "Path: ${truncate(path, 60)}"

        val text = node.text
        if (!text.isNullOrEmpty()) return "Text: ${truncate(text.toString(), 50)}"

        val className = node.className
        if (className != null) return "Class: $className"

        return "Unknown element"
    }

    private fun truncate(s: String, max: Int): String {
        return if (s.length <= max) s else s.substring(0, max - 1) + "…"
    }

    companion object {
        private const val TAG = "ElementPicker"
    }
}
