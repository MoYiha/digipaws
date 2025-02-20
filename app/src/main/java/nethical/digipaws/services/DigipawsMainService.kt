package nethical.digipaws.services

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import nethical.digipaws.Constants
import nethical.digipaws.ui.activity.TimedActionActivity
import nethical.digipaws.utils.GrayscaleControl
import nethical.digipaws.utils.TimeTools
import nethical.digipaws.utils.getCurrentKeyboardPackageName
import nethical.digipaws.utils.getDefaultLauncherPackageName
import java.util.Calendar
import java.util.Locale

class DigipawsMainService : BaseBlockingService() {
    companion object {
        const val INTENT_ACTION_REFRESH_FOCUS_MODE = "nethical.digipaws.refresh.focus_mode"
        const val INTENT_ACTION_REFRESH_ANTI_UNINSTALL = "nethical.digipaws.refresh.anti_uninstall"
        const val INTENT_ACTION_REFRESH_GRAYSCALE = "nethical.digipaws.refresh.grayscale"
    }

    private var focusModeData = FocusModeData()
    private var selectedFocusModeApps: HashSet<String> = hashSetOf()

    private var lastPackageName: String? = null // Store the last active app's package name

    private var selectedGrayScaleApps: HashSet<String> = hashSetOf()
    private var grayScaleMode = Constants.GRAYSCALE_MODE_ONLY_SELECTED

    private var launcherPackage = "nethical.digipaws"

    private var isAntiUninstallOn = true
    private val grayscaleControl = GrayscaleControl()

    private var autoFocusData: List<TimedActionActivity.AutoTimedActionItem> = emptyList()
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        super.onAccessibilityEvent(event)

        try {

//        if(event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED){
            val currentPackageName = event?.packageName?.toString()
            // Check if the app has changed
            if (currentPackageName != null && currentPackageName != lastPackageName) {
                lastPackageName = currentPackageName // Update the last package name
//                Log.d("apps",selectedGrayScaleApps.toString() + "mode: " + grayScaleMode)
                when (grayScaleMode) {
                    Constants.GRAYSCALE_MODE_ONLY_SELECTED -> {
                        if (selectedGrayScaleApps.contains(event.packageName)) {
                            grayscaleControl.enableGrayscale()
                            Log.d("enabled", "enabled")
                        } else {
                            grayscaleControl.disableGrayscale()
                        }
                    }

                    Constants.GRAYSCALE_MODE_ALL_EXCEPT_SELECTED -> {
                        if (selectedGrayScaleApps.contains(event.packageName)) {
                            grayscaleControl.disableGrayscale()
                        } else {
                            grayscaleControl.enableGrayscale()
                            Log.d("enabled", "enabled")
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

//        }
        // Check if autofocus hour ongoing
        autoFocusData.forEach { item ->
            val currentTime = Calendar.getInstance()
            val currentHour = currentTime.get(Calendar.HOUR_OF_DAY)
            val currentMinute = currentTime.get(Calendar.MINUTE)

            val currentMinutes = TimeTools.convertToMinutesFromMidnight(currentHour, currentMinute)
            if ((item.startTimeInMins <= item.endTimeInMins && currentMinutes in item.startTimeInMins until item.endTimeInMins) || (item.startTimeInMins > item.endTimeInMins && (currentMinutes >= item.startTimeInMins || currentMinutes < item.endTimeInMins))) {
                Log.d("yes", "yes")
                if (item.packages.contains(event?.packageName) && launcherPackage != event?.packageName) {
                    pressHome()
                    Toast.makeText(
                        this,
                        "Auto focus running, ends in ${item.endTimeInMins - currentMinutes} minutes",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

        }

        if (focusModeData.isTurnedOn) {
            when (focusModeData.modeType) {
                // Block only apps selected by user
                Constants.FOCUS_MODE_BLOCK_SELECTED -> {
                    if (selectedFocusModeApps.contains(event?.packageName) && launcherPackage != event?.packageName) {
                        pressHome()
                    }
                }

                // Block all apps except the ones selected
                Constants.FOCUS_MODE_BLOCK_ALL_EX_SELECTED -> {
                    if (!(selectedFocusModeApps.contains(event?.packageName))) {
                        pressHome()
                    }
                }
            }

            if (focusModeData.endTime < System.currentTimeMillis()) {
                focusModeData.isTurnedOn = false
                savedPreferencesLoader.saveFocusModeData(focusModeData)
            }
        }


        if (isAntiUninstallOn) {
            Log.d("package name", event?.packageName.toString())
            if (event?.packageName == "com.android.settings") {
                traverseNodesForKeywords(rootInActiveWindow)
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onServiceConnected() {
        super.onServiceConnected()

        val filter = IntentFilter().apply {
            addAction(INTENT_ACTION_REFRESH_FOCUS_MODE)
            addAction(INTENT_ACTION_REFRESH_ANTI_UNINSTALL)
            addAction(INTENT_ACTION_REFRESH_GRAYSCALE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(refreshReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(refreshReceiver, filter)
        }
        setupFocusMode()
        setupAntiUninstall()
        setupGrayscale()
    }

    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {

            if (intent != null) {
                when (intent.action) {
                    INTENT_ACTION_REFRESH_FOCUS_MODE -> setupFocusMode()
                    INTENT_ACTION_REFRESH_ANTI_UNINSTALL -> setupAntiUninstall()
                    INTENT_ACTION_REFRESH_GRAYSCALE -> setupGrayscale()
                }
            }
        }
    }

    fun setupFocusMode() {
        selectedFocusModeApps = savedPreferencesLoader.getFocusModeSelectedApps().toHashSet()
        getDefaultLauncherPackageName(packageManager)?.let { launcherPackage = it }
        focusModeData = savedPreferencesLoader.getFocusModeData()

        // As all apps wil get blocked except the selected ones, add essential packages
        // to the list of selected apps
        if (focusModeData.modeType == Constants.FOCUS_MODE_BLOCK_ALL_EX_SELECTED) {
            selectedFocusModeApps.add("com.android.systemui")
            selectedFocusModeApps.add(launcherPackage)
            getCurrentKeyboardPackageName(this)?.let { selectedFocusModeApps.add(it) }

        }

        autoFocusData = savedPreferencesLoader.loadAutoFocusHoursList()
    }

    fun setupAntiUninstall() {
        val info = getSharedPreferences("anti_uninstall", Context.MODE_PRIVATE)
        isAntiUninstallOn = info.getBoolean("is_anti_uninstall_on", false)
    }

    fun setupGrayscale() {
        selectedGrayScaleApps = savedPreferencesLoader.loadGrayScaleApps().toHashSet()
        val sp = getSharedPreferences("grayscale", MODE_PRIVATE)
        grayScaleMode = sp.getInt("mode",Constants.GRAYSCALE_MODE_ONLY_SELECTED)
    }


    private fun traverseNodesForKeywords(
        node: AccessibilityNodeInfo?
    ) {
        if (node == null) {
            return
        }
        if (node.className != null && node.className == "android.widget.TextView") {
            val nodeText = node.text
            if (nodeText != null) {
                val editTextContent = nodeText.toString().lowercase(Locale.getDefault())
                if (editTextContent.lowercase(Locale.getDefault()).contains("digipaws")) {
                    pressHome()
                }
            }
        }

        for (i in 0 until node.childCount) {
            val childNode = node.getChild(i)
            traverseNodesForKeywords(childNode)
        }
    }
    data class FocusModeData(
        var isTurnedOn: Boolean = false,
        val endTime: Long = -1,
        val modeType: Int = Constants.FOCUS_MODE_BLOCK_ALL_EX_SELECTED
    )

}