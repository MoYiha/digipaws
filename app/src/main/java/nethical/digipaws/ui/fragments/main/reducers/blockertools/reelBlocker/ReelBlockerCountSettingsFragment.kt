package nethical.digipaws.ui.fragments.main.reducers.blockertools.reelBlocker

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import nethical.digipaws.data.models.ReelCountConfig
import nethical.digipaws.databinding.FragmentReelBlockerCountSettingsBinding

class ReelBlockerCountSettingsFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentReelBlockerCountSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ReelBlockerViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReelBlockerCountSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val config = viewModel.getReelCountConfig()
        if (config.maxReelsAllowed > 0) {
            binding.etMaxReels.setText(config.maxReelsAllowed.toString())
        }
        
        binding.btnSave.setOnClickListener {
            val input = binding.etMaxReels.text.toString()
            val maxReels = input.toIntOrNull() ?: 10
            viewModel.saveReelCountConfig(ReelCountConfig(maxReelsAllowed = maxReels))
            dismiss()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        return dialog
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val FRAGMENT_ID = "ReelBlockerCountSettingsFragment"
    }
}
