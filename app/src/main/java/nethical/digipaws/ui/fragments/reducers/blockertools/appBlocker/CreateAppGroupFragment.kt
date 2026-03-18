package nethical.digipaws.ui.fragments.reducers.blockertools.appBlocker

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.gson.Gson
import nethical.digipaws.R
import nethical.digipaws.data.models.AppBlockingType
import nethical.digipaws.data.models.AppGroup
import nethical.digipaws.databinding.FragmentCreateAppGroupBinding
import nethical.digipaws.ui.activity.SelectAppsActivity
import java.util.UUID
class CreateAppGroupFragment : Fragment() {

    companion object {
        const val FRAGMENT_ID = "create_app_group"
    }

    private var _binding: FragmentCreateAppGroupBinding? = null
    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private var selectedApps: ArrayList<String> = arrayListOf()
    private val viewModel: AppBlockerSettingViewModel by activityViewModels()

    private val selectAppsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            val apps = result.data?.getStringArrayListExtra("SELECTED_APPS")
            if (apps != null) {
                selectedApps = apps
                binding.btnSelectApps.text = "Select Apps (${selectedApps.size})"
            }
        }
    }



    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateAppGroupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            requireActivity().finish()
        }

        binding.btnSelectApps.setOnClickListener {
            val intent = Intent(requireContext(), SelectAppsActivity::class.java)
            intent.putStringArrayListExtra("PRE_SELECTED_APPS", selectedApps)
            selectAppsLauncher.launch(intent)
        }

        binding.btnConfigureSettings.setOnClickListener {
            val isUsageBased = binding.rgBlockingType.checkedRadioButtonId == R.id.rb_usage_based

            if (isUsageBased) {
                UsageBasedSettingsFragment().show(parentFragmentManager, UsageBasedSettingsFragment.FRAGMENT_ID)
            } else {
                TimeBasedSettingsFragment().show(parentFragmentManager, UsageBasedSettingsFragment.FRAGMENT_ID)
            }
        }
        binding.configureWarningScreen.setOnClickListener {
            AppBlockerWarningConfigFragment().show(parentFragmentManager,
                AppBlockerWarningConfigFragment.FRAGMENT_ID)
        }


        binding.fabSaveGroup.setOnClickListener {
            val name = binding.etGroupName.text.toString().trim()
            if (name.isEmpty()) {
                binding.etGroupName.error = "Please enter a group name"
                return@setOnClickListener
            }
            
            if (selectedApps.isEmpty()) {
                Toast.makeText(requireContext(), "Please select at least one app", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val isUsageBased = binding.rgBlockingType.checkedRadioButtonId == R.id.rb_usage_based
            val blockingType = if (isUsageBased) AppBlockingType.Usage else AppBlockingType.Timed

            val newGroup = AppGroup(
                id = UUID.randomUUID().toString(),
                name = name,
                selectedPackages = selectedApps.toList(),
                blockingType = blockingType,
                isActive = true,
                setting = if(isUsageBased){
                    Gson().toJson(viewModel.currentUsageConfig)
                } else {
                    Gson().toJson(viewModel.currentTimeConfig)
                },
                warningScreenConfig = viewModel.warningScrnConfig
            )

            viewModel.addGroup(newGroup)

            Toast.makeText(requireContext(), "Group saved successfully", Toast.LENGTH_SHORT).show()
            requireActivity().finish()
        }
    }
}
