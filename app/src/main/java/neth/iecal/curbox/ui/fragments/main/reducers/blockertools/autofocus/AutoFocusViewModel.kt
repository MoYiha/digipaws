package neth.iecal.curbox.ui.fragments.main.reducers.blockertools.autofocus

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import neth.iecal.curbox.blockers.FocusModeBlocker
import neth.iecal.curbox.data.models.AutoFocusGroup
import neth.iecal.curbox.utils.DataStoreManager

class AutoFocusViewModel(application: Application) : AndroidViewModel(application) {
    private val dataStoreManager = DataStoreManager(application)
    
    private val _groups = MutableStateFlow<List<AutoFocusGroup>>(emptyList())
    val groups: StateFlow<List<AutoFocusGroup>> = _groups

    var currentDailyIntervals: MutableMap<Int, MutableList<neth.iecal.curbox.data.models.TimeInterval>> = mutableMapOf()

    init {
        viewModelScope.launch {
            dataStoreManager.settings.collectLatest { settings ->
                _groups.value = settings.autoFocusGroups
            }
        }
    }

    fun addGroup(group: AutoFocusGroup) {
        val currentGroups = _groups.value.toMutableList()
        currentGroups.add(group)
        updateGroups(currentGroups)
    }

    fun removeGroup(group: AutoFocusGroup) {
        val currentGroups = _groups.value.toMutableList()
        currentGroups.remove(group)
        updateGroups(currentGroups)
    }

    fun updateGroup(group: AutoFocusGroup) {
        val currentGroups = _groups.value.toMutableList()
        val index = currentGroups.indexOfFirst { it.groupId == group.groupId }
        if (index != -1) {
            currentGroups[index] = group
            updateGroups(currentGroups)
        }
    }

    private fun updateGroups(newGroups: List<AutoFocusGroup>) {
        viewModelScope.launch {
            dataStoreManager.updateAutoFocusGroups(newGroups)
            requestFocusBlockerRefresh()
        }
    }

    private fun requestFocusBlockerRefresh() {
        val intent = Intent(FocusModeBlocker.INTENT_ACTION_REFRESH_FOCUS_MODE)
        application.sendBroadcast(intent)
    }
}
