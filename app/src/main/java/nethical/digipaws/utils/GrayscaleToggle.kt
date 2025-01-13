package nethical.digipaws.utils
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider
import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.UserHandle

class GrayscaleToggle(private val context: Context) {
    private var isGrayscaleEnabled = false

    fun toggleGrayscale() {
        isGrayscaleEnabled = !isGrayscaleEnabled

        try {
            // Get settings service
            val settings = Shizuku.getSystemService("settings")

            // Use Shizuku to modify secure settings
            Shizuku.checkSelfPermission(context, "android.permission.WRITE_SECURE_SETTINGS")

            if (isGrayscaleEnabled) {
                // Enable grayscale
                Shizuku.execCommand(arrayOf(
                    "settings", "put", "secure",
                    "accessibility_display_daltonizer", "1"
                ))
                Shizuku.execCommand(arrayOf(
                    "settings", "put", "secure",
                    "accessibility_display_daltonizer_enabled", "1"
                ))
            } else {
                // Disable grayscale
                Shizuku.execCommand(arrayOf(
                    "settings", "put", "secure",
                    "accessibility_display_daltonizer", "0"
                ))
                Shizuku.execCommand(arrayOf(
                    "settings", "put", "secure",
                    "accessibility_display_daltonizer_enabled", "0"
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.checkSelfPermission(context, "android.permission.WRITE_SECURE_SETTINGS") == 0
        } catch (e: Exception) {
            false
        }
    }
}
