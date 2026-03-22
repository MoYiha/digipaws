package nethical.digipaws.ui.fragments.main.focus

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
import kotlinx.coroutines.launch
import nethical.digipaws.blockers.FocusModeBlocker
import nethical.digipaws.data.models.ManualFocusGroup
import nethical.digipaws.utils.DataStoreManager

class FocusViewModel(application: Application) : AndroidViewModel(application) {
    var newGroupSelectedApps = hashSetOf<String>()

    private val dataStoreManager = DataStoreManager(application)

    private val _groups = MutableStateFlow<List<ManualFocusGroup>>(emptyList())
    val groups: StateFlow<List<ManualFocusGroup>> = _groups

    var selectedMins = 25

    var selectedGroup : ManualFocusGroup? = null


    private val _currentRunningFocus = MutableStateFlow<Pair<String?, Long>>(Pair(null,0L))
    val currentRunningFocus: StateFlow<Pair<String?, Long>> = _currentRunningFocus

    private var timerJob: Job? = null
    private var _currentRunningTimer = MutableStateFlow<Long>(0L)
    var currentRunningTimer: StateFlow<Long> = _currentRunningTimer


    init {
        viewModelScope.launch {
            dataStoreManager.settings.collectLatest { settings ->
                _groups.value = settings.manualFocusGroups
                _currentRunningFocus.value = settings.activeManualFocusGroupId
                if(_currentRunningFocus.value.second < System.currentTimeMillis()) forceStopFocus() else requestFocusBlockerRefresh()
            }
        }
    }


    fun updateGroups(newGroups: List<ManualFocusGroup>) {
        viewModelScope.launch {
            dataStoreManager.updateManualFocusGroups(newGroups)
        }
    }

    private fun requestFocusBlockerRefresh() {
        val intent = Intent(FocusModeBlocker.INTENT_ACTION_REFRESH_FOCUS_MODE)
        application.sendBroadcast(intent)
    }

    fun forceStopFocus(){
        viewModelScope.launch {
            dataStoreManager.setManualFocusStateToInactive()
            requestFocusBlockerRefresh()
        }
    }
    fun startFocusing(){
        if(selectedGroup == null) return
        viewModelScope.launch {
            dataStoreManager.setManualFocusStateToActive(selectedGroup!!.groupId,selectedMins * 60_000L)
            requestFocusBlockerRefresh()
        }
    }
    fun addGroup(group: ManualFocusGroup) {
        val updatedGroups = _groups.value.toMutableList().apply { add(group) }
        updateGroups(updatedGroups)
    }


    fun startTimer( endTime: Long) {
        // Stop any existing timer before starting a new one
        timerJob?.cancel()

        timerJob = viewModelScope.launch {

            while (System.currentTimeMillis() < endTime) {
                val remaining = endTime - System.currentTimeMillis()

                _currentRunningTimer.value = remaining

                delay(1000L)
            }

            // Timer Finished
            onTimerFinished()
        }
    }

    fun stopTimer() {
        timerJob?.cancel()
        _currentRunningTimer.value = 0L
    }

    private fun onTimerFinished() {
        _currentRunningTimer.value = 0L
    }
}
