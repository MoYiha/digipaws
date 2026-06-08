package neth.iecal.curbox.ui.fragments.main.reducers.blockertools.reelBlocker

import android.R.attr.type
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import neth.iecal.curbox.R
import neth.iecal.curbox.data.models.ReelBlockingType
import neth.iecal.curbox.databinding.ReelBlockerFragmentBinding

class ReelBlockerFragment : Fragment() {

    private var _binding: ReelBlockerFragmentBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: ReelBlockerViewModel by activityViewModels()
    private var isUpdatingUi = false
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ReelBlockerFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        observeViewModel()
    }

    private fun setupListeners() {
        binding.switchEnableBlocker.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingUi) {
                viewModel.setIsActive(isChecked)
            }
        }

        binding.rgBlockingType.setOnCheckedChangeListener { _, checkedId ->
            if (!isUpdatingUi) {
                val type = when (checkedId) {
                    R.id.rb_type_time -> ReelBlockingType.TIMED
                    R.id.rb_type_usage -> ReelBlockingType.USAGE
                    R.id.rb_type_count -> ReelBlockingType.REEL_COUNT
                    else -> ReelBlockingType.TIMED
                }
                viewModel.setBlockingType(type)
                updateConfigureButtonText(type)
            }
        }

        binding.btnWarningConfig.setOnClickListener {
            val configFragment = neth.iecal.curbox.ui.fragments.main.reducers.blockertools.shared.WarningConfigFragment.newInstance(viewModel.reelBlockerConfig.value.warningScreenConfig, "result_warning_config_reel")
            parentFragmentManager.beginTransaction()
                .hide(this)
                .add(R.id.fragment_holder, configFragment)
                .addToBackStack(null)
                .commit()
        }

        parentFragmentManager.setFragmentResultListener("result_warning_config_reel", viewLifecycleOwner) { _, bundle ->
            val configStr = bundle.getString("result_config")
            if (configStr != null) {
                viewModel.updateWarningConfig(com.google.gson.Gson().fromJson(configStr, neth.iecal.curbox.data.models.AppBlockerWarningScreenConfig::class.java))
            }
        }

        binding.btnConfigureLimits.setOnClickListener {
            when (viewModel.reelBlockerConfig.value.blockingType) {
                ReelBlockingType.TIMED -> ReelBlockerTimeSettingsFragment().show(childFragmentManager, ReelBlockerTimeSettingsFragment.FRAGMENT_ID)
                ReelBlockingType.USAGE -> ReelBlockerUsageSettingsFragment().show(childFragmentManager, ReelBlockerUsageSettingsFragment.FRAGMENT_ID)
                ReelBlockingType.REEL_COUNT -> ReelBlockerCountSettingsFragment().show(childFragmentManager, ReelBlockerCountSettingsFragment.FRAGMENT_ID)
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.reelBlockerConfig.collectLatest { config ->
                isUpdatingUi = true
                // Avoid infinite loops by checking if state actually changed before triggering listeners
                if (binding.switchEnableBlocker.isChecked != config.isActive) {
                    binding.switchEnableBlocker.isChecked = config.isActive
                }

                val checkedId = when (config.blockingType) {
                    ReelBlockingType.TIMED -> R.id.rb_type_time
                    ReelBlockingType.USAGE -> R.id.rb_type_usage
                    ReelBlockingType.REEL_COUNT -> R.id.rb_type_count
                }
                
                if (binding.rgBlockingType.checkedRadioButtonId != checkedId) {
                    binding.rgBlockingType.check(checkedId)
                }
                
                updateConfigureButtonText(config.blockingType)
                isUpdatingUi = false
            }
        }
    }
    
    private fun updateConfigureButtonText(type: ReelBlockingType) {
        binding.btnConfigureLimits.text = when (type) {
            ReelBlockingType.TIMED -> "Configure Allowed Schedule"
            ReelBlockingType.USAGE -> "Configure Usage Limits"
            ReelBlockingType.REEL_COUNT -> "Configure Reel Count Limit"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val FRAGMENT_ID = "reel_blocker"
    }
}