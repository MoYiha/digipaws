package nethical.digipaws.ui.activity

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nethical.digipaws.Constants
import nethical.digipaws.R
import nethical.digipaws.databinding.DialogFocusModeBinding
import nethical.digipaws.services.DigipawsMainService
import nethical.digipaws.ui.dialogs.StartFocusMode
import nethical.digipaws.ui.dialogs.TweakAppBlockerWarning
import nethical.digipaws.utils.NotificationTimerManager
import nethical.digipaws.utils.SavedPreferencesLoader

class ShortcutActivity : AppCompatActivity() {

    private val savedPreferencesLoader = SavedPreferencesLoader(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isFocusedModeOn = savedPreferencesLoader.getFocusModeData().isTurnedOn
        if(isFocusedModeOn){
            Toast.makeText(this,"Focus Mode is already active",Toast.LENGTH_SHORT).show()
            finish()
        }

        val isGeneralSettingsOn = isAccessibilityServiceEnabled(DigipawsMainService::class.java)
        if(!isGeneralSettingsOn){
            Toast.makeText(this,"Find 'General Features' and press enable",Toast.LENGTH_LONG).show()
            openAccessibilityServiceScreen(cls = DigipawsMainService::class.java)
            finish()
        }
        StartFocusMode(savedPreferencesLoader, onPositiveButtonPressed = {
            finish()
        }).show(
            supportFragmentManager,
            "start_focus_mode_from_shortcut"
        )
    }


    private fun openAccessibilityServiceScreen(cls: Class<*>) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            val componentName = ComponentName(this, cls)

            intent.putExtra(":settings:fragment_args_key", componentName.flattenToString())
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to general Accessibility Settings
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    private fun isAccessibilityServiceEnabled(serviceClass: Class<out AccessibilityService>): Boolean {
        val serviceName = ComponentName(this, serviceClass).flattenToString()
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val isAccessibilityEnabled = Settings.Secure.getInt(
            contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        )
        return isAccessibilityEnabled == 1 && enabledServices.contains(serviceName)
    }
}