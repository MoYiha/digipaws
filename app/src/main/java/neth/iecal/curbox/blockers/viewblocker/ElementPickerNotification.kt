package neth.iecal.curbox.blockers.viewblocker

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import neth.iecal.curbox.R

class ElementPickerNotification(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "element_picker_channel"
        private const val NOTIFICATION_ID = 1051

        const val ACTION_START_PICKER = "neth.iecal.curbox.ACTION_START_PICKER"
        const val ACTION_STOP_PICKER = "neth.iecal.curbox.ACTION_STOP_PICKER"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.picker_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.picker_channel_description)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    @SuppressLint("LaunchActivityFromNotification")
    fun showNotification() {
        val pickerIntent = Intent(ACTION_START_PICKER).apply {
            setPackage(context.packageName)
        }
        val pickerPendingIntent = PendingIntent.getBroadcast(
            context, 0, pickerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_view_blocker_aesthetic)
            .setContentTitle(context.getString(R.string.picker_notification_title))
            .setContentText(context.getString(R.string.picker_notification_text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pickerPendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun showPickerActiveNotification() {
        val stopIntent = Intent(ACTION_STOP_PICKER).apply {
            setPackage(context.packageName)
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_view_blocker_aesthetic)
            .setContentTitle(context.getString(R.string.picker_active_title))
            .setContentText(context.getString(R.string.picker_active_text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(stopPendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
