package nethical.digipaws.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * A coroutine-based, thread-safe notification timer manager.
 * Safe to use from Accessibility Services, which run on the main thread
 * but may interact with background components.
 */
class TimerNotification(private val context: Context) {


    enum class TimerState { IDLE, RUNNING, FINISHED }

    private val _timerState = MutableStateFlow(TimerState.IDLE)
    val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

    private val _elapsedMillis = MutableStateFlow(0L)
    val elapsedMillis: StateFlow<Long> = _elapsedMillis.asStateFlow()


    companion object {
        private const val TAG = "NotificationTimerMgr"
        private const val CHANNEL_ID = "TimerNotificationChannel"
        private const val NOTIFICATION_ID = 1001
        private const val TICK_INTERVAL_MS = 1000L
    }

    private val notificationManager: NotificationManager by lazy {
        context.applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    // Dedicated dispatcher so timer work never blocks the main/accessibility thread
    private val timerDispatcher = Dispatchers.Default
    private val scope = CoroutineScope(SupervisorJob() + timerDispatcher)

    private var timerJob: Job? = null
    private val currentTimerId = AtomicReference("")
    private val isRunning = AtomicBoolean(false)

    init {
        createNotificationChannel()
    }

    /**
     * Starts a timer. Safe to call from any thread, including the accessibility thread.
     *
     * @param totalMillis     Total duration of the timer in milliseconds.
     * @param isCountdown     If true, notification counts down; if false, counts up.
     * @param title           Notification title.
     * @param timerId         Unique ID; calling with the same ID while running is a no-op.
     * @param onTickCallback  Invoked every second on [Dispatchers.Main] with display millis.
     * @param onFinishCallback Invoked on [Dispatchers.Main] when the timer completes.
     */
    fun startTimer(
        totalMillis: Long,
        isCountdown: Boolean = true,
        title: String = "Timer",
        timerId: String = "focusMode",
        onTickCallback: ((Long) -> Unit)? = null,
        onFinishCallback: (() -> Unit)? = null,
    ) {
        require(totalMillis > 0) { "totalMillis must be > 0" }

        // No-op if same timer is already running
        if (currentTimerId.get() == timerId && isRunning.get()) {
            Log.d(TAG, "Timer '$timerId' already running — ignoring startTimer call.")
            return
        }

        cancelCurrentTimer()
        currentTimerId.set(timerId)
        isRunning.set(true)
        _timerState.value = TimerState.RUNNING

        timerJob = scope.launch {
            try {
                runTimer(
                    totalMillis = totalMillis,
                    isCountdown = isCountdown,
                    title = title,
                    onTickCallback = onTickCallback,
                    onFinishCallback = onFinishCallback,
                )
            } catch (e: CancellationException) {
                Log.d(TAG, "Timer '$timerId' cancelled.")
                // Normal cancellation — no-op
            } catch (e: Exception) {
                Log.e(TAG, "Timer '$timerId' crashed: ${e.message}", e)
            } finally {
                isRunning.set(false)
            }
        }
    }

    /** Stops the running timer and dismisses the notification. */
    fun stopTimer() {
        cancelCurrentTimer()
        dismissNotification()
        _timerState.value = TimerState.IDLE
        _elapsedMillis.value = 0L
        Log.d(TAG, "Timer stopped and notification dismissed.")
    }

    /**
     * Call this when the host Service is destroyed to avoid coroutine leaks.
     * After this, the manager is unusable.
     */
    fun release() {
        scope.cancel()
        dismissNotification()
        Log.d(TAG, "NotificationTimerManager released.")
    }


    private suspend fun runTimer(
        totalMillis: Long,
        isCountdown: Boolean,
        title: String,
        onTickCallback: ((Long) -> Unit)?,
        onFinishCallback: (() -> Unit)?,
    ) {
        val startTime = System.currentTimeMillis()
        var elapsed = 0L

        while (elapsed < totalMillis) {
            val displayMillis = if (isCountdown) totalMillis - elapsed else elapsed
            _elapsedMillis.value = displayMillis

            // Post notification and callback safely
            updateNotificationUI(title, displayMillis)
            onTickCallback?.let { cb ->
                withContext(Dispatchers.Main) { cb(displayMillis) }
            }

            // Sleep until the next tick, accounting for drift
            val nextTick = startTime + elapsed + TICK_INTERVAL_MS
            val sleepMs = nextTick - System.currentTimeMillis()
            if (sleepMs > 0) delay(sleepMs)

            elapsed = System.currentTimeMillis() - startTime
        }

        // Finished
        dismissNotification()
        _timerState.value = TimerState.FINISHED
        _elapsedMillis.value = if (isCountdown) 0L else totalMillis
        currentTimerId.set("")
        isRunning.set(false)

        onFinishCallback?.let { cb ->
            withContext(Dispatchers.Main) { cb() }
        }
    }

    private fun cancelCurrentTimer() {
        timerJob?.cancel()
        timerJob = null
        currentTimerId.set("")
        isRunning.set(false)
    }

    private fun updateNotificationUI(title: String, remainingMillis: Long) {
        runCatching {
            val hours   = remainingMillis / 3_600_000
            val minutes = (remainingMillis % 3_600_000) / 60_000
            val seconds = (remainingMillis % 60_000)    / 1_000
            val timeString = "%02d:%02d:%02d".format(hours, minutes, seconds)

            val notification = NotificationCompat.Builder(context.applicationContext, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(timeString)
                .setSmallIcon(android.R.drawable.ic_dialog_info) // swap for your icon
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                // Accessibility: makes the notification readable by screen readers
                .setContentInfo(timeString)
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
        }.onFailure { e ->
            Log.e(TAG, "Failed to update notification: ${e.message}", e)
        }
    }

    private fun dismissNotification() {
        runCatching { notificationManager.cancel(NOTIFICATION_ID) }
            .onFailure { Log.w(TAG, "Failed to cancel notification: ${it.message}") }
    }

    private fun createNotificationChannel() {
        runCatching {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Timer Notifications",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Timer progress notifications"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }.onFailure { e ->
            Log.e(TAG, "Failed to create notification channel: ${e.message}", e)
        }
    }
}