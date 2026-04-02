package neth.iecal.curbox.ui.fragments.main.reducers.anti_stimulants.mindful_messages

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import neth.iecal.curbox.databinding.FragmentMindfulMessagesBinding
import neth.iecal.curbox.ui.activity.SelectAppsActivity

class MindfulMessagesFragment : Fragment() {

    companion object {
        const val FRAGMENT_ID = "MINDFUL_MESSAGES"
    }

    private var _binding: FragmentMindfulMessagesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MindfulMessagesViewModel by viewModels()
    private var selectedApps = arrayListOf<String>()
    private var isUpdatingFromViewModel = false

    private val selectAppsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            val apps = result.data?.getStringArrayListExtra("SELECTED_APPS")
            if (apps != null) {
                selectedApps = apps
                updateAppsButtonText()
                viewModel.updateSelectedApps(apps.toList())
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMindfulMessagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            requireActivity().finish()
        }

        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        binding.switchIsActive.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingFromViewModel) return@setOnCheckedChangeListener
            viewModel.updateIsActive(isChecked)
        }

        binding.btnSelectApps.setOnClickListener {
            val intent = Intent(requireContext(), SelectAppsActivity::class.java)
            intent.putStringArrayListExtra("PRE_SELECTED_APPS", selectedApps)
            selectAppsLauncher.launch(intent)
        }

        binding.etMessages.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isUpdatingFromViewModel) return
                viewModel.updateMessages( s?.toString() ?: "")
            }
        })

        binding.rgPosition.setOnCheckedChangeListener { _, checkedId ->
            if (isUpdatingFromViewModel) return@setOnCheckedChangeListener
            val position = when (checkedId) {
                binding.rbTop.id -> Gravity.TOP
                binding.rbBottom.id -> Gravity.BOTTOM
                else -> Gravity.CENTER
            }
            viewModel.updatePosition(position)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.configState.collect { config ->
                    isUpdatingFromViewModel = true
                    
                    if (binding.switchIsActive.isChecked != config.isActive) {
                        binding.switchIsActive.isChecked = config.isActive
                    }

                    if (selectedApps.toList() != config.selectedApps) {
                        selectedApps = ArrayList(config.selectedApps)
                        updateAppsButtonText()
                    }

                    val messagesText = config.messages
                    if (binding.etMessages.text.toString() != messagesText) {
                        val cursor = binding.etMessages.selectionStart
                        binding.etMessages.setText(messagesText)
                        if (cursor >= 0 && cursor <= (binding.etMessages.text?.length ?: 0)) {
                            binding.etMessages.setSelection(cursor)
                        }
                    }

                    val checkedId = when (config.position) {
                        Gravity.TOP -> binding.rbTop.id
                        Gravity.BOTTOM -> binding.rbBottom.id
                        else -> binding.rbCenter.id
                    }
                    if (binding.rgPosition.checkedRadioButtonId != checkedId) {
                        binding.rgPosition.check(checkedId)
                    }

                    isUpdatingFromViewModel = false
                }
            }
        }
    }

    private fun updateAppsButtonText() {
        binding.btnSelectApps.text = "Select Apps (${selectedApps.size})"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
