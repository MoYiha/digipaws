package nethical.digipaws.blockers

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import nethical.digipaws.Constants
import nethical.digipaws.services.BaseBlockingService
import nethical.digipaws.ui.activity.AppUsageConfig
import nethical.digipaws.ui.activity.TimedActionActivity
import nethical.digipaws.ui.activity.WarningActivity
import nethical.digipaws.utils.NotificationTimerManager
import nethical.digipaws.utils.TimeTools
import nethical.digipaws.utils.UsageStatsHelper
import java.util.Calendar
import androidx.core.content.edit
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import nethical.digipaws.data.models.AppBlockingType
import nethical.digipaws.ui.activity.AppTimeConfig
import nethical.digipaws.utils.DataStoreManager


data class AppBlockerWarningScreenConfig(
    val message: String = "You can setup a custom message to appear here!",
    val timeInterval: Int = 120000, // default cooldown period
    val isDynamicIntervalSettingAllowed: Boolean = false,
    val isProceedDisabled: Boolean = false,
    val isWarningDialogHidden: Boolean = false, // perform back/home action directly without showing warning screen
    val proceedDelayInSecs: Int = 15
)

class AppBlocker(private val context: Context) : BaseBlocker() {

    companion object {

        /**
         * Refreshes information about warning screen, cheat hours and blocked app list
         */
        const val INTENT_ACTION_REFRESH_APP_BLOCKER = "nethical.digipaws.refresh.appblocker"

        /**
         * Add cooldown to an app.
         * This broadcast should always be sent together with the following keys:
         * selected_time: Int -> Duration of cooldown in minutes
         * result_id : String -> Package name of app to be put into cooldown
         */
        const val INTENT_ACTION_REFRESH_APP_BLOCKER_COOLDOWN =
            "nethical.digipaws.refresh.appblocker.cooldown"

    }
    private val prefs: SharedPreferences = context.getSharedPreferences("app_blocker_prefs", Context.MODE_PRIVATE)

    /**
     * stores what blocked apps have been allowed by the user to be used and until when
     * package-name -> end-time-in-real-time-millis
     */
    private var cooldownAppsList: MutableMap<String, Long> = mutableMapOf()

    /**
     * Stores general simple general list of block apps with their configs
     */
    var blockedAppsList: HashMap<String, AppUsageConfig> = hashMapOf()

    var timeBlockedAppsList: HashMap<String, AppTimeConfig> = hashMapOf()


    private var usageStats = UsageStatsHelper(context)


    private var lastPackage = ""
    private lateinit var service : BaseBlockingService


    // responsible to trigger a recheck for what app user is currently using even when no event is received. Used in putting the usage recheck logic into
    // cooldown for an app and later when the cooldown duration is over, trigger a recheck
    private val handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null

    private var appBlockerWarningScrnConfgs = HashMap<String, AppBlockerWarningScreenConfig>()

    private lateinit var notificationManager: NotificationTimerManager
    init {
        loadPersistedData()
    }


    /**
     * Check if app needs to be blocked and perform necessary actions
     *
     * @param event
     * @return
     */
    fun doAppBlockerCheck(event: AccessibilityEvent?){

        val packageName = event?.packageName.toString()
        if (lastPackage == packageName || packageName == service.packageName || packageName == "com.android.systemui") return

        lastPackage = packageName

        // check if a blocked apps previously allowed to be used for some time has expired
        if (cooldownAppsList.containsKey(packageName)) {
            if (cooldownAppsList[packageName]!! < System.currentTimeMillis()) {
                removeCooldownFrom(packageName)
            } else {
                // app is still under cooldown so we let it to be used
                return
            }
        }

        // check if app is under time block
        if (timeBlockedAppsList.contains(packageName)) {
            val endAllowedMillis = getEndTimeInMillis(packageName)
            if (endAllowedMillis == null) {
                // Not in allowed time, block immediately
                notificationManager.stopTimer()
                showWarningScreen(packageName)
                return
            } else {
                // App is in allowed time, ensure it gets blocked when time ends
                setUpForcedRefreshChecker(packageName, endAllowedMillis)
            }
        }

        // Check if app is in blocked list and has exceeded usage limit
        if (blockedAppsList.contains(packageName)) {

            val config = blockedAppsList[packageName]!!
            val currentUsage = usageStats.getForegroundStatsByRelativeDay(0)
                .firstOrNull { it.packageName == packageName }?.totalTime ?: 0L
            val usageLimit = getUsageLimitForToday(config) * 60_000L

            val remainingUsage = ((usageLimit - currentUsage))
            if ( currentUsage >= usageLimit) {
                // App has exceeded its usage limit
                notificationManager.stopTimer()
                showWarningScreen(packageName)
            } else {
                // App is in blocked list but hasn't exceeded usage limit yet
                notificationManager.startTimer(remainingUsage, timerIdU = packageName)
                setUpForcedRefreshChecker(packageName,remainingUsage + SystemClock.uptimeMillis())
                return
            }
        }
        notificationManager.stopTimer()
    }


