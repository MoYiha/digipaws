package neth.iecal.curbox.trackers

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.RECEIVER_EXPORTED
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import neth.iecal.curbox.blockers.ReelBlocker
import neth.iecal.curbox.data.db.AppDatabase
import neth.iecal.curbox.data.db.ReelStatsDao
import neth.iecal.curbox.data.db.ReelStatsEntity
import neth.iecal.curbox.data.db.ScrollPatternDao
import neth.iecal.curbox.data.db.ScrollPatternEntity
import neth.iecal.curbox.services.BaseBlockingService
import neth.iecal.curbox.ui.overlay.UsageStatOverlayManager
import neth.iecal.curbox.utils.TimeTools
import kotlin.math.max
import kotlin.math.roundToInt

class ReelsCountTracker {

    companion object {
        const val INTENT_ACTION_REFRESH_REEL_COUNTER = "neth.iecal.curbox.refresh.reel_counter"

        val SUPPORTED_APPS = hashSetOf(
            "com.ss.android.ugc.trill",
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.aweme",
            "com.instagram.android",
            "com.myinsta.android",
            "com.google.android.youtube",
            "app.revanced.android.youtube",
            "com.facebook.katana"
        )

        private val TIKTOK_PACKAGES = hashSetOf(
            "com.ss.android.ugc.trill",
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.aweme",
        )

        private val DEFAULT_THRESHOLD = mapOf(
            "com.ss.android.ugc.trill" to 1,
            "com.zhiliaoapp.musically" to 1,
            "com.ss.android.ugc.aweme" to 1,
            "com.google.android.youtube" to 2,
            "app.revanced.android.youtube" to 2,
            "com.facebook.katana" to 2,
            "com.instagram.android" to 2,
            "com.myinsta.android" to 2
        )

        private const val COOLDOWN_MS = 300L
        private const val EMA_ALPHA = 0.15f
        private const val BOOTSTRAP_COUNT = 15

        private const val TARGET_EVENTS_MASK = AccessibilityEvent.TYPE_VIEW_SCROLLED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
    }

    private lateinit var service: BaseBlockingService
    private lateinit var overlayManager: UsageStatOverlayManager
    private lateinit var reelStatsDao: ReelStatsDao
    private lateinit var scrollPatternDao: ScrollPatternDao

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var isOnDisplayCounter = true
    private var todayCount = 0
    private var lastVideoViewFoundTime: Long? = null
    private var lastContentChangeTimestamp = 0L

    // per-app counters
    private val scrollCounters = mutableMapOf<String, Int>()
    private val lastCountTime = mutableMapOf<String, Long>()
    private val learnedThresholds = mutableMapOf<String, Float>()
    private val totalSwipesSeen = mutableMapOf<String, Int>()

    fun setup(service: BaseBlockingService, overlayManager: UsageStatOverlayManager) {
        this.service = service
        this.overlayManager = overlayManager

        val db = AppDatabase.getInstance(service)
        this.reelStatsDao = db.reelStatsDao()
        this.scrollPatternDao = db.scrollPatternDao()

        scope.launch {
            service.dataStoreManager.settings.collectLatest { settings ->
                isOnDisplayCounter = settings.isReelCounterOn
            }
        }

        scope.launch {
            try {
                todayCount = reelStatsDao.getCount(TimeTools.getCurrentDate()) ?: 0
            } catch (_: Exception) {
                todayCount = 0
            }
        }

        // load learned patterns
        scope.launch {
            try {
                SUPPORTED_APPS.forEach { pkg ->
                    val pattern = scrollPatternDao.getPattern(pkg)
                    if (pattern != null) {
                        learnedThresholds[pkg] = pattern.learnedEventsPerBurst
                        totalSwipesSeen[pkg] = pattern.totalBurstsSeen
                    }
                }
            } catch (_: Exception) { }
        }
    }

