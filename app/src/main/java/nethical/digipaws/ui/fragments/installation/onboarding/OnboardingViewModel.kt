package nethical.digipaws.ui.fragments.installation.onboarding

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class OnboardingViewModel : ViewModel() {

    private val _estimatedHours = MutableLiveData<Int>(4) // Default guess
    val estimatedHours: LiveData<Int> = _estimatedHours

    private val _selectedValues = MutableLiveData<Set<String>>(emptySet())
    val selectedValues: LiveData<Set<String>> = _selectedValues

    private val _targetAppPackage = MutableLiveData<String?>(null)
    val targetAppPackage: LiveData<String?> = _targetAppPackage

    private val _dailyLimitMinutes = MutableLiveData<Long>(30L)
    val dailyLimitMinutes: LiveData<Long> = _dailyLimitMinutes

    fun setEstimatedHours(hours: Int) {
        _estimatedHours.value = hours
    }

    fun toggleValue(value: String) {
        val current = _selectedValues.value?.toMutableSet() ?: mutableSetOf()
        if (current.contains(value)) {
            current.remove(value)
        } else {
            current.add(value)
        }
        _selectedValues.value = current
    }

    fun setTargetApp(packageName: String) {
        _targetAppPackage.value = packageName
    }

    fun setDailyLimit(limit: Long) {
        _dailyLimitMinutes.value = limit
    }
}
