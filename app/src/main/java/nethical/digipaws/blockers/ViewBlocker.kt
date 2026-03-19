package nethical.digipaws.blockers

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat.startActivity
import nethical.digipaws.Constants
import nethical.digipaws.data.models.AppBlockerWarningScreenConfig
import nethical.digipaws.services.BaseBlockingService
import nethical.digipaws.ui.activity.WarningActivity
import nethical.digipaws.utils.TimeTools
import java.util.Calendar


class ViewBlocker : BaseBlocker() {
    companion object {
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

    }
    private lateinit var service : BaseBlockingService

    private var warningScreenConfig = AppBlockerWarningScreenConfig()
    private val cooldownViewIdsList = mutableMapOf<String, Long>()
    var screenWidth: Int = 0
    var screenHeight: Int = 0

    var isIGInboxReelAllowed = false

    var cheatMinuteStartTime: Int? = null
    var cheatMinutesEndTIme: Int? = null

    fun doesViewNeedToBeBlocked(
        node: AccessibilityNodeInfo,
    ){
        fun showWarningScreen(viewId: String){
            service.pressBack()

            if(warningScreenConfig.isWarningDialogHidden) return
            val dialogIntent = Intent(service, WarningActivity::class.java)
            dialogIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            dialogIntent.putExtra("mode", Constants.WARNING_SCREEN_MODE_VIEW_BLOCKER)
            dialogIntent.putExtra("result_id", viewId)
            service.startActivity(dialogIntent)
        }

        if (isCheatHourActive()) {
            return
        }

        if (isIGInboxReelAllowed && isViewOpened(
                node,
                "com.instagram.android:id/reply_bar_container"
            )
        ) {
            return
        }


        BLOCKED_VIEW_ID_LIST.forEach { viewId ->
            if(isViewOpened(node,viewId)){
                if (!isCooldownActive(viewId)) {
                    showWarningScreen(viewId)
                }
            }
        }

    }


    fun applyCooldown(viewId: String, endTime: Long) {
        cooldownViewIdsList[viewId] = endTime
    }


//    private fun setupBlocker() {
//        warningScreenConfig = savedPreferencesLoader.loadViewBlockerWarningInfo()
//
//        val viewBlockerCheatHours = getSharedPreferences("cheat_hours", Context.MODE_PRIVATE)
//        viewBlocker.cheatMinuteStartTime =
//            viewBlockerCheatHours.getInt("view_blocker_start_time", -1)
//        viewBlocker.cheatMinutesEndTIme = viewBlockerCheatHours.getInt("view_blocker_end_time", -1)
//
//        val addReelData = getSharedPreferences("config_reels", Context.MODE_PRIVATE)
//        viewBlocker.isIGInboxReelAllowed = addReelData.getBoolean("is_reel_inbox", false)
//        viewBlocker.isFirstReelInFeedAllowed = addReelData.getBoolean("is_reel_first", false)
//        Log.d("data", viewBlocker.isFirstReelInFeedAllowed.toString())
//    }

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

    private fun isCheatHourActive(): Boolean {

        val currentTime = Calendar.getInstance()
        val currentHour = currentTime.get(Calendar.HOUR_OF_DAY)
        val currentMinute = currentTime.get(Calendar.MINUTE)

        val currentMinutes = TimeTools.convertToMinutesFromMidnight(currentHour, currentMinute)

        // If cheat hours are not set, treat as inactive
        if (cheatMinuteStartTime == null || cheatMinutesEndTIme == null || cheatMinuteStartTime == -1 || cheatMinutesEndTIme == -1) {
            return false
        }

        return if (cheatMinuteStartTime!! <= cheatMinutesEndTIme!!) {
            // Regular case: start time is before or equal to end time
            currentMinutes in cheatMinuteStartTime!!..cheatMinutesEndTIme!!
        } else {
            // Wraparound case: time range spans midnight
            currentMinutes in cheatMinuteStartTime!!..1439 || currentMinutes in 0..cheatMinutesEndTIme!!
        }
    }
    data class ViewBlockerResult(
        val isBlocked: Boolean = false,
        val requestHomePressInstead: Boolean = false,
        val isReelFoundInCooldownState: Boolean = false,
        val viewId: String = ""
    )

}
