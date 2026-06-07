package neth.iecal.curbox.blockers

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.RECEIVER_EXPORTED
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.core.content.edit
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import neth.iecal.curbox.Constants
import neth.iecal.curbox.R
import neth.iecal.curbox.data.db.AppDatabase
import neth.iecal.curbox.data.models.AppBlockingType
import neth.iecal.curbox.data.models.AppTimeConfig
import neth.iecal.curbox.data.models.AppUsageConfig
import neth.iecal.curbox.data.models.KeywordGroup
import neth.iecal.curbox.data.models.KeywordUnlockBehavior
import neth.iecal.curbox.services.BaseBlockingService
import neth.iecal.curbox.ui.activity.WarningActivity
import neth.iecal.curbox.utils.TimeTools
import java.util.Calendar
import java.util.Locale

class KeywordBlocker : BaseBlocker() {
    companion object {
        val URL_BAR_ID_LIST = mapOf(
            "com.android.chrome" to BrowserUrlBarInfo(
                displayUrlBarId = "url_bar",
                browserSugggestionBoxId = "omnibox_suggestions_dropdown"
            ),
            "app.vanadium.browser" to BrowserUrlBarInfo(
                displayUrlBarId = "url_bar",
                browserSugggestionBoxId = "omnibox_suggestions_dropdown"
            ),
            "com.brave.browser" to BrowserUrlBarInfo(
                displayUrlBarId = "url_bar",
                browserSugggestionBoxId = "omnibox_suggestions_dropdown"
            ),
            "org.mozilla.firefox" to BrowserUrlBarInfo(
                displayUrlBarId = "mozac_browser_toolbar_url_view",
                browserSugggestionBoxId = "sfcnt",
            ),
            "com.opera.browser" to BrowserUrlBarInfo(
                displayUrlBarId = "url_field",
                browserSugggestionBoxId = "right_state_button",
                isSuggestionEqualToGo = true
            ),
        )

        const val INTENT_ACTION_REFRESH_CONFIG = "neth.iecal.curbox.refresh.keywordblocker.config"
        const val INTENT_ACTION_REFRESH_KEYWORD_BLOCKER_COOLDOWN = "neth.iecal.curbox.refresh.keywordblocker.cooldown"
        private const val TARGET_EVENTS_MASK = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
    }

    private lateinit var service: BaseBlockingService
    private lateinit var browserBlocker: BrowserBlocker
    private lateinit var prefs: SharedPreferences

    private var activeGroups = listOf<KeywordGroup>()
    private var groupRegexMap = mutableMapOf<String, List<Regex>>()
    
    private val detectionCache = LruCache<String, KeywordGroup>(200)
    private var isTurnedOn = false
    private var isUnsupportedBrowserBlockingOn = false

    private var lastEventTimeStamp = 0L
    private var refreshCooldown: Int = 2000

    private var cooldownGroupsList = HashMap<String, Long>()

    private val wordSplitRegex = Regex("[^a-zA-Z0-9]+")

    private fun findMatchingGroup(text: String): KeywordGroup? {
        val cachedGroup = detectionCache.get(text)
        if (cachedGroup != null) return if (cachedGroup.id == "SAFE") null else cachedGroup

        val lowerText = text.lowercase(Locale.ROOT)

        for (group in activeGroups) {
            // Check wildcards
            val regexes = groupRegexMap[group.id] ?: emptyList()
            if (regexes.any { it.containsMatchIn(lowerText) }) {
                detectionCache.put(text, group)
                Log.d("found",text)

                return group
            }

            // Check literals
            val literals = group.selectedKeywords.filter { !it.contains("*") }
                .map { it.lowercase(Locale.ROOT) }.toSet()
            if (literals.isNotEmpty()) {
                val keywords = parseTextForKeywords(text)
                if (keywords.any { literals.contains(it) }) {
                    detectionCache.put(text, group)
                    Log.d("found",text)
                    return group
                }
            }
        }

        detectionCache.put(text, KeywordGroup(id = "SAFE"))
        return null
    }

