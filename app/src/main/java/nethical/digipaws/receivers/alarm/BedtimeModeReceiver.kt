package nethical.digipaws.receivers.alarm

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.icu.util.Calendar
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import nethical.digipaws.R
import nethical.digipaws.utils.SavedPreferencesLoader
import nethical.digipaws.utils.ShizukuControl
import nethical.digipaws.utils.ShizukuRunner
import nethical.digipaws.utils.TimeTools
import nethical.digipaws.utils.scheduleExactAlarm
import rikka.shizuku.Shizuku
import java.text.SimpleDateFormat
import java.util.Locale


class BedtimeModeReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_START_BEDTIME = "nethical.digipaws.bedtime.START"
        const val ACTION_STOP_BEDTIME = "nethical.digipaws.bedtime.STOP"


        const val CHANNEL_ID = "BedtimeNotificationChannel"
    }
    var isShizukuAvailable = false
    private val BINDER_RECEIVED_LISTENER = Shizuku.OnBinderReceivedListener {
        if (!Shizuku.isPreV11()) {
            if(Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED){
                isShizukuAvailable = true
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {

        Shizuku.addBinderReceivedListenerSticky(BINDER_RECEIVED_LISTENER);


        val sp = SavedPreferencesLoader(context)
        val bedTimeModeData = sp.getBedTimeData()
        val alarmManager = context. getSystemService(Context.ALARM_SERVICE) as AlarmManager


        val todaysDate = TimeTools.getCurrentDate()

        Log.d("Alarm data",todaysDate + " : " + bedTimeModeData.lastAlarmSetupForDate)
        if(bedTimeModeData.lastAlarmSetupForDate == todaysDate){

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
                action = ACTION_STOP_BEDTIME,
                triggerTimeInMillis = endCal.timeInMillis,
                receiverClass = BedtimeModeReceiver::class.java
            )


            scheduleExactAlarm(
                alarmManager = alarmManager,
                context = context,
                requestCode = 0,
                action = ACTION_START_BEDTIME,
                triggerTimeInMillis = startCal.timeInMillis,
                receiverClass = BedtimeModeReceiver::class.java
            )
            bedTimeModeData.lastAlarmSetupForDate = TimeTools.getNextDate()
            sp.saveBedTimeData(bedTimeModeData)

            Log.d("Alarm set success",TimeTools.getNextDate())

            val dateFormat = SimpleDateFormat("dd MMMM yyyy, hh:mm a", Locale.getDefault()) // Customize format as needed
            val formattedEndDate = dateFormat.format(endCal.time)
            val formattedStartDate = dateFormat.format(startCal.time)

            Log.d("Alarm setup done",formattedStartDate+ " to " + formattedEndDate)

        }

        val shizukuControl = ShizukuControl()
        if(intent.action == ACTION_START_BEDTIME){
            Log.d("sleep","start bedtime")


            if(bedTimeModeData.isDnd){
                enableDnd(context)
            }

            if(isShizukuAvailable){
                if(bedTimeModeData.isScreenDim){
                    shizukuControl.enableScreenDim()
                }
                if(bedTimeModeData.isGrayScaleOn){
                    shizukuControl.enableGrayscale()
                }
            }

            sendNotification(
                context = context,
                channelId = CHANNEL_ID,
                title = "Time to sleep!!",
                message = "Have a good night!",
                notificationId = 1001,
                smallIcon = R.drawable.baseline_lock_24
            )




        } else if (intent.action == ACTION_STOP_BEDTIME){


            if(bedTimeModeData.isGrayScaleOn){
                disableDnd(context)
            }

            sendNotification(
                context = context,
                channelId = CHANNEL_ID,
                title = "Good Morning!!",
                message = "Bedtime mode has been turned off.",
                notificationId = 1001,
                smallIcon = R.drawable.baseline_lock_24
            )

            val isShizukuRequired = bedTimeModeData.isGrayScaleOn || bedTimeModeData.isScreenDim
            if(isShizukuAvailable && isShizukuRequired){
                if(bedTimeModeData.isGrayScaleOn){
                    shizukuControl.disableGrayscale()
                }
                if(bedTimeModeData.isScreenDim){
                    shizukuControl.disableScreenDim()
                }
            }else {
                sendNotification(
                    context = context,
                    channelId = CHANNEL_ID,
                    title = "Shizuku Unavailable!!",
                    message = "Failed to turn off certain settings. Please press this notification to try turning off manually!",
                    notificationId = 1001,
                    smallIcon = R.drawable.baseline_lock_24
                )
            }

        }


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
            .setSilent(true)
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

    fun enableDnd(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.isNotificationPolicyAccessGranted) {
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            Log.d("DndStatus", "Do Not Disturb enabled")
        } else {
            Log.e("DndError", "Notification policy access not granted")
        }
    }

    fun disableDnd(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.isNotificationPolicyAccessGranted) {
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            Log.d("DndStatus", "Do Not Disturb disabled")
        } else {
            sendNotification(
                context = context,
                channelId = CHANNEL_ID,
                title = "Failed to turn off dnd",
                message = "Please recheck permissions",
                notificationId = 1001,
                smallIcon = R.drawable.baseline_lock_24
            )
        }
    }
}