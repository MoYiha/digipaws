package neth.iecal.curbox.trackers

import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import neth.iecal.curbox.data.models.MindfulMessageConfig
import neth.iecal.curbox.services.BaseBlockingService
import neth.iecal.curbox.ui.overlay.MindfulMessageOverlayManager
import neth.iecal.curbox.utils.TimeTools
import neth.iecal.curbox.utils.UsageStatsHelper

class MindfulMessageTracker {

    private lateinit var service: BaseBlockingService
    private lateinit var overlayManager: MindfulMessageOverlayManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var config = MindfulMessageConfig()
    private var currentPkg: String? = null


    fun setup(service: BaseBlockingService) {
        this.service = service
        this.overlayManager = MindfulMessageOverlayManager(service)

        scope.launch {
            service.dataStoreManager.settings.collectLatest { settings ->
                config = settings.mindfulMessageConfig
            }
        }
    }

    fun onEvent(event: AccessibilityEvent?) {
        if (!config.isActive || event == null) {
            if (overlayManager.isOverlayVisible) overlayManager.removeOverlay()
            return
        }

        val pkg = event.packageName?.toString() ?: return

        if (config.selectedApps.contains(pkg)) {
            if (pkg != currentPkg) {
                overlayManager.removeOverlay()
                currentPkg = pkg
            }
            if (android.provider.Settings.canDrawOverlays(service)) {

                overlayManager.startDisplaying(pkg,config)
            }
        } else if (overlayManager.isOverlayVisible) {
            currentPkg = null
            overlayManager.removeOverlay()
        }
    }

    fun onDestroy() {
        overlayManager.removeOverlay()
    }
}
