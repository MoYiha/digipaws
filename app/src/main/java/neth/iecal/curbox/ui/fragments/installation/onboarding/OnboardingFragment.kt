package neth.iecal.curbox.ui.fragments.installation.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.viewpager2.adapter.FragmentStateAdapter
import neth.iecal.curbox.databinding.FragmentOnboardingBinding
import neth.iecal.curbox.ui.fragments.installation.onboarding.screens.EmpathyFragment
import neth.iecal.curbox.ui.fragments.installation.onboarding.screens.ScreenTimeEstimateFragment
import neth.iecal.curbox.ui.fragments.installation.onboarding.screens.CoreValuesFragment
import neth.iecal.curbox.ui.fragments.installation.onboarding.screens.TargetSelectionFragment
import neth.iecal.curbox.ui.fragments.installation.onboarding.screens.OnboardingPermissionsFragment

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

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }
        val pagerAdapter = OnboardingPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.isUserInputEnabled = false 
        binding.viewPager.setPageTransformer(InteractivePageTransformer())

        binding.viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                // Smooth background fade between steps
                val totalProgress = position + positionOffset
                val alpha = (totalProgress % 1.0f) * 0.2f
                binding.bgOverlay.alpha = alpha
            }
        })
    }

    private class InteractivePageTransformer : androidx.viewpager2.widget.ViewPager2.PageTransformer {
        override fun transformPage(page: View, position: Float) {
            val absPos = Math.abs(position)
            page.apply {
                alpha = 1f - absPos
                translationX = -position * width / 1.5f
                val scale = 0.85f + (1f - absPos) * 0.15f
                scaleX = scale
                scaleY = scale

                if (this is ViewGroup) {
                    val content = getChildAt(0) as? ViewGroup
                    if (content != null) {
                        for (i in 0 until content.childCount) {
                            val child = content.getChildAt(i)
                            child.translationX = position * (i + 1) * 200f
                            child.alpha = 1f - absPos * 2f // Faster fade for children
                        }
                    }
                }
            }
        }
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
        override fun getItemCount(): Int = 5

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> EmpathyFragment()
                1 -> ScreenTimeEstimateFragment()
                2 -> CoreValuesFragment()
                3 -> TargetSelectionFragment()
                4 -> OnboardingPermissionsFragment()
                else -> throw IllegalArgumentException("Invalid position $position")
            }
        }
    }
}
