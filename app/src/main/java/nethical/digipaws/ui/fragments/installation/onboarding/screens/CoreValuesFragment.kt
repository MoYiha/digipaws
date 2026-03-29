package nethical.digipaws.ui.fragments.installation.onboarding.screens

import android.content.res.ColorStateList
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.card.MaterialCardView
import nethical.digipaws.databinding.FragmentCoreValuesBinding
import nethical.digipaws.ui.fragments.installation.onboarding.OnboardingFragment
import nethical.digipaws.ui.fragments.installation.onboarding.OnboardingViewModel

class CoreValuesFragment : Fragment() {

    private var _binding: FragmentCoreValuesBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: OnboardingViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCoreValuesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val strokeColorDefault = getThemeColor(com.google.android.material.R.attr.colorOutline)
        val strokeColorChecked = getThemeColor(com.google.android.material.R.attr.colorPrimary)

        val cards = listOf(
            binding.cardWork to "Deep work/studying",
            binding.cardLearn to "Learning a new skill",
            binding.cardFamily to "Friends and family",
            binding.cardSleep to "Restful sleep"
        )

        cards.forEach { (card, value) ->
            card.setOnClickListener {
                card.isChecked = !card.isChecked
                viewModel.toggleValue(value)
                
                card.strokeColor = if (card.isChecked) strokeColorChecked else strokeColorDefault
                card.invalidate()
            }
        }

        viewModel.selectedValues.observe(viewLifecycleOwner) { selected ->
            binding.btnAction.isEnabled = selected.isNotEmpty()
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