    fun setupAppBlocker(service: BaseBlockingService) {
        this.service = service
        notificationManager = NotificationTimerManager(service)
        val dataStoreManager = DataStoreManager(service)

        CoroutineScope(Dispatchers.IO).launch {
            dataStoreManager.settings.collectLatest { settings ->
                val tempBlockedApps = mutableMapOf<String, AppUsageConfig>()
                val tempTimeBlockedApps = mutableMapOf<String, AppTimeConfig>()
                val warningScrnConfigs = mutableMapOf<String, AppBlockerWarningScreenConfig>()
                settings.blockedAppGroups.forEach {  group ->
                    if(!group.isActive) return@forEach
                    if(group.blockingType == AppBlockingType.Usage ){
                        val settings = Gson().fromJson<AppUsageConfig>(group.setting, AppUsageConfig::class.java)
                        group.selectedPackages.forEach {
                            tempBlockedApps[it] = settings
                            warningScrnConfigs[it] = group.warningScreenConfig
                        }
                    }else{
                        val settings =  Gson().fromJson<AppTimeConfig>(group.setting,
                            AppTimeConfig::class.java)
                        group.selectedPackages.forEach {
                            tempTimeBlockedApps[it] = settings
                            warningScrnConfigs[it] = group.warningScreenConfig
                        }
                    }
                }
                blockedAppsList = HashMap(tempBlockedApps)
                timeBlockedAppsList = HashMap(tempTimeBlockedApps)
                appBlockerWarningScrnConfgs = HashMap(warningScrnConfigs)
                Log.d("blocked Apps List updated",blockedAppsList.toString())

            }
        }
    }

    fun handlePutCooldownIntentBroadcast(intent: Intent){
        val coolPackage = intent.getStringExtra("result_id") ?: ""

        val interval =
            intent.getIntExtra("selected_time",
                appBlockerWarningScrnConfgs[coolPackage]?.timeInterval ?: 10
            )
        val cooldownUntil =
            SystemClock.uptimeMillis() + interval
        putCooldownTo(
            coolPackage,
            cooldownUntil
        )
        setUpForcedRefreshChecker(coolPackage, cooldownUntil)
    }

    /**
     * Get the usage limit for today based on config
     */
    private fun getUsageLimitForToday(config: AppUsageConfig): Long {
        return if (config.isDailyUniform) {
            config.uniformLimit
        } else {
            val calendar = Calendar.getInstance()
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1 // 0=Sunday
            config.dailyLimits[dayOfWeek]
        }
    }



    /**
     * Load persisted data from SharedPreferences
     */
    private fun loadPersistedData() {
        // Load cooldowns
        val cooldownKeys = prefs.getStringSet("cooldown_keys", setOf()) ?: setOf()
        cooldownKeys.forEach { packageName ->
            val endTime = prefs.getLong("cooldown_$packageName", 0L)
            if (endTime > System.currentTimeMillis()) {
                cooldownAppsList[packageName] = endTime
            }
        }
    }

    /**
     * Persist cooldown data
     */
    private fun persistCooldownData() {
        prefs.edit {
            putStringSet("cooldown_keys", cooldownAppsList.keys)
            cooldownAppsList.forEach { (packageName, endTime) ->
                putLong("cooldown_$packageName", endTime)
            }
        }
    }

    private fun putCooldownTo(packageName: String, endTime: Long) {
        // Store as real time (System.currentTimeMillis() + duration)
        val realTimeEnd = System.currentTimeMillis() + endTime
        cooldownAppsList[packageName] = realTimeEnd
        persistCooldownData()
        Log.d("cooldownAppsList", cooldownAppsList.toString())
    }

    private fun removeCooldownFrom(packageName: String) {
        cooldownAppsList.remove(packageName)
        val editor = prefs.edit()
        editor.remove("cooldown_$packageName")
        val cooldownKeys = prefs.getStringSet("cooldown_keys", mutableSetOf())?.toMutableSet()
        cooldownKeys?.remove(packageName)
        editor.putStringSet("cooldown_keys", cooldownKeys)
        editor.apply()
    }

    /**
     * @param packageName The app package name.
     * @return null if app is not currently allowed by time config, or the end time in uptimeMillis if allowed.
     */
    private fun getEndTimeInMillis(packageName: String): Long? {
        val config = timeBlockedAppsList[packageName] ?: return null

        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)
        val currentMinutes = TimeTools.convertToMinutesFromMidnight(currentHour, currentMinute)

        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1 // 0=Sunday, 1=Monday...

        val intervals = if (config.isEveryday) {
            config.everydayIntervals
        } else {
            config.dailyIntervals[dayOfWeek] ?: emptyList()
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

    /**
     * Setup a runnable that executes after n millis to check if a package is still being used that was allowed to be used previously
     * as it was put into cooldown or found in cheat-minutes. Basically shows the warning dialog after cooldown is over.
     * @param coolPackage
     * @param endMillis
     */
    private fun setUpForcedRefreshChecker(coolPackage: String, endMillis: Long) {
        if (updateRunnable != null) {
            updateRunnable?.let { handler.removeCallbacks(it) }
            updateRunnable = null
        }
        Log.d("setting up recheck",coolPackage)
        updateRunnable = Runnable {

            Log.d("AppBlockerService", "Triggered Recheck for  $coolPackage")
            try {
                if (service.rootInActiveWindow.packageName == coolPackage) {
                    removeCooldownFrom(coolPackage)

                    showWarningScreen(coolPackage)
                    lastPackage = ""
                }
            } catch (e: Exception) {
                Log.e("AppBlockerService", e.toString())
                setUpForcedRefreshChecker(coolPackage, endMillis + 60_000) // recheck after a minute
            }
        }

        handler.postAtTime(updateRunnable!!, endMillis)
    }

    private fun showWarningScreen(packageName: String){
        notificationManager.stopTimer()

        service.pressHome()
        lastPackage = ""

        if (appBlockerWarningScrnConfgs[packageName]?.isWarningDialogHidden == true) {
            return
        }

        Thread.sleep(300)
        val dialogIntent = Intent(service, WarningActivity::class.java)
        dialogIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        dialogIntent.putExtra("mode", Constants.WARNING_SCREEN_MODE_APP_BLOCKER)
        dialogIntent.putExtra("result_id", packageName)
        dialogIntent.putExtra("warning_config",Gson().toJson(appBlockerWarningScrnConfgs[packageName]))
        service.startActivity(dialogIntent)
    }

}