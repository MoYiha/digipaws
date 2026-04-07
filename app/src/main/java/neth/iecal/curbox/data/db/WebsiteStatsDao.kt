package neth.iecal.curbox.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface WebsiteStatsDao {

    @Query("SELECT * FROM website_stats WHERE date = :date")
    suspend fun getStatsForDate(date: String): List<WebsiteStatsEntity>
    
    @Query("SELECT * FROM website_stats WHERE date = :date AND packageName = :packageName AND domain = :domain")
    suspend fun getStat(date: String, packageName: String, domain: String): WebsiteStatsEntity?

    @Upsert
    suspend fun upsert(entity: WebsiteStatsEntity)
}
