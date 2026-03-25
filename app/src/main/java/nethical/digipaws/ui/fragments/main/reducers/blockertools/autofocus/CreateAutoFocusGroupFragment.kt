package nethical.digipaws.ui.fragments.main.reducers.blockertools.autofocus

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import nethical.digipaws.R
import nethical.digipaws.data.models.AutoFocusGroup
import nethical.digipaws.data.models.FocusBlockMode
import nethical.digipaws.databinding.FragmentCreateAutofocusGroupBinding
import nethical.digipaws.ui.activity.SelectAppsActivity
import java.util.UUID

class CreateAutoFocusGroupFragment : Fragment() {

    companion object {
        const val FRAGMENT_ID = "create_autofocus_group"
    }

    private var _binding: FragmentCreateAutofocusGroupBinding? = null
    private val binding get() = _binding!!

    private var selectedApps: ArrayList<String> = arrayListOf()
    private val viewModel: AutoFocusViewModel by activityViewModels()

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
        _binding = FragmentCreateAutofocusGroupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            requireActivity().finish()
        }

        var isEditing = false
        var existingGroup: AutoFocusGroup? = null
        val groupId = arguments?.getString("group_id")

        if (groupId == null) {
            viewModel.currentDailyIntervals = mutableMapOf()
        }

        if (groupId != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.groups.collectLatest { groups ->
                    val group = groups.find { it.groupId == groupId }
                    if (group != null && !isEditing) {
                        isEditing = true
                        existingGroup = group
                        binding.toolbar.title = "Edit AutoFocus Group"
                        binding.etGroupName.setText(group.groupName)
                        selectedApps = ArrayList(group.packages.toList())
                        binding.btnSelectApps.text = "Select Apps (${selectedApps.size})"

                        viewModel.currentDailyIntervals = group.dailyIntervals.toMutableMap()

                        if (group.blockMode == FocusBlockMode.BLOCK_SELECTED) {
                            binding.rgBlockingType.check(R.id.rb_block_selected)
                        } else {
                            binding.rgBlockingType.check(R.id.rb_block_all_except_selected)
                        }
                        
                        binding.switchExitable.isChecked = group.exitable

                        binding.toolbar.menu.clear()
                        val deleteItem = binding.toolbar.menu.add(0, 1001, 0, "Delete")
                        deleteItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                        binding.toolbar.setOnMenuItemClickListener { item ->
                            if (item.itemId == 1001) {
                                viewModel.removeGroup(group)
                                Toast.makeText(requireContext(), "Group deleted", Toast.LENGTH_SHORT).show()
                                requireActivity().finish()
                                true
                            } else {
                                false
                            }
                        }
                    }
                }
            }
        }

        binding.btnSelectApps.setOnClickListener {
            val intent = Intent(requireContext(), SelectAppsActivity::class.java)
            intent.putStringArrayListExtra("PRE_SELECTED_APPS", selectedApps)
            selectAppsLauncher.launch(intent)
        }

        binding.btnConfigureSchedule.setOnClickListener {
            AutoFocusTimeSettingsFragment().show(parentFragmentManager, AutoFocusTimeSettingsFragment.FRAGMENT_ID)
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

            val isBlockSelected = binding.rgBlockingType.checkedRadioButtonId == R.id.rb_block_selected
            val blockMode = if (isBlockSelected) FocusBlockMode.BLOCK_SELECTED else FocusBlockMode.BLOCK_ALL_EXCEPT_SELECTED
            val exitable = binding.switchExitable.isChecked

            val savedGroupId = arguments?.getString("group_id")
            val isEditingRecord = savedGroupId != null
            val targetExistingGroup = viewModel.groups.value.find { it.groupId == savedGroupId }

            val newGroup = AutoFocusGroup(
                groupId = if (isEditingRecord && targetExistingGroup != null) targetExistingGroup.groupId else UUID.randomUUID().toString(),
                groupName = name,
                packages = HashSet(selectedApps),
                blockMode = blockMode,
                exitable = exitable,
                dailyIntervals = viewModel.currentDailyIntervals
            )

            if (isEditingRecord && targetExistingGroup != null) {
                viewModel.updateGroup(newGroup)
            } else {
                viewModel.addGroup(newGroup)
            }

            Toast.makeText(requireContext(), "Group saved successfully", Toast.LENGTH_SHORT).show()
            requireActivity().finish()
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