    private fun parseTextForKeywords(input: String): Set<String> {
        fun extractWords(text: String): Set<String> =
            text.split(wordSplitRegex)
                .filter { it.isNotEmpty() }
                .map { it.lowercase(Locale.ROOT) }
                .toSet()

        val words = mutableSetOf<String>()
        try {
            val uri = java.net.URI(input)
            if (uri.host != null) {
                val host = uri.host.lowercase(Locale.ROOT)
                words.add(host)
                val parts = host.split(".")
                if (parts.size >= 2) words.add(parts[parts.size - 2])
                uri.path?.let { words.addAll(extractWords(it)) }
                uri.query?.split("&")?.forEach { param ->
                    val (key, value) = param.split("=", limit = 2).let {
                        it[0] to it.getOrNull(1)
                    }
                    words.addAll(extractWords(key))
                    value?.let { words.addAll(extractWords(it)) }
                }
                return words
            }
        } catch (_: Exception) {}

        words.add(input.lowercase(Locale.ROOT))
        words.addAll(extractWords(input))
        return words
    }

    fun checkIfUserGettingFreaky(event: AccessibilityEvent?) {
        fun showMessage(word: String) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(service, service.getString(R.string.blocked_keyword_word_was_found).replace("-word", word), Toast.LENGTH_LONG).show()
            }
        }

        fun pressHome(word: String) {
            showMessage(word)
            service.pressHome()
        }


        if (event == null || (event.eventType and TARGET_EVENTS_MASK) == 0) return

        if (!service.isDelayOver(lastEventTimeStamp, refreshCooldown) || 
            event.packageName == "neth.iecal.curbox") return

        lastEventTimeStamp = android.os.SystemClock.uptimeMillis()

        if (isUnsupportedBrowserBlockingOn && browserBlocker.isAppBrowser(event)) {
            return pressHome("/ unsupported browser")
        }

        val rootNode = service.rootInActiveWindow ?: return
        val urlBarInfo = URL_BAR_ID_LIST[event.packageName] ?: return
        val idPrefixPart = event.packageName.toString() + ":id/"
        
        val displayUrlTextNode = ReelBlocker.findElementById(rootNode, idPrefixPart + urlBarInfo.displayUrlBarId)
        
        val webViewKeywordGroup = searchKeywordsInWebViewTitle(rootNode)
        val displayText = displayUrlTextNode?.text?.toString() ?: ""

        val matchedGroup = webViewKeywordGroup ?: (if (displayText.isNotEmpty())
            findMatchingGroup(displayText)
        else null) ?: return

        // Check cooldown
        if (cooldownGroupsList.containsKey(matchedGroup.id)) {
            if (cooldownGroupsList[matchedGroup.id]!! > System.currentTimeMillis()) {
                return // In cooldown
            } else {
                removeCooldownFrom(matchedGroup.id)
            }
        }
        Log.d("no cooldown","")

        // Check if currently blocked
        if (isBlocked(matchedGroup, event.packageName.toString(), displayText)) {
            if (matchedGroup.unlockBehavior == KeywordUnlockBehavior.Redirection) {
                Log.d("no cooldown","redirecting")
                val redirectUrl = matchedGroup.redirectUrl
                performSmallUpwardScroll()
                Thread.sleep(200)
                displayUrlTextNode?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Thread.sleep(200)

                val editUrlBarId = urlBarInfo.editUrlBarId ?: urlBarInfo.displayUrlBarId
                val editUrlBar = ReelBlocker.findElementById(rootNode, idPrefixPart + editUrlBarId)
                    ?: return pressHome(matchedGroup.id)

                editUrlBar.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, redirectUrl
                    )
                })
                Thread.sleep(300)

                val goBtnNode =
                    ReelBlocker.findElementById(rootNode, idPrefixPart + urlBarInfo.browserSugggestionBoxId)
                        ?: return pressHome(matchedGroup.id)

                if (urlBarInfo.isSuggestionEqualToGo) {
                    goBtnNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                } else {
                    goBtnNode.getChild(urlBarInfo.suggestionBoxIndexOfGoBtn)
                        ?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }

                Thread.sleep(2000)
            } else {
                Log.d("no cooldown","warning")
                service.pressHome()
                Handler(Looper.getMainLooper()).postDelayed({
                    val intent = Intent(service, WarningActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        putExtra("mode", Constants.WARNING_SCREEN_MODE_KEYWORD_BLOCKER)
                        putExtra("result_id", matchedGroup.id)
                        putExtra("warning_config", Gson().toJson(matchedGroup.warningScreenConfig))
                    }
                    service.startActivity(intent)
                }, 300)
            }
        }
    }

    private fun isBlocked(group: KeywordGroup, packageName: String, url: String): Boolean {
        if (group.blockingType == AppBlockingType.Timed) {
            return isTimedBlockActive(group)
        } else {
            return isUsageLimitExceeded(group, packageName, url)
        }
    }

    private fun isTimedBlockActive(group: KeywordGroup): Boolean {
        val config = Gson().fromJson(group.setting, AppTimeConfig::class.java) ?: return false
        val calendar = Calendar.getInstance()
        val currentMinutes = TimeTools.convertToMinutesFromMidnight(
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE)
        )
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1
        val intervals = if (config.isEveryday) config.everydayIntervals else config.dailyIntervals[dayOfWeek] ?: emptyList()

        for (interval in intervals) {
            val start = TimeTools.convertToMinutesFromMidnight(interval.startHour, interval.startMinute)
            val end = TimeTools.convertToMinutesFromMidnight(interval.endHour, interval.endMinute)
            if (start <= end) {
                if (currentMinutes in start until end) return true
            } else {
                if (currentMinutes >= start || currentMinutes < end) return true
            }
        }
        return false
    }

    private fun isUsageLimitExceeded(group: KeywordGroup, packageName: String, url: String): Boolean {
        val config = Gson().fromJson(group.setting, AppUsageConfig::class.java) ?: return false
        val limit = (if (config.isDailyUniform) config.uniformLimit else {
            val day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1
            config.dailyLimits[day]
        }) * 60_000L

        if (limit <= 0) return false

        val domain = extractDomain(url)
        if (domain.isEmpty()) return false

        val date = TimeTools.getCurrentDate()
        val totalUsage = runBlocking(Dispatchers.IO) {
            AppDatabase.getInstance(service).websiteStatsDao().getStat(date, packageName, domain)?.totalTime ?: 0L
        }

        return totalUsage >= limit
    }

    private fun extractDomain(urlText: String): String {
        return try {
            var url = urlText
            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
            val host = java.net.URI(url).host?.lowercase() ?: urlText
            if (host.startsWith("www.")) host.substring(4) else host
        } catch (_: Exception) { urlText }
    }

    private fun searchKeywordsInWebViewTitle(rootNode: AccessibilityNodeInfo): KeywordGroup? {
        val webView = findWebView(rootNode) ?: return null
        val titleText = webView.text?.toString() ?: ""
        if (titleText.isEmpty()) return null

        return findMatchingGroup(titleText)
    }

    private fun findWebView(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        node ?: return null
        if (node.className == "android.webkit.WebView") return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val result = findWebView(child)
            if (result != null) return result
        }
        return null
    }

    fun performSmallUpwardScroll() {
        val path = Path()
        val screenHeight = Resources.getSystem().displayMetrics.heightPixels
        val startY = (screenHeight * 0.75).toFloat()
        val endY = startY - (screenHeight * 0.1).toFloat()
        val centerX = Resources.getSystem().displayMetrics.widthPixels / 2f

        path.moveTo(centerX, startY)
        path.lineTo(centerX, endY)

        val gestureBuilder = GestureDescription.Builder()
        val gestureStroke = GestureDescription.StrokeDescription(path, 0, 200)

        val gesture = gestureBuilder.addStroke(gestureStroke).build()

        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                service.performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }, null)
    }

    private var configJob: kotlinx.coroutines.Job? = null

    fun setupBlocker(service: BaseBlockingService) {
        this.service = service
        this.browserBlocker = BrowserBlocker(service)
        this.prefs = service.getSharedPreferences("keyword_blocker_prefs", Context.MODE_PRIVATE)
        loadPersistedData()

        configJob?.cancel()
        configJob = CoroutineScope(Dispatchers.IO).launch {
            service.dataStoreManager.settings.collectLatest { settings ->
                isTurnedOn = settings.keywordBlockerConfig.isActive
                isUnsupportedBrowserBlockingOn = settings.keywordBlockerConfig.blockAllExceptSupported
                browserBlocker.isTurnedOn = isTurnedOn
                
                activeGroups = if (isTurnedOn) {
                    settings.keywordBlockerConfig.keywordGroups.filter { it.isActive }
                } else {
                    emptyList()
                }
                
                val newRegexMap = mutableMapOf<String, List<Regex>>()
                activeGroups.forEach { group ->
                    val wildcards = group.selectedKeywords.filter { it.contains("*") }
                    newRegexMap[group.id] = wildcards.map { pattern ->
                        val lowerPattern = pattern.lowercase(Locale.ROOT)
                        val looksLikeDomain = !lowerPattern.startsWith("http") && !lowerPattern.startsWith("*") && !lowerPattern.startsWith("/")
                        val prefix = if (looksLikeDomain) "(?:https?://)?(?:www\\.)?" else ""
                        val escaped = lowerPattern.replace(Regex("[.\\+^${'$'}()|\\[\\]\\\\{}]"), "\\\\$0").replace("*", ".*")
                        Regex(prefix + escaped)
                    }
                }
                groupRegexMap = newRegexMap
                detectionCache.evictAll()
            }
        }
    }

    private fun loadPersistedData() {
        val keys = prefs.getStringSet("cooldown_keys", setOf()) ?: setOf()
        keys.forEach { id ->
            val end = prefs.getLong("cooldown_$id", 0L)
            if (end > System.currentTimeMillis()) cooldownGroupsList[id] = end
        }
    }

    private fun persistCooldownData() {
        prefs.edit {
            putStringSet("cooldown_keys", cooldownGroupsList.keys)
            cooldownGroupsList.forEach { (id, end) -> putLong("cooldown_$id", end) }
        }
    }

    private fun removeCooldownFrom(id: String) {
        cooldownGroupsList.remove(id)
        prefs.edit {
            remove("cooldown_$id")
            putStringSet("cooldown_keys", cooldownGroupsList.keys)
        }
    }

    private fun handleCooldownIntent(intent: Intent) {
        val groupId = intent.getStringExtra("result_id") ?: return
        val duration = intent.getIntExtra("selected_time", 120000)
        val end = System.currentTimeMillis() + duration
        cooldownGroupsList[groupId] = end
        persistCooldownData()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun setupReceivers() {
        val filter = IntentFilter().apply {
            addAction(INTENT_ACTION_REFRESH_CONFIG)
            addAction(INTENT_ACTION_REFRESH_KEYWORD_BLOCKER_COOLDOWN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            service.registerReceiver(refreshReceiver, filter, RECEIVER_EXPORTED)
        } else {
            service.registerReceiver(refreshReceiver, filter)
        }
    }

    fun removeReceivers() = service.unregisterReceiver(refreshReceiver)

    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                INTENT_ACTION_REFRESH_CONFIG -> setupBlocker(service)
                INTENT_ACTION_REFRESH_KEYWORD_BLOCKER_COOLDOWN -> handleCooldownIntent(intent)
            }
        }
    }

    data class BrowserUrlBarInfo(
        val displayUrlBarId: String,
        val editUrlBarId: String? = null,
        val browserSugggestionBoxId: String,
        val suggestionBoxIndexOfGoBtn: Int = 0,
        val isSuggestionEqualToGo: Boolean = false
    )
}
