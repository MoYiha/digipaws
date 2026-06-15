package neth.iecal.curbox.ui.fragments.main.reducers.blockertools.uiHider

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import neth.iecal.curbox.blockers.uihider.UiHider
import neth.iecal.curbox.blockers.uihider.script.Parser
import neth.iecal.curbox.data.models.UiHiderConfig
import neth.iecal.curbox.data.models.UiHiderScript
import neth.iecal.curbox.hardcoded.DEFAULT_UIHIDER_SCRIPTS
import neth.iecal.curbox.utils.DataStoreManager

class UiHiderViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStoreManager = DataStoreManager(application)

    private val _config = MutableStateFlow(UiHiderConfig())
    val config: StateFlow<UiHiderConfig> = _config

    init {
        viewModelScope.launch {
            dataStoreManager.settings.collectLatest { settings ->
                val persisted = settings.uiHiderConfig
                // Show the shipped sample(s) as a starter until the user has any of their own.
                _config.value = if (persisted.scripts.isEmpty()) {
                    persisted.copy(scripts = DEFAULT_UIHIDER_SCRIPTS)
                } else {
                    persisted
                }
            }
        }
    }

    private fun updateConfig(newConfig: UiHiderConfig) {
        _config.value = newConfig
        viewModelScope.launch {
            dataStoreManager.updateUiHiderConfig(newConfig)
            getApplication<Application>().sendBroadcast(
                Intent(UiHider.INTENT_ACTION_REFRESH_UI_HIDER).setPackage(getApplication<Application>().packageName)
            )
        }
    }

    fun setIsActive(isActive: Boolean) {
        updateConfig(_config.value.copy(isActive = isActive))
    }

    fun setScriptEnabled(id: String, enabled: Boolean) {
        val updated = _config.value.scripts.map { if (it.id == id) it.copy(isEnabled = enabled) else it }
        updateConfig(_config.value.copy(scripts = updated))
    }

    /** Insert a new script or replace an existing one with the same id. */
    fun upsertScript(script: UiHiderScript) {
        val current = _config.value.scripts
        val updated = if (current.any { it.id == script.id }) {
            current.map { if (it.id == script.id) script else it }
        } else {
            current + script
        }
        updateConfig(_config.value.copy(scripts = updated))
    }

    fun deleteScript(id: String) {
        updateConfig(_config.value.copy(scripts = _config.value.scripts.filterNot { it.id == id }))
    }

    fun scriptById(id: String): UiHiderScript? = _config.value.scripts.firstOrNull { it.id == id }

    fun newScriptId(): String = "uihider_${System.currentTimeMillis()}"

    /** Compile the source to surface syntax errors; returns the error message, or null if valid. */
    fun validate(source: String): String? = try {
        Parser.parse(source)
        null
    } catch (e: Exception) {
        e.message ?: "Invalid script"
    }
}
