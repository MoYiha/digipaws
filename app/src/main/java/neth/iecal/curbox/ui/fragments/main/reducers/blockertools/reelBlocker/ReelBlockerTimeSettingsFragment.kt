package neth.iecal.curbox.ui.fragments.main.reducers.blockertools.reelBlocker

import android.content.DialogInterface
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import neth.iecal.curbox.databinding.FragmentReelBlockerTimeSettingsBinding
import neth.iecal.curbox.data.models.ReelTimeConfig
import neth.iecal.curbox.data.models.TimeInterval
import neth.iecal.curbox.data.models.fixOvernightInterval
import neth.iecal.curbox.ui.fragments.main.reducers.blockertools.DayAdapter
import neth.iecal.curbox.ui.fragments.main.reducers.blockertools.DayItem
import neth.iecal.curbox.ui.fragments.main.reducers.blockertools.TimeIntervalAdapter

class ReelBlockerTimeSettingsFragment : BottomSheetDialogFragment() {

    companion object {
        const val FRAGMENT_ID = "reel_blocker_time_settings"
    }

    private val viewModel: ReelBlockerViewModel by activityViewModels()
    
    private var _binding: FragmentReelBlockerTimeSettingsBinding? = null
    private val binding get() = _binding!!

    private val daysOfWeek = listOf(
        "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
    )

    private val everydayIntervals = mutableListOf<TimeInterval>()
    private lateinit var everydayAdapter: TimeIntervalAdapter

    private val dayItems = mutableListOf<DayItem>()
    private lateinit var daysAdapter: DayAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReelBlockerTimeSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerViews()

        binding.switchEveryDay.setOnCheckedChangeListener { _, isChecked ->
            binding.everydayContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
            daysAdapter.isInteractionEnabled = !isChecked
        }

        binding.everydayContainer.setOnClickListener {
            if (!binding.switchEveryDay.isChecked) {
                Toast.makeText(requireContext(), "Enable everyday to set unified range", Toast.LENGTH_SHORT).show()
            }
        }
        
        binding.btnAddEverydayInterval.setOnClickListener {
            everydayIntervals.add(TimeInterval(9, 0, 17, 0))
            everydayAdapter.notifyItemInserted(everydayIntervals.size - 1)
        }

        loadExistingSettings()
    }

    override fun onDismiss(dialog: DialogInterface) {
        saveSettings()
        super.onDismiss(dialog)
    }

    private fun setupRecyclerViews() {
        // Everyday intervals
        everydayAdapter = TimeIntervalAdapter(
            everydayIntervals,
            onTimeClick = { interval, isStart, position ->
                showTimePicker(if (isStart) "Select Start Time" else "Select End Time", interval, isStart, everydayIntervals) {
                    everydayAdapter.notifyDataSetChanged()
                }
            },
            onRemove = { position ->
                everydayIntervals.removeAt(position)
                everydayAdapter.notifyItemRemoved(position)
            }
        )
        binding.everydayIntervalsContainer.layoutManager = LinearLayoutManager(requireContext())
        binding.everydayIntervalsContainer.adapter = everydayAdapter

        // Days list
        setupDayItems()
        daysAdapter = DayAdapter(
            dayItems,
            onAddTimeInterval = { dayItem, dayPosition ->
                dayItem.intervals.add(TimeInterval(9, 0, 17, 0))
                daysAdapter.notifyItemChanged(dayPosition)
            },
            onTimeClick = { interval, isStart, dayPosition, intervalPosition ->
                showTimePicker(if (isStart) "Select Start Time" else "Select End Time", interval, isStart, dayItems[dayPosition].intervals) {
                    daysAdapter.notifyDataSetChanged()
                }
            },
            onRemoveInterval = { dayPosition, intervalPosition ->
                dayItems[dayPosition].intervals.removeAt(intervalPosition)
                daysAdapter.notifyItemChanged(dayPosition)
            },
            onDisabledClick = {
                Toast.makeText(requireContext(), "Disable everyday to set granular ranges", Toast.LENGTH_SHORT).show()
            }
        )
        binding.daysListContainer.layoutManager = LinearLayoutManager(requireContext())
        binding.daysListContainer.adapter = daysAdapter
    }

    private fun setupDayItems() {
        dayItems.clear()
        daysOfWeek.forEachIndexed { index, day ->
            dayItems.add(DayItem(day, index, false, mutableListOf()))
        }
    }
    
    private fun loadExistingSettings() {
        val config = viewModel.getReelTimeConfig()
        binding.switchEveryDay.isChecked = config.isEveryday
        
        // Load Everyday intervals
        everydayIntervals.clear()
        everydayIntervals.addAll(config.everydayIntervals.map { it.copy() })
        everydayAdapter.notifyDataSetChanged()
        
        // Load Daily intervals
        config.dailyIntervals.forEach { (dayIndex, intervals) ->
            val dayItem = dayItems.find { it.dayIndex == dayIndex }
            if (dayItem != null) {
                dayItem.isActive = intervals.isNotEmpty()
                dayItem.intervals.clear()
                dayItem.intervals.addAll(intervals.map { it.copy() })
            }
        }
        daysAdapter.notifyDataSetChanged()
    }

    private fun showTimePicker(title: String, interval: TimeInterval, isStart: Boolean, list: MutableList<TimeInterval>, onComplete: () -> Unit) {
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

            if (list.fixOvernightInterval(interval)) {
                Toast.makeText(requireContext(), "Overnight range split into two (up to midnight and from midnight)", Toast.LENGTH_LONG).show()
            }

            onComplete()
        }

        picker.show(childFragmentManager, "time_picker")
    }

    private fun saveSettings() {
        val isEveryday = binding.switchEveryDay.isChecked
        val newConfig = ReelTimeConfig(
            isEveryday = isEveryday,
            everydayIntervals = everydayIntervals.toMutableList(),
            dailyIntervals = mutableMapOf()
        )
        
        dayItems.forEach { dayItem ->
            if (dayItem.isActive) {
                newConfig.dailyIntervals[dayItem.dayIndex] = dayItem.intervals.toMutableList()
            }
        }
        
        viewModel.saveReelTimeConfig(newConfig)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
