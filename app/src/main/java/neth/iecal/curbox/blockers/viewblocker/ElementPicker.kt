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
                updateInfo("No element found at that point")
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
            Toast.makeText(service, "No element selected", Toast.LENGTH_SHORT).show()
            return
        }
        val node = nodesAtPoint[currentNodeIndex]
        val selectorDesc = getSelectorDescription(node, currentRootNode)
        showConfirmationOverlay(node, selectorDesc, false)
    }

    private fun confirmBlockAll() {
        if (nodesAtPoint.isEmpty() || currentNodeIndex >= nodesAtPoint.size) {
            Toast.makeText(service, "No element selected", Toast.LENGTH_SHORT).show()
            return
        }
        val node = nodesAtPoint[currentNodeIndex]
        showConfirmationOverlay(node, "All similar elements", true)
    }

    private enum class MatchStrategy(val label: String) {
        AUTO("Auto (Best Choice)"),
        VIEW_ID("View ID"),
        DESC("Content Description"),
        TEXT("Text"),
        CLASS_NAME("Class Name"),
        PATH("Path")
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
        dialogBinding.titleText.text = if (isBlockAll) "Block All Similar?" else "Block This Element?"

        val nodeText = snapshot.text
        val nodeDesc = snapshot.desc

        dialogBinding.chkMatchByText.visibility = if (!nodeText.isNullOrEmpty()) View.VISIBLE else View.GONE
        dialogBinding.chkMatchByText.text = "Match by text: \"${truncate(nodeText ?: "", 30)}\""

        dialogBinding.chkMatchByDesc.visibility = if (!nodeDesc.isNullOrEmpty()) View.VISIBLE else View.GONE
        dialogBinding.chkMatchByDesc.text = "Match by description: \"${truncate(nodeDesc ?: "", 30)}\""

        var optionsExpanded = false
        dialogBinding.optionsToggle.setOnClickListener {
            optionsExpanded = !optionsExpanded
            dialogBinding.optionsContainer.visibility = if (optionsExpanded) View.VISIBLE else View.GONE
            dialogBinding.optionsToggle.text = if (optionsExpanded) "▼ Options" else "▶ Options"
        }

        dialogBinding.chkRequireAbsent.setOnCheckedChangeListener { _, checked ->
            dialogBinding.inputAbsentViewId.visibility = if (checked) View.VISIBLE else View.GONE
        }
        dialogBinding.chkMatchChildren.setOnCheckedChangeListener { _, checked ->
            dialogBinding.inputMatchChildren.visibility = if (checked) View.VISIBLE else View.GONE
        }
        dialogBinding.chkPressBack.setOnCheckedChangeListener { _, checked ->
            if (checked) dialogBinding.chkBlockTouches.isChecked = true
        }

        val availableStrategies = mutableListOf(MatchStrategy.AUTO)
        if (!snapshot.viewId.isNullOrEmpty()) availableStrategies.add(MatchStrategy.VIEW_ID)
        if (!snapshot.desc.isNullOrEmpty()) availableStrategies.add(MatchStrategy.DESC)
        if (!snapshot.text.isNullOrEmpty()) availableStrategies.add(MatchStrategy.TEXT)
        if (!snapshot.className.isNullOrEmpty()) availableStrategies.add(MatchStrategy.CLASS_NAME)
        // Path is always an option
        availableStrategies.add(MatchStrategy.PATH)

        val adapter = object : ArrayAdapter<String>(service, android.R.layout.simple_spinner_dropdown_item, availableStrategies.map { it.label }) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = super.getView(position, convertView, parent)
                (view as? android.widget.TextView)?.setTextColor(android.graphics.Color.WHITE)
                return view
            }
        }
        dialogBinding.spinMatchType.adapter = adapter

        fun getOptions(): RuleOptions {
            return RuleOptions(
                pressBack = dialogBinding.chkPressBack.isChecked,
                blockTouches = dialogBinding.chkBlockTouches.isChecked,
                matchByText = dialogBinding.chkMatchByText.isChecked,
                matchByDesc = dialogBinding.chkMatchByDesc.isChecked,
                requireAbsent = dialogBinding.chkRequireAbsent.isChecked,
                absentViewId = dialogBinding.inputAbsentViewId.text.toString().trim(),
                blockLayout = dialogBinding.chkBlockLayout.isChecked,
                matchChildren = dialogBinding.chkMatchChildren.isChecked,
                childrenText = dialogBinding.inputMatchChildren.text.toString().trim(),
                clickableOnly = dialogBinding.chkClickableOnly.isChecked,
                matchStrategy = availableStrategies[dialogBinding.spinMatchType.selectedItemPosition.coerceAtLeast(0)]
            )
        }

        var currentPreviewRule = ""

        fun updatePreview() {
            val options = getOptions()
            currentPreviewRule = if (isBlockAll) {
                generateRuleForAll(snapshot, currentPackageName, null, options)
            } else {
                generateRule(snapshot, currentPackageName, null, options)
            }
            dialogBinding.descText.text = "Selector: $selectorDesc\nRule: $currentPreviewRule"
        }

        val checkBoxes = listOf(
            dialogBinding.chkPressBack, dialogBinding.chkBlockTouches,
            dialogBinding.chkMatchByText, dialogBinding.chkMatchByDesc,
            dialogBinding.chkRequireAbsent, dialogBinding.chkBlockLayout,
            dialogBinding.chkMatchChildren, dialogBinding.chkClickableOnly
        )

        checkBoxes.forEach { cb ->
            cb.setOnClickListener { updatePreview() }
        }

        dialogBinding.spinMatchType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updatePreview()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        updatePreview()

        dialogBinding.descText.setOnClickListener {
            val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("rule", currentPreviewRule)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(service, "Rule copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        dialogBinding.cancelBtn.setOnClickListener { removeSafely(dialogBinding.root) }

        dialogBinding.confirmBtn.setOnClickListener {
            var comment = dialogBinding.commentInput.text.toString().trim()
            if (comment.isEmpty()) comment = selectorDesc

            val options = getOptions()

            val finalRule = if (isBlockAll) {
                generateRuleForAll(snapshot, currentPackageName, comment, options)
            } else {
                generateRule(snapshot, currentPackageName, comment, options)
            }
            listener.onRuleChosen(finalRule)
            lastAppliedRule = finalRule
            removeSafely(dialogBinding.root)
            hideHighlight()
            recycleNodes()
            showUndoBar(comment)
        }

        dialogBinding.root.setOnClickListener { removeSafely(dialogBinding.root) }
        dialogBinding.card.setOnClickListener { /* intercept */ }

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

    private fun appendOptions(rule: StringBuilder, snapshot: NodeSnapshot, options: RuleOptions) {
        if (options.pressBack) {
            rule.append("##action=back")
        }
        if (!options.blockTouches) {
            rule.append("##blockTouches=false")
        }
        if (options.matchByText) {
            val text = snapshot.text
            if (!text.isNullOrEmpty()) {
                rule.append("##textContains=").append(text)
            }
        }
        if (options.matchByDesc) {
            val desc = snapshot.desc
            if (!desc.isNullOrEmpty()) {
                rule.append("##descContains=").append(desc)
            }
        }
        if (options.requireAbsent && options.absentViewId.isNotEmpty()) {
            val value = if (options.absentViewId.contains(":")) {
                "viewId:${options.absentViewId}"
            } else {
                "text:${options.absentViewId}"
            }
            rule.append("##requireAbsent=").append(value)
        }
        if (options.blockLayout) {
            val parentViewId = snapshot.parentViewId
            if (!parentViewId.isNullOrEmpty()) {
                rule.append("##blockLayout=viewId:").append(parentViewId)
            }
        }
        if (options.matchChildren && options.childrenText.isNotEmpty()) {
            rule.append("##matchChildren=text:").append(options.childrenText)
        }
        if (options.clickableOnly) {
            rule.append("##clickable=true")
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

        val appendViewId = { if (!viewId.isNullOrEmpty()) rule.append("##viewId=").append(viewId) }
        val appendDesc = { if (!desc.isNullOrEmpty() && !options.matchByDesc) rule.append("##desc=").append(desc) }
        val appendText = { if (!text.isNullOrEmpty() && !options.matchByText) rule.append("##text=").append(text) }
        val appendClass = { if (!className.isNullOrEmpty()) rule.append("##className=").append(className) }
        val appendPath = { if (path != null) rule.append("##path=").append(path) }

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
        val rule = StringBuilder(packageName)
        applyMainStrategy(rule, snapshot, options, false)
        appendOptions(rule, snapshot, options)

        if (!comment.isNullOrEmpty()) {
            rule.append("##comment=").append(comment)
        }
        return rule.toString()
    }

    private fun generateRuleForAll(
        snapshot: NodeSnapshot,
        packageName: String,
        comment: String?,
        options: RuleOptions
    ): String {
        val rule = StringBuilder(packageName)
        applyMainStrategy(rule, snapshot, options, true)
        appendOptions(rule, snapshot, options)

        if (!comment.isNullOrEmpty()) {
            rule.append("##comment=").append(comment)
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
