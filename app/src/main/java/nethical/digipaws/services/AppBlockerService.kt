package nethical.digipaws.services

import android.annotation.SuppressLint
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import nethical.digipaws.blockers.AppBlocker
import nethical.digipaws.blockers.FocusModeBlocker
import nethical.digipaws.blockers.KeywordBlocker
import nethical.digipaws.blockers.ReelBlocker

/**
 * Responsible for handling regular app blocking + focus mode
 */
class AppBlockerService : BaseBlockingService() {

    private lateinit var appBlocker : AppBlocker

    private val focusModeBlocker = FocusModeBlocker()
    private val reelBlocker = ReelBlocker()
    private var keywordBlocker = KeywordBlocker()

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val eventChannel = Channel<AccessibilityEvent>(Channel.CONFLATED)
    override fun onCreate() {
        appBlocker = AppBlocker(this)
        super.onCreate()
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        focusModeBlocker.doFocusModeCheck(event)
        appBlocker.doAppBlockerCheck(event)
        reelBlocker.doViewBlockerCheck(event)
        keywordBlocker.checkIfUserGettingFreaky(event)
    }

    override fun onInterrupt() {
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onServiceConnected() {
        super.onServiceConnected()
        appBlocker.setupAppBlocker(this)
        focusModeBlocker.setupFocusMode(this)
        reelBlocker.setupBlocker(this)
        keywordBlocker.setupBlocker(this)

        focusModeBlocker.setupReceivers()
        appBlocker.setupReceivers()
        reelBlocker.setupReceivers()
        keywordBlocker.setupReceivers()

    }


    override fun onDestroy() {
        super.onDestroy()
        focusModeBlocker.removeReceivers()
        reelBlocker.removeReceivers()
        appBlocker.onDestroy()
        keywordBlocker.removeReceivers()
    }

}
