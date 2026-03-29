package neth.iecal.curbox.ui.fragments.main.reducers.blockertools.reelBlocker

import android.app.Dialog
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import neth.iecal.curbox.databinding.AppBlockerUsageLimitItemBinding
import neth.iecal.curbox.databinding.FragmentReelBlockerUsageSettingsBinding
import neth.iecal.curbox.data.models.ReelUsageConfig

class ReelBlockerUsageSettingsFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentReelBlockerUsageSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ReelBlockerViewModel by activityViewModels()

    // Define the days (Index 0 is the Uniform toggle)
    private val daysOfWeek = listOf(
        "Same Limit Everyday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
    )

    private val rowBindings = mutableListOf<AppBlockerUsageLimitItemBinding>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReelBlockerUsageSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        populateDaysList()
        loadConfig()
        binding.saveSettings.setOnClickListener {
            saveConfig()
            dismiss()
        }
    }

    private fun loadConfig() {
        val config = viewModel.getReelUsageConfig()
        if (config.isDailyUniform) {
            val uniformRow = rowBindings[0]
            uniformRow.daySwitch.isChecked = true
            setRowTime(uniformRow, config.uniformLimit)
            handleUniformLimitToggle(true)
        } else {
            rowBindings[0].daySwitch.isChecked = false
            setRowTime(rowBindings[1], config.dailyLimits[1]) // Mon
            setRowTime(rowBindings[2], config.dailyLimits[2]) // Tue
            setRowTime(rowBindings[3], config.dailyLimits[3]) // Wed
            setRowTime(rowBindings[4], config.dailyLimits[4]) // Thu
            setRowTime(rowBindings[5], config.dailyLimits[5]) // Fri
            setRowTime(rowBindings[6], config.dailyLimits[6]) // Sat
            setRowTime(rowBindings[7], config.dailyLimits[0]) // Sun
            handleUniformLimitToggle(false)
        }
    }

    private fun setRowTime(row: AppBlockerUsageLimitItemBinding, minutesLimit: Long) {
        if (minutesLimit > 0) {
            row.daySwitch.isChecked = true
            val hours = minutesLimit / 60
            val mins = minutesLimit % 60
            row.hoursInput.setText(if (hours > 0) hours.toString() else "")
            row.minutesInput.setText(if (mins > 0) mins.toString() else "")
            row.timeInputContainer.visibility = View.VISIBLE
            row.timeInputContainer.alpha = 1f
        } else {
            row.daySwitch.isChecked = false
            row.hoursInput.setText("")
            row.minutesInput.setText("")
            row.timeInputContainer.visibility = View.GONE
            row.timeInputContainer.alpha = 0f
        }
    }

    private fun populateDaysList() {
        val inflater = LayoutInflater.from(requireContext())

        daysOfWeek.forEachIndexed { index, dayName ->
            val rowBinding = AppBlockerUsageLimitItemBinding.inflate(inflater, binding.daysListContainer, true)
            rowBindings.add(rowBinding)

            // Disable automatic state saving to prevent ID collisions from overriding loadConfig()
            rowBinding.hoursInput.isSaveEnabled = false
            rowBinding.minutesInput.isSaveEnabled = false
            rowBinding.daySwitch.isSaveEnabled = false

            rowBinding.dayNameText.text = dayName

            rowBinding.daySwitch.setOnCheckedChangeListener { switchView, isChecked ->
                switchView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)

                // Expand/Collapse Animation
                if (isChecked) {
                    rowBinding.timeInputContainer.visibility = View.VISIBLE
                    rowBinding.timeInputContainer.animate().alpha(1f).setDuration(250).start()
                } else {
                    rowBinding.timeInputContainer.animate().alpha(0f).setDuration(200).withEndAction {
                        rowBinding.timeInputContainer.visibility = View.GONE
                        rowBinding.hoursInput.text?.clear()
                        rowBinding.minutesInput.text?.clear()
                    }.start()
                }

                // If this is the "Same Limit Everyday" switch (index 0)
                if (index == 0) {
                    handleUniformLimitToggle(isChecked)
                }
            }

            // Auto-advance to minutes when hours are filled
            rowBinding.hoursInput.setOnKeyListener { _, _, _ ->
                if (rowBinding.hoursInput.text?.length == 2) {
                    rowBinding.minutesInput.requestFocus()
                }
                false
            }
        }
    }

    private fun handleUniformLimitToggle(isUniform: Boolean) {
        // Loop through Monday (1) to Sunday (7)
        for (i in 1 until rowBindings.size) {
            val dayRow = rowBindings[i]

            // Disable interactions and dim the view
            dayRow.root.alpha = if (isUniform) 0.5f else 1.0f
            dayRow.daySwitch.isEnabled = !isUniform

            // Force uncheck if uniform is turned on
            if (isUniform) {
                dayRow.daySwitch.isChecked = false
            }
        }
    }

    private fun saveConfig() {
        val newConfig = viewModel.getReelUsageConfig()
        val uniformRow = rowBindings[0]
        newConfig.isDailyUniform = uniformRow.daySwitch.isChecked

        if (newConfig.isDailyUniform) {
            newConfig.uniformLimit = calculateMinutes(uniformRow)
            // Optional: zero out individual days to keep data clean
            newConfig.dailyLimits.fill(0)
        } else {
            newConfig.uniformLimit = 0

            // Map our UI indices to your DailyLimits array (where 0 = Sunday)
            // UI mapping: Monday(1), Tuesday(2), Wednesday(3), Thursday(4), Friday(5), Saturday(6), Sunday(7)
            newConfig.dailyLimits[1] = calculateMinutes(rowBindings[1]) // Monday
            newConfig.dailyLimits[2] = calculateMinutes(rowBindings[2]) // Tuesday
            newConfig.dailyLimits[3] = calculateMinutes(rowBindings[3]) // Wednesday
            newConfig.dailyLimits[4] = calculateMinutes(rowBindings[4]) // Thursday
            newConfig.dailyLimits[5] = calculateMinutes(rowBindings[5]) // Friday
            newConfig.dailyLimits[6] = calculateMinutes(rowBindings[6]) // Saturday
            newConfig.dailyLimits[0] = calculateMinutes(rowBindings[7]) // Sunday
        }
        
        viewModel.saveReelUsageConfig(newConfig)
    }

    private fun calculateMinutes(row: AppBlockerUsageLimitItemBinding): Long {
        if (!row.daySwitch.isChecked) return 0L

        val hoursStr = row.hoursInput.text.toString()
        val minutesStr = row.minutesInput.text.toString()

        val hours = if (hoursStr.isNotEmpty()) hoursStr.toLong() else 0L
        val minutes = if (minutesStr.isNotEmpty()) minutesStr.toLong() else 0L

        return (hours * 60) + minutes
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        rowBindings.clear()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        return dialog
    }

    companion object {
        const val FRAGMENT_ID = "ReelBlockerUsageSettingsBottomSheet"
    }
}
