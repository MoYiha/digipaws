package nethical.digipaws.services

import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import nethical.digipaws.trackers.ReelsCountTracker
import nethical.digipaws.ui.overlay.UsageStatOverlayManager
import java.util.concurrent.TimeUnit

class UsageTrackingService : BaseBlockingService() {

    private var screenOnTime: Long = 0
    private var accumulatedTime: Long = 0
    private var isScreenOn = false
    private val handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null

    private val usageStatOverlayManager by lazy { UsageStatOverlayManager(this) }
    private val reelsCountTracker = ReelsCountTracker()

    private var isTimeElapsedCounterOn = true

    private var displayOverlayApps = hashSetOf("")

    companion object {
        const val INTENT_ACTION_REFRESH_USAGE_TRACKER = "nethical.digipaws.refresh.usage_tracker"
        private const val UPDATE_INTERVAL = 1000L
        private const val TAG = "ScreenTimeTracking"
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!isTimeElapsedCounterOn) return
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> handleScreenOn()
                Intent.ACTION_SCREEN_OFF -> handleScreenOff()
            }
        }
    }

    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                INTENT_ACTION_REFRESH_USAGE_TRACKER -> setupTracker()
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onServiceConnected() {
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes =
                AccessibilityEvent.TYPE_VIEW_SCROLLED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.DEFAULT
        }

        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        })

        val filter = IntentFilter().apply {
            addAction(INTENT_ACTION_REFRESH_USAGE_TRACKER)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(refreshReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(refreshReceiver, filter)
        }

        setupTracker()
        reelsCountTracker.setup(this, usageStatOverlayManager)

        if (Settings.canDrawOverlays(this)) {
//            usageStatOverlayManager.startDisplaying()
        } else {
            Toast.makeText(
                this,
                "Please provide 'Draw over other apps' permission to make this service work properly. ",
                Toast.LENGTH_LONG
            ).show()

            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            startActivity(intent)
        }
    }

    private fun setupTracker() {
        val sp = getSharedPreferences("config_tracker", Context.MODE_PRIVATE)

        isTimeElapsedCounterOn = sp.getBoolean("is_time_elapsed", false)

        displayOverlayApps = savedPreferencesLoader.getOverlayApps().toHashSet()
        displayOverlayApps.addAll(ReelsCountTracker.SUPPORTED_APPS)

        if (!isTimeElapsedCounterOn) {
            usageStatOverlayManager.binding?.timeElapsedTxt?.visibility = View.GONE
            handleScreenOff()
        } else {
            usageStatOverlayManager.binding?.timeElapsedTxt?.visibility = View.VISIBLE
            if ((getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive) {
                handleScreenOn()
            }
        }
    }

    private fun handleScreenOn() {
        isScreenOn = true
        screenOnTime = System.currentTimeMillis()
        startTimeTracking()
        Log.d(TAG, "Screen ON - Continuing from: ${formatElapsedTime(accumulatedTime)}")
    }

    private fun handleScreenOff() {
        isScreenOn = false
        updateAccumulatedTime()
        stopTimeTracking()
        Log.d(TAG, "Screen OFF - Current accumulated: ${formatElapsedTime(accumulatedTime)}")
    }

    private fun startTimeTracking() {
        stopTimeTracking()
        updateRunnable = object : Runnable {
            override fun run() {
                if (isScreenOn) {
                    val currentTime = System.currentTimeMillis()
                    val totalTime = accumulatedTime + (currentTime - screenOnTime)
                    usageStatOverlayManager.binding?.timeElapsedTxt?.text =
                        formatElapsedTime(totalTime)
                    handler.postDelayed(this, UPDATE_INTERVAL)
                }
            }
        }
        handler.post(updateRunnable!!)
    }

    private fun stopTimeTracking() {
        updateRunnable?.let { handler.removeCallbacks(it) }
        updateRunnable = null
    }

    private fun updateAccumulatedTime() {
        if (isScreenOn) {
            accumulatedTime += System.currentTimeMillis() - screenOnTime
            screenOnTime = System.currentTimeMillis()
        }
    }

    private fun formatElapsedTime(milliseconds: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(milliseconds)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            if (displayOverlayApps.contains(event?.packageName)) {
                if (Settings.canDrawOverlays(this)) {
                    usageStatOverlayManager.startDisplaying()
                }
            } else if (usageStatOverlayManager.isOverlayVisible) {
                usageStatOverlayManager.removeOverlay()
            }

            reelsCountTracker.onEvent(event)
        } catch (_: Exception) { }
    }

    override fun onInterrupt() {
        stopTimeTracking()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) { }
        stopTimeTracking()
    }

    data class AttentionSpanVideoItem(
        val elapsedTime: Float,
        val time: String,
        val type: Int = 1
    )
}
