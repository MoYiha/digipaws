package nethical.digipaws.ui.fragments.main.reducers.anti_stimulants.grayscale

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import nethical.digipaws.data.models.GrayscaleGroup
import nethical.digipaws.data.models.TimeInterval
import nethical.digipaws.utils.DataStoreManager
import nethical.digipaws.services.GeneralFeaturesService

class GrayscaleViewModel(application: Application) : AndroidViewModel(application) {
    private val dataStoreManager = DataStoreManager(application)
    
    private val _groups = MutableStateFlow<List<GrayscaleGroup>>(emptyList())
    val groups: StateFlow<List<GrayscaleGroup>> = _groups

    var currentDailyIntervals: MutableMap<Int, MutableList<TimeInterval>> = mutableMapOf()

    init {
        viewModelScope.launch {
            dataStoreManager.settings.collectLatest { settings ->
                _groups.value = settings.grayscaleGroups
            }
        }
    }

    fun addGroup(group: GrayscaleGroup) {
        val currentGroups = _groups.value.toMutableList()
        currentGroups.add(group)
        updateGroups(currentGroups)
    }

    fun removeGroup(group: GrayscaleGroup) {
        val currentGroups = _groups.value.toMutableList()
        currentGroups.remove(group)
        updateGroups(currentGroups)
    }

    fun updateGroup(group: GrayscaleGroup) {
        val currentGroups = _groups.value.toMutableList()
        val index = currentGroups.indexOfFirst { it.groupId == group.groupId }
        if (index != -1) {
            currentGroups[index] = group
            updateGroups(currentGroups)
        }
    }

    private fun updateGroups(newGroups: List<GrayscaleGroup>) {
        viewModelScope.launch {
            dataStoreManager.updateGrayscaleGroups(newGroups)
            requestGrayscaleRefresh()
        }
    }

    private fun requestGrayscaleRefresh() {
        val intent = Intent(GeneralFeaturesService.INTENT_ACTION_REFRESH_GRAYSCALE)
        getApplication<Application>().sendBroadcast(intent)
    }
}
