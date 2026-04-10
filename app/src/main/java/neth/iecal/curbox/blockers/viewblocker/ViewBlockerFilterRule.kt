package neth.iecal.curbox.blockers.viewblocker

import android.graphics.Color

data class PathSegment(
    val className: String,
    val index: Int,
    val isWildcard: Boolean
)

enum class RuleAction { OVERLAY, BACK }

enum class MatchType { VIEW_ID, DESC, TEXT, CLASS_NAME, PATH, TEXT_CONTAINS, DESC_CONTAINS, DESC_RES, IS_SELECTED, IS_CHECKED, IS_FOCUSED, IS_ENABLED, IS_CLICKABLE }

data class NodeMatcher(
    val criteria: List<Pair<MatchType, String>>
) {
    companion object {
        fun parse(raw: String): NodeMatcher? {
            val parts = raw.split(";")
            val parsedCriteria = parts.mapNotNull { part ->
                val sep = part.indexOf(':')
                if (sep < 0) return@mapNotNull null
                val typeStr = part.substring(0, sep).trim().lowercase()
                val value = part.substring(sep + 1).trim()
                if (value.isEmpty()) return@mapNotNull null
                val type = when (typeStr) {
                    "viewid" -> MatchType.VIEW_ID
                    "desc" -> MatchType.DESC
                    "text" -> MatchType.TEXT
                    "classname" -> MatchType.CLASS_NAME
                    "path" -> MatchType.PATH
                    "textcontains" -> MatchType.TEXT_CONTAINS
                    "desccontains" -> MatchType.DESC_CONTAINS
                    "descres" -> MatchType.DESC_RES
                    "isselected" -> MatchType.IS_SELECTED
                    "ischecked" -> MatchType.IS_CHECKED
                    "isfocused" -> MatchType.IS_FOCUSED
                    "isenabled" -> MatchType.IS_ENABLED
                    "isclickable" -> MatchType.IS_CLICKABLE
                    else -> return@mapNotNull null
                }
                Pair(type, value)
            }
            if (parsedCriteria.isEmpty()) return null
            return NodeMatcher(parsedCriteria)
        }

        fun parseList(raw: String): List<NodeMatcher> =
            raw.split("|").mapNotNull { parse(it.trim()) }
    }
}

