package nethical.digipaws.ui.fragments.bedtime

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.icu.util.Calendar
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import nethical.digipaws.R
import nethical.digipaws.databinding.DialogPermissionInfoBinding
import nethical.digipaws.databinding.FragmentConfigureBedtimeBinding
import nethical.digipaws.receivers.alarm.BedtimeModeReceiver
import nethical.digipaws.receivers.alarm.BedtimeModeReceiver.Companion.CHANNEL_ID
import nethical.digipaws.utils.SavedPreferencesLoader
import nethical.digipaws.utils.TimeTools
import nethical.digipaws.utils.scheduleExactAlarm
import nl.joery.timerangepicker.TimeRangePicker
import java.text.SimpleDateFormat
import java.util.Locale


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


        if (data.startTimeinMins != -1 && data.endTimeInMins != -1) {
            binding.picker.startTimeMinutes = data.startTimeinMins
            binding.picker.endTimeMinutes = data.endTimeInMins

            binding.fromTime.text =
                binding.picker.startTime.toString()

            binding.endTime.text =
                binding.picker.endTime.toString()
        }

        binding.dimBrightness.isChecked = data.isScreenDim
        binding.turnOnGrayScale.isChecked = data.isGrayScaleOn


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

        checkDndAccessPermission()
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
                binding.picker.startTimeMinutes,
                binding.picker.endTimeMinutes,
                calendarStartTime.timeInMillis,
                calendarEndTime.timeInMillis,
                binding.dimBrightness.isChecked,
                true,
                binding.turnOnGrayScale.isChecked,
                TimeTools.getCurrentDate()
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


            val dateFormat = SimpleDateFormat("dd MMMM yyyy, hh:mm a", Locale.getDefault()) // Customize format as needed
            val formattedEndDate = dateFormat.format(calendarEndTime.time)
            val formattedStartDate = dateFormat.format(calendarStartTime.time)

            Log.d("Alarm setup done",formattedStartDate+ " to " + formattedEndDate)

            Snackbar.make(binding.textView23,"Bedtime mode has ben setup!",Snackbar.LENGTH_LONG).show()
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

    fun checkDndAccessPermission(){
        val notificationManager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            makeDndAccessDialog()
        }
    }

    private fun makeDndAccessDialog() {
        val dialogDndPermissionDialog =
            DialogPermissionInfoBinding.inflate(layoutInflater)
        dialogDndPermissionDialog.title.text = getString(R.string.enable_2, "DND access")

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogDndPermissionDialog.root)
            .setCancelable(false)
            .show()

        dialogDndPermissionDialog.desc.setText("DigiPaws uses the Do Not Disturb (DND) permission to automatically silence notifications during bedtime mode, ensuring uninterrupted sleep for users.")

        dialogDndPermissionDialog.point1.setText("Turns off notifications automatically.")
        dialogDndPermissionDialog.point2.setText("Helps you sleep peacefully.")
        dialogDndPermissionDialog.point3.setText("Restores notifications when awake.")
        dialogDndPermissionDialog.point4.visibility = View.GONE

        dialogDndPermissionDialog.btnReject.setOnClickListener {
            dialog.dismiss()
            requireActivity().finish()
        }
        dialogDndPermissionDialog.btnAccept.setOnClickListener {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            requireContext().startActivity(intent)

            dialog.dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    data class BedTimeModeData(
        //stores picker time
        val startTimeinMins: Int = -1,
        val endTimeInMins: Int = -1,

        // stores calendar time
        val startTimeInMillis:Long = -1L,
        val endTimeInMillis:Long = -1L,

        val isScreenDim:Boolean = false,
        val isDnd:Boolean = true,
        val isGrayScaleOn: Boolean = false,

        var lastAlarmSetupForDate: String = TimeTools.getCurrentDate(),
    )
}
