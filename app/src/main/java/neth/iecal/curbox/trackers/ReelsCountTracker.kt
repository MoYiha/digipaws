package neth.iecal.curbox.trackers

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.RECEIVER_EXPORTED
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.util.LruCache
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import neth.iecal.curbox.blockers.ReelBlocker
import neth.iecal.curbox.blockers.viewblocker.NodeMatcher
import neth.iecal.curbox.blockers.viewblocker.ViewBlocker
import neth.iecal.curbox.data.db.AppDatabase
import neth.iecal.curbox.data.db.ReelStatsDao
import neth.iecal.curbox.data.db.ReelStatsEntity
import neth.iecal.curbox.data.models.ReelAppData
import neth.iecal.curbox.data.models.ReelCounterOverlayConfig
import neth.iecal.curbox.hardcoded.ReelAppConfig.Companion.reelData
import neth.iecal.curbox.services.BaseBlockingService
import neth.iecal.curbox.ui.overlay.ReelsOverlayManager
import neth.iecal.curbox.utils.AccessibilityHelper
import neth.iecal.curbox.utils.TimeTools



class ReelsCountTracker {

    companion object {
        const val INTENT_ACTION_REFRESH_REEL_COUNTER = "neth.iecal.curbox.refresh.reel_counter"
    }

    private lateinit var service: BaseBlockingService
    private lateinit var overlayManager: ReelsOverlayManager
    private lateinit var reelStatsDao: ReelStatsDao

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var isOnDisplayCounter = true
    private var overlayConfig = ReelCounterOverlayConfig()
    private var todayCount = 0
    private var lastDateStr = TimeTools.getCurrentDate()


    private val lastDynamicText = mutableMapOf<String, String>()
    private val seenReelsCache = mutableMapOf<String, LruCache<String, Boolean>>()
    private val viewBlockerHelper = ViewBlocker()

    fun setup(service: BaseBlockingService, overlayManager: ReelsOverlayManager) {
        this.service = service
        this.viewBlockerHelper.service = service
        this.overlayManager = overlayManager

        val db = AppDatabase.getInstance(service)
        this.reelStatsDao = db.reelStatsDao()

        scope.launch {
            service.dataStoreManager.settings.collectLatest { settings ->
                isOnDisplayCounter = settings.isReelCounterOn
                overlayConfig = settings.reelCounterOverlayConfig
            }
        }

        scope.launch {
            try {
                lastDateStr = TimeTools.getCurrentDate()
                todayCount = reelStatsDao.getCount(lastDateStr) ?: 0
            } catch (_: Exception) {
                todayCount = 0
            }
        }
    }

    fun onEvent(event: AccessibilityEvent?) {

        if (event == null) return

        try {
            val pkg = event.packageName?.toString() ?: return
            Log.d("event", event?.source.toString() + event?.eventType.toString()  )

            if (reelData.containsKey(pkg)) {
                if((event.eventType and reelData[pkg]!!.eventType) == 0) return
                if (Settings.canDrawOverlays(service)) {
                    overlayManager.reelsScrolledThisSession = todayCount
                    overlayManager.startDisplaying(overlayConfig, isOnDisplayCounter)
                }
            } else if (overlayManager.isOverlayVisible) {
                overlayManager.removeOverlay()
                return
            }

            val data = reelData[pkg] ?: return

            checkForReelProgression(pkg, data)


        } catch (_: Exception) { }
    }

