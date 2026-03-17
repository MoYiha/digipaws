package nethical.digipaws.blockers

import android.accessibilityservice.AccessibilityService
import android.media.metrics.Event
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import nethical.digipaws.Constants
import nethical.digipaws.services.BaseBlockingService
import nethical.digipaws.ui.activity.TimedActionActivity
import nethical.digipaws.utils.TimeTools
import nethical.digipaws.utils.getCurrentKeyboardPackageName
import nethical.digipaws.utils.getDefaultLauncherPackageName
import java.util.Calendar


/**
 * Stores information related to manual focus mode
 *
 * @property isTurnedOn specifies if manual focys mode is turned on
 * @property endTime specifies when manual focus hours ends. -1 if not under manual focus hours
 * @property modeType Can either be of type [Constants.FOCUS_MODE_BLOCK_ALL_EX_SELECTED] or [Constants.FOCUS_MODE_BLOCK_SELECTED].
 * @property selectedApps
 */

data class FocusModeData(
    var isTurnedOn: Boolean = false,
    val endTime: Long = -1,
    val modeType: Int = Constants.FOCUS_MODE_BLOCK_ALL_EX_SELECTED,
    var selectedApps: HashSet<String> = hashSetOf()
)


class FocusModeBlocker : BaseBlocker() {

    companion object {
        /**
         * Refreshes information related to focus mode.
         */
        const val INTENT_ACTION_REFRESH_FOCUS_MODE = "nethical.digipaws.refresh.focus_mode"
    }
    // package-name -> [(start-time, end-time), ...]
    private var autoFocusHours: MutableMap<String, List<Pair<Int, Int>>> = mutableMapOf()

    private var focusModeData = FocusModeData()

    private var lastPackage = ""

    private lateinit var service : BaseBlockingService

    fun doFocusModeCheck(event: AccessibilityEvent?){
        val packageName = event?.packageName.toString()
        if (lastPackage == packageName || packageName == service.getPackageName()) return

        lastPackage = packageName

        fun performFocusModeAction(){
            service.pressHome()
            lastPackage = ""
            Toast.makeText(service, "This app is currently under focus mode", Toast.LENGTH_LONG).show()
        }

        if (focusModeData.isTurnedOn) {
            if (focusModeData.endTime < System.currentTimeMillis()) { // turn off manual focus mode
                focusModeData.isTurnedOn = false
                service.savedPreferencesLoader.saveFocusModeData(focusModeData)
            }

            // deal with auto focus
            when (focusModeData.modeType) {
                Constants.FOCUS_MODE_BLOCK_SELECTED -> {
                    if (focusModeData.selectedApps.contains(packageName)) performFocusModeAction()
                }

                Constants.FOCUS_MODE_BLOCK_ALL_EX_SELECTED -> {
                    if (!focusModeData.selectedApps.contains(packageName)) performFocusModeAction()
                }
            }
        }
    }

    private fun refreshCheatHoursData(focusData: List<TimedActionActivity.AutoTimedActionItem>) {
        autoFocusHours.clear()
        focusData.forEach { item ->
            val startTime = item.startTimeInMins
            val endTime = item.endTimeInMins
            val packageNames: ArrayList<String> = item.packages

            packageNames.forEach { packageName ->

                if (autoFocusHours.containsKey(packageName)) {
                    val cheatHourTimeData: List<Pair<Int, Int>>? = autoFocusHours[packageName]
                    val cheatHourNewTimeData: MutableList<Pair<Int, Int>> =
                        cheatHourTimeData!!.toMutableList()

                    cheatHourNewTimeData.add(Pair(startTime, endTime))
                    autoFocusHours[packageName] = cheatHourNewTimeData
                } else {
                    autoFocusHours[packageName] = listOf(Pair(startTime, endTime))
                }
            }
        }
        Log.d("FocusModeBlocker", "Auto Focus Data updated $autoFocusHours")

    }

    fun setupFocusMode(service: BaseBlockingService) {
        this.service = service
        refreshCheatHoursData(service.savedPreferencesLoader.loadAutoFocusHoursList())

        val selectedFocusModeApps = service.savedPreferencesLoader.getFocusModeSelectedApps().toHashSet()
        focusModeData = service.savedPreferencesLoader.getFocusModeData()

        // As all apps will get blocked except the selected ones, add essential packages that need not be blocked (systemui, launcher, keyboard)
        // to the list of selected apps
        if (focusModeData.modeType == Constants.FOCUS_MODE_BLOCK_ALL_EX_SELECTED) {
            selectedFocusModeApps.add("com.android.systemui")
            getDefaultLauncherPackageName(service.packageManager)?.let { selectedFocusModeApps.add(it) }
            getCurrentKeyboardPackageName(service)?.let { selectedFocusModeApps.add(it) }
        }

        focusModeData.selectedApps = selectedFocusModeApps

    }


}