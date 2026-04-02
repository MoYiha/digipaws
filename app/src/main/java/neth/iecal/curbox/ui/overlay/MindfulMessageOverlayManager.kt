package neth.iecal.curbox.ui.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlinx.coroutines.*
import neth.iecal.curbox.R
import neth.iecal.curbox.data.models.MindfulMessageConfig
import neth.iecal.curbox.utils.TimeTools // Assuming your package path
import neth.iecal.curbox.utils.UsageStatsHelper // Assuming your package path

class MindfulMessageOverlayManager(private val context: Context) {

    private var overlayView: View? = null
    var isOverlayVisible = false
    private var windowManager: WindowManager? = null

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var updateJob: Job? = null

    private var sessionStartTime = 0L
    private var textView: TextView? = null

    // Initialize the helper once using the provided context
    private val usageHelper = UsageStatsHelper(context)

    @SuppressLint("InflateParams")
    fun startDisplaying(
        pkgName: String,
        config: MindfulMessageConfig
    ) {
        if (!isOverlayVisible || overlayView == null) {
            setupView(config)
            startTicker(pkgName, config)
        }
    }

    private fun setupView(config: MindfulMessageConfig) {
        sessionStartTime = System.currentTimeMillis()
        overlayView = LayoutInflater.from(context).inflate(R.layout.mindfulmsg_overlay, null)
        textView = overlayView?.findViewById<TextView>(R.id.mindful_txt)

        textView?.apply {
            textSize = 14f
            setTextColor(Color.WHITE)
            setTextColor(Color.argb(120,250,250,250))
            setBackgroundColor(Color.argb(95, 0, 0, 0))
            setPadding(32, 32, 32, 32)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = config.position
        }

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager?.addView(overlayView, params)
        isOverlayVisible = true
    }

    private fun startTicker(pkgName: String, config: MindfulMessageConfig) {
        updateJob?.cancel()
        updateJob = scope.launch {
            while (isActive) {
                // 1. Grab fresh stats from the system
                val todayStats = usageHelper.getForegroundStatsByRelativeDay(0)
                val appStat = todayStats.find { it.packageName == pkgName }

                val appUsageToday = TimeTools.formatTime(appStat?.totalTime ?: 0, false)
                val totalScreenTime = TimeTools.formatTime(todayStats.sumOf { it.totalTime }, false)

                // 2. Calculate session duration
                val liveSessionMs = System.currentTimeMillis() - sessionStartTime
                val totalSeconds = liveSessionMs / 1000
                val mins = totalSeconds / 60
                val secs = totalSeconds % 60
                val liveSessionStr = if (mins > 0) "${mins}m ${secs}s" else "${secs}s"

                // 3. Update the UI
                val formatted = config.messages
                    .replace("{app_usage_today}", appUsageToday)
                    .replace("{screentime_today}", totalScreenTime)
                    .replace("{live_session_duration}", liveSessionStr)

                textView?.text = formatted

                delay(1000)
            }
        }
    }

    fun removeOverlay() {
        updateJob?.cancel()
        if (overlayView != null && windowManager != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (e: Exception) {
                // Already detached
            }
            overlayView = null
            isOverlayVisible = false
        }
    }
}