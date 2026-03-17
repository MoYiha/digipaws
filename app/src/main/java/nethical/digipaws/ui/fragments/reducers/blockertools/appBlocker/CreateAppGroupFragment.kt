package nethical.digipaws.ui.fragments.reducers.blockertools.appBlocker

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import nethical.digipaws.R
import nethical.digipaws.data.models.AppBlockingType
import nethical.digipaws.data.models.AppGroup
import nethical.digipaws.ui.activity.SelectAppsActivity
import nethical.digipaws.utils.DataStoreManager
import nethical.digipaws.utils.SavedPreferencesLoader
import java.util.UUID
class CreateAppGroupFragment : Fragment() {

    companion object {
        const val FRAGMENT_ID = "create_app_group"
    }

    private lateinit var etGroupName: TextInputEditText
    private lateinit var btnSelectApps: MaterialButton
    private lateinit var rgBlockingType: RadioGroup
    private lateinit var btnConfigureSettings: MaterialButton
    private lateinit var fabSaveGroup: ExtendedFloatingActionButton
    private lateinit var toolbar: MaterialToolbar

    private var selectedApps: ArrayList<String> = arrayListOf()
    private val viewModel: AppBlockerSettingViewModel by activityViewModels()

    private val selectAppsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            val apps = result.data?.getStringArrayListExtra("SELECTED_APPS")
            if (apps != null) {
                selectedApps = apps
                btnSelectApps.text = "Select Apps (${selectedApps.size})"
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_create_app_group, container, false)
        etGroupName = view.findViewById(R.id.et_group_name)
        btnSelectApps = view.findViewById(R.id.btn_select_apps)
        rgBlockingType = view.findViewById(R.id.rg_blocking_type)
        btnConfigureSettings = view.findViewById(R.id.btn_configure_settings)
        fabSaveGroup = view.findViewById(R.id.fab_save_group)
        toolbar = view.findViewById(R.id.toolbar)

        toolbar.setNavigationOnClickListener {
            requireActivity().finish()
        }

        btnSelectApps.setOnClickListener {
            val intent = Intent(requireContext(), SelectAppsActivity::class.java)
            intent.putStringArrayListExtra("PRE_SELECTED_APPS", selectedApps)
            selectAppsLauncher.launch(intent)
        }

        btnConfigureSettings.setOnClickListener {
            val isUsageBased = rgBlockingType.checkedRadioButtonId == R.id.rb_usage_based

            if (isUsageBased) {
                UsageBasedSettingsFragment().show(parentFragmentManager, UsageBasedSettingsFragment.FRAGMENT_ID)


            } else {
                TimeBasedSettingsFragment().show(parentFragmentManager, UsageBasedSettingsFragment.FRAGMENT_ID)
            }
        }

        fabSaveGroup.setOnClickListener {
            val name = etGroupName.text.toString().trim()
            if (name.isEmpty()) {
                etGroupName.error = "Please enter a group name"
                return@setOnClickListener
            }
            
            if (selectedApps.isEmpty()) {
                Toast.makeText(requireContext(), "Please select at least one app", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val isUsageBased = rgBlockingType.checkedRadioButtonId == R.id.rb_usage_based
            val blockingType = if (isUsageBased) AppBlockingType.Usage else AppBlockingType.Timed
            val dataStoreManager = DataStoreManager(requireContext())

            CoroutineScope(Dispatchers.IO).launch {
                dataStoreManager.settings.collect {
                    val existingGroups = it.blockedAppGroups.toMutableList()

                    val newGroup = AppGroup(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        selectedPackages = selectedApps.toList(),
                        blockingType = blockingType,
                        isActive = true,
                        setting = if(isUsageBased){
                            Gson().toJson(viewModel.currentConfig)
                        } else {
                            ""
                        }
                    )

                    existingGroups.add(newGroup)
                    dataStoreManager.updateGroups(existingGroups)
                }
            }

            Toast.makeText(requireContext(), "Group saved successfully", Toast.LENGTH_SHORT).show()
            requireActivity().finish()
        }

        return view
    }
}
