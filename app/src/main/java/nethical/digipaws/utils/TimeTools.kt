package nethical.digipaws.utils

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

class TimeTools {
    companion object {
        fun convertToMinutesFromMidnight(hour: Int, minute: Int): Int {
            return (hour * 60) + minute
        }

        fun convertMinutesTo24Hour(minutes: Int): Pair<Int, Int> {
            return Pair(minutes / 60, minutes % 60)
        }

        fun getCurrentDate(): String {
            val currentDate = LocalDate.now()

            val formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy")
            return currentDate.format(formatter)
        }
        fun getPreviousDate(daysAgo:Long = 1): String {
            val previousDate = LocalDate.now().minusDays(daysAgo)

            val formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy")
            return previousDate.format(formatter)
        }


        fun getCurrentTime(): String {
            val currentTime = LocalTime.now()

            val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")

            return currentTime.format(formatter)
        }

        fun shortenDate(dateString: String): String {
            val parts = dateString.split(" ")

            if (parts.size >= 2) {
                val day = parts[0]
                val month = parts[1].take(3)
                return "$day $month"
            }

            return dateString
        }

        fun convertMillisToDate(millis: Long): String {
            val instant = Instant.ofEpochMilli(millis)

            val dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())

            val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy")

            return dateTime.format(formatter)
        }
        fun convertTo24HourTimeFormat(currentTimeMillis: Long): String {
            val dateFormat =
                SimpleDateFormat("HH:mm", Locale.getDefault()) // HH:mm is 24-hour format
            val date = Date(currentTimeMillis)
            return dateFormat.format(date)
        }

    }
}