package neth.iecal.curbox.ui.fragments.main.reducers.blockertools.keywordBlocker

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import neth.iecal.curbox.data.models.KeywordBlocker
import neth.iecal.curbox.data.models.KeywordGroup
import neth.iecal.curbox.utils.DataStoreManager
import neth.iecal.curbox.data.models.AppUsageConfig
import neth.iecal.curbox.data.models.AppTimeConfig
import neth.iecal.curbox.data.models.AppBlockerWarningScreenConfig

class KeywordBlockerViewModel(application: Application) : AndroidViewModel(application) {
    private val dataStoreManager = DataStoreManager(application)
    
    private val _keywordBlockerConfig = MutableStateFlow(KeywordBlocker())
    val keywordBlockerConfig: StateFlow<KeywordBlocker> = _keywordBlockerConfig

    var currentUsageConfig = AppUsageConfig()
    var currentTimeConfig = AppTimeConfig()
    var warningScrnConfig = AppBlockerWarningScreenConfig()

    init {
        viewModelScope.launch {
            dataStoreManager.settings.collectLatest { settings ->
                _keywordBlockerConfig.value = settings.keywordBlockerConfig
            }
        }
    }


    private fun requestKeywordBlockerRefresh() {
        val intent = Intent(neth.iecal.curbox.blockers.KeywordBlocker.INTENT_ACTION_REFRESH_CONFIG)
        getApplication<Application>().sendBroadcast(intent)
    }
    private fun updateConfig(transform: (neth.iecal.curbox.data.models.KeywordBlocker) -> neth.iecal.curbox.data.models.KeywordBlocker) {
        viewModelScope.launch {
            dataStoreManager.updateKeywordBlockerConfig(transform)
            requestKeywordBlockerRefresh()
        }
    }

    fun setIsActive(isActive: Boolean) {
        updateConfig { it.copy(isActive = isActive) }
    }

    fun setBlockAllExceptSupported(enabled: Boolean) {
        updateConfig { it.copy(blockAllExceptSupported = enabled) }
    }

    fun addGroup(group: KeywordGroup) {
        updateConfig { config ->
            val groups = config.keywordGroups.toMutableList()
            groups.add(group)
            config.copy(keywordGroups = groups)
        }
    }

    fun updateGroupById(group: KeywordGroup) {
        updateConfig { config ->
            val groups = config.keywordGroups.toMutableList()
            val index = groups.indexOfFirst { it.id == group.id }
            if (index != -1) {
                groups[index] = group
            }
            config.copy(keywordGroups = groups)
        }
    }

    fun deleteGroup(groupId: String) {
        updateConfig { config ->
            val groups = config.keywordGroups.toMutableList()
            groups.removeAll { it.id == groupId }
            config.copy(keywordGroups = groups)
        }
    }

    fun updateGroupActiveState(groupId: String, isActive: Boolean) {
        updateConfig { config ->
            val groups = config.keywordGroups.toMutableList()
            val index = groups.indexOfFirst { it.id == groupId }
            if (index != -1) {
                groups[index] = groups[index].copy(isActive = isActive)
            }
            config.copy(keywordGroups = groups)
        }
    }
}
