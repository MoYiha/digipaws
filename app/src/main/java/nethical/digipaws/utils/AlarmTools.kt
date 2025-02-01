package nethical.digipaws.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

fun scheduleExactAlarm(
    alarmManager: AlarmManager,
    context: Context,
    requestCode: Int,
    action: String,
    triggerTimeInMillis: Long,
    receiverClass: Class<*>
) {
    val intent = Intent(context, receiverClass).apply {
        this.action = action
    }

    val pendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // Check permission for exact alarms on Android 12+ (API 31+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
        val permissionIntent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        context.startActivity(permissionIntent)
    }

    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeInMillis, pendingIntent)
}