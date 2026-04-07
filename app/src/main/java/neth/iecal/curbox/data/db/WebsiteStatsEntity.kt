package neth.iecal.curbox.data.db

import androidx.room.Entity

@Entity(tableName = "website_stats", primaryKeys = ["date", "packageName", "domain"])
data class WebsiteStatsEntity(
    val date: String,
    val packageName: String,
    val domain: String,
    val totalTime: Long = 0L
)
