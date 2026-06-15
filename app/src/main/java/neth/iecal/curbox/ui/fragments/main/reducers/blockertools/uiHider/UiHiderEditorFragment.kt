package neth.iecal.curbox.ui.fragments.main.reducers.blockertools.uiHider

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import neth.iecal.curbox.data.models.UiHiderScript
import neth.iecal.curbox.databinding.FragmentUiHiderEditorBinding

class UiHiderEditorFragment : Fragment() {

    private var _binding: FragmentUiHiderEditorBinding? = null
    private val binding get() = _binding!!

    private val viewModel: UiHiderViewModel by activityViewModels()

    private var scriptId: String? = null
    private var existing: UiHiderScript? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUiHiderEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        scriptId = arguments?.getString(EXTRA_SCRIPT_ID)
        existing = scriptId?.let { viewModel.scriptById(it) }

        existing?.let { script ->
            binding.editPackage.setText(script.packageName)
            binding.editLabel.setText(script.label)
            binding.editSource.setText(script.source)
        }
        binding.btnDelete.visibility = if (existing != null) View.VISIBLE else View.GONE

        binding.btnSave.setOnClickListener { save() }
        binding.btnDelete.setOnClickListener {
            scriptId?.let { viewModel.deleteScript(it) }
            requireActivity().finish()
        }
    }

    private fun save() {
        val packageName = binding.editPackage.text?.toString()?.trim().orEmpty()
        val source = binding.editSource.text?.toString().orEmpty()
        val label = binding.editLabel.text?.toString()?.trim().orEmpty()

        if (packageName.isEmpty()) {
            Toast.makeText(requireContext(), "Enter a package name", Toast.LENGTH_SHORT).show()
            return
        }

        val error = viewModel.validate(source)
        if (error != null) {
            binding.textOutput.text = error
            binding.textOutput.visibility = View.VISIBLE
            return
        }

        val script = UiHiderScript(
            id = scriptId ?: viewModel.newScriptId(),
            packageName = packageName,
            label = label.ifEmpty { packageName.substringAfterLast('.') },
            source = source,
            isEnabled = existing?.isEnabled ?: true
        )
        viewModel.upsertScript(script)
        requireActivity().finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val FRAGMENT_ID = "ui_hider_editor"
        const val EXTRA_SCRIPT_ID = "scriptId"
    }
}
