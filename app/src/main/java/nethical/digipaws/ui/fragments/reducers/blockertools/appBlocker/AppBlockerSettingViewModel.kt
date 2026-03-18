package nethical.digipaws.ui.fragments.reducers.blockertools.appBlocker

import android.R.attr.action
import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nethical.digipaws.blockers.AppBlocker
import nethical.digipaws.blockers.AppBlockerWarningScreenConfig
import nethical.digipaws.data.models.AppGroup
import nethical.digipaws.ui.activity.AppUsageConfig
import nethical.digipaws.utils.DataStoreManager

class AppBlockerSettingViewModel(application: Application) : AndroidViewModel(application) {
    var currentUsageConfig: AppUsageConfig = AppUsageConfig()
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
            dataStoreManager.updateGroups(newGroups)
            withContext(Dispatchers.Main) {
                sendAppBlockerRefreshBroadcast()
            }
        }
    }
    
    fun addGroup(group: AppGroup) {
        val updatedGroups = _groups.value.toMutableList().apply { add(group) }
        updateGroups(updatedGroups)
    }

    fun updateGroupActiveState(index: Int, isActive: Boolean) {
        val updatedGroups = _groups.value.toMutableList()
        if (index in updatedGroups.indices) {
            updatedGroups[index] = updatedGroups[index].copy(isActive = isActive)
            updateGroups(updatedGroups)
        }
    }
    fun sendAppBlockerRefreshBroadcast(){
        val intent = Intent(AppBlocker.INTENT_ACTION_REFRESH_APP_BLOCKER)
        application.sendBroadcast(intent)
    }
}