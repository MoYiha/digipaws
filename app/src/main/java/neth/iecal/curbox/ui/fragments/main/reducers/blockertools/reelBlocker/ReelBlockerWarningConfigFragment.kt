package neth.iecal.curbox.ui.fragments.main.reducers.blockertools.reelBlocker

import android.app.Dialog
import android.os.Bundle
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import neth.iecal.curbox.R
import neth.iecal.curbox.data.models.AppBlockerWarningScreenConfig
import neth.iecal.curbox.databinding.FragmentReelBlockerWarningConfigBinding

class ReelBlockerWarningConfigFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentReelBlockerWarningConfigBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ReelBlockerViewModel by activityViewModels()

    private var selectedProceedDelay = 15

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReelBlockerWarningConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupInitialState()
        setupListeners()
    }

    private fun setupInitialState() {
        val config = viewModel.reelBlockerConfig.value.warningScreenConfig

        val initialRadioId = when {
            config.isWarningDialogHidden -> R.id.direct_back_rb
            config.isProceedDisabled -> R.id.disable_proceed_rb
            config.isDynamicIntervalSettingAllowed -> R.id.dynamic_timing_rb
            else -> R.id.predefined_time_rb
        }
        binding.warningRadioGroup.check(initialRadioId)
        updateUiVisibility(initialRadioId, animate = false)

        selectedProceedDelay = config.proceedDelayInSecs
        val initialChipId = when (selectedProceedDelay) {
            3 -> R.id.three_sec_chip
            9 -> R.id.nine_sec_chip
            30 -> R.id.thirty_sec_chip
            else -> R.id.fifteen_sec_chip
        }
        binding.proceedDelayChips.check(initialChipId)
        binding.warningMsgEdit.setText(config.message)
        binding.selectMins.setValue(config.timeInterval / 60000)
        binding.switchVibrateBrightness.isChecked = config.vibrateAndIncBrightness
    }

    private fun setupListeners() {
        binding.warningRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            updateUiVisibility(checkedId, animate = true)
        }

        binding.proceedDelayChips.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedProceedDelay = when (checkedIds.firstOrNull()) {
                R.id.three_sec_chip -> 3
                R.id.nine_sec_chip -> 9
                R.id.thirty_sec_chip -> 30
                R.id.fifteen_sec_chip -> 15
                else -> 15
            }
        }

        binding.saveconfigs.setOnClickListener {
            val config = AppBlockerWarningScreenConfig(
                message = binding.warningMsgEdit.text.toString(),
                timeInterval = binding.selectMins.getValue() * 60_000,
                isDynamicIntervalSettingAllowed = binding.dynamicTimingRb.isChecked,
                isProceedDisabled = binding.disableProceedRb.isChecked,
                isWarningDialogHidden = binding.directBackRb.isChecked,
                proceedDelayInSecs = selectedProceedDelay,
                vibrateAndIncBrightness = binding.switchVibrateBrightness.isChecked
            )
            viewModel.updateWarningConfig(config)
            dismiss()
        }
    }

    private fun updateUiVisibility(checkedId: Int, animate: Boolean) {
        if (animate) {
            TransitionManager.beginDelayedTransition(
                binding.mainContentContainer,
                AutoTransition()
            )
        }

        binding.apply {
            dynamicTiming.visibility = if (checkedId == R.id.predefined_time_rb) View.VISIBLE else View.GONE
            proceedDelay.visibility = if (checkedId == R.id.predefined_time_rb || checkedId == R.id.dynamic_timing_rb) View.VISIBLE else View.GONE
            textInputLayout2.visibility = if (checkedId != R.id.direct_back_rb) View.VISIBLE else View.GONE
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val FRAGMENT_ID = "reel_blocker_warning_screen_config"
    }
}
