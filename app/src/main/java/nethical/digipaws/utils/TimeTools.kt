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

        fun formatDate(timestamp: Long): String {
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            return dateFormat.format(Date(timestamp))
        }
    }
}