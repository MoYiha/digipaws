package nethical.digipaws.blockers

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.RECEIVER_EXPORTED
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import nethical.digipaws.Constants
import nethical.digipaws.data.models.ReelBlocker
import nethical.digipaws.data.models.ReelBlockingType
import nethical.digipaws.data.models.ReelTimeConfig
import nethical.digipaws.services.BaseBlockingService
import nethical.digipaws.services.ViewBlockerService.Companion.INTENT_ACTION_REFRESH_VIEW_BLOCKER
import nethical.digipaws.services.ViewBlockerService.Companion.INTENT_ACTION_REFRESH_VIEW_BLOCKER_COOLDOWN
import nethical.digipaws.ui.activity.WarningActivity
import nethical.digipaws.utils.TimeTools
import java.util.Calendar


class ReelBlocker : BaseBlocker() {

    companion object {
        const val INTENT_ACTION_REFRESH_REEL_BLOCKER = "nethical.digipaws.refresh.reelblocker"
        const val INTENT_ACTION_REFRESH_REEL_BLOCKER_COOLDOWN =
            "nethical.digipaws.refresh.reelblocker.cooldown"

        fun findElementById(node: AccessibilityNodeInfo?, id: String?): AccessibilityNodeInfo? {
            if (node == null) return null
            var targetNode: AccessibilityNodeInfo? = null
            try {
                targetNode = node.findAccessibilityNodeInfosByViewId(id!!)[0]
            } catch (e: Exception) {
                //	e.printStackTrace();
            }
            return targetNode
        }

        val BLOCKED_VIEW_ID_LIST = mutableListOf(
            "com.instagram.android:id/root_clips_layout",
            "com.myinsta.android:id/root_clips_layout",
            "com.google.android.youtube:id/reel_recycler",
            "app.revanced.android.youtube:id/reel_recycler"
        )

        private const val TARGET_EVENTS_MASK = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_VIEW_SCROLLED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_FOCUSED

    }
    private lateinit var service : BaseBlockingService

    private var reelBlockerConfig: ReelBlocker = ReelBlocker(isActive = false)
    private var  timeBAsedConfig : ReelTimeConfig? = null
    private val cooldownViewIdsList = mutableMapOf<String, Long>()
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0

    private var lastEventTimeStamp = 0L

    fun doViewBlockerCheck(
        event: AccessibilityEvent?
    ){
        fun showWarningScreen(viewId: String){
            service.pressBack()

            if(reelBlockerConfig.warningScreenConfig.isWarningDialogHidden) return
            val dialogIntent = Intent(service, WarningActivity::class.java)
            dialogIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            dialogIntent.putExtra("mode", Constants.WARNING_SCREEN_MODE_VIEW_BLOCKER)
            dialogIntent.putExtra("result_id", viewId)
            dialogIntent.putExtra("warning_config", Gson().toJson(reelBlockerConfig.warningScreenConfig))
            service.startActivity(dialogIntent)
        }
        if (event == null || (event.eventType and TARGET_EVENTS_MASK) == 0) return

        if (!reelBlockerConfig.isActive ) {
            return
        }


        val node = service.rootInActiveWindow
        if(node==null) return

        BLOCKED_VIEW_ID_LIST.forEach { viewId ->
            if(isViewOpened(node,viewId)){
                // ignore if view-id under cooldown
                if (isCooldownActive(viewId)) {
                    return
                }

                // check if currently under allowed hours
                when(reelBlockerConfig.blockingType) {
                    ReelBlockingType.TIMED -> {
                        val endAllowedMillis = getEndTimeInMillis()
                        if(endAllowedMillis==null) {
                            showWarningScreen(viewId)
                        }
                        Log.d("stuff","stuff")

                    }
                    ReelBlockingType.USAGE -> TODO()
                    ReelBlockingType.REEL_COUNT -> TODO()
                }

            }
        }
        lastEventTimeStamp = SystemClock.uptimeMillis()

    }


    fun applyCooldown(viewId: String, endTime: Long) {
        cooldownViewIdsList[viewId] = endTime
    }


