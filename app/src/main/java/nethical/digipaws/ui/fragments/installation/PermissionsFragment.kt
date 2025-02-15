package nethical.digipaws.ui.fragments.installation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import nethical.digipaws.R
import nethical.digipaws.databinding.FragmentPermissionsBinding


class PermissionsFragment : Fragment() {

    companion object {
        const val FRAGMENT_ID = "permission_fragment"
    }

    private var _binding: FragmentPermissionsBinding? = null
    private val binding get() = _binding!!  // Safe getter for binding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPermissionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.next.setOnClickListener {
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(
                    R.id.fragment_holder,
                    AccessibilityGuide()
                ) // Replace with FragmentB
                .addToBackStack(null)
                .commit()
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}