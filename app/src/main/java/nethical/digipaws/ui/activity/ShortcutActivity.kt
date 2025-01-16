package nethical.digipaws.ui.activity

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
        StartFocusMode(savedPreferencesLoader, onPositiveButtonPressed = {
            finish()
        }).show(
            supportFragmentManager,
            "start_focus_mode_from_shortcut"
        )
    }


}