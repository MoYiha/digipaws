package neth.iecal.curbox.trackers

import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import neth.iecal.curbox.blockers.KeywordBlocker
import neth.iecal.curbox.data.db.AppDatabase
import neth.iecal.curbox.data.db.WebsiteStatsDao
import neth.iecal.curbox.data.db.WebsiteStatsEntity
import neth.iecal.curbox.services.BaseBlockingService
import neth.iecal.curbox.utils.TimeTools
import kotlin.math.log

class WebsiteUsageTracker {

    private lateinit var service: BaseBlockingService
    private lateinit var websiteStatsDao: WebsiteStatsDao
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var currentPackage: String? = null
    private var currentDomain: String? = null
    private var domainStartTimeMs: Long = 0L

    fun setup(service: BaseBlockingService) {
        this.service = service
        val db = AppDatabase.getInstance(service)
        this.websiteStatsDao = db.websiteStatsDao()
    }

    fun onEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        val packageName = event.packageName?.toString() ?: return
        
        if (!KeywordBlocker.URL_BAR_ID_LIST.containsKey(packageName)) {
            // Not a supported browser package
            if (currentPackage != null) {
                saveSession()
                currentPackage = null
                currentDomain = null
            }
            return
        }

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) {
            return
        }

        val rootNode = service.rootInActiveWindow ?: return
        val urlBarInfo = KeywordBlocker.URL_BAR_ID_LIST[packageName] ?: return

        try {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(event.packageName.toString() + ":id/"+ urlBarInfo.displayUrlBarId)
            val displayUrlTextNode = nodes?.firstOrNull()
            val text = displayUrlTextNode?.text?.toString()

            Log.d("website", text.toString())
            if (!text.isNullOrEmpty()) {
                val domain = extractDomain(text)
                if (domain.isNotEmpty()) {
                    if (domain != currentDomain || packageName != currentPackage) {
                        saveSession()
                        currentDomain = domain
                        currentPackage = packageName
                        domainStartTimeMs = SystemClock.uptimeMillis()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("WebsiteUsageTracker", "Failed to find node", e)
        }
    }

    private fun extractDomain(urlText: String): String {
        return try {
            var url = urlText
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }
            val uri = java.net.URI(url)
            val host = uri.host?.lowercase() ?: urlText
            if (host.startsWith("www.")) {
                host.substring(4)
            } else {
                host
            }
        } catch (e: Exception) {
            urlText
        }
    }

    private fun saveSession() {
        val domain = currentDomain
        val packageName = currentPackage
        val startTime = domainStartTimeMs

        if (domain != null && packageName != null && startTime > 0) {
            val durationMs = SystemClock.uptimeMillis() - startTime
            if (durationMs > 1000) {
                Log.d("saved session", durationMs.toString())
                val date = TimeTools.getCurrentDate()
                scope.launch {
                    try {
                        val stat = websiteStatsDao.getStat(date, packageName, domain)
                        val totalTime = (stat?.totalTime ?: 0L) + durationMs
                        websiteStatsDao.upsert(
                            WebsiteStatsEntity(
                                date = date,
                                packageName = packageName,
                                domain = domain,
                                totalTime = totalTime
                            )
                        )
                    } catch (e: Exception) {
                        Log.e("WebsiteUsageTracker", "Failed to save website trace", e)
                    }
                }
            }
        }
    }

    fun onDestroy() {
        saveSession()
    }
}
