package nethical.digipaws.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ReelStatsDao {

    @Query("SELECT count FROM reel_stats WHERE date = :date")
    suspend fun getCount(date: String): Int?

    @Upsert
    suspend fun upsert(entity: ReelStatsEntity)

    @Query("SELECT * FROM reel_stats ORDER BY date DESC")
    suspend fun getAll(): List<ReelStatsEntity>
}
