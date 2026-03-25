package nethical.digipaws.trackers

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import nethical.digipaws.blockers.ReelBlocker
import nethical.digipaws.data.db.AppDatabase
import nethical.digipaws.data.db.ReelStatsDao
import nethical.digipaws.data.db.ReelStatsEntity
import nethical.digipaws.data.db.ScrollPatternDao
import nethical.digipaws.data.db.ScrollPatternEntity
import nethical.digipaws.services.BaseBlockingService
import nethical.digipaws.ui.overlay.UsageStatOverlayManager
import nethical.digipaws.utils.TimeTools
import kotlin.math.max
import kotlin.math.roundToInt

class ReelsCountTracker {

    companion object {
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

        // bootstrap defaults used before the algorithm has learned enough
        private val DEFAULT_EVENTS_PER_BURST = mapOf(
            "com.ss.android.ugc.trill" to 1f,
            "com.zhiliaoapp.musically" to 1f,
            "com.ss.android.ugc.aweme" to 1f,
            "com.google.android.youtube" to 2f,
            "app.revanced.android.youtube" to 2f,
            "com.facebook.katana" to 2f,
            "com.instagram.android" to 2f,
            "com.myinsta.android" to 2f
        )

        private const val DEFAULT_BURST_GAP_MS = 400L
        private const val BOOTSTRAP_THRESHOLD = 10
        private const val EMA_ALPHA = 0.2f
    }

