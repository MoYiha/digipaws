package nethical.digipaws.receivers

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import nethical.digipaws.receivers.alarm.BedtimeModeReceiver
import nethical.digipaws.utils.SavedPreferencesLoader
import nethical.digipaws.utils.scheduleExactAlarm


class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Load the saved bedtime data
            val sp = SavedPreferencesLoader(context)
            val bedTimeModeData = sp.getBedTimeData()

            // Check if the current time is between start and end time
            val currentTime = System.currentTimeMillis()
            if (currentTime >= bedTimeModeData.startTimeInMillis && currentTime <= bedTimeModeData.endTimeInMillis) {
                // If current time is within the bedtime window, start bedtime mode
                val startIntent = Intent(context, BedtimeModeReceiver::class.java).apply {
                    action = BedtimeModeReceiver.ACTION_START_BEDTIME
                }
                context.sendBroadcast(startIntent)
            }

            // Schedule the next alarms
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            scheduleExactAlarm(
                alarmManager = alarmManager,
                context = context,
                requestCode = 0,
                action = BedtimeModeReceiver.ACTION_START_BEDTIME,
                triggerTimeInMillis = bedTimeModeData.startTimeInMillis,
                receiverClass = BedtimeModeReceiver::class.java
            )
            scheduleExactAlarm(
                alarmManager = alarmManager,
                context = context,
                requestCode = 1,
                action = BedtimeModeReceiver.ACTION_STOP_BEDTIME,
                triggerTimeInMillis = bedTimeModeData.endTimeInMillis,
                receiverClass = BedtimeModeReceiver::class.java
            )

            Log.d("BootReceiver", "Alarms rescheduled after boot")
        }
    }
}