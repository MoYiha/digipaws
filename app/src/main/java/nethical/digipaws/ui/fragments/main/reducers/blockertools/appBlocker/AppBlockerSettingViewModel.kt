package nethical.digipaws.ui.fragments.main.reducers.blockertools.appBlocker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import nethical.digipaws.blockers.AppBlockerWarningScreenConfig
import nethical.digipaws.data.models.AppGroup
import nethical.digipaws.ui.activity.AppUsageConfig
import nethical.digipaws.ui.activity.AppTimeConfig
import nethical.digipaws.utils.DataStoreManager

class AppBlockerSettingViewModel(application: Application) : AndroidViewModel(application) {
    var currentUsageConfig: AppUsageConfig = AppUsageConfig()
    var currentTimeConfig: AppTimeConfig = AppTimeConfig()
    var warningScrnConfig: AppBlockerWarningScreenConfig = AppBlockerWarningScreenConfig()

    private val dataStoreManager = DataStoreManager(application)

    private val _groups = MutableStateFlow<List<AppGroup>>(emptyList())
    val groups: StateFlow<List<AppGroup>> = _groups
    init {
        viewModelScope.launch {
            dataStoreManager.settings.collectLatest { settings ->
                _groups.value = settings.blockedAppGroups
            }
        }
    }

    fun updateGroups(newGroups: List<AppGroup>) {
        viewModelScope.launch {
            dataStoreManager.updateAppGroups(newGroups)
        }
    }
    
    fun addGroup(group: AppGroup) {
        val updatedGroups = _groups.value.toMutableList().apply { add(group) }
        updateGroups(updatedGroups)
    }

    fun updateGroupById(updatedGroup: AppGroup) {
        val updatedGroups = _groups.value.toMutableList()
        val index = updatedGroups.indexOfFirst { it.id == updatedGroup.id }
        if (index != -1) {
            updatedGroups[index] = updatedGroup
            updateGroups(updatedGroups)
        }
    }

    fun deleteGroup(groupId: String) {
        val updatedGroups = _groups.value.toMutableList()
        updatedGroups.removeAll { it.id == groupId }
        updateGroups(updatedGroups)
    }

    fun updateGroupActiveState(index: Int, isActive: Boolean) {
        val updatedGroups = _groups.value.toMutableList()
        if (index in updatedGroups.indices) {
            updatedGroups[index] = updatedGroups[index].copy(isActive = isActive)
            updateGroups(updatedGroups)
        }
    }
}