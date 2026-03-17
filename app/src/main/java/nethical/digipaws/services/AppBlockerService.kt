package nethical.digipaws.services

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import nethical.digipaws.Constants
import nethical.digipaws.blockers.AppBlocker
import nethical.digipaws.blockers.AppBlocker.Companion.INTENT_ACTION_REFRESH_APP_BLOCKER
import nethical.digipaws.blockers.AppBlocker.Companion.INTENT_ACTION_REFRESH_APP_BLOCKER_COOLDOWN
import nethical.digipaws.blockers.FocusModeBlocker
import nethical.digipaws.blockers.FocusModeBlocker.Companion.INTENT_ACTION_REFRESH_FOCUS_MODE
import nethical.digipaws.ui.activity.MainActivity
import nethical.digipaws.ui.activity.WarningActivity
import nethical.digipaws.utils.NotificationTimerManager
import nethical.digipaws.utils.UsageStatsHelper
import nethical.digipaws.utils.getCurrentKeyboardPackageName
import nethical.digipaws.utils.getDefaultLauncherPackageName

/**
 * Responsible for handling regular app blocking + focus mode
 */
class AppBlockerService : BaseBlockingService() {

    private lateinit var appBlocker : AppBlocker
    private val focusModeBlocker = FocusModeBlocker()


    override fun onCreate() {
        appBlocker = AppBlocker(this)
        super.onCreate()
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        focusModeBlocker.doFocusModeCheck(event)
        appBlocker.doAppBlockerCheck(event)
    }

    override fun onInterrupt() {
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onServiceConnected() {
        super.onServiceConnected()
        appBlocker.setupAppBlocker(this)
        focusModeBlocker.setupFocusMode(this)

        val filter = IntentFilter().apply {
            addAction(INTENT_ACTION_REFRESH_FOCUS_MODE)
            addAction(INTENT_ACTION_REFRESH_APP_BLOCKER)
            addAction(INTENT_ACTION_REFRESH_APP_BLOCKER_COOLDOWN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(refreshReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(refreshReceiver, filter)
        }
    }


    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            when (intent.action) {
                INTENT_ACTION_REFRESH_FOCUS_MODE -> focusModeBlocker.setupFocusMode(this@AppBlockerService)
                INTENT_ACTION_REFRESH_APP_BLOCKER -> appBlocker.setupAppBlocker(this@AppBlockerService)
                INTENT_ACTION_REFRESH_APP_BLOCKER_COOLDOWN -> appBlocker.handlePutCooldownIntentBroadcast(intent)
            }

        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(refreshReceiver)
    }

}
