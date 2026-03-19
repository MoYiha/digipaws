package nethical.digipaws.ui.fragments.main.focus

import android.R.attr.action
import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nethical.digipaws.Constants
import nethical.digipaws.blockers.FocusModeBlocker
import nethical.digipaws.data.models.AppGroup
import nethical.digipaws.data.models.ManualFocusGroup
import nethical.digipaws.utils.DataStoreManager
import java.util.Calendar
import java.util.UUID

class FocusViewModel(application: Application) : AndroidViewModel(application) {
    var newGroupSelectedApps = hashSetOf<String>()

    private val dataStoreManager = DataStoreManager(application)

    private val _groups = MutableStateFlow<List<ManualFocusGroup>>(emptyList())
    val groups: StateFlow<List<ManualFocusGroup>> = _groups

    var selectedMins = 25

    var selectedGroup : ManualFocusGroup? = null
    init {
        viewModelScope.launch {
            dataStoreManager.settings.collectLatest { settings ->
                _groups.value = settings.manualFocusGroups
            }
        }
    }


    fun updateGroups(newGroups: List<ManualFocusGroup>) {
        viewModelScope.launch {
            dataStoreManager.updateManualFocusGroups(newGroups)
        }
    }

    fun startFocusing(){

        if(selectedGroup == null) return
        viewModelScope.launch {
            dataStoreManager.setManualFocusStateToActive(selectedGroup!!.groupId,selectedMins * 60_000L)
            val intent = Intent(FocusModeBlocker.INTENT_ACTION_REFRESH_FOCUS_MODE)
            application.sendBroadcast(intent)
        }
    }
    fun addGroup(group: ManualFocusGroup) {
        val updatedGroups = _groups.value.toMutableList().apply { add(group) }
        updateGroups(updatedGroups)
    }

}
