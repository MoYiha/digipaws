package nethical.digipaws.services

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import nethical.digipaws.blockers.BrowserBlocker
import nethical.digipaws.blockers.KeywordBlocker
import nethical.digipaws.blockers.KeywordBlocker.Companion.INTENT_ACTION_REFRESH_CONFIG
import nethical.digipaws.data.blockers.KeywordPacks

class KeywordBlockerService : BaseBlockingService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {

    }

    override fun onInterrupt() {
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onServiceConnected() {
        super.onServiceConnected()
    }



    override fun onDestroy() {
        super.onDestroy()
    }
}