data class ViewBlockerFilterRule(
    val packageName: String,
    val targetViewId: String? = null,
    val contentDescriptions: Set<String> = emptySet(),
    val targetClassName: String? = null,
    val targetText: String? = null,
    val targetPath: String? = null,
    val parsedPath: List<PathSegment>? = null,
    val color: Int = Color.WHITE,
    val description: String? = null,
    val ruleString: String,
    val baseKey: String = "",
    val blockTouches: Boolean = true,
    var enabled: Boolean = true,
    var isCustom: Boolean = false,
    val requireAbsent: List<NodeMatcher> = emptyList(),
    val requirePresent: List<NodeMatcher> = emptyList(),
    val blockLayoutMatcher: NodeMatcher? = null,
    val excludeFromLayoutMatchers: List<NodeMatcher> = emptyList(),
    val matchChildren: List<NodeMatcher> = emptyList(),
    val action: RuleAction = RuleAction.OVERLAY,
    val textContains: String? = null,
    val descContains: String? = null,
    val textRegex: Regex? = null,
    val descRegex: Regex? = null,
    val clickableFilter: Boolean? = null,
    val maxPerScreen: Int = 0
) {
    val isRecursiveRule: Boolean
        get() = targetViewId.isNullOrEmpty() && targetPath.isNullOrEmpty() && blockLayoutMatcher == null

    val needsViewIdLookup: Boolean
        get() = !targetViewId.isNullOrEmpty() && contentDescriptions.isEmpty()

    val needsViewIdWithDescLookup: Boolean
        get() = !targetViewId.isNullOrEmpty() && contentDescriptions.isNotEmpty()

    val isLayoutRule: Boolean
        get() = blockLayoutMatcher != null

    fun matchesPackage(pkgName: CharSequence?): Boolean {
        if (pkgName == null) return false
        if (pkgName is String) return packageName == pkgName
        return packageName.length == pkgName.length && packageName.contentEquals(pkgName)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ViewBlockerFilterRule) return false
        return ruleString == other.ruleString
    }

    override fun hashCode(): Int = ruleString.hashCode()

    companion object {
        private val tokenRegex = Regex("""([+\-~]?[a-zA-Z0-9_]+~?):(?:([^"\s]+)|"([^"]*)")""")
        
        fun parse(raw: String): ViewBlockerFilterRule? {
            if (raw.isBlank()) return null
            
            val tokens = mutableMapOf<String, String>()
            val requirePresent = mutableListOf<NodeMatcher>()
            val requireAbsent = mutableListOf<NodeMatcher>()
            val matchChildren = mutableListOf<NodeMatcher>()
            
            tokenRegex.findAll(raw).forEach { match ->
                val fullKey = match.groupValues[1]
                val value = if (match.groupValues[3].isNotEmpty()) match.groupValues[3] else match.groupValues[2]
                
                when {
                    fullKey.startsWith("+") -> {
                        val key = fullKey.drop(1).lowercase()
                        NodeMatcher.parse("$key:$value")?.let { requirePresent.add(it) }
                    }
                    fullKey.startsWith("-") -> {
                        val key = fullKey.drop(1).lowercase()
                        NodeMatcher.parse("$key:$value")?.let { requireAbsent.add(it) }
                    }
                    fullKey.startsWith("~") -> {
                        val key = fullKey.drop(1).lowercase()
                        NodeMatcher.parse("$key:$value")?.let { matchChildren.add(it) }
                    }
                    else -> tokens[fullKey.lowercase()] = value
                }
            }
            
            val packageName = tokens["pkg"] ?: return null
            
            val targetViewId = tokens["id"]
            val targetClassName = tokens["class"]
            val targetText = tokens["text"]
            val targetPath = tokens["path"]
            val description = tokens["desc"]?.let { setOf(it) } ?: emptySet()
            
            val color = tokens["color"]?.let { colorStr ->
                try {
                    android.graphics.Color.parseColor(if (colorStr.startsWith("#")) colorStr else "#$colorStr")
                } catch (e: Exception) {
                    android.graphics.Color.WHITE
                }
            } ?: android.graphics.Color.WHITE
            
            fun parseBoolean(value: String?, default: Boolean): Boolean {
                return when (value?.lowercase()) {
                    "true", "1", "yes" -> true
                    "false", "0", "no" -> false
                    else -> default
                }
            }
            
            val action = when (tokens["action"]?.lowercase()) {
                "back" -> RuleAction.BACK
                else -> RuleAction.OVERLAY
            }
            
            val textContains = tokens["textcontains"]
            val descContains = tokens["desccontains"]
            val textRegex = tokens["text~"]?.let { try { Regex(it) } catch (e: Exception) { null } }
            val descRegex = tokens["desc~"]?.let { try { Regex(it) } catch (e: Exception) { null } }
            
            val clickableFilter = tokens["clickable"]?.let { parseBoolean(it, false) }
            val maxPerScreen = tokens["max"]?.toIntOrNull() ?: 0
            val blockTouches = parseBoolean(tokens["blocktouches"], true)
            
            return ViewBlockerFilterRule(
                packageName = packageName,
                targetViewId = targetViewId,
                contentDescriptions = description,
                targetClassName = targetClassName,
                targetText = targetText,
                targetPath = targetPath,
                color = color,
                ruleString = raw,
                blockTouches = blockTouches,
                requireAbsent = requireAbsent,
                requirePresent = requirePresent,
                matchChildren = matchChildren,
                action = action,
                textContains = textContains,
                descContains = descContains,
                textRegex = textRegex,
                descRegex = descRegex,
                clickableFilter = clickableFilter,
                maxPerScreen = maxPerScreen
            )
        }
    }
}
