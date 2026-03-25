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
import nethical.digipaws.services.BaseBlockingService
import nethical.digipaws.ui.overlay.UsageStatOverlayManager
import nethical.digipaws.utils.TimeTools

class ReelsCountTracker {

    companion object {
        private val MIN_SCROLL_THRESHOLD = mapOf(
            "com.ss.android.ugc.trill" to 1,
            "com.zhiliaoapp.musically" to 1,
            "com.ss.android.ugc.aweme" to 1,
            "com.google.android.youtube" to 2,
            "app.revanced.android.youtube" to 2,
            "com.facebook.katana" to 2,
            "com.instagram.android" to 2,
            "com.myinsta.android" to 2
        )

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

        private const val SCROLL_DEBOUNCE_TIME = 800L
        private const val MIN_SCROLL_DISTANCE = 100f
    }

    private lateinit var service: BaseBlockingService
    private lateinit var overlayManager: UsageStatOverlayManager
    private lateinit var dao: ReelStatsDao

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var isEnabled = true
    private var todayCount = 0

    private var scrollEventCounter: Long = 0
    private var lastScrollTime: Long = 0
    private var lastScrollY: Float = 0f
    private var isScrollInProgress = false
    private var lastVideoViewFoundTime: Long? = null
    private var lastContentChangeTimestamp = 0L

    private val handler = Handler(Looper.getMainLooper())

    fun setup(service: BaseBlockingService, overlayManager: UsageStatOverlayManager) {
        this.service = service
        this.overlayManager = overlayManager
        this.dao = AppDatabase.getInstance(service).reelStatsDao()

        // load config from DataStore
        scope.launch {
            service.dataStoreManager.settings.collectLatest { settings ->
                isEnabled = settings.isReelCounterOn
            }
        }

        // load today's count from Room
        scope.launch {
            try {
                todayCount = dao.getCount(TimeTools.getCurrentDate()) ?: 0
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
                val currentTime = System.currentTimeMillis()
                val scrollY = event.scrollY.toFloat()

                if (!isScrollInProgress ||
                    (currentTime - lastScrollTime > SCROLL_DEBOUNCE_TIME &&
                            Math.abs(scrollY - lastScrollY) > MIN_SCROLL_DISTANCE)
                ) {
                    detectAndHandleScroll(event)
                    lastScrollY = scrollY
                    lastScrollTime = currentTime
                }
            }
        } catch (_: Exception) { }
    }

    private fun detectAndHandleScroll(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        val className = try { event.source?.className?.toString() } catch (_: Exception) { null }

        try {
            when {
                TIKTOK_PACKAGES.contains(pkg) && className == "androidx.viewpager.widget.ViewPager" ->
                    handleScroll(pkg)

                pkg == "com.facebook.katana" && className == "androidx.recyclerview.widget.RecyclerView" -> {
                    val root = service.rootInActiveWindow ?: return
                    val nodes = root.findAccessibilityNodeInfosByText(
                        "FbShortsComposerAttachmentComponentSpec_STICKER"
                    )
                    if (nodes?.firstOrNull() != null) handleScroll(pkg)
                }

                pkg == "com.instagram.android" && className == "androidx.viewpager.widget.ViewPager" -> {
                    val root = service.rootInActiveWindow ?: return
                    if (ReelBlocker.findElementById(root, "com.instagram.android:id/root_clips_layout") != null)
                        handleScroll(pkg)
                    else hideReelCounter()
                }

                pkg == "com.myinsta.android" && className == "androidx.viewpager.widget.ViewPager" -> {
                    val root = service.rootInActiveWindow ?: return
                    if (ReelBlocker.findElementById(root, "com.myinsta.android:id/root_clips_layout") != null)
                        handleScroll(pkg)
                    else hideReelCounter()
                }

                pkg == "com.google.android.youtube" && className == "android.support.v7.widget.RecyclerView" -> {
                    val root = service.rootInActiveWindow ?: return
                    val reelView = ReelBlocker.findElementById(root, "com.google.android.youtube:id/reel_recycler")
                    val comments = ReelBlocker.findElementById(root, "com.google.android.youtube:id/engagement_panel_content")
                    if (reelView != null && comments == null) handleScroll(pkg) else hideReelCounter()
                }

                pkg == "app.revanced.android.youtube" && className == "android.support.v7.widget.RecyclerView" -> {
                    val root = service.rootInActiveWindow ?: return
                    val reelView = ReelBlocker.findElementById(root, "app.revanced.android.youtube:id/reel_recycler")
                    val comments = ReelBlocker.findElementById(root, "app.revanced.android.youtube:id/engagement_panel_content")
                    if (reelView != null && comments == null) handleScroll(pkg) else hideReelCounter()
                }
            }
        } catch (_: Exception) { }
    }

    private fun handleScroll(packageName: String) {
        if (++scrollEventCounter > (MIN_SCROLL_THRESHOLD[packageName] ?: 1)) {
            isScrollInProgress = true
            scrollEventCounter = 0

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

            // persist to Room asynchronously
            scope.launch {
                try {
                    dao.upsert(ReelStatsEntity(date = date, count = todayCount))
                } catch (_: Exception) { }
            }

            service.lastBackPressTimeStamp = SystemClock.uptimeMillis()

            handler.postDelayed({ isScrollInProgress = false }, SCROLL_DEBOUNCE_TIME)
        }
    }

    private fun hideReelCounter() {
        overlayManager.binding?.reelCounter?.visibility = View.GONE
        lastVideoViewFoundTime = null
    }
}