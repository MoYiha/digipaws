package nethical.digipaws.ui.activity

data class TimeInterval(
    var startHour: Int = 9,
    var startMinute: Int = 0,
    var endHour: Int = 17,
    var endMinute: Int = 0
)

data class AppTimeConfig(
    var isEveryday: Boolean = true,
    var everydayIntervals: MutableList<TimeInterval> = mutableListOf(),
    var dailyIntervals: MutableMap<Int, MutableList<TimeInterval>> = mutableMapOf()
)
