package neth.iecal.curbox.ui.fragments.main.reducers.anti_stimulants.grayscale

import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import neth.iecal.curbox.data.models.TimeInterval
import neth.iecal.curbox.databinding.FragmentGrayscaleTimeSettingsBinding
import neth.iecal.curbox.databinding.ItemDayTimeRangesBinding
import neth.iecal.curbox.databinding.ItemTimeRangeIntervalBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class GrayscaleTimeSettingsFragment : BottomSheetDialogFragment() {

    companion object {
        const val FRAGMENT_ID = "grayscale_time_settings"
    }

    private val viewModel: GrayscaleViewModel by activityViewModels()
    
    private var _binding: FragmentGrayscaleTimeSettingsBinding? = null
    private val binding get() = _binding!!

    private val daysOfWeek = listOf(
        "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
    )

    private val dayBindings = mutableMapOf<Int, ItemDayTimeRangesBinding>()
    private val intervalViews = mutableMapOf<ViewGroup, MutableList<TimeIntervalViewData>>()

    data class TimeIntervalViewData(
        val binding: ItemTimeRangeIntervalBinding,
        var interval: TimeInterval
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGrayscaleTimeSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDayViews()

        binding.saveSettings.setOnClickListener {
            saveSettings()
            dismiss()
        }
        
        loadExistingSettings()
    }
    
    private fun loadExistingSettings() {
        val config = viewModel.currentDailyIntervals
        
        config.forEach { (dayIndex, intervals) ->
            val dayBinding = dayBindings[dayIndex]
            if (dayBinding != null) {
                dayBinding.switchDayActive.isChecked = intervals.isNotEmpty()
                intervals.forEach { interval ->
                    addTimeIntervalView(dayBinding.intervalsContainer, interval)
                }
            }
        }
    }

    private fun setupDayViews() {
        binding.daysListContainer.removeAllViews()
        dayBindings.clear()
        
        daysOfWeek.forEachIndexed { index, day ->
            addDayView(day, index)
        }
    }

    private fun addDayView(dayName: String, dayIndex: Int) {
        val dayBinding = ItemDayTimeRangesBinding.inflate(layoutInflater, binding.daysListContainer, true)
        dayBinding.dayLabel.text = dayName
        
        dayBindings[dayIndex] = dayBinding
        intervalViews[dayBinding.intervalsContainer] = mutableListOf()

        dayBinding.switchDayActive.setOnCheckedChangeListener { _, isChecked ->
            dayBinding.intervalsContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
            dayBinding.btnAddInterval.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        dayBinding.btnAddInterval.setOnClickListener {
            addTimeIntervalView(dayBinding.intervalsContainer)
        }
        
        // Initial state
        dayBinding.intervalsContainer.visibility = View.GONE
        dayBinding.btnAddInterval.visibility = View.GONE
    }

    private fun addTimeIntervalView(
        container: ViewGroup, 
        interval: TimeInterval = TimeInterval(9, 0, 17, 0)
    ) {
        val intervalBinding = ItemTimeRangeIntervalBinding.inflate(layoutInflater, container, true)
        val viewData = TimeIntervalViewData(intervalBinding, interval.copy())
        
        intervalViews[container]?.add(viewData)
        
        updateTimeText(intervalBinding.llStartTime, viewData.interval.startHour, viewData.interval.startMinute)
        updateTimeText(intervalBinding.llEndTime, viewData.interval.endHour, viewData.interval.endMinute)

        intervalBinding.llStartTime.setOnClickListener {
            showTimePicker(intervalBinding.llStartTime, "Select Start Time", viewData.interval, true)
        }

        intervalBinding.llEndTime.setOnClickListener {
            showTimePicker(intervalBinding.llEndTime, "Select End Time", viewData.interval, false)
        }

        intervalBinding.btnRemove.setOnClickListener {
            container.removeView(intervalBinding.root)
            intervalViews[container]?.remove(viewData)
        }
    }

    private fun showTimePicker(timeTextView: TextView, title: String, interval: TimeInterval, isStart: Boolean) {
        val isSystem24Hour = DateFormat.is24HourFormat(requireContext())
        val clockFormat = if (isSystem24Hour) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H

        val currentHour = if (isStart) interval.startHour else interval.endHour
        val currentMinute = if (isStart) interval.startMinute else interval.endMinute

        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(clockFormat)
            .setHour(currentHour)
            .setMinute(currentMinute)
            .setTitleText(title)
            .build()

        picker.addOnPositiveButtonClickListener {
            if (isStart) {
                interval.startHour = picker.hour
                interval.startMinute = picker.minute
            } else {
                interval.endHour = picker.hour
                interval.endMinute = picker.minute
            }
            updateTimeText(timeTextView, picker.hour, picker.minute)
        }

        picker.show(childFragmentManager, "time_picker")
    }

    private fun updateTimeText(textView: TextView, hour: Int, minute: Int) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        val isSystem24Hour = DateFormat.is24HourFormat(requireContext())
        val formatPattern = if (isSystem24Hour) "HH:mm" else "hh:mm a"
        val sdf = SimpleDateFormat(formatPattern, Locale.getDefault())
        textView.text = sdf.format(calendar.time)
    }

    private fun saveSettings() {
        val newIntervals = mutableMapOf<Int, MutableList<TimeInterval>>()
        
        dayBindings.forEach { (index, dayBinding) ->
            if (dayBinding.switchDayActive.isChecked) {
                val intervals = intervalViews[dayBinding.intervalsContainer]?.map { it.interval } ?: emptyList()
                newIntervals[index] = intervals.toMutableList()
            }
        }
        
        viewModel.currentDailyIntervals = newIntervals
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