    private lateinit var service: BaseBlockingService
    private lateinit var overlayManager: UsageStatOverlayManager
    private lateinit var reelStatsDao: ReelStatsDao
    private lateinit var scrollPatternDao: ScrollPatternDao

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())

    private var isEnabled = true
    private var todayCount = 0
    private var lastVideoViewFoundTime: Long? = null
    private var lastContentChangeTimestamp = 0L

    // per-app burst detectors, created lazily
    private val burstDetectors = mutableMapOf<String, BurstDetector>()

    fun setup(service: BaseBlockingService, overlayManager: UsageStatOverlayManager) {
        this.service = service
        this.overlayManager = overlayManager

        val db = AppDatabase.getInstance(service)
        this.reelStatsDao = db.reelStatsDao()
        this.scrollPatternDao = db.scrollPatternDao()

        scope.launch {
            service.dataStoreManager.settings.collectLatest { settings ->
                isEnabled = settings.isReelCounterOn
            }
        }

        scope.launch {
            try {
                todayCount = reelStatsDao.getCount(TimeTools.getCurrentDate()) ?: 0
            } catch (_: Exception) {
                todayCount = 0
            }
        }
    }

    fun onEvent(event: AccessibilityEvent?) {
        if (event == null) return

        try {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                && SystemClock.uptimeMillis() - lastContentChangeTimestamp > 2000
            ) {
                if (SUPPORTED_APPS.contains(event.packageName)) {
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
                    val pkg = event.packageName.toString()
                    val detector = getOrCreateDetector(pkg)
                    detector.onScrollEvent()
                }
            }
        } catch (_: Exception) { }
    }

    /**
     * Checks if this scroll event is happening on a reel view (not just any scroll in the app).
     */
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

    private fun getOrCreateDetector(pkg: String): BurstDetector {
        return burstDetectors.getOrPut(pkg) {
            val defaultEpb = DEFAULT_EVENTS_PER_BURST[pkg] ?: 2f
            val detector = BurstDetector(pkg, defaultEpb)

            // load learned params from Room asynchronously
            scope.launch {
                try {
                    val pattern = scrollPatternDao.getPattern(pkg)
                    if (pattern != null) {
                        detector.learnedEventsPerBurst = pattern.learnedEventsPerBurst
                        detector.learnedBurstGapMs = pattern.learnedBurstGapMs
                        detector.totalBurstsSeen = pattern.totalBurstsSeen
                    }
                } catch (_: Exception) { }
            }

            detector
        }
    }

    private fun onReelCounted() {
        val date = TimeTools.getCurrentDate()
        todayCount++
        overlayManager.reelsScrolledThisSession = todayCount

        if (isEnabled) {
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

    private fun hideReelCounter() {
        overlayManager.binding?.reelCounter?.visibility = View.GONE
        lastVideoViewFoundTime = null
    }

    /**
     * Adaptive burst detector. Groups rapid scroll events into bursts.
     * One burst = one reel swipe. Uses EMA to learn the typical events-per-burst
     * for each app and self-adjusts its threshold over time.
     */
    private inner class BurstDetector(
        private val packageName: String,
        private val defaultEventsPerBurst: Float
    ) {
        var learnedEventsPerBurst: Float = defaultEventsPerBurst
        var learnedBurstGapMs: Float = DEFAULT_BURST_GAP_MS.toFloat()
        var totalBurstsSeen: Int = 0

        private var currentBurstSize: Int = 0
        private var lastEventTime: Long = 0
        private var burstStartTime: Long = 0
        private var pendingBurstEval: Runnable? = null

        fun onScrollEvent() {
            val now = SystemClock.uptimeMillis()
            val gap = now - lastEventTime

            if (currentBurstSize == 0 || gap > getBurstGap()) {
                // previous burst ended, evaluate it
                if (currentBurstSize > 0) {
                    cancelPendingEval()
                    evaluateBurst()
                }
                // start new burst
                currentBurstSize = 1
                burstStartTime = now
            } else {
                currentBurstSize++
            }

            lastEventTime = now

            // schedule a delayed evaluation in case no more events come
            // (handles the "last burst" that has no following gap to trigger evaluation)
            schedulePendingEval()
        }

        private fun getBurstGap(): Long {
            return if (totalBurstsSeen < BOOTSTRAP_THRESHOLD) {
                DEFAULT_BURST_GAP_MS
            } else {
                learnedBurstGapMs.toLong().coerceIn(200L, 1200L)
            }
        }

        private fun getThreshold(): Int {
            return if (totalBurstsSeen < BOOTSTRAP_THRESHOLD) {
                max(1, defaultEventsPerBurst.roundToInt())
            } else {
                max(1, (learnedEventsPerBurst * 0.6f).roundToInt())
            }
        }

        private fun evaluateBurst() {
            val size = currentBurstSize
            if (size <= 0) return

            if (size >= getThreshold()) {
                onReelCounted()

                // update EMA for events-per-burst
                learnedEventsPerBurst = EMA_ALPHA * size + (1 - EMA_ALPHA) * learnedEventsPerBurst

                // update EMA for burst gap (time from burst start to now is the burst duration,
                // we care about the gap between bursts but as a proxy we use the observed gap)
                val burstDuration = (SystemClock.uptimeMillis() - burstStartTime).toFloat()
                if (burstDuration > 50f) {
                    val newGap = (burstDuration * 1.5f).coerceIn(200f, 1200f)
                    learnedBurstGapMs = EMA_ALPHA * newGap + (1 - EMA_ALPHA) * learnedBurstGapMs
                }

                totalBurstsSeen++
                persistPattern()
            }

            currentBurstSize = 0
        }

        private fun schedulePendingEval() {
            cancelPendingEval()
            val evalDelay = getBurstGap() + 100
            pendingBurstEval = Runnable {
                if (currentBurstSize > 0) {
                    evaluateBurst()
                }
            }
            handler.postDelayed(pendingBurstEval!!, evalDelay)
        }

        private fun cancelPendingEval() {
            pendingBurstEval?.let { handler.removeCallbacks(it) }
            pendingBurstEval = null
        }

        private fun persistPattern() {
            scope.launch {
                try {
                    scrollPatternDao.upsert(
                        ScrollPatternEntity(
                            packageName = packageName,
                            learnedEventsPerBurst = learnedEventsPerBurst,
                            learnedBurstGapMs = learnedBurstGapMs,
                            totalBurstsSeen = totalBurstsSeen
                        )
                    )
                } catch (_: Exception) { }
            }
        }
    }
}