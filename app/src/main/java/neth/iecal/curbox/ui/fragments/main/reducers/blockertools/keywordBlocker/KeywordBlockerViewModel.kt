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
    private fun updateConfig(newConfig: KeywordBlocker) {
        viewModelScope.launch {
            dataStoreManager.updateKeywordBlockerConfig(newConfig)
            requestKeywordBlockerRefresh()
        }
    }

    fun setIsActive(isActive: Boolean) {
        updateConfig(_keywordBlockerConfig.value.copy(isActive = isActive))
    }

    fun setBlockAllExceptSupported(enabled: Boolean) {
        updateConfig(_keywordBlockerConfig.value.copy(blockAllExceptSupported = enabled))
    }

    fun addGroup(group: KeywordGroup) {
        val groups = _keywordBlockerConfig.value.keywordGroups.toMutableList()
        groups.add(group)
        updateConfig(_keywordBlockerConfig.value.copy(keywordGroups = groups))
    }

    fun updateGroupById(group: KeywordGroup) {
        val groups = _keywordBlockerConfig.value.keywordGroups.toMutableList()
        val index = groups.indexOfFirst { it.id == group.id }
        if (index != -1) {
            groups[index] = group
            updateConfig(_keywordBlockerConfig.value.copy(keywordGroups = groups))
        }
    }

    fun deleteGroup(groupId: String) {
        val groups = _keywordBlockerConfig.value.keywordGroups.toMutableList()
        groups.removeAll { it.id == groupId }
        updateConfig(_keywordBlockerConfig.value.copy(keywordGroups = groups))
    }

    fun updateGroupActiveState(position: Int, isActive: Boolean) {
        val groups = _keywordBlockerConfig.value.keywordGroups.toMutableList()
        if (position in groups.indices) {
            groups[position] = groups[position].copy(isActive = isActive)
            updateConfig(_keywordBlockerConfig.value.copy(keywordGroups = groups))
        }
    }
}
