package nethical.digipaws.services

import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import nethical.digipaws.blockers.ReelBlocker
import nethical.digipaws.data.models.AppBlockerWarningScreenConfig

class ViewBlockerService : BaseBlockingService() {

    companion object {
        const val INTENT_ACTION_REFRESH_VIEW_BLOCKER = "nethical.digipaws.refresh.viewblocker"
        const val INTENT_ACTION_REFRESH_VIEW_BLOCKER_COOLDOWN =
            "nethical.digipaws.refresh.viewblocker.cooldown"
    }

    private val reelBlocker = ReelBlocker()
    private var warningScreenConfig = AppBlockerWarningScreenConfig()
    private var lastEventTimeStamp = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isDelayOver(lastEventTimeStamp, 2000)) {
            return
        }
        val rootNode: AccessibilityNodeInfo? = rootInActiveWindow
        if(rootNode==null)return
        reelBlocker.doViewBlockerCheck(event)
        lastEventTimeStamp = SystemClock.uptimeMillis()
    }

    override fun onInterrupt() {
    }


    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onServiceConnected() {
        super.onServiceConnected()
        reelBlocker.setupBlocker(this)
        val info = AccessibilityServiceInfo().apply {
            eventTypes =
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.DEFAULT

        }
        serviceInfo = info
        reelBlocker.setupReceivers()


    }

    override fun onDestroy() {
        super.onDestroy()
        reelBlocker.removeReceivers()
    }


}