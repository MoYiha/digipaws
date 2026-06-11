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
import androidx.room.InvalidationTracker
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import neth.iecal.curbox.Constants
import neth.iecal.curbox.R
import neth.iecal.curbox.data.db.AppDatabase
import neth.iecal.curbox.data.db.WebsiteStatsEntity
import neth.iecal.curbox.data.models.AppBlockingType
import neth.iecal.curbox.data.models.AppTimeConfig
import neth.iecal.curbox.data.models.AppUsageConfig
import neth.iecal.curbox.data.models.FocusBlockMode
import neth.iecal.curbox.data.models.KeywordGroup
import neth.iecal.curbox.data.models.KeywordUnlockBehavior
import neth.iecal.curbox.services.BaseBlockingService
import neth.iecal.curbox.trackers.WebsiteUsageTracker.Companion.URL_BAR_ID_LIST
import neth.iecal.curbox.trackers.WebsiteUsageTracker.Companion.findElementById
import neth.iecal.curbox.ui.activity.WarningActivity
import neth.iecal.curbox.utils.TimeTools
import java.util.Calendar
import java.util.Locale

class KeywordBlocker : BaseBlocker() {
    companion object {
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

    private var lastpkg = ""

    private var cooldownGroupsList = HashMap<String, Long>()

    private val wordSplitRegex = Regex("[^a-zA-Z0-9]+")

    private var observationJob: Job? = null

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

    fun parseTextForKeywords(input: String): Set<String> {
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

    fun checkIfUnsupportedBrowser(event: AccessibilityEvent?) {
        fun showMessage() {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(service, "Unsupported Browser", Toast.LENGTH_LONG).show()
            }
        }

        fun pressHome() {
            showMessage()
            service.pressHome()
        }


        if (event == null || lastpkg == event.packageName || (event.eventType and TARGET_EVENTS_MASK) == 0) return
        lastpkg = event.packageName.toString()
        if (isUnsupportedBrowserBlockingOn && browserBlocker.isAppBrowser(event)) {
            return pressHome()
        }

    }

    private fun startObservingDatabase() {
        observationJob?.cancel()
        observationJob = CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getInstance(service)
            val dao = db.websiteStatsDao()

            callbackFlow {
                val observer = object : InvalidationTracker.Observer("website_stats") {
                    override fun onInvalidated(tables: Set<String>) {
                        trySend(Unit)
                    }
                }
                db.invalidationTracker.addObserver(observer)
                awaitClose { db.invalidationTracker.removeObserver(observer) }
            }.collect {
                val date = TimeTools.getCurrentDate()
                val stats = dao.getStatsForDate(date)
                val latest = stats.maxByOrNull { it.lastVisited }

                if (latest != null && latest.lastVisited > (System.currentTimeMillis() - 2500)) {
                    evaluateAndBlock(latest)
                }
            }
        }
    }

    private fun evaluateAndBlock(entry: WebsiteStatsEntity) {
        Log.d("website eval", entry.toString())
        val matchedGroup = findMatchingGroup(entry.urlIdentifier) ?: return


        // Check cooldown
        if (cooldownGroupsList.containsKey(matchedGroup.id)) {
            if (cooldownGroupsList[matchedGroup.id]!! > System.currentTimeMillis()) {
                return // In cooldown
            } else {
                removeCooldownFrom(matchedGroup.id)
            }
        }

        if (isBlocked(matchedGroup, entry.packageName, entry.urlIdentifier)) {
            handleBlocking(matchedGroup, entry.packageName, entry.urlIdentifier)
        } else {
            calculateAndSetNextRecheck(matchedGroup, entry.packageName, entry.urlIdentifier)
        }
    }

