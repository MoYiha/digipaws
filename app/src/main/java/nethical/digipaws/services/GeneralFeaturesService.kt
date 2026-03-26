package nethical.digipaws.services

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import nethical.digipaws.anti_stimulants.GrayScaleFilter
import java.util.Locale

class GeneralFeaturesService : BaseBlockingService() {

    companion object {
        const val INTENT_ACTION_REFRESH_ANTI_UNINSTALL = "nethical.digipaws.refresh.anti_uninstall"
    }

    private var isAntiUninstallOn = true
    private val grayScaleFilter = GrayScaleFilter()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        super.onAccessibilityEvent(event)

        if (isAntiUninstallOn) {
            if (event?.packageName == "com.android.settings") {
                traverseNodesForKeywords(rootInActiveWindow)
            }
        }

        try {
            grayScaleFilter.doGrayscaleCheck(event)
        } catch (_: Exception) {
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onServiceConnected() {
        super.onServiceConnected()
        val filter = IntentFilter().apply {
            addAction(INTENT_ACTION_REFRESH_ANTI_UNINSTALL)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(refreshReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(refreshReceiver, filter)
        }
        setupAntiUninstall()
        
        grayScaleFilter.setup(this)
        grayScaleFilter.setupReceivers()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(refreshReceiver)
        } catch (_: Exception) {}
        grayScaleFilter.unregisterReceivers()
    }

    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null) {
                when (intent.action) {
                    INTENT_ACTION_REFRESH_ANTI_UNINSTALL -> setupAntiUninstall()
                }
            }
        }
    }

    fun setupAntiUninstall() {
        val info = getSharedPreferences("anti_uninstall", Context.MODE_PRIVATE)
        isAntiUninstallOn = info.getBoolean("is_anti_uninstall_on", false)
    }

    private fun traverseNodesForKeywords(
        node: AccessibilityNodeInfo?
    ) {
        if (node == null) {
            return
        }
        if (node.className != null && node.className == "android.widget.TextView") {
            val nodeText = node.text
            if (nodeText != null) {
                val editTextContent = nodeText.toString().lowercase(Locale.getDefault())
                if (editTextContent.lowercase(Locale.getDefault()).contains("digipaws")) {
                    pressHome()
                }
            }
        }

        for (i in 0 until node.childCount) {
            val childNode = node.getChild(i)
            traverseNodesForKeywords(childNode)
        }
    }
}