    fun setupBlocker(service: BaseBlockingService) {
        this.service = service
        var displayMetrics: DisplayMetrics = service.resources.displayMetrics
        screenHeight = displayMetrics.heightPixels
        screenWidth = displayMetrics.widthPixels


        CoroutineScope(Dispatchers.IO).launch {
            service.dataStoreManager.settings.collectLatest { settings ->
                reelBlockerConfig = settings.reelBlockerConfig
                when(reelBlockerConfig.blockingType) {
                    ReelBlockingType.TIMED -> {
                        timeBAsedConfig = Gson().fromJson<ReelTimeConfig>(settings.reelBlockerConfig.settings,
                            ReelTimeConfig::class.java)
                    }
                    ReelBlockingType.USAGE -> TODO()
                    ReelBlockingType.REEL_COUNT -> TODO()
                }
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun setupReceivers(){
        val filter = IntentFilter().apply {
            addAction(INTENT_ACTION_REFRESH_VIEW_BLOCKER)
            addAction(INTENT_ACTION_REFRESH_VIEW_BLOCKER_COOLDOWN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            service.registerReceiver(refreshReceiver, filter, RECEIVER_EXPORTED)
        } else {
            service.registerReceiver(refreshReceiver, filter)
        }
    }

    fun removeReceivers(){
        service.unregisterReceiver(refreshReceiver)
    }

    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            when (intent.action) {
                INTENT_ACTION_REFRESH_VIEW_BLOCKER -> setupBlocker(service)

                INTENT_ACTION_REFRESH_VIEW_BLOCKER_COOLDOWN -> {
                    val interval = intent.getIntExtra("selected_time", reelBlockerConfig.warningScreenConfig.timeInterval)
                    applyCooldown(
                        intent.getStringExtra("result_id") ?: "xxxxxxxxxxxxxx",
                        SystemClock.uptimeMillis() + interval
                    )
                }
            }
        }
    }

    private fun isCooldownActive(viewId: String): Boolean {
        val cooldownEnd = cooldownViewIdsList[viewId] ?: return false
        if (SystemClock.uptimeMillis() > cooldownEnd) {
            cooldownViewIdsList.remove(viewId)
            return false
        }
        return true
    }

    private fun isViewOpened(rootNode: AccessibilityNodeInfo, viewId: String): Boolean {
        val viewNode =
            findElementById(rootNode, viewId)
        val nodeRect = Rect()
        viewNode?.getBoundsInScreen(nodeRect)
        val isOffScreenLeft = nodeRect.right <= 0
        val isOffScreenRight = nodeRect.left >= screenWidth
        return (viewNode != null && !isOffScreenLeft && !isOffScreenRight)
    }

    /**
     * @return null if reels is not currently allowed by time config, or the end time in uptimeMillis if allowed.
     */
    private fun getEndTimeInMillis(): Long? {

        if(timeBAsedConfig==null) return null
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)
        val currentMinutes = TimeTools.convertToMinutesFromMidnight(currentHour, currentMinute)

        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1 // 0=Sunday, 1=Monday...

        val intervals = if (timeBAsedConfig!!.isEveryday) {
            timeBAsedConfig!!.everydayIntervals
        } else {
            timeBAsedConfig!!.dailyIntervals[dayOfWeek] ?: emptyList()
        }

        intervals.forEach { interval ->
            val startMinutes = TimeTools.convertToMinutesFromMidnight(interval.startHour, interval.startMinute)
            val endMinutes = TimeTools.convertToMinutesFromMidnight(interval.endHour, interval.endMinute)

            if (startMinutes <= endMinutes) {
                if (currentMinutes in startMinutes until endMinutes) {
                    val remainingMins = endMinutes - currentMinutes
                    return SystemClock.uptimeMillis() + (remainingMins * 60 * 1000L)
                }
            } else {
                // cross midnight
                if (currentMinutes >= startMinutes || currentMinutes < endMinutes) {
                    val remainingMins = if (currentMinutes >= startMinutes) {
                        (1440 - currentMinutes) + endMinutes
                    } else {
                        endMinutes - currentMinutes
                    }
                    return SystemClock.uptimeMillis() + (remainingMins * 60 * 1000L)
                }
            }
        }
        return null
    }

    data class ViewBlockerResult(
        val isBlocked: Boolean = false,
        val requestHomePressInstead: Boolean = false,
        val isReelFoundInCooldownState: Boolean = false,
        val viewId: String = ""
    )

}
