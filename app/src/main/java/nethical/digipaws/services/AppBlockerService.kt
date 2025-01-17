package nethical.digipaws.services

import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import nethical.digipaws.Constants
import nethical.digipaws.blockers.AppBlocker
import nethical.digipaws.ui.activity.MainActivity
import nethical.digipaws.ui.activity.WarningActivity

class AppBlockerService : BaseBlockingService() {

    companion object {
        const val INTENT_ACTION_REFRESH_APP_BLOCKER = "nethical.digipaws.refresh.appblocker"
        const val INTENT_ACTION_REFRESH_APP_BLOCKER_COOLDOWN =
            "nethical.digipaws.refresh.appblocker.cooldown"
    }

    private val appBlocker = AppBlocker()

    private var warningScreenConfig = MainActivity.WarningData()


    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if(isDelayOver(4000)){
            val packageName = event?.packageName.toString()
            handleAppBlockerResult(appBlocker.doesAppNeedToBeBlocked(packageName), packageName)
        }
    }



    private fun handleAppBlockerResult(result: AppBlocker.AppBlockerResult?, packageName: String) {
        Log.d("Appblocker result", result.toString())
        if (result == null || !result.isBlocked) return

        lastEventActionTakenTimeStamp = SystemClock.uptimeMillis()

        if(warningScreenConfig.isWarningDialogHidden){
            pressHome()
            return
        }

        val dialogIntent = Intent(this, WarningActivity::class.java)
        dialogIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        dialogIntent.putExtra("warning_message", warningScreenConfig.message)
        dialogIntent.putExtra("mode", Constants.WARNING_SCREEN_MODE_APP_BLOCKER)
        dialogIntent.putExtra("is_dynamic_timing", warningScreenConfig.isDynamicIntervalSettingAllowed)
        dialogIntent.putExtra("result_id", packageName)
        dialogIntent.putExtra("default_cooldown", warningScreenConfig.timeInterval / 60000)
        dialogIntent.putExtra("is_proceed_disabled", warningScreenConfig.isProceedDisabled)
        startActivity(dialogIntent)

    }

    override fun onInterrupt() {
        TODO("Not yet implemented")
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onServiceConnected() {
        super.onServiceConnected()
        setupBlocker()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.DEFAULT
        }
        serviceInfo = info


        val filter = IntentFilter().apply {
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
                INTENT_ACTION_REFRESH_APP_BLOCKER -> setupBlocker()
                INTENT_ACTION_REFRESH_APP_BLOCKER_COOLDOWN -> {
                    val interval = intent.getIntExtra("selected_time", warningScreenConfig.timeInterval)
                    appBlocker.putCooldownTo(
                        intent.getStringExtra("result_id") ?: "xxxxxxxxxxxxxx",
                        SystemClock.uptimeMillis() + interval
                    )
                }
            }

        }
    }


    private fun setupBlocker() {
        appBlocker.blockedAppsList = savedPreferencesLoader.loadBlockedApps().toHashSet()
        appBlocker.refreshCheatMinutesData(savedPreferencesLoader.loadAppBlockerCheatHoursList())

        warningScreenConfig = savedPreferencesLoader.loadAppBlockerWarningInfo()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(refreshReceiver)
    }

}