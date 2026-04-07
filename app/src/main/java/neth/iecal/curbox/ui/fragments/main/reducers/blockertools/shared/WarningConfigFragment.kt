package neth.iecal.curbox.ui.fragments.main.reducers.blockertools.shared

import android.os.Bundle
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.gson.Gson
import neth.iecal.curbox.R
import neth.iecal.curbox.data.models.AppBlockerWarningScreenConfig
import neth.iecal.curbox.databinding.FragmentWarningConfigBinding

class WarningConfigFragment : Fragment() {

    private var _binding: FragmentWarningConfigBinding? = null
    private val binding get() = _binding!!

    private var selectedProceedDelay = 15
    private var initialConfig: AppBlockerWarningScreenConfig? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val configStr = arguments?.getString(ARG_CONFIG)
        initialConfig = if (configStr != null) {
            Gson().fromJson(configStr, AppBlockerWarningScreenConfig::class.java)
        } else null
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWarningConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupInitialState()
        setupListeners()
    }

    private fun setupInitialState() {
        val config = initialConfig ?: AppBlockerWarningScreenConfig()

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
        
        binding.proceedLimitSwitch.isChecked = config.proceedLimitEnabled
        binding.proceedLimitContainer.visibility = if (config.proceedLimitEnabled) View.VISIBLE else View.GONE
        binding.selectAllowedProceeds.setValue(config.allowedProceeds)
        binding.selectAllowedProceeds.minValue = 1
        binding.selectProceedsTimeWindow.setValue(config.proceedsTimeWindowMn)
        binding.selectProceedsTimeWindow.minValue = 1
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

        binding.proceedLimitSwitch.setOnCheckedChangeListener { _, isChecked ->
            binding.proceedLimitContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        
        binding.saveconfigs.setOnClickListener {
            val config = AppBlockerWarningScreenConfig(
                message = binding.warningMsgEdit.text.toString(),
                timeInterval = binding.selectMins.getValue() * 60_000,
                isDynamicIntervalSettingAllowed = binding.dynamicTimingRb.isChecked,
                isProceedDisabled = binding.disableProceedRb.isChecked,
                isWarningDialogHidden = binding.directBackRb.isChecked,
                proceedDelayInSecs = selectedProceedDelay,
                vibrateAndIncBrightness = binding.switchVibrateBrightness.isChecked,
                proceedLimitEnabled = binding.proceedLimitSwitch.isChecked,
                allowedProceeds = binding.selectAllowedProceeds.getValue(),
                proceedsTimeWindowMn = binding.selectProceedsTimeWindow.getValue()
            )
            val requestKey = arguments?.getString(ARG_REQUEST_KEY) ?: RESULT_KEY
            parentFragmentManager.setFragmentResult(requestKey, Bundle().apply {
                putString(RESULT_CONFIG, Gson().toJson(config))
            })
            parentFragmentManager.popBackStack()
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val FRAGMENT_ID = "warning_config_fragment"
        const val ARG_CONFIG = "arg_config"
        const val ARG_REQUEST_KEY = "arg_request_key"
        const val RESULT_KEY = "request_key_warning_config"
        const val RESULT_CONFIG = "result_config"

        fun newInstance(config: AppBlockerWarningScreenConfig, requestKey: String = RESULT_KEY): WarningConfigFragment {
            return WarningConfigFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CONFIG, Gson().toJson(config))
                    putString(ARG_REQUEST_KEY, requestKey)
                }
            }
        }
    }
}