    private fun checkForReelProgression(pkg: String, data: ReelAppData) {
        val root = service.rootInActiveWindow ?: return

        Log.d("reel","searchin view")

        val viewIdMatcher = NodeMatcher.parse(data.viewId)
        if (viewIdMatcher != null) {
            if (!viewBlockerHelper.findNodeByMatcher(root, viewIdMatcher, pkg)) {
                hideReelCounter()
                return
            }
        } else {
            if (AccessibilityHelper.findElementById(root, data.viewId) == null) {
                hideReelCounter()
                return
            }
        }
        Log.d("reel","found view")

        // Check if required views are present
        for (req in data.requiresPresent) {
            val matcher = NodeMatcher.parse(req)
            Log.d("nodematcher", matcher.toString())
            if (matcher != null) {
                if (!viewBlockerHelper.findNodeByMatcher(root, matcher, pkg)) {
                    hideReelCounter()
                    return
                }
            } else {
                if (AccessibilityHelper.findElementById(root, req) == null) {
                    hideReelCounter()
                    return
                }
            }
        }

        Log.d("reel","all present")

        // Check if requires absent views are found
        for (req in data.requiresAbsent) {
            val matcher = NodeMatcher.parse(req)
            if (matcher != null) {
                if (viewBlockerHelper.findNodeByMatcher(root, matcher, pkg)) {
                    hideReelCounter()
                    return
                }
            } else {
                if (AccessibilityHelper.findElementById(root, req) != null) {
                    hideReelCounter()
                    return
                }
            }
        }
        Log.d("reel","all absent")

        // Loop dynamic comparator viewgroups and extract text
        var currentText = ""
        for (compId in data.dynamicComparator) {
            val matcher = NodeMatcher.parse(compId)
            val compNode = if (matcher != null) {
                viewBlockerHelper.resolveMatcherToNode(root, matcher, pkg)
            } else {
                AccessibilityHelper.findElementById(root, compId)
            }
            if (compNode != null) {
                currentText += data.comparsionResultCleanser( extractTextFromNode(compNode))
            }
        }

        Log.d("reel_text",currentText)

        if (currentText.trim().isBlank()) return

        val previousText = lastDynamicText[pkg] ?: ""
        if (currentText != previousText) {
            val isSubstantialChange = isSubstantialTextChange(currentText, previousText)

            if (previousText.isNotEmpty() && isSubstantialChange) {
                val appCache = seenReelsCache.getOrPut(pkg) { LruCache(50) }
                if (appCache.get(currentText) == null) {
                    onReelCounted()
                    appCache.put(currentText, true)
                }
            }
            
            if (isSubstantialChange || currentText.length > previousText.length) {
                lastDynamicText[pkg] = currentText
            }
        }
    }

    private fun extractTextFromNode(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        var result = ""
        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()
        if (text != null) result += text
        if (desc != null) result += desc

        for (i in 0 until node.childCount) {
            result += extractTextFromNode(node.getChild(i))
//            if(result!="") return result
        }
        return result
    }

    fun getTodayCount(): Int = todayCount

    private fun onReelCounted() {
        val date = TimeTools.getCurrentDate()
        if (date != lastDateStr) {
            todayCount = 0
            lastDateStr = date
        }
        todayCount++
        overlayManager.reelsScrolledThisSession = todayCount

        if (isOnDisplayCounter) {
            overlayManager.binding?.reelCounter?.apply {
                visibility = View.VISIBLE
                text = todayCount.toString()
            }
        } else {
            overlayManager.binding?.reelCounter?.visibility = View.GONE
        }

        scope.launch {
            try {
                reelStatsDao.upsert(ReelStatsEntity(date = date, count = todayCount))
            } catch (_: Exception) { }
        }

        service.lastBackPressTimeStamp = SystemClock.uptimeMillis()
    }

    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                INTENT_ACTION_REFRESH_REEL_COUNTER -> setup(service, overlayManager)
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun setupReceivers() {
        val filter = IntentFilter().apply {
            addAction(INTENT_ACTION_REFRESH_REEL_COUNTER)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            service.registerReceiver(refreshReceiver, filter, RECEIVER_EXPORTED)
        } else {
            service.registerReceiver(refreshReceiver, filter)
        }
    }

    fun onDestroy() {
        overlayManager.binding = null
        try { service.unregisterReceiver(refreshReceiver) } catch (_: Exception) {}
    }

    private fun hideReelCounter() {
        overlayManager.binding?.reelCounter?.visibility = View.GONE
    }

    private fun isSubstantialTextChange(currentText: String, previousText: String): Boolean {
        if (currentText.isEmpty() || previousText.isEmpty()) return true

        fun countWords(text: String, wordCounts: HashMap<String, Int>) {
            val len = text.length
            var start = -1
            for (i in 0 until len) {
                if (text[i].isWhitespace()) {
                    if (start != -1) {
                        val word = text.substring(start, i)
                        wordCounts[word] = wordCounts.getOrDefault(word, 0) + 1
                        start = -1
                    }
                } else {
                    if (start == -1) start = i
                }
            }
            if (start != -1) {
                val word = text.substring(start, len)
                wordCounts[word] = wordCounts.getOrDefault(word, 0) + 1
            }
        }

        val currentWords = HashMap<String, Int>()
        val previousWords = HashMap<String, Int>()
        
        countWords(currentText, currentWords)
        countWords(previousText, previousWords)

        if (currentWords.isEmpty() || previousWords.isEmpty()) return true

        var intersectionSize = 0
        var totalSmaller = 0
        
        val smallerMap = if (currentWords.size < previousWords.size) currentWords else previousWords
        val largerMap = if (currentWords.size < previousWords.size) previousWords else currentWords

        for ((word, count) in smallerMap) {
            totalSmaller += count
            val largerCount = largerMap[word] ?: 0
            intersectionSize += minOf(count, largerCount)
        }

        if (totalSmaller == 0) return true

        val overlapRatio = intersectionSize.toFloat() / totalSmaller
        return overlapRatio < 0.90f
    }
}
