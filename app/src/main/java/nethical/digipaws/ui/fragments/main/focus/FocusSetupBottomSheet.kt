package nethical.digipaws.ui.fragments.main.focus

import android.app.Activity
import android.app.Dialog
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
import kotlinx.coroutines.launch
import nethical.digipaws.R
import nethical.digipaws.data.models.FocusBlockMode
import nethical.digipaws.data.models.ManualFocusGroup
import nethical.digipaws.databinding.DialogFocusSessionConfigBinding
import nethical.digipaws.ui.activity.SelectAppsActivity

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
        binding.btnCreateGroup.setOnClickListener {
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
        }

        // code dealing with new group creation
        binding.btnSelectApps.setOnClickListener {
            val intent = Intent(requireContext(), SelectAppsActivity::class.java)
            selectAppsLauncher.launch(intent)
        }
        binding.saveGroup.setOnClickListener {
            viewModel.addGroup(ManualFocusGroup(
                groupName = binding.groupName.text.toString(),
                packages = viewModel.newGroupSelectedApps,
                blockMode = if(binding.selectedBlockAction.checkedButtonId == R.id.btn_selected) FocusBlockMode.BLOCK_SELECTED else FocusBlockMode.BLOCK_ALL_EXCEPT_SELECTED,
                exitable = binding.exitable.isChecked
            ))
            binding.createGroup.visibility = View.GONE
            binding.selectGrouo.visibility = View.VISIBLE
        }
        binding.selectedBlockAction.checkedButtonId
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
        // Collect the StateFlow safely, respecting the Fragment's lifecycle
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                viewModel.groups.collect { suggestions ->
                    // Update the adapter data
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