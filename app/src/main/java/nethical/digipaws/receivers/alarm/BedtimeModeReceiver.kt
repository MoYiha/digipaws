package nethical.digipaws.receivers.alarm

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.icu.util.Calendar
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import nethical.digipaws.R
import nethical.digipaws.utils.SavedPreferencesLoader
import nethical.digipaws.utils.scheduleExactAlarm


class BedtimeModeReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_START_BEDTIME = "nethical.digipaws.bedtime.START"
        const val ACTION_STOP_BEDTIME = "nethical.digipaws.bedtime.STOP"


        const val CHANNEL_ID = "BedtimeNotificationChannel"
    }
    override fun onReceive(context: Context, intent: Intent) {
        if(intent.action == ACTION_START_BEDTIME){
            Log.d("sleep","start bedtime")
            sendNotification(
                context = context,
                channelId = CHANNEL_ID,
                title = "Bedtime Mode Started",
                message = "Your bedtime mode is now active.",
                notificationId = 1001,
                smallIcon = R.drawable.baseline_lock_24
            )


        } else if (intent.action == ACTION_STOP_BEDTIME){
            sendNotification(
                context = context,
                channelId = CHANNEL_ID,
                title = "Good Morning!!",
                message = "Bedtime mode has been turned off.",
                notificationId = 1001,
                smallIcon = R.drawable.baseline_lock_24
            )

        }
        val alarmManager = context. getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val sp = SavedPreferencesLoader(context)
        val bedTimeModeData = sp.getBedTimeData()

        val startCal = Calendar.getInstance()
        startCal.timeInMillis = bedTimeModeData.startTimeInMillis
        startCal.add(Calendar.DAY_OF_MONTH, 1); // Set for the next day


        val endCal = Calendar.getInstance()
        endCal.timeInMillis = bedTimeModeData.endTimeInMillis
        endCal.add(Calendar.DAY_OF_MONTH, 1); // Set for the next day

        scheduleExactAlarm(
            alarmManager = alarmManager,
            context = context,
            requestCode = 0,
            action = BedtimeModeReceiver.ACTION_START_BEDTIME,
            triggerTimeInMillis = startCal.timeInMillis,
            receiverClass = BedtimeModeReceiver::class.java
        )
        scheduleExactAlarm(
            alarmManager = alarmManager,
            context = context,
            requestCode = 0,
            action = BedtimeModeReceiver.ACTION_STOP_BEDTIME,
            triggerTimeInMillis = endCal.timeInMillis,
            receiverClass = BedtimeModeReceiver::class.java
        )


    }

    fun sendNotification(
        context: Context,
        channelId: String,
        title: String,
        message: String,
        notificationId: Int,
        smallIcon: Int
    ) {
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(false) // Prevent dismissal on tap

        with(NotificationManagerCompat.from(context)) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            notify(notificationId, builder.build())
        }
    }

}