package neth.iecal.curbox.ui.fragments.main.reducers.blockertools.keywordBlocker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.gson.Gson
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import neth.iecal.curbox.R
import neth.iecal.curbox.data.models.*
import neth.iecal.curbox.databinding.FragmentCreateKeywordGroupBinding
import java.util.*

class CreateKeywordGroupFragment : Fragment() {

    companion object {
        const val FRAGMENT_ID = "create_keyword_group"
    }

    private var _binding: FragmentCreateKeywordGroupBinding? = null
    private val binding get() = _binding!!

    private val viewModel: KeywordBlockerViewModel by activityViewModels()
    private var selectedKeywords = mutableListOf<String>()
    private var isEditing = false
    private var existingGroupId: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCreateKeywordGroupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        existingGroupId = requireActivity().intent.getStringExtra("group_id") ?: arguments?.getString("group_id")
        
        if (existingGroupId != null) {
            loadExistingGroup(existingGroupId!!)
        }

        setupListeners()
    }

    private fun loadExistingGroup(groupId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.keywordBlockerConfig.collectLatest { config ->
                val group = config.keywordGroups.find { it.id == groupId }
                if (group != null && !isEditing) {
                    isEditing = true
                    binding.tvTitle.text = "Edit Keyword Group"
                    binding.etGroupName.setText(group.name)
                    selectedKeywords = group.selectedKeywords.toMutableList()
                    updateKeywordsList()
                    
                    if (group.blockingType == AppBlockingType.Usage) {
                        binding.rbUsageBased.isChecked = true
                        viewModel.currentUsageConfig = Gson().fromJson(group.setting, AppUsageConfig::class.java)
                    } else {
                        binding.rbTimeBased.isChecked = true
                        viewModel.currentTimeConfig = Gson().fromJson(group.setting, AppTimeConfig::class.java)
                    }
                    
                    if (group.unlockBehavior == KeywordUnlockBehavior.Redirection) {
                        binding.rbRedirection.isChecked = true
                        binding.tilRedirectUrl.visibility = View.VISIBLE
                        binding.etRedirectUrl.setText(group.redirectUrl)
                        binding.btnConfigureWarningScreen.visibility = View.GONE
                    } else {
                        binding.rbWarningScreen.isChecked = true
                        binding.tilRedirectUrl.visibility = View.GONE
                        binding.btnConfigureWarningScreen.visibility = View.VISIBLE
                    }
                    
                    viewModel.warningScrnConfig = group.warningScreenConfig
                    binding.btnDeleteGroup.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun setupListeners() {
        binding.btnAddKeyword.setOnClickListener {
            val kw = binding.etKeyword.text.toString().trim()
            if (kw.isNotEmpty() && !selectedKeywords.contains(kw)) {
                selectedKeywords.add(kw)
                updateKeywordsList()
                binding.etKeyword.setText("")
            }
        }

        binding.rgUnlockBehavior.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rb_redirection) {
                binding.tilRedirectUrl.visibility = View.VISIBLE
                binding.btnConfigureWarningScreen.visibility = View.GONE
            } else {
                binding.tilRedirectUrl.visibility = View.GONE
                binding.btnConfigureWarningScreen.visibility = View.VISIBLE
            }
        }

        binding.btnConfigureSettings.setOnClickListener {
            if (binding.rbUsageBased.isChecked) {
                KeywordUsageBasedSettingsFragment().show(parentFragmentManager, KeywordUsageBasedSettingsFragment.FRAGMENT_ID)
            } else {
                KeywordTimeBasedSettingsFragment().show(parentFragmentManager, KeywordTimeBasedSettingsFragment.FRAGMENT_ID)
            }
        }

        binding.btnConfigureWarningScreen.setOnClickListener {
            val configFragment = neth.iecal.curbox.ui.fragments.main.reducers.blockertools.shared.WarningConfigFragment.newInstance(
                viewModel.warningScrnConfig, 
                "result_warning_config",
                isNew = existingGroupId == null
            )
            parentFragmentManager.beginTransaction()
                .hide(this)
                .add(R.id.fragment_holder, configFragment)
                .addToBackStack(null)
                .commit()
        }

        parentFragmentManager.setFragmentResultListener("result_warning_config", viewLifecycleOwner) { _, bundle ->
            bundle.getString("result_config")?.let {
                viewModel.warningScrnConfig = Gson().fromJson(it, AppBlockerWarningScreenConfig::class.java)
            }
        }

        binding.btnDeleteGroup.setOnClickListener {
            existingGroupId?.let { viewModel.deleteGroup(it) }
            requireActivity().finish()
        }

        binding.fabSaveGroup.setOnClickListener { saveGroup() }
    }

    private fun updateKeywordsList() {
        binding.cgKeywords.removeAllViews()
        selectedKeywords.forEach { kw ->
            val chip = Chip(requireContext()).apply {
                text = kw
                isCloseIconVisible = true
                setOnCloseIconClickListener {
                    selectedKeywords.remove(kw)
                    updateKeywordsList()
                }
            }
            binding.cgKeywords.addView(chip)
        }
    }

    private fun saveGroup() {
        val name = binding.etGroupName.text.toString().trim()
        if (name.isEmpty()) {
            binding.etGroupName.error = "Enter group name"
            return
        }
        if (selectedKeywords.isEmpty()) {
            Toast.makeText(requireContext(), "Add at least one keyword", Toast.LENGTH_SHORT).show()
            return
        }

        val blockingType = if (binding.rbUsageBased.isChecked) AppBlockingType.Usage else AppBlockingType.Timed
        val unlockBehavior = if (binding.rbRedirection.isChecked) KeywordUnlockBehavior.Redirection else KeywordUnlockBehavior.WarningScreen
        val redirectUrl = binding.etRedirectUrl.text.toString()

        val group = KeywordGroup(
            id = existingGroupId ?: UUID.randomUUID().toString(),
            name = name,
            selectedKeywords = selectedKeywords.toList(),
            blockingType = blockingType,
            isActive = true,
            setting = if (blockingType == AppBlockingType.Usage) Gson().toJson(viewModel.currentUsageConfig) else Gson().toJson(viewModel.currentTimeConfig),
            unlockBehavior = unlockBehavior,
            redirectUrl = redirectUrl,
            warningScreenConfig = viewModel.warningScrnConfig
        )

        if (existingGroupId != null) viewModel.updateGroupById(group) else viewModel.addGroup(group)
        requireActivity().finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
