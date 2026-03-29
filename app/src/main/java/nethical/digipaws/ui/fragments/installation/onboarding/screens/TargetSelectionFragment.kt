package nethical.digipaws.ui.fragments.installation.onboarding.screens

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.card.MaterialCardView
import nethical.digipaws.databinding.FragmentTargetSelectionBinding
import nethical.digipaws.R
import nethical.digipaws.ui.fragments.installation.onboarding.OnboardingFragment
import nethical.digipaws.ui.fragments.installation.onboarding.OnboardingViewModel

class TargetSelectionFragment : Fragment() {

    private var _binding: FragmentTargetSelectionBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: OnboardingViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTargetSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val strokeColorDefault = getThemeColor(com.google.android.material.R.attr.colorOutline)
        val strokeColorChecked = getThemeColor(com.google.android.material.R.attr.colorPrimary)

        val cards = listOf(
            binding.cardInstagram to "Instagram",
            binding.cardTiktok to "TikTok",
            binding.cardYoutube to "YouTube",
            binding.cardReddit to "Reddit",
            binding.cardOther to "Other"
        )

        cards.forEach { (card, value) ->
            card.setOnClickListener {
                // Clear all
                cards.forEach { (c, _) ->
                    c.isChecked = false
                    c.strokeColor = strokeColorDefault
                    c.invalidate()
                }
                
                // Check this one
                card.isChecked = true
                card.strokeColor = strokeColorChecked
                card.invalidate()
                
                viewModel.setTargetApp(value)
                binding.btnAction.isEnabled = true
            }
        }

        binding.limitChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val limit = when (checkedIds.first()) {
                R.id.chip_15m -> 15L
                R.id.chip_30m -> 30L
                R.id.chip_1h -> 60L
                R.id.chip_2h -> 120L
                else -> 30L
            }
            viewModel.setDailyLimit(limit)
        }

        binding.btnAction.setOnClickListener {
            (parentFragment as? OnboardingFragment)?.goToNextPage()
        }
    }

    private fun getThemeColor(attr: Int): Int {
        val typedValue = TypedValue()
        requireContext().theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
