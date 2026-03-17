package nethical.digipaws.utils

import android.content.Context
import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.datastore.core.Serializer
import com.google.gson.Gson
import nethical.digipaws.data.models.AppGroup
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

    // One DataStore for everything
    private val settingsDataStore = MultiProcessDataStoreFactory.create(
        serializer = GsonSerializer(
            gson = gson,
            type = Settings::class.java,
            defaultValue = Settings()
        ),
        produceFile = { File(context.filesDir, "datastore/settings.json") }
    )

    // Access the flow exactly like before
    val settings = settingsDataStore.data

    suspend fun updateGroups(newGroups: List<AppGroup>) {
        settingsDataStore.updateData { it.copy(blockedAppGroups = newGroups) }
    }
}