    private fun calculateAndSetNextRecheck(group: KeywordGroup, packageName: String, url: String) {
        val now = System.currentTimeMillis()
        var nextRecheck = 0L

        // 1. Check Usage Limit
        if (group.blockingType == AppBlockingType.Usage) {
            val config = Gson().fromJson(group.setting, AppUsageConfig::class.java)
            if (config != null) {
                val limit = (if (config.isDailyUniform) config.uniformLimit else {
                    val day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1
                    config.dailyLimits[day]
                }) * 60_000L

                if (limit > 0) {
                    val date = TimeTools.getCurrentDate()
                    val totalUsage = runBlocking(Dispatchers.IO) {
                        AppDatabase.getInstance(service).websiteStatsDao().getStatsForPackage(date, packageName)
                            .filter { matchesGroup(group, it.urlIdentifier) }
                            .sumOf { it.totalTime }
                    }
                    val remaining = limit - totalUsage
                    if (remaining > 0) {
                        nextRecheck = now + remaining + 1000 // Add a small buffer
                    }
                }
            }
        }

        // 2. Check Timed Intervals
        if (group.blockingType == AppBlockingType.Timed) {
            val config = Gson().fromJson(group.setting, AppTimeConfig::class.java)
            if (config != null) {
                val calendar = Calendar.getInstance()
                val currentMinutes = TimeTools.convertToMinutesFromMidnight(
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE)
                )
                val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1
                val intervals = if (config.isEveryday) config.everydayIntervals else config.dailyIntervals[dayOfWeek] ?: emptyList()

                var minMinutesUntilStart = Int.MAX_VALUE
                for (interval in intervals) {
                    val start = TimeTools.convertToMinutesFromMidnight(interval.startHour, interval.startMinute)
                    val end = TimeTools.convertToMinutesFromMidnight(interval.endHour, interval.endMinute)
                    
                    if (start <= end) {
                        if (start > currentMinutes) {
                            minMinutesUntilStart = minOf(minMinutesUntilStart, start - currentMinutes)
                        }
                    } else {
                        // Interval crosses midnight
                        if (currentMinutes < end) {
                            // Already in a crossing interval (should be blocked)
                        } else if (start > currentMinutes) {
                            minMinutesUntilStart = minOf(minMinutesUntilStart, start - currentMinutes)
                        }
                    }
                }

                if (minMinutesUntilStart != Int.MAX_VALUE) {
                    val recheckAt = now + (minMinutesUntilStart * 60_000L) - (calendar.get(Calendar.SECOND) * 1000L) - calendar.get(Calendar.MILLISECOND)
                    if (nextRecheck == 0L || recheckAt < nextRecheck) {
                        nextRecheck = recheckAt
                    }
                }
            }
        }

        // 3. Check Cooldowns
        val currentCooldown = cooldownGroupsList[group.id]
        if (currentCooldown != null && currentCooldown > now) {
            if (nextRecheck == 0L || currentCooldown < nextRecheck) {
                nextRecheck = currentCooldown + 500 // Recheck right after cooldown expires
            }
        }

