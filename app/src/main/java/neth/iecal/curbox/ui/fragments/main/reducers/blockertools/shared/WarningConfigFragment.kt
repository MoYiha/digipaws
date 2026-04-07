package neth.iecal.curbox.ui.fragments.main.reducers.blockertools.shared

import android.os.Bundle
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.google.zxing.BarcodeFormat
import java.util.UUID
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import neth.iecal.curbox.R
import neth.iecal.curbox.data.models.AppBlockerWarningScreenConfig
import neth.iecal.curbox.databinding.FragmentWarningConfigBinding

class WarningConfigFragment : Fragment() {

    private var _binding: FragmentWarningConfigBinding? = null
    private val binding get() = _binding!!

    private var selectedProceedDelay = 15
    private var initialConfig: AppBlockerWarningScreenConfig? = null
    private var currentQrMap = mutableMapOf<String, Long>()
    private var pendingQrDuration = -1L

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val duration = pendingQrDuration
            currentQrMap[result.contents] = duration
            updateQrList()
            Toast.makeText(requireContext(), "QR Code Saved Successfully!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Scan cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val configStr = arguments?.getString(ARG_CONFIG)
        initialConfig = if (configStr != null) {
            Gson().fromJson(configStr, AppBlockerWarningScreenConfig::class.java)
        } else null
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWarningConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupInitialState()
        setupListeners()
    }

    private fun setupInitialState() {
        val config = initialConfig ?: AppBlockerWarningScreenConfig()
        
        currentQrMap = config.qrKeys.toMutableMap()
        updateQrList()

        val initialRadioId = when {
            config.isQrUnlockRequirementEnabled -> R.id.qr_unlock_rb
            config.isWarningDialogHidden -> R.id.direct_back_rb
            config.isProceedDisabled -> R.id.disable_proceed_rb
            config.isDynamicIntervalSettingAllowed -> R.id.dynamic_timing_rb
            else -> R.id.predefined_time_rb
        }
        binding.warningRadioGroup.check(initialRadioId)
        updateUiVisibility(initialRadioId, animate = false)

        selectedProceedDelay = config.proceedDelayInSecs
        val initialChipId = when (selectedProceedDelay) {
            3 -> R.id.three_sec_chip
            9 -> R.id.nine_sec_chip
            30 -> R.id.thirty_sec_chip
            else -> R.id.fifteen_sec_chip
        }
        binding.proceedDelayChips.check(initialChipId)
        binding.warningMsgEdit.setText(config.message)
        binding.selectMins.setValue(config.timeInterval / 60000)
        binding.switchVibrateBrightness.isChecked = config.vibrateAndIncBrightness
        
        binding.proceedLimitSwitch.isChecked = config.proceedLimitEnabled
        binding.proceedLimitContainer.visibility = if (config.proceedLimitEnabled) View.VISIBLE else View.GONE
        binding.selectAllowedProceeds.setValue(config.allowedProceeds)
        binding.selectAllowedProceeds.minValue = 1
        binding.selectProceedsTimeWindow.setValue(config.proceedsTimeWindowMn)
        binding.selectProceedsTimeWindow.minValue = 1
    }

    private fun setupListeners() {
        binding.warningRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            updateUiVisibility(checkedId, animate = true)
        }

