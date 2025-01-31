package nethical.digipaws.ui.fragments.bedtime

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import nethical.digipaws.databinding.FragmentConfigureBedtimeBinding
import nl.joery.timerangepicker.TimeRangePicker

class SetupBedtimeMode : Fragment() {

    companion object {
        const val FRAGMENT_ID = "setup_bedtime_mode"
    }

    private var _binding: FragmentConfigureBedtimeBinding? = null
    private val binding get() = _binding!!  // Safe getter for binding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConfigureBedtimeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        var endTimeInMins: Int? = null
        var startTimeInMins: Int? = null

        binding.picker.hourFormat = TimeRangePicker.HourFormat.FORMAT_24
        binding.picker.setOnTimeChangeListener(object :
            TimeRangePicker.OnTimeChangeListener {
            override fun onStartTimeChange(startTime: TimeRangePicker.Time) {
                binding.fromTime.text =
                    binding.picker.startTime.toString()
                startTimeInMins = binding.picker.startTimeMinutes
            }

            override fun onEndTimeChange(endTime: TimeRangePicker.Time) {
                binding.endTime.text =
                    binding.picker.endTime.toString()
                endTimeInMins = binding.picker.endTimeMinutes
            }

            override fun onDurationChange(duration: TimeRangePicker.TimeDuration) {
            }
        })

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
