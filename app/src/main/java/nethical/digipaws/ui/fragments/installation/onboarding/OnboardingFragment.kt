package nethical.digipaws.ui.fragments.installation.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.viewpager2.adapter.FragmentStateAdapter
import nethical.digipaws.databinding.FragmentOnboardingBinding
import nethical.digipaws.ui.fragments.installation.onboarding.screens.EmpathyFragment
import nethical.digipaws.ui.fragments.installation.onboarding.screens.ScreenTimeEstimateFragment
import nethical.digipaws.ui.fragments.installation.onboarding.screens.CoreValuesFragment
import nethical.digipaws.ui.fragments.installation.onboarding.screens.FrictionExplanationFragment
import nethical.digipaws.ui.fragments.installation.onboarding.screens.TargetSelectionFragment
import nethical.digipaws.ui.fragments.installation.onboarding.screens.OnboardingPermissionsFragment

class OnboardingFragment : Fragment() {

    companion object {
        const val FRAGMENT_ID = "onboarding_fragment"
    }

    private var _binding: FragmentOnboardingBinding? = null
    private val binding get() = _binding!!
    
    // Use activityViewModels so children fragments can share this ViewModel
    private val viewModel: OnboardingViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val pagerAdapter = OnboardingPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.isUserInputEnabled = false // disable swipe, MUST click buttons
    }

    fun goToNextPage() {
        val currentItem = binding.viewPager.currentItem
        if (currentItem < (binding.viewPager.adapter?.itemCount ?: 0) - 1) {
            binding.viewPager.currentItem = currentItem + 1
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class OnboardingPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun getItemCount(): Int = 6

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> EmpathyFragment()
                1 -> ScreenTimeEstimateFragment()
                2 -> CoreValuesFragment()
                3 -> FrictionExplanationFragment()
                4 -> TargetSelectionFragment()
                5 -> OnboardingPermissionsFragment()
                else -> throw IllegalArgumentException("Invalid position $position")
            }
        }
    }
}
