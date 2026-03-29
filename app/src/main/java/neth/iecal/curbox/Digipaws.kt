package neth.iecal.curbox

import android.app.Application
import com.google.android.material.color.DynamicColors

class Curbox: Application() {
  override fun onCreate() {
    DynamicColors.applyToActivitiesIfAvailable(this)
    Thread.setDefaultUncaughtExceptionHandler(CrashLogger(this))
    super.onCreate()
  }
}
