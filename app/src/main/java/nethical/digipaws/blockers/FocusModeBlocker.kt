package nethical.digipaws.blockers

import android.accessibilityservice.AccessibilityService
import android.media.metrics.Event
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nethical.digipaws.Constants
import nethical.digipaws.data.models.FocusBlockMode
import nethical.digipaws.data.models.ManualFocusGroup
import nethical.digipaws.services.BaseBlockingService
import nethical.digipaws.ui.activity.TimedActionActivity
import nethical.digipaws.utils.DataStoreManager
import nethical.digipaws.utils.NotificationTimerManager
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

    private data class ManualFocusModeData(
        val focusGroupData: ManualFocusGroup,
        val endTimeInMillis: Long
    )
    companion object {
        /**
         * Refreshes information related to focus mode.
         */
        const val INTENT_ACTION_REFRESH_FOCUS_MODE = "nethical.digipaws.refresh.focus_mode"
    }
    // package-name -> [(start-time, end-time), ...]
    private var autoFocusHours: MutableMap<String, List<Pair<Int, Int>>> = mutableMapOf()

    // Pair(FocusData, endtimeinmillis)
    private var focusModeData : ManualFocusModeData?  = null

    private var lastPackage = ""

    private lateinit var service : BaseBlockingService

    private lateinit var notificationManager: NotificationTimerManager

    private fun turnOffFocusMode(){
        focusModeData = null
        CoroutineScope(Dispatchers.IO).launch {
            service.dataStoreManager.setManualFocusStateToInactive()
        }
        notificationManager.stopTimer()
    }
    fun doFocusModeCheck(event: AccessibilityEvent?){
        val packageName = event?.packageName.toString()
        if (lastPackage == packageName || packageName == service.getPackageName()) return

        lastPackage = packageName

        fun performFocusModeAction(){
            service.pressHome()
            lastPackage = ""
            Toast.makeText(service, "This app is currently under focus mode", Toast.LENGTH_LONG).show()
        }

        if (focusModeData!= null) {
            when (focusModeData!!.focusGroupData.blockMode) {
                FocusBlockMode.BLOCK_SELECTED -> {
                    if (focusModeData!!.focusGroupData.packages.contains(packageName)) performFocusModeAction()
                }
                FocusBlockMode.BLOCK_ALL_EXCEPT_SELECTED -> {
                    if (!focusModeData!!.focusGroupData.packages.contains(packageName)) performFocusModeAction()
                }
            }

            if (focusModeData!!.endTimeInMillis < System.currentTimeMillis()) { // turn off manual focus mode
                turnOffFocusMode()
            }


        }
    }

    fun setupFocusMode(service: BaseBlockingService) {
        this.service = service

        notificationManager = NotificationTimerManager(service)

        val selectedFocusModeApps = mutableListOf<String>()
        CoroutineScope(Dispatchers.IO).launch {
            service.dataStoreManager.settings.collectLatest { settings ->
                // manual focus mode has been turned on / is turn on
                if(settings.activeManualFocusGroupId.first != null){
                    val currentFocusingGroup = settings.manualFocusGroups.find { it.groupId == settings.activeManualFocusGroupId.first }
                    if(currentFocusingGroup!=null){

                        if (currentFocusingGroup.blockMode == FocusBlockMode.BLOCK_ALL_EXCEPT_SELECTED) {
                            selectedFocusModeApps.add("com.android.systemui")
                            getDefaultLauncherPackageName(service.packageManager)?.let { selectedFocusModeApps.add(it) }
                            getCurrentKeyboardPackageName(service)?.let { selectedFocusModeApps.add(it) }
                            // As all apps will get blocked except the selected ones, add essential packages that need not be blocked (systemui, launcher, keyboard)
                            // to the list of selected apps
                            currentFocusingGroup.packages.addAll(selectedFocusModeApps)
                        }

                        focusModeData = ManualFocusModeData(currentFocusingGroup,settings.activeManualFocusGroupId.second)

                        withContext(Dispatchers.Main) {
                            notificationManager.startTimer(focusModeData!!.endTimeInMillis - System.currentTimeMillis(),timerIdU = "focus_mode")
                        }
                    }
                }
            }
        }

    }


}