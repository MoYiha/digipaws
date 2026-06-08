package neth.iecal.curbox.ui.fragments.main.focus

import android.app.Activity
import android.app.Dialog
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import neth.iecal.curbox.R
import neth.iecal.curbox.data.models.FocusBlockMode
import neth.iecal.curbox.data.models.ManualFocusGroup
import neth.iecal.curbox.databinding.DialogFocusSessionConfigBinding
import neth.iecal.curbox.ui.activity.SelectAppsActivity

class FocusSetupBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogFocusSessionConfigBinding? = null
    private val binding get() = _binding!!
    private val viewModel: FocusViewModel by activityViewModels()

    private lateinit var autoCompleteAdapter: ArrayAdapter<ManualFocusGroup>

    private val selectAppsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val selectedApps = result.data?.getStringArrayListExtra("SELECTED_APPS") ?: return@registerForActivityResult
            viewModel.newGroupSelectedApps = HashSet(selectedApps)
            binding.selectedAppCount.text = "Selected: " + selectedApps.size
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogFocusSessionConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupGroupSelectionDropdown()
        observeViewModel()

        if (viewModel.groups.value.isEmpty()) {
            binding.createGroup.visibility = View.VISIBLE
            binding.selectGrouo.visibility = View.GONE
        } else {
            viewModel.selectedGroup?.let { group ->
                binding.groupDropdown.setText(group.toString(), false)
                binding.btnEditGroup.visibility = View.VISIBLE
                binding.btnDeleteGroup.visibility = View.VISIBLE
            }
        }

        binding.btnCreateGroup.setOnClickListener {
            // clear if creating new
            viewModel.selectedGroup = null
            binding.groupName.setText("")
            viewModel.newGroupSelectedApps = HashSet()
            viewModel.newGroupSelectedKeywords = hashSetOf()
            binding.selectedAppCount.text = "Selected: 0"
            binding.selectedWebsiteCount.text = "Selected: ${viewModel.newGroupSelectedKeywords.size}"
            binding.exitable.isChecked = true
            binding.autoTurnOnDnd.isChecked = false
            
            binding.createGroup.visibility = View.VISIBLE
            binding.selectGrouo.visibility = View.GONE
        }

        binding.btnConfirmStart.setOnClickListener {
            viewModel.startFocusing()
            dismiss()
        }

        binding.groupDropdown.setOnItemClickListener { parent, view, position, id ->
            val clickedItem = parent.getItemAtPosition(position)
            val selectedGroup = clickedItem as ManualFocusGroup
            viewModel.selectedGroup = selectedGroup
            binding.btnEditGroup.visibility = View.VISIBLE
            binding.btnDeleteGroup.visibility = View.VISIBLE
        }

        binding.btnEditGroup.setOnClickListener {
            val group = viewModel.selectedGroup ?: return@setOnClickListener
            // Pre-fill the create group form with this group's details
            binding.createGroup.visibility = View.VISIBLE
            binding.selectGrouo.visibility = View.GONE
            
            binding.groupName.setText(group.groupName)
            viewModel.newGroupSelectedApps = HashSet(group.packages)
            viewModel.newGroupSelectedKeywords = HashSet(group.keywords)
            binding.selectedAppCount.text = "Selected: ${group.packages.size}"
            binding.selectedWebsiteCount.text = "Selected: ${group.keywords.size}"
            if (group.blockMode == FocusBlockMode.BLOCK_SELECTED) {
                binding.selectedBlockAction.check(R.id.btn_selected)
            } else {
                binding.selectedBlockAction.check(R.id.btn_block_all_excpt_selected)
            }
            binding.exitable.isChecked = group.exitable
            binding.autoTurnOnDnd.isChecked = group.autoTurnOnDnd
        }

        binding.btnDeleteGroup.setOnClickListener {
            val group = viewModel.selectedGroup ?: return@setOnClickListener
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete Focus Group")
                .setMessage("Are you sure you want to delete this group? All associated focus statistics will also be deleted.")
                .setPositiveButton("Delete") { _, _ ->
                    viewModel.removeGroup(group)
                    viewModel.selectedGroup = null
                    binding.groupDropdown.setText("", false)
                    binding.btnEditGroup.visibility = View.GONE
                    binding.btnDeleteGroup.visibility = View.GONE
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnSelectApps.setOnClickListener {
            val intent = Intent(requireContext(), SelectAppsActivity::class.java)
            intent.putStringArrayListExtra("PRE_SELECTED_APPS", ArrayList(viewModel.newGroupSelectedApps))
            selectAppsLauncher.launch(intent)
        }

        binding.btnAddWebsites.setOnClickListener {
            showAddWebsitesDialog()
        }

        binding.autoTurnOnDnd.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                val nm = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (!nm.isNotificationPolicyAccessGranted) {
                    binding.autoTurnOnDnd.isChecked = false
                    val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    startActivity(intent)
                    android.widget.Toast.makeText(requireContext(), "Please grant Do Not Disturb access to use this feature", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }

        binding.saveGroup.setOnClickListener {
            val isEditing = viewModel.selectedGroup != null
            val blockMode = if(binding.selectedBlockAction.checkedButtonId == R.id.btn_selected) FocusBlockMode.BLOCK_SELECTED else FocusBlockMode.BLOCK_ALL_EXCEPT_SELECTED
            
            if (viewModel.newGroupSelectedKeywords.isNotEmpty()) {
                val supportedBrowsers = neth.iecal.curbox.blockers.KeywordBlocker.URL_BAR_ID_LIST.keys
                val hasBrowserSelected = viewModel.newGroupSelectedApps.any { it in supportedBrowsers }
                
                if (!hasBrowserSelected) {
                    if (blockMode == FocusBlockMode.BLOCK_ALL_EXCEPT_SELECTED) {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("No Browser Selected")
                            .setMessage("You have set some websites to be allowed, but no supported browser is in your 'Allowed Apps' list. \n\nTo access these websites, please add a browser (like Chrome or Firefox) to the selected apps.")
                            .setPositiveButton("Add Browser") { _, _ ->
                                binding.btnSelectApps.performClick()
                            }
                            .setNegativeButton("Save Anyway") { _, _ ->
                                saveFocusGroup(isEditing, blockMode)
                            }
                            .show()
                    } else {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Website Blocking Notice")
                            .setMessage("You have set some websites to be blocked, but no browser is in your 'Blocked Apps' list. \n\nNote that website blocking only works on supported browsers (like Chrome or Firefox). Any other browser will still be able to access these websites. Consider blocking unsupported browsers if needed.")
                            .setPositiveButton("Add Browser") { _, _ ->
                                binding.btnSelectApps.performClick()
                            }
                            .setNegativeButton("Save Anyway") { _, _ ->
                                saveFocusGroup(isEditing, blockMode)
                            }
                            .show()
                    }
                    return@setOnClickListener
                }
            }

            saveFocusGroup(isEditing, blockMode)
        }
    }

    private fun saveFocusGroup(isEditing: Boolean, blockMode: FocusBlockMode) {
        val newGroup = ManualFocusGroup(
            groupId = if (isEditing) viewModel.selectedGroup!!.groupId else java.util.UUID.randomUUID().toString(),
            groupName = binding.groupName.text.toString(),
            packages = viewModel.newGroupSelectedApps,
            keywords = viewModel.newGroupSelectedKeywords,
            blockMode = blockMode,
            exitable = binding.exitable.isChecked,
            autoTurnOnDnd = binding.autoTurnOnDnd.isChecked
        )

        if (isEditing) {
            viewModel.updateGroup(newGroup)
        } else {
            viewModel.addGroup(newGroup)
        }

        binding.createGroup.visibility = View.GONE
        binding.selectGrouo.visibility = View.VISIBLE

        // Select it after it was created/edited
        viewModel.selectedGroup = newGroup
        binding.groupDropdown.setText(newGroup.toString(), false)
        binding.btnEditGroup.visibility = View.VISIBLE
        binding.btnDeleteGroup.visibility = View.VISIBLE
    }

    private fun showAddWebsitesDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_focus_add_websites, null)
        val etWebsite = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_website)
        val btnAdd = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_add)
        val cgWebsites = view.findViewById<com.google.android.material.chip.ChipGroup>(R.id.cg_websites)
        
        val tempKeywords = HashSet(viewModel.newGroupSelectedKeywords)

        fun updateDialogChips() {
            cgWebsites.removeAllViews()
            tempKeywords.forEach { keyword ->
                val chip = com.google.android.material.chip.Chip(requireContext())
                chip.text = keyword
                chip.isCloseIconVisible = true
                chip.setOnCloseIconClickListener {
                    tempKeywords.remove(keyword)
                    updateDialogChips()
                }
                cgWebsites.addView(chip)
            }
        }

        updateDialogChips()

        btnAdd.setOnClickListener {
            val text = etWebsite.text.toString().trim()
            if (text.isNotEmpty()) {
                val parts = text.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                tempKeywords.addAll(parts)
                etWebsite.setText("")
                updateDialogChips()
            }
        }

        etWebsite.setOnEditorActionListener { _, _, _ ->
            btnAdd.performClick()
            true
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add Websites/Keywords")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                viewModel.newGroupSelectedKeywords = tempKeywords
                binding.selectedWebsiteCount.text = "Selected: ${tempKeywords.size}"
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupGroupSelectionDropdown() {
        autoCompleteAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            mutableListOf()
        )

        binding.groupDropdown.setAdapter(autoCompleteAdapter)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.groups.collect { suggestions ->
                    autoCompleteAdapter.clear()
                    autoCompleteAdapter.addAll(suggestions)
                    autoCompleteAdapter.notifyDataSetChanged()
                }
            }
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
        const val FRAGMENT_ID = "focus_session_screen_config"
    }
}
