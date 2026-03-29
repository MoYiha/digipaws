package neth.iecal.curbox.services

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
import neth.iecal.curbox.trackers.ReelsCountTracker
import neth.iecal.curbox.ui.overlay.UsageStatOverlayManager
import java.util.concurrent.TimeUnit
import androidx.core.net.toUri

class UsageTrackingService : BaseBlockingService() {


    private val usageStatOverlayManager by lazy { UsageStatOverlayManager(this) }
    private val reelsCountTracker = ReelsCountTracker()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            reelsCountTracker.onEvent(event)
        } catch (error: Exception) {
            Log.e("Usage Tracking error", error.toString())
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
        reelsCountTracker.setup(this, usageStatOverlayManager)
        reelsCountTracker.setupReceivers()
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "Please provide 'Draw over other apps' permission to make this service work properly. ",
                Toast.LENGTH_LONG
            ).show()

            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri()
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            startActivity(intent)
        }
    }


    override fun onInterrupt() {
    }

    override fun onDestroy() {
        super.onDestroy()
        reelsCountTracker.onDestroy()
    }

}
