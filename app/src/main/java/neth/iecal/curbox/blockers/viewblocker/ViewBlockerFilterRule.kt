package neth.iecal.curbox.blockers.viewblocker

import android.graphics.Color

data class PathSegment(
    val className: String,
    val index: Int,
    val isWildcard: Boolean
)

enum class RuleAction { OVERLAY, BACK }

enum class MatchType { VIEW_ID, DESC, TEXT, CLASS_NAME, PATH, TEXT_CONTAINS, DESC_CONTAINS, DESC_RES }

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
}
