package nethical.digipaws.ui.fragments.main.reducers.blockertools.reelBlocker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import nethical.digipaws.data.models.ReelBlocker
import nethical.digipaws.data.models.ReelCountConfig
import nethical.digipaws.data.models.ReelTimeConfig
import nethical.digipaws.data.models.ReelUsageConfig
import nethical.digipaws.data.models.ReelBlockingType
import nethical.digipaws.data.models.AppBlockerWarningScreenConfig
import nethical.digipaws.utils.DataStoreManager

class ReelBlockerViewModel(application: Application) : AndroidViewModel(application) {
    private val dataStoreManager = DataStoreManager(application)
    private val gson = Gson()

    private val _reelBlockerConfig = MutableStateFlow(ReelBlocker())
    val reelBlockerConfig: StateFlow<ReelBlocker> = _reelBlockerConfig

    init {
        viewModelScope.launch {
            dataStoreManager.settings.collectLatest { settings ->
                _reelBlockerConfig.value = settings.reelBlockerWarningConfig
            }
        }
    }

    private fun updateConfig(newConfig: ReelBlocker) {
        viewModelScope.launch {
            dataStoreManager.updateReelBlockerConfig(newConfig)
        }
    }

    fun setIsActive(isActive: Boolean) {
        updateConfig(_reelBlockerConfig.value.copy(isActive = isActive))
    }

    fun setBlockingType(type: ReelBlockingType) {
        updateConfig(_reelBlockerConfig.value.copy(blockingType = type))
    }

    fun updateWarningConfig(config: AppBlockerWarningScreenConfig) {
        updateConfig(_reelBlockerConfig.value.copy(warningScreenConfig = config))
    }

    fun getReelTimeConfig(): ReelTimeConfig {
        return try {
            if (_reelBlockerConfig.value.settings.isEmpty()) ReelTimeConfig()
            else gson.fromJson(_reelBlockerConfig.value.settings, ReelTimeConfig::class.java)
        } catch(e: Exception) { ReelTimeConfig() }
    }

    fun saveReelTimeConfig(config: ReelTimeConfig) {
        updateConfig(_reelBlockerConfig.value.copy(settings = gson.toJson(config)))
    }

    fun getReelUsageConfig(): ReelUsageConfig {
        return try {
            if (_reelBlockerConfig.value.settings.isEmpty()) ReelUsageConfig()
            else gson.fromJson(_reelBlockerConfig.value.settings, ReelUsageConfig::class.java)
        } catch(e: Exception) { ReelUsageConfig() }
    }

    fun saveReelUsageConfig(config: ReelUsageConfig) {
        updateConfig(_reelBlockerConfig.value.copy(settings = gson.toJson(config)))
    }

    fun getReelCountConfig(): ReelCountConfig {
        return try {
            if (_reelBlockerConfig.value.settings.isEmpty()) ReelCountConfig()
            else gson.fromJson(_reelBlockerConfig.value.settings, ReelCountConfig::class.java)
        } catch(e: Exception) { ReelCountConfig() }
    }

    fun saveReelCountConfig(config: ReelCountConfig) {
        updateConfig(_reelBlockerConfig.value.copy(settings = gson.toJson(config)))
    }
}
