package neth.iecal.curbox.ui.fragments.main.reducers.anti_stimulants.mindful_messages

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import neth.iecal.curbox.data.models.MindfulMessageConfig
import neth.iecal.curbox.utils.DataStoreManager

class MindfulMessagesViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStoreManager = DataStoreManager(application)

    private val _configState = MutableStateFlow(MindfulMessageConfig())
    val configState: StateFlow<MindfulMessageConfig> = _configState.asStateFlow()

    init {
        viewModelScope.launch {
            dataStoreManager.settings.collectLatest { settings ->
                _configState.value = settings.mindfulMessageConfig
            }
        }
    }

    fun updateIsActive(isActive: Boolean) {
        val current = _configState.value
        if (current.isActive != isActive) {
            updateConfig(current.copy(isActive = isActive))
        }
    }

    fun updateMessages(messages: String) {
        val current = _configState.value
        if (current.messages != messages) {
            updateConfig(current.copy(messages = messages))
        }
    }

    fun updatePosition(position: Int) {
        val current = _configState.value
        if (current.position != position) {
            updateConfig(current.copy(position = position))
        }
    }

    fun updateSelectedApps(selectedApps: List<String>) {
        val current = _configState.value
        if (current.selectedApps != selectedApps) {
            updateConfig(current.copy(selectedApps = selectedApps))
        }
    }

    private fun updateConfig(config: MindfulMessageConfig) {
        viewModelScope.launch {
            dataStoreManager.updateMindfulMessageConfig(config)
        }
    }
}
