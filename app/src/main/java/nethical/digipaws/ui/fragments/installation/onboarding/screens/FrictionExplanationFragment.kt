package nethical.digipaws.ui.fragments.installation.onboarding.screens

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import nethical.digipaws.databinding.FragmentFrictionExplanationBinding
import nethical.digipaws.ui.fragments.installation.onboarding.OnboardingFragment

class FrictionExplanationFragment : Fragment() {

    private var _binding: FragmentFrictionExplanationBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFrictionExplanationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnAction.setOnClickListener {
            (parentFragment as? OnboardingFragment)?.goToNextPage()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
