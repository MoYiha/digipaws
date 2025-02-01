package nethical.digipaws.ui.fragments.bedtime

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.icu.util.Calendar
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import nethical.digipaws.databinding.FragmentConfigureBedtimeBinding
import nethical.digipaws.receivers.alarm.BedtimeModeReceiver
import nethical.digipaws.receivers.alarm.BedtimeModeReceiver.Companion.CHANNEL_ID
import nethical.digipaws.utils.SavedPreferencesLoader
import nethical.digipaws.utils.scheduleExactAlarm
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

        val savedPreferencesLoader = SavedPreferencesLoader(requireContext())
        val data = savedPreferencesLoader.getBedTimeData()

        if (data.startTimeInMillis != -1L || data.endTimeInMillis != -1L) {
            binding.picker.startTimeMinutes = (data.startTimeInMillis / 60000L).toInt()
            binding.picker.endTimeMinutes = (data.endTimeInMillis / 60000L).toInt()
        }

        binding.dimBrightness.isChecked = data.isScreenDim
        binding.turnOnGrayScale.isChecked = data.isGrayScaleOn
        binding.turnOnDnd.isChecked = data.isDnd


        binding.picker.hourFormat = TimeRangePicker.HourFormat.FORMAT_24
        binding.picker.setOnTimeChangeListener(object :
            TimeRangePicker.OnTimeChangeListener {
            override fun onStartTimeChange(startTime: TimeRangePicker.Time) {
                binding.fromTime.text =
                    binding.picker.startTime.toString()
            }

            override fun onEndTimeChange(endTime: TimeRangePicker.Time) {
                binding.endTime.text =
                    binding.picker.endTime.toString()
            }

            override fun onDurationChange(duration: TimeRangePicker.TimeDuration) {
            }
        })

        binding.turnonBedtime.setOnClickListener {

            val calendarStartTime = Calendar.getInstance()
            calendarStartTime[Calendar.HOUR_OF_DAY] = binding.picker.startTime.hour
            calendarStartTime[Calendar.MINUTE] = binding.picker.startTime.minute
            calendarStartTime[Calendar.SECOND] = 0


            val calendarEndTime = Calendar.getInstance()
            calendarEndTime[Calendar.HOUR_OF_DAY] = binding.picker.endTime.hour
            calendarEndTime[Calendar.MINUTE] = binding.picker.endTime.minute
            calendarEndTime[Calendar.SECOND] = 0


            val bedTimeModeData = BedTimeModeData(
                calendarStartTime.timeInMillis,
                calendarEndTime.timeInMillis,
                binding.dimBrightness.isChecked,
                binding.turnOnDnd.isChecked,
                binding.turnOnGrayScale.isChecked
            )

            savedPreferencesLoader.saveBedTimeData(bedTimeModeData)

            val alarmManager = requireContext(). getSystemService(Context.ALARM_SERVICE) as AlarmManager
            scheduleExactAlarm(
                alarmManager = alarmManager,
                context = requireContext(),
                requestCode = 0,
                action = BedtimeModeReceiver.ACTION_START_BEDTIME,
                triggerTimeInMillis = bedTimeModeData.startTimeInMillis,
                receiverClass = BedtimeModeReceiver::class.java
            )
            scheduleExactAlarm(
                alarmManager = alarmManager,
                context = requireContext(),
                requestCode = 0,
                action = BedtimeModeReceiver.ACTION_STOP_BEDTIME,
                triggerTimeInMillis = bedTimeModeData.endTimeInMillis,
                receiverClass = BedtimeModeReceiver::class.java
            )
  }



        val channel = NotificationChannel(
            CHANNEL_ID,
            "Bedtime Notifications",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Show information about bedtime mode"
        }
        NotificationManagerCompat.from(requireContext()).createNotificationChannel(channel)

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    data class BedTimeModeData(
        val startTimeInMillis:Long = -1L,
        val endTimeInMillis:Long = -1L,
        val isScreenDim:Boolean = false,
        val isDnd:Boolean = false,
        val isGrayScaleOn: Boolean = false,
    )
}
