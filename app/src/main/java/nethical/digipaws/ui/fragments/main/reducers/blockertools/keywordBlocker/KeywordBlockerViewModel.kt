package nethical.digipaws.ui.fragments.main.reducers.blockertools.keywordBlocker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import nethical.digipaws.data.models.KeywordBlocker
import nethical.digipaws.utils.DataStoreManager

class KeywordBlockerViewModel(application: Application) : AndroidViewModel(application) {
    private val dataStoreManager = DataStoreManager(application)
    
    private val _keywordBlockerConfig = MutableStateFlow(KeywordBlocker())
    val keywordBlockerConfig: StateFlow<KeywordBlocker> = _keywordBlockerConfig

    init {
        viewModelScope.launch {
            dataStoreManager.settings.collectLatest { settings ->
                _keywordBlockerConfig.value = settings.keywordBlockerConfig
            }
        }
    }

    private fun updateConfig(newConfig: KeywordBlocker) {
        viewModelScope.launch {
            dataStoreManager.updateKeywordBlockerConfig(newConfig)
        }
    }

    fun setIsActive(isActive: Boolean) {
        updateConfig(_keywordBlockerConfig.value.copy(isActive = isActive))
    }

    fun addKeyword(keyword: String) {
        val currentKeywords = _keywordBlockerConfig.value.blockedKeywords.toMutableList()
        val trimmed = keyword.trim()
        if (!currentKeywords.contains(trimmed) && trimmed.isNotBlank()) {
            currentKeywords.add(trimmed)
            updateConfig(_keywordBlockerConfig.value.copy(blockedKeywords = currentKeywords))
        }
    }

    fun removeKeyword(keyword: String) {
        val currentKeywords = _keywordBlockerConfig.value.blockedKeywords.toMutableList()
        if (currentKeywords.contains(keyword)) {
            currentKeywords.remove(keyword)
            updateConfig(_keywordBlockerConfig.value.copy(blockedKeywords = currentKeywords))
        }
    }

    fun setRedirectUrl(url: String) {
        updateConfig(_keywordBlockerConfig.value.copy(redirectUrl = url))
    }

    fun setSearchRecursively(enabled: Boolean) {
        updateConfig(_keywordBlockerConfig.value.copy(searchRecursively = enabled))
    }

    fun setBlockAllExceptSupported(enabled: Boolean) {
        updateConfig(_keywordBlockerConfig.value.copy(blockAllExceptSupported = enabled))
    }
}
