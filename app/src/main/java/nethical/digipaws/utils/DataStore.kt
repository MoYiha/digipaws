package nethical.digipaws.utils

import android.content.Context
import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.datastore.core.Serializer
import com.google.gson.Gson
import nethical.digipaws.data.models.AppGroup
import nethical.digipaws.data.models.ManualFocusGroup
import nethical.digipaws.data.models.Settings
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Type
import kotlin.jvm.java

class GsonSerializer<T>(
    private val gson: Gson,
    private val type: Type,
    override val defaultValue: T
) : Serializer<T> {

    override suspend fun readFrom(input: InputStream): T {
        return try {
            gson.fromJson(input.readBytes().decodeToString(), type) ?: defaultValue
        } catch (e: Exception) {
            e.printStackTrace()
            defaultValue
        }
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        output.write(gson.toJson(t).toByteArray())
    }
}

class DataStoreManager(private val context: Context) {
    private val gson = Gson()

    companion object {
        @Volatile
        private var INSTANCE: androidx.datastore.core.DataStore<Settings>? = null

        fun getSettingsDataStore(context: Context, gson: Gson): androidx.datastore.core.DataStore<Settings> {
            return INSTANCE ?: synchronized(this) {
                val instance = MultiProcessDataStoreFactory.create(
                    serializer = GsonSerializer(
                        gson = gson,
                        type = Settings::class.java,
                        defaultValue = Settings()
                    ),
                    produceFile = { File(context.applicationContext.filesDir, "datastore/settings.json") }
                )
                INSTANCE = instance
                instance
            }
        }
    }

    // One DataStore for everything
    private val settingsDataStore = getSettingsDataStore(context, gson)

    // Access the flow exactly like before
    val settings = settingsDataStore.data

    suspend fun updateAppGroups(newGroups: List<AppGroup>) {
        settingsDataStore.updateData { it.copy(blockedAppGroups = newGroups) }
    }

    suspend fun updateManualFocusGroups(newGroup: List<ManualFocusGroup>){
        settingsDataStore.updateData { it.copy(manualFocusGroups = newGroup) }
    }
    suspend fun setManualFocusStateToActive(focusGroupId:String, durationInMs: Long){
        settingsDataStore.updateData { it.copy(activeManualFocusGroupId = Pair(focusGroupId, System.currentTimeMillis() + durationInMs)) }
    }
    suspend fun setManualFocusStateToInactive(){
        settingsDataStore.updateData { it.copy(activeManualFocusGroupId = Pair(null, 0)) }
    }

    suspend fun updateReelBlockerConfig(config: nethical.digipaws.data.models.ReelBlocker) {
        settingsDataStore.updateData { it.copy(reelBlockerConfig = config) }
    }
}