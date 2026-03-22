package nethical.digipaws.services

import android.annotation.SuppressLint
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import nethical.digipaws.CrashLogger
import nethical.digipaws.blockers.AppBlocker
import nethical.digipaws.blockers.FocusModeBlocker
import nethical.digipaws.blockers.KeywordBlocker
import nethical.digipaws.blockers.ReelBlocker

/**
 * Responsible for handling blockers
 */
@Suppress("DEPRECATION")
class AppBlockerService : BaseBlockingService() {

    private val appBlocker: AppBlocker = AppBlocker()
    private val focusModeBlocker = FocusModeBlocker()
    private val reelBlocker = ReelBlocker()
    private var keywordBlocker = KeywordBlocker()

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val eventChannel = Channel<AccessibilityEvent>(Channel.CONFLATED) { droppedEvent ->
        droppedEvent.recycle()
    }

    private lateinit var crashLogger: CrashLogger

    override fun onCreate() {
        super.onCreate()
        crashLogger = CrashLogger(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        try {
            appBlocker.doAppBlockerCheck(event)
            focusModeBlocker.doFocusModeCheck(event)
        } catch (t: Throwable) {
            Log.e("error",t.message.toString())
            crashLogger.logNonFatalError(Exception(t))
        }

        val eventCopy = AccessibilityEvent.obtain(event)
        val result = eventChannel.trySend(eventCopy)

        // If the channel is closed or rejected it, recycle immediately
        if (result.isFailure) {
            eventCopy.recycle()
        }
    }

    override fun onInterrupt() {
    }

    private fun startBackgroundWorker() {
        serviceScope.launch {
            for (event in eventChannel) {
                try {
                    reelBlocker.doViewBlockerCheck(event)
                    keywordBlocker.checkIfUserGettingFreaky(event)
                } catch (t: Throwable) {
                    // Don't log normal coroutine cancellations as crashes
                    if (t is CancellationException) throw t

                    crashLogger.logNonFatalError(Exception(t))
                    Log.e("Blocker", "Background worker error", t)
                } finally {
                    event.recycle()
                }
            }
        }
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

        startBackgroundWorker()
    }

    override fun onDestroy() {
        super.onDestroy()
        focusModeBlocker.removeReceivers()
        reelBlocker.removeReceivers()
        appBlocker.onDestroy()
        keywordBlocker.removeReceivers()

        eventChannel.close()
        serviceScope.cancel()
    }
}