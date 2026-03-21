package nethical.digipaws.ui.fragments.main.reducers.blockertools.keywordBlocker

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import nethical.digipaws.databinding.FragmentKeywordBlockerBinding

class KeywordBlockerFragment : Fragment() {

    private var _binding: FragmentKeywordBlockerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: KeywordBlockerViewModel by activityViewModels()
    private var isUpdatingUi = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentKeywordBlockerBinding.inflate(inflater, container, false)
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

        binding.btnAddKeyword.setOnClickListener {
            val keyword = binding.etKeyword.text.toString()
            if (keyword.isNotBlank()) {
                viewModel.addKeyword(keyword)
                binding.etKeyword.setText("")
            }
        }

        binding.etRedirectUrl.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!isUpdatingUi) {
                    viewModel.setRedirectUrl(s.toString())
                }
            }
        })

        binding.cbSearchRecursively.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingUi) {
                viewModel.setSearchRecursively(isChecked)
            }
        }

        binding.cbBlockUnsupportedBrowsers.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingUi) {
                viewModel.setBlockAllExceptSupported(isChecked)
            }
        }

        binding.btnSelectIgnoredApps.setOnClickListener {
            // Future implementation for ignored apps
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.keywordBlockerConfig.collectLatest { config ->
                isUpdatingUi = true

                if (binding.switchEnableBlocker.isChecked != config.isActive) {
                    binding.switchEnableBlocker.isChecked = config.isActive
                }

                if (binding.etRedirectUrl.text.toString() != config.redirectUrl) {
                    binding.etRedirectUrl.setText(config.redirectUrl)
                }

                if (binding.cbSearchRecursively.isChecked != config.searchRecursively) {
                    binding.cbSearchRecursively.isChecked = config.searchRecursively
                }

                if (binding.cbBlockUnsupportedBrowsers.isChecked != config.blockAllExceptSupported) {
                    binding.cbBlockUnsupportedBrowsers.isChecked = config.blockAllExceptSupported
                }

                updateKeywordsList(config.blockedKeywords)

                isUpdatingUi = false
            }
        }
    }

    private fun updateKeywordsList(keywords: List<String>) {
        binding.cgKeywords.removeAllViews()
        for (keyword in keywords) {
            val chip = Chip(requireContext()).apply {
                text = keyword
                isCloseIconVisible = true
                setOnCloseIconClickListener {
                    viewModel.removeKeyword(keyword)
                }
            }
            binding.cgKeywords.addView(chip)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val FRAGMENT_ID = "keyword_blocker"
    }
}
