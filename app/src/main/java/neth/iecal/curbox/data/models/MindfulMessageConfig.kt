package neth.iecal.curbox.data.models

import android.view.Gravity

data class MindfulMessageConfig(
    val isActive: Boolean = false,
    val selectedApps: List<String> = emptyList(),
    val messages: String =
        "Is this really important? \n App Usage: {app_usage_today} \n Screen Time: {screentime_today} \n Session: {live_session_duration}" ,
    val position: Int = Gravity.TOP
)
