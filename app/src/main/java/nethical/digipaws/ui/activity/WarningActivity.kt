package nethical.digipaws.ui.activity

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import nethical.digipaws.Constants
import nethical.digipaws.R
import nethical.digipaws.blockers.AppBlocker
import nethical.digipaws.data.models.AppBlockerWarningScreenConfig
import nethical.digipaws.databinding.DialogWarningOverlayBinding
import nethical.digipaws.services.AppBlockerService
import nethical.digipaws.services.ViewBlockerService
import nethical.digipaws.utils.SavedPreferencesLoader
import kotlin.random.Random

class WarningActivity : AppCompatActivity() {

    private var proceedTimer: CountDownTimer? = null
    private var dialog: AlertDialog? = null

    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layoutParams = window.attributes
        layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        window.attributes = layoutParams

        val mode = intent.getIntExtra("mode", 0)

        val warningScreenConfig = Gson().fromJson<AppBlockerWarningScreenConfig>(
            intent.getStringExtra("warning_config"),
            AppBlockerWarningScreenConfig::class.java
        )
        triggerRandomizedVibration(maxOf(3000L, (warningScreenConfig.proceedDelayInSecs / 2) * 1000L))

        val binding = DialogWarningOverlayBinding.inflate(layoutInflater)
        val isHomePressRequested = intent.getBooleanExtra("is_press_home", false)
        binding.minsPicker.setValue(3)
        binding.minsPicker.minValue = 2
        val isDialogCancelable =
            mode != Constants.WARNING_SCREEN_MODE_APP_BLOCKER || isHomePressRequested

        if (warningScreenConfig.isProceedDisabled) {
            binding.btnProceed.visibility = View.GONE
            binding.proceedSeconds.visibility = View.GONE

        } else {
            proceedTimer =
                object : CountDownTimer(warningScreenConfig.proceedDelayInSecs * 1000L, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        binding.proceedSeconds.text =
                            getString(R.string.proceed_in, millisUntilFinished / 1000)
                    }

                    override fun onFinish() {
                        binding.btnProceed.let { button ->
                            button.isEnabled = true
                            if (warningScreenConfig.isDynamicIntervalSettingAllowed) {
                                binding.minsPicker.visibility = View.VISIBLE
                            }
                            button.setText(R.string.proceed)
                        }
                        binding.proceedSeconds.visibility = View.GONE
                    }
                }.start()
        }

        dialog = MaterialAlertDialogBuilder(this)
            .setView(binding.root)
            .setCancelable(isDialogCancelable)
            .setOnCancelListener {
                finishAffinity()
            }
            .show()

        binding.warningMsg.text = warningScreenConfig.message
        binding.minsPicker.setValue(warningScreenConfig.timeInterval / 60000)

        binding.btnCancel.setOnClickListener {
            if (mode == Constants.WARNING_SCREEN_MODE_APP_BLOCKER || isHomePressRequested) {
                val intent = Intent(Intent.ACTION_MAIN)
                intent.addCategory(Intent.CATEGORY_HOME)
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
            dialog?.dismiss()
            finishAffinity()
        }

        binding.btnProceed.setOnClickListener {
            if (mode == Constants.WARNING_SCREEN_MODE_VIEW_BLOCKER) {
                intent.getStringExtra("result_id")
                    ?.let { it1 ->
                        sendRefreshRequest(
                            it1,
                            ViewBlockerService.INTENT_ACTION_REFRESH_VIEW_BLOCKER_COOLDOWN,
                            binding.minsPicker.getValue()
                        )
                    }
            }

            if (mode == Constants.WARNING_SCREEN_MODE_APP_BLOCKER) {
                intent.getStringExtra("result_id")
                    ?.let { it1 ->
                        sendRefreshRequest(
                            it1,
                            AppBlocker.INTENT_ACTION_REFRESH_APP_BLOCKER_COOLDOWN,
                            binding.minsPicker.getValue()
                        )
                        val intent = packageManager.getLaunchIntentForPackage(it1)
                        if (intent != null) {
                            startActivity(intent)
                        }
                    }
            }

            dialog?.dismiss()
            finishAffinity()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        proceedTimer?.cancel()
        vibrator?.cancel()
        dialog?.dismiss()
    }

    private fun sendRefreshRequest(id: String, action: String, time: Int) {
        val intent = Intent(action)
        intent.putExtra("result_id", id)
        intent.putExtra("selected_time", time * 60_000)
        sendBroadcast(intent)
    }

    /**
     * Triggers a jagged, unpredictable vibration waveform.
     * The lack of a steady rhythm prevents habituation and breaks focus.
     */
    private fun triggerRandomizedVibration(durationMillis: Long) {
        // Initialize the class-level vibrator variable
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        vibrator?.let { currentVibrator ->
            if (currentVibrator.hasVibrator()) {
                val patternList = mutableListOf<Long>()
                patternList.add(0L) // Start immediately (0ms initial delay)

                var elapsedTime = 0L

                while (elapsedTime < durationMillis) {
                    val vibrateDuration = Random.nextLong(40, 250)
                    val pauseDuration = Random.nextLong(40, 150)

                    if (elapsedTime + vibrateDuration >= durationMillis) {
                        patternList.add(durationMillis - elapsedTime) // Cap exactly at duration
                        break
                    }
                    patternList.add(vibrateDuration)
                    elapsedTime += vibrateDuration

                    if (elapsedTime + pauseDuration >= durationMillis) {
                        patternList.add(durationMillis - elapsedTime) // Cap exactly at duration
                        break
                    }
                    patternList.add(pauseDuration)
                    elapsedTime += pauseDuration
                }

                val jaggedPattern = patternList.toLongArray()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    currentVibrator.vibrate(VibrationEffect.createWaveform(jaggedPattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    currentVibrator.vibrate(jaggedPattern, -1)
                }
            }
        }
    }
}