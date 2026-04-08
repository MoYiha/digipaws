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
import neth.iecal.curbox.data.db.AppDatabase
import neth.iecal.curbox.data.db.ReelStatsDao
import neth.iecal.curbox.data.db.ReelStatsEntity
import neth.iecal.curbox.services.BaseBlockingService
import neth.iecal.curbox.ui.overlay.ReelsOverlayManager
import neth.iecal.curbox.utils.TimeTools

data class ReelCounterData(
    val viewId: String,
    val requiresPresent: List<String>,
    val dynamicComparator: List<String>
)

class ReelsCountTracker {

    companion object {
        const val INTENT_ACTION_REFRESH_REEL_COUNTER = "neth.iecal.curbox.refresh.reel_counter"

        val reelData: Map<String, ReelCounterData> = mapOf(
            "com.instagram.android" to ReelCounterData(
                viewId = "com.instagram.android:id/clips_viewer_view_pager",
                requiresPresent = listOf("com.instagram.android:id/clips_ufi_component"),
                dynamicComparator = listOf("com.instagram.android:id/clips_captions_component", "com.instagram.android:id/clips_author_username")
            ),
            "com.google.android.youtube" to ReelCounterData(
                viewId = "com.google.android.youtube:id/reel_recycler",
                requiresPresent = listOf("com.google.android.youtube:id/reel_player_page_content"),
                dynamicComparator = TODO()
            )
        )

        private const val TARGET_EVENTS_MASK = AccessibilityEvent.TYPE_VIEW_SCROLLED
    }

    private lateinit var service: BaseBlockingService
    private lateinit var overlayManager: ReelsOverlayManager
    private lateinit var reelStatsDao: ReelStatsDao

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var isOnDisplayCounter = true
    private var todayCount = 0
    private var lastDateStr = TimeTools.getCurrentDate()

    private val lastDynamicText = mutableMapOf<String, String>()
    private val seenReelsCache = mutableMapOf<String, LruCache<String, Boolean>>()

    fun setup(service: BaseBlockingService, overlayManager: ReelsOverlayManager) {
        this.service = service
        this.overlayManager = overlayManager

        val db = AppDatabase.getInstance(service)
        this.reelStatsDao = db.reelStatsDao()

        scope.launch {
            service.dataStoreManager.settings.collectLatest { settings ->
                isOnDisplayCounter = settings.isReelCounterOn
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
        if (event == null || (event.eventType and TARGET_EVENTS_MASK) == 0) return

        try {
            val pkg = event.packageName?.toString() ?: return

            if (reelData.containsKey(pkg)) {
                if (Settings.canDrawOverlays(service)) {
                    overlayManager.startDisplaying()
                }
            } else if (overlayManager.isOverlayVisible) {
                overlayManager.removeOverlay()
                return
            }

            val data = reelData[pkg] ?: return

            checkForReelProgression(pkg, data)


        } catch (_: Exception) { }
    }

    private fun checkForReelProgression(pkg: String, data: ReelCounterData) {
        val root = service.rootInActiveWindow ?: return

        if (ReelBlocker.findElementById(root, data.viewId) == null) return

        // Check if required views are present
        for (req in data.requiresPresent) {
            if (ReelBlocker.findElementById(root, req) == null){
                hideReelCounter()
                return
            }
        }

        // Loop dynamic comparator viewgroups and extract text
        var currentText = ""
        for (compId in data.dynamicComparator) {
            val compNode = ReelBlocker.findElementById(root, compId)
            if (compNode != null) {
                currentText += extractTextFromNode(compNode)
            }
        }

        if (currentText.isBlank()) return

        val previousText = lastDynamicText[pkg] ?: ""
        if (currentText != previousText) {

            if (previousText.isNotEmpty()) {
                val appCache = seenReelsCache.getOrPut(pkg) { LruCache(50) }
                if (appCache.get(currentText) == null) {
                    onReelCounted()
                    appCache.put(currentText, true)
                }
            }
            
            lastDynamicText[pkg] = currentText
        }
    }

    private fun extractTextFromNode(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        var result = ""
        val text = node.text?.toString()
        if (text != null) result += text

        for (i in 0 until node.childCount) {
            result += extractTextFromNode(node.getChild(i))
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
}
