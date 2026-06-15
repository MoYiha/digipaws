package neth.iecal.curbox.ui.fragments.main.reducers.blockertools.uiHider

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import neth.iecal.curbox.data.models.UiHiderScript
import neth.iecal.curbox.databinding.FragmentUiHiderBinding
import neth.iecal.curbox.ui.activity.FragmentActivity

class UiHiderFragment : Fragment() {

    private var _binding: FragmentUiHiderBinding? = null
    private val binding get() = _binding!!

    private val viewModel: UiHiderViewModel by activityViewModels()
    private var isUpdatingUi = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUiHiderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.switchEnableUiHider.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingUi) viewModel.setIsActive(isChecked)
        }
        binding.btnAddScript.setOnClickListener { openEditor(null) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.config.collectLatest { config ->
                isUpdatingUi = true
                if (binding.switchEnableUiHider.isChecked != config.isActive) {
                    binding.switchEnableUiHider.isChecked = config.isActive
                }
                buildScriptCards(config.scripts)
                isUpdatingUi = false
            }
        }
    }

    private fun buildScriptCards(scripts: List<UiHiderScript>) {
        val container = binding.scriptsContainer
        container.removeAllViews()
        for (script in scripts) {
            val title = script.label.ifBlank { script.packageName.substringAfterLast('.') }
            val subtitle = appNameOrPackage(script.packageName)
            container.addView(createScriptCard(title, subtitle, script.isEnabled,
                onClick = { openEditor(script.id) },
                onSwitchChange = { checked -> viewModel.setScriptEnabled(script.id, checked) }
            ))
        }
    }

    private fun openEditor(scriptId: String?) {
        val intent = Intent(requireContext(), FragmentActivity::class.java).apply {
            putExtra("fragment", UiHiderEditorFragment.FRAGMENT_ID)
            if (scriptId != null) putExtra(UiHiderEditorFragment.EXTRA_SCRIPT_ID, scriptId)
        }
        startActivity(intent)
    }

    private fun appNameOrPackage(packageName: String): String {
        val pm = requireContext().packageManager
        return try {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private fun createScriptCard(
        title: String,
        subtitle: String,
        isChecked: Boolean,
        onClick: () -> Unit,
        onSwitchChange: (Boolean) -> Unit
    ): View {
        val density = resources.displayMetrics.density
        return MaterialCardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (8 * density).toInt() }

            val surfaceColor = TypedValue()
            context.theme.resolveAttribute(
                com.google.android.material.R.attr.colorSurfaceContainerHigh, surfaceColor, true
            )
            setCardBackgroundColor(surfaceColor.data)
            radius = 16 * density
            cardElevation = 0f
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }

            val onSurface = TypedValue().also {
                context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, it, true)
            }
            val onSurfaceVariant = TypedValue().also {
                context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, it, true)
            }

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val p = (16 * density).toInt()
                setPadding(p, p, p, p)
            }

            val textCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginEnd = (16 * density).toInt() }
            }
            textCol.addView(TextView(context).apply {
                text = title
                textSize = 15f
                setTextColor(onSurface.data)
                setTypeface(null, Typeface.BOLD)
            })
            textCol.addView(TextView(context).apply {
                text = subtitle
                textSize = 13f
                setTextColor(onSurfaceVariant.data)
            })

            val toggle = MaterialSwitch(context).apply {
                this.isChecked = isChecked
                setOnCheckedChangeListener { _, state -> if (!isUpdatingUi) onSwitchChange(state) }
            }

            row.addView(textCol)
            row.addView(toggle)
            addView(row)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val FRAGMENT_ID = "ui_hider"
    }
}