    fun onEvent(event: AccessibilityEvent?) {
        if (event == null || (event.eventType and TARGET_EVENTS_MASK) == 0) return

        try {
            val pkg = event.packageName?.toString() ?: return

            // show/hide overlay based on whether we're in a supported app
            if (SUPPORTED_APPS.contains(pkg)) {
                if (android.provider.Settings.canDrawOverlays(service)) {
                    overlayManager.startDisplaying()
                }
            } else if (overlayManager.isOverlayVisible) {
                overlayManager.removeOverlay()
            }

            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                && SystemClock.uptimeMillis() - lastContentChangeTimestamp > 2000
            ) {
                if (SUPPORTED_APPS.contains(pkg)) {
                    ReelBlocker.BLOCKED_VIEW_ID_LIST.forEach { viewId ->
                        if (ReelBlocker.findElementById(service.rootInActiveWindow, viewId) == null) {
                            hideReelCounter()
                        }
                    }
                } else {
                    hideReelCounter()
                }
                lastContentChangeTimestamp = SystemClock.uptimeMillis()
            }

            if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                if (isReelScrollEvent(event)) {
                    handleScrollEvent(pkg)
                }
            }
        } catch (_: Exception) { }
    }

    private fun handleScrollEvent(pkg: String) {
        val now = SystemClock.uptimeMillis()
        val lastTime = lastCountTime[pkg] ?: 0L

        // cooldown: if we just counted a reel, reset counter and skip
        if (now - lastTime < COOLDOWN_MS && lastTime > 0L) {
            scrollCounters[pkg] = 0
            return
        }

        val counter = (scrollCounters[pkg] ?: 0) + 1
        scrollCounters[pkg] = counter

        val threshold = getThreshold(pkg)
        if (counter >= threshold) {
            scrollCounters[pkg] = 0
            lastCountTime[pkg] = now
            onReelCounted()
            updateLearnedThreshold(pkg, counter)
        }
    }

    private fun getThreshold(pkg: String): Int {
        val seen = totalSwipesSeen[pkg] ?: 0
        return if (seen < BOOTSTRAP_COUNT) {
            DEFAULT_THRESHOLD[pkg] ?: 2
        } else {
            max(1, (learnedThresholds[pkg] ?: (DEFAULT_THRESHOLD[pkg]?.toFloat() ?: 2f)).roundToInt())
        }
    }

    private fun updateLearnedThreshold(pkg: String, eventsUsed: Int) {
        val current = learnedThresholds[pkg] ?: (DEFAULT_THRESHOLD[pkg]?.toFloat() ?: 2f)
        val updated = EMA_ALPHA * eventsUsed + (1 - EMA_ALPHA) * current
        learnedThresholds[pkg] = updated

        val seen = (totalSwipesSeen[pkg] ?: 0) + 1
        totalSwipesSeen[pkg] = seen

        scope.launch {
            try {
                scrollPatternDao.upsert(
                    ScrollPatternEntity(
                        packageName = pkg,
                        learnedEventsPerBurst = updated,
                        learnedBurstGapMs = COOLDOWN_MS.toFloat(),
                        totalBurstsSeen = seen
                    )
                )
            } catch (_: Exception) { }
        }
    }

    private fun isReelScrollEvent(event: AccessibilityEvent): Boolean {
        val pkg = event.packageName?.toString() ?: return false
        val className = try { event.source?.className?.toString() } catch (_: Exception) { return false }

        return try {
            when {
                TIKTOK_PACKAGES.contains(pkg) && className == "androidx.viewpager.widget.ViewPager" ->
                    true

                pkg == "com.facebook.katana" && className == "androidx.recyclerview.widget.RecyclerView" -> {
                    val root = service.rootInActiveWindow ?: return false
                    val nodes = root.findAccessibilityNodeInfosByText(
                        "FbShortsComposerAttachmentComponentSpec_STICKER"
                    )
                    nodes?.firstOrNull() != null
                }

                pkg == "com.instagram.android" && className == "androidx.viewpager.widget.ViewPager" -> {
                    val root = service.rootInActiveWindow ?: return false
                    ReelBlocker.findElementById(root, "com.instagram.android:id/root_clips_layout") != null
                }

                pkg == "com.myinsta.android" && className == "androidx.viewpager.widget.ViewPager" -> {
                    val root = service.rootInActiveWindow ?: return false
                    ReelBlocker.findElementById(root, "com.myinsta.android:id/root_clips_layout") != null
                }

                pkg == "com.google.android.youtube" && className == "android.support.v7.widget.RecyclerView" -> {
                    val root = service.rootInActiveWindow ?: return false
                    val reelView = ReelBlocker.findElementById(root, "com.google.android.youtube:id/reel_recycler")
                    val comments = ReelBlocker.findElementById(root, "com.google.android.youtube:id/engagement_panel_content")
                    reelView != null && comments == null
                }

                pkg == "app.revanced.android.youtube" && className == "android.support.v7.widget.RecyclerView" -> {
                    val root = service.rootInActiveWindow ?: return false
                    val reelView = ReelBlocker.findElementById(root, "app.revanced.android.youtube:id/reel_recycler")
                    val comments = ReelBlocker.findElementById(root, "app.revanced.android.youtube:id/engagement_panel_content")
                    reelView != null && comments == null
                }

                else -> false
            }
        } catch (_: Exception) { false }
    }

    private fun onReelCounted() {
        val date = TimeTools.getCurrentDate()
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
                INTENT_ACTION_REFRESH_REEL_COUNTER -> setup(service,overlayManager)
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun setupReceivers(){
        val filter = IntentFilter().apply {
            addAction(INTENT_ACTION_REFRESH_REEL_COUNTER)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            service.registerReceiver(refreshReceiver, filter, RECEIVER_EXPORTED)
        } else {
            service.registerReceiver(refreshReceiver, filter)
        }

    }
    fun  onDestroy(){
        overlayManager.binding = null
        service.unregisterReceiver(refreshReceiver)
    }
    private fun hideReelCounter() {
        overlayManager.binding?.reelCounter?.visibility = View.GONE
        lastVideoViewFoundTime = null
    }
}