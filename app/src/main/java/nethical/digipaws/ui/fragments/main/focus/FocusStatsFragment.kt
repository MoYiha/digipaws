package nethical.digipaws.ui.fragments.main.focus

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import nethical.digipaws.R
import nethical.digipaws.databinding.FragmentFocusStatsBinding

class FocusStatsFragment : Fragment() {

    private var _binding: FragmentFocusStatsBinding? = null
    private val binding get() = _binding!!

    // Shared ViewModel with FocusFragment to get stats
    private val viewModel: FocusViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFocusStatsBinding.inflate(inflater, container, false)
        
        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        return binding.root
    }



    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