        if (nextRecheck > now) {
            CoroutineScope(Dispatchers.IO).launch {
                service.dataStoreManager.updateNextWebsiteRecheckTime(nextRecheck)
                Log.d("KeywordBlocker", "Scheduled next recheck in ${(nextRecheck - now) / 1000}s")
            }
        }
    }

    private fun handleBlocking(group: KeywordGroup, packageName: String, url: String) {
        if (group.unlockBehavior == KeywordUnlockBehavior.Redirection) {
            val redirectUrl = group.redirectUrl
            val rootNode = service.rootInActiveWindow ?: return
            
            // Ensure we are still on the same browser/app
            if (rootNode.packageName != packageName) return

            val urlBarInfo = URL_BAR_ID_LIST[packageName] ?: return

            val displayUrlTextNode = findElementById(rootNode, urlBarInfo.displayUrlBarId)
                ?: return service.pressHome()

            performSmallUpwardScroll()
            Thread.sleep(200)
            displayUrlTextNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Thread.sleep(200)

            val editUrlBarId = urlBarInfo.editUrlBarId ?: urlBarInfo.displayUrlBarId
            val editUrlBar = findElementById(rootNode, editUrlBarId)
                ?: return service.pressHome()

            editUrlBar.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, redirectUrl
                )
            })
            Thread.sleep(300)

            val goBtnNode =
                findElementById(rootNode, urlBarInfo.browserSugggestionBoxId)
                    ?: return service.pressHome()

            if (urlBarInfo.isSuggestionEqualToGo) {
                goBtnNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            } else {
                goBtnNode.getChild(urlBarInfo.suggestionBoxIndexOfGoBtn)
                    ?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }

            Thread.sleep(2000)
        } else {
            service.pressBack()
            Thread.sleep(1000)
            service.pressHome()
            Handler(Looper.getMainLooper()).postDelayed({
                val intent = Intent(service, WarningActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra("mode", Constants.WARNING_SCREEN_MODE_KEYWORD_BLOCKER)
                    putExtra("result_id", group.id)
                    putExtra("warning_config", Gson().toJson(group.warningScreenConfig))
                }
                service.startActivity(intent)
            }, 300)
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

        if (limit <= 0) return true

        val date = TimeTools.getCurrentDate()
        val totalUsage = runBlocking(Dispatchers.IO) {
            val allStats = AppDatabase.getInstance(service).websiteStatsDao().getStatsForPackage(date, packageName)
            Log.d("KeywordBlocker", "Stats count for $packageName on $date: ${allStats.size}")
            
            // Sum up all identifiers that belong to this specific group
            allStats.filter { stat ->
                val matches = matchesGroup(group, stat.urlIdentifier)
                 Log.d("KeywordBlocker", "Match: ${stat.urlIdentifier} (${stat.totalTime}ms)")
                matches
            }.sumOf { it.totalTime }
        }

        Log.d("total usage", "$totalUsage / $limit [Group: ${group.id}]")

        return totalUsage >= limit
    }

    private fun matchesGroup(group: KeywordGroup, text: String): Boolean {
        val lowerText = text.lowercase(Locale.ROOT)
        
        // 1. Check Wildcards/Regex
        val regexes = groupRegexMap[group.id] ?: emptyList()
        if (regexes.any { it.containsMatchIn(lowerText) }) return true

        // 2. Check Literals
        val literals = group.selectedKeywords.filter { !it.contains("*") }
            .map { it.lowercase(Locale.ROOT) }.toSet()
        if (literals.isNotEmpty()) {
            if (literals.contains(lowerText)) return true
            // Also check if text starts with www. but literal doesn't, or vice-versa
            val noWwwText = lowerText.removePrefix("www.")
            if (literals.contains(noWwwText)) return true
            
            // Fallback to keyword decomposition
            val keywords = parseTextForKeywords(text)
            if (keywords.any { literals.contains(it) }) return true
        }
        
        return false
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

    private var configJob: Job? = null

    fun setupBlocker(service: BaseBlockingService, watchSettings: Boolean = true) {
        this.service = service
        this.browserBlocker = BrowserBlocker(service)
        this.prefs = service.getSharedPreferences("keyword_blocker_prefs", Context.MODE_PRIVATE)
        loadPersistedData()

        if (!watchSettings) return

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

                if (isTurnedOn) {
                    startObservingDatabase()
                } else {
                    observationJob?.cancel()
                }
            }
        }
    }

    fun getRegexList(keywords: Collection<String>): List<Regex> {
        val wildcards = keywords.filter { it.contains("*") }
        return wildcards.map { pattern ->
            val lowerPattern = pattern.lowercase(Locale.ROOT)
            val looksLikeDomain = !lowerPattern.startsWith("http") && !lowerPattern.startsWith("*") && !lowerPattern.startsWith("/")
            val prefix = if (looksLikeDomain) "(?:https?://)?(?:www\\.)?" else ""
            val escaped = lowerPattern.replace(Regex("[.\\+^${'$'}()|\\[\\]\\\\{}]"), "\\\\$0").replace("*", ".*")
            Regex(prefix + escaped)
        }
    }

    fun isFocusWebsiteBlocked(packageName: String, keywords: Set<String>, regexList: List<Regex>, blockMode: FocusBlockMode): Boolean {
        val date = TimeTools.getCurrentDate()
        val latest = runBlocking(Dispatchers.IO) {
            AppDatabase.getInstance(service).websiteStatsDao().getStatsForPackage(date, packageName)
                .maxByOrNull { it.lastVisited }
        } ?: return false

        // Check if the latest visited URL is recent enough (within last 5 seconds)
        if (latest.lastVisited < System.currentTimeMillis() - 5000) return false

        val content = latest.urlIdentifier
        if (content.isEmpty()) return false

        if (blockMode == FocusBlockMode.BLOCK_ALL_EXCEPT_SELECTED) {
            val lower = content.lowercase(Locale.ROOT)
            if (lower.startsWith("chrome://") || lower.startsWith("about:") || 
                lower.contains("newtab") || lower.contains("bookmarks") || lower.contains("history")||
                lower.startsWith("search" )|| lower.endsWith("url") || lower.contains("Search Google or type URL") || !lower.contains(".") || lower.contains("null")) return false
        }

        val lowerText = content.lowercase(Locale.ROOT)
        var matched = false

        if (regexList.any { it.containsMatchIn(lowerText) }) {
            matched = true
        } else {
            val literals = keywords.filter { !it.contains("*") }.map { it.lowercase(Locale.ROOT) }.toSet()
            if (literals.isNotEmpty()) {
                val parsedKeywords = parseTextForKeywords(content)
                if (parsedKeywords.any { literals.contains(it) }) matched = true
            }
        }

        return if (blockMode == FocusBlockMode.BLOCK_SELECTED) matched else !matched
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

        // Trigger a re-evaluation to schedule the next recheck
        val date = TimeTools.getCurrentDate()
        CoroutineScope(Dispatchers.IO).launch {
            val stats = AppDatabase.getInstance(service).websiteStatsDao().getStatsForDate(date)
            val latest = stats.maxByOrNull { it.lastVisited }
            if (latest != null && latest.lastVisited > (System.currentTimeMillis() - 5000)) {
                evaluateAndBlock(latest)
            }
        }
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

    fun removeReceivers() {
        service.unregisterReceiver(refreshReceiver)
        observationJob?.cancel()
    }

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
