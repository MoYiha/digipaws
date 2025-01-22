package nethical.digipaws.utils

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import nethical.digipaws.ui.fragments.usage.AllAppsUsageFragment
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class UsageStatsHelper(private val context: Context) {

    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    /**
     * Collects event information from the system to calculate precise
     * foreground usage statistics for the specified period.
     *
     * @param start Start timestamp in milliseconds
     * @param end End timestamp in milliseconds
     * @return A list of stats containing app usage data
     */
    fun getForegroundStatsByTimestamps(start: Long, end: Long): List<AllAppsUsageFragment.Stat> {
        val events = usageStatsManager.queryEvents(start, end)
        val foregroundEvents = mutableMapOf<String, Long>()
        val usageStatsMap = mutableMapOf<String, MutableList<Pair<Long, ZonedDateTime>>>()
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)

            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    foregroundEvents[event.packageName] = event.timeStamp
                }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val startTime = foregroundEvents[event.packageName]
                    if (startTime != null) {
                        val usageDuration = event.timeStamp - startTime
                        // Only process if usage duration is at least 3 seconds (3000 milliseconds)
                        if (usageDuration >= 3000) {
                            val startDateTime = ZonedDateTime.ofInstant(
                                Instant.ofEpochMilli(startTime),
                                ZoneId.systemDefault()
                            )
                            usageStatsMap.getOrPut(event.packageName) { mutableListOf() }
                                .add(Pair(usageDuration, startDateTime))
                        }
                        foregroundEvents.remove(event.packageName)
                    }
                }
            }
        }

        // Handle any events that were not properly closed
        val currentTime = System.currentTimeMillis()
        foregroundEvents.forEach { (packageName, startTime) ->
            if (startTime < end) {
                val usageDuration = minOf(currentTime, end) - startTime
                if (usageDuration >= 3000) {
                    val startDateTime = ZonedDateTime.ofInstant(
                        Instant.ofEpochMilli(startTime),
                        ZoneId.systemDefault()
                    )
                    usageStatsMap.getOrPut(packageName) { mutableListOf() }
                        .add(Pair(usageDuration, startDateTime))
                }
            }
        }

        // Convert the map to list of Stats, combining all usage times for each package
        return usageStatsMap.map { (packageName, usageList) ->
            AllAppsUsageFragment.Stat(
                packageName = packageName,
                totalTime = usageList.sumOf { it.first },
                startTimes = usageList.map { it.second }
            )
        }.sortedByDescending{ it.totalTime }
    }

    /**
     * Collects event information for a specific relative day (e.g., today, yesterday).
     *
     * @param offset Day offset from today (e.g., 0 for today, 1 for yesterday)
     * @return A list of stats containing app usage data for the specified day
     */
    fun getForegroundStatsByRelativeDay(offset: Int): List<AllAppsUsageFragment.Stat> {
        val queryDay = LocalDate.now().minusDays(offset.toLong())
        val start = queryDay.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = queryDay.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        return getForegroundStatsByTimestamps(start, end)
    }

    /**
     * Collects event information for a specific day
     */
    fun getForegroundStatsByDay(queryDate: LocalDate): List<AllAppsUsageFragment.Stat> {

        // Calculate the start and end times for the given date
        val start = queryDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = queryDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()


        return getForegroundStatsByTimestamps(start, end)
    }
}