        binding.proceedDelayChips.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedProceedDelay = when (checkedIds.firstOrNull()) {
                R.id.three_sec_chip -> 3
                R.id.nine_sec_chip -> 9
                R.id.thirty_sec_chip -> 30
                R.id.fifteen_sec_chip -> 15
                else -> 15
            }
        }

        binding.proceedLimitSwitch.setOnCheckedChangeListener { _, isChecked ->
            binding.proceedLimitContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        
        binding.btnGenerateQr.setOnClickListener {
            showQrConfigDialog { duration ->
                val uniqueStr = UUID.randomUUID().toString()
                currentQrMap[uniqueStr] = duration
                updateQrList()
                
                try {
                    val barcodeEncoder = BarcodeEncoder()
                    val bitmap = barcodeEncoder.encodeBitmap(uniqueStr, BarcodeFormat.QR_CODE, 800, 800)
                    val imageView = ImageView(requireContext()).apply {
                        setImageBitmap(bitmap)
                        setPadding(32, 32, 32, 32)
                    }
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("QR Code Generated")
                        .setMessage("Please save or print this QR code. You will need it to unlock.")
                        .setView(imageView)
                        .setPositiveButton("Done", null)
                        .show()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Failed to generate QR image", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnScanExistingQr.setOnClickListener {
            showQrConfigDialog { duration ->
                pendingQrDuration = duration
                val options = ScanOptions()
                options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                options.setPrompt("Scan a QR Code to unlock this blocker later")
                options.setCameraId(0) // Use a specific camera of the device
                options.setBeepEnabled(false)
                options.setBarcodeImageEnabled(true)
                options.setCaptureActivity(neth.iecal.curbox.ui.activity.PortraitCaptureActivity::class.java)
                barcodeLauncher.launch(options)
            }
        }
        
        binding.saveconfigs.setOnClickListener {
            val config = AppBlockerWarningScreenConfig(
                message = binding.warningMsgEdit.text.toString(),
                timeInterval = binding.selectMins.getValue() * 60_000,
                isDynamicIntervalSettingAllowed = binding.dynamicTimingRb.isChecked,
                isProceedDisabled = binding.disableProceedRb.isChecked,
                isWarningDialogHidden = binding.directBackRb.isChecked,
                isQrUnlockRequirementEnabled = binding.qrUnlockRb.isChecked,
                qrKeys = if (binding.qrUnlockRb.isChecked) currentQrMap else mapOf(),
                proceedDelayInSecs = selectedProceedDelay,
                vibrateAndIncBrightness = binding.switchVibrateBrightness.isChecked,
                proceedLimitEnabled = binding.proceedLimitSwitch.isChecked,
                allowedProceeds = binding.selectAllowedProceeds.getValue(),
                proceedsTimeWindowMn = binding.selectProceedsTimeWindow.getValue()
            )
            val requestKey = arguments?.getString(ARG_REQUEST_KEY) ?: RESULT_KEY
            parentFragmentManager.setFragmentResult(requestKey, Bundle().apply {
                putString(RESULT_CONFIG, Gson().toJson(config))
            })
            parentFragmentManager.popBackStack()
        }
    }

    private fun updateUiVisibility(checkedId: Int, animate: Boolean) {
        if (animate) {
            TransitionManager.beginDelayedTransition(
                binding.mainContentContainer,
                AutoTransition()
            )
        }

        binding.apply {
            dynamicTiming.visibility = if (checkedId == R.id.predefined_time_rb) View.VISIBLE else View.GONE
            proceedDelay.visibility = if (checkedId == R.id.predefined_time_rb || checkedId == R.id.dynamic_timing_rb || checkedId == R.id.qr_unlock_rb) View.VISIBLE else View.GONE
            textInputLayout2.visibility = if (checkedId != R.id.direct_back_rb) View.VISIBLE else View.GONE
            qrSetupContainer.visibility = if (checkedId == R.id.qr_unlock_rb) View.VISIBLE else View.GONE
        }
    }
    
    private fun showQrConfigDialog(onConfigured: (Long) -> Unit) {
        val pickerContainer = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 16, 48, 16)
        }
        val switchDynamic = com.google.android.material.switchmaterial.SwitchMaterial(requireContext()).apply {
            text = "Use dynamic timing (User selects time during unlock)"
            isChecked = true
        }

        val pickerInnerContainer = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val timeLabel = TextView(requireContext()).apply {
            text = "Fixed unlock duration (in minutes):"
            setPadding(8, 8, 8, 8)
        }
        val picker = neth.iecal.curbox.views.HorizontalNumberPicker(requireContext()).apply {
            setValue(5)
            minValue = 1
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = android.view.Gravity.CENTER }
        }

        pickerInnerContainer.addView(timeLabel)
        pickerInnerContainer.addView(picker)

        switchDynamic.setOnCheckedChangeListener { _, isChecked ->
            pickerInnerContainer.visibility = if (isChecked) View.GONE else View.VISIBLE
        }

        pickerContainer.addView(switchDynamic)
        pickerContainer.addView(pickerInnerContainer)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("QR Code Timing")
            .setMessage("Configure the time behavior for this code before continuing.")
            .setView(pickerContainer)
            .setPositiveButton("Continue") { _, _ ->
                val duration = if (switchDynamic.isChecked) -1L else picker.getValue() * 60_000L
                onConfigured(duration)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateQrList() {
        binding.qrListContainer.removeAllViews()
        if (currentQrMap.isEmpty()) {
            binding.qrStatusTxt.visibility = View.VISIBLE
        } else {
            binding.qrStatusTxt.visibility = View.GONE
            currentQrMap.forEach { (uuid, duration) ->
                val itemView = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    setPadding(0, 16, 0, 16)
                    weightSum = 1f
                }
                
                val infoText = TextView(requireContext()).apply {
                    val durationText = if (duration == -1L) "Dynamic time" else "${duration / 60000} mins"
                    text = "QR Code - $durationText"
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setPadding(4, 4, 4, 4)
                }
                
                val removeBtn = com.google.android.material.button.MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    text = "Remove"
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    setOnClickListener {
                        currentQrMap.remove(uuid)
                        updateQrList()
                    }
                }
                
                itemView.addView(infoText)
                itemView.addView(removeBtn)
                binding.qrListContainer.addView(itemView)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val FRAGMENT_ID = "warning_config_fragment"
        const val ARG_CONFIG = "arg_config"
        const val ARG_REQUEST_KEY = "arg_request_key"
        const val RESULT_KEY = "request_key_warning_config"
        const val RESULT_CONFIG = "result_config"

        fun newInstance(config: AppBlockerWarningScreenConfig, requestKey: String = RESULT_KEY): WarningConfigFragment {
            return WarningConfigFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CONFIG, Gson().toJson(config))
                    putString(ARG_REQUEST_KEY, requestKey)
                }
            }
        }
    }
}
