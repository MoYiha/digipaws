package neth.iecal.curbox.blockers.viewblocker

import android.graphics.Color

data class PathSegment(
    val className: String,
    val index: Int,
    val isWildcard: Boolean
)

enum class RuleAction { OVERLAY, BACK }

enum class MatchType {
    VIEW_ID, DESC, TEXT, CLASS_NAME, PATH,
    TEXT_CONTAINS, DESC_CONTAINS, DESC_RES,
    IS_SELECTED, IS_CHECKED, IS_FOCUSED, IS_ENABLED, IS_CLICKABLE
}

/**
 * A NodeMatcher holds one or more criteria that must ALL be satisfied by a
 * single AccessibilityNodeInfo for the node to be considered a match.
 *
 * Rule syntax: `type:value;type2:value2;…`
 * Multiple matchers can be listed as `matcher1|matcher2|…` (see parseList).
 */
data class NodeMatcher(
    val criteria: List<Pair<MatchType, String>>
) {
    companion object {
        /**
         * Parse a single matcher from a semicolon-separated string such as
         * `"viewId:com.example:id/foo;isSelected:true"`.
         * Returns null if no valid criteria are found.
         */
        fun parse(raw: String): NodeMatcher? {
            val parsedCriteria = raw.split(";").mapNotNull { part ->
                val sep = part.indexOf(':')
                if (sep < 0) return@mapNotNull null
                val typeStr = part.substring(0, sep).trim().lowercase()
                val value = part.substring(sep + 1).trim()
                if (value.isEmpty()) return@mapNotNull null
                val type = when (typeStr) {
                    "viewid"      -> MatchType.VIEW_ID
                    "desc"        -> MatchType.DESC
                    "text"        -> MatchType.TEXT
                    "classname"   -> MatchType.CLASS_NAME
                    "path"        -> MatchType.PATH
                    "textcontains"-> MatchType.TEXT_CONTAINS
                    "desccontains"-> MatchType.DESC_CONTAINS
                    "descres"     -> MatchType.DESC_RES
                    "isselected"  -> MatchType.IS_SELECTED
                    "ischecked"   -> MatchType.IS_CHECKED
                    "isfocused"   -> MatchType.IS_FOCUSED
                    "isenabled"   -> MatchType.IS_ENABLED
                    "isclickable" -> MatchType.IS_CLICKABLE
                    else          -> return@mapNotNull null
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

/**
 * The fully parsed, runtime-ready representation of a single view-blocking rule.
 *
 * Rules are categorised by how the target view is located:
 *  - [isLayoutRule]           – uses [blockLayoutMatcher] to find a layout to cover
 *  - parsedPath != null       – walks a fixed [targetPath] through the view hierarchy
 *  - [needsViewIdWithDescLookup] – locates by [targetViewId] then filters by [contentDescriptions]
 *  - [needsViewIdLookup]      – locates by [targetViewId] only
 *  - [isRecursiveRule]        – walks the entire tree matching by class/text/desc/regex
 *
 * [ruleString] is the original text the rule was parsed from; it is used as a
 * stable identity key for deduplication (equals/hashCode).
 * [baseKey] is a scoped key used to identify overlay windows on screen.
 */
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ViewBlockerFilterRule) return false
        return ruleString == other.ruleString
    }

    override fun hashCode(): Int = ruleString.hashCode()

    companion object {
        /**
         * Parse a custom rule string in the token-based format documented in view_blocker_rule.md.
         *
         * Format: space-separated `key:value` pairs, e.g.:
         *   `pkg:com.example id:com.example:id/foo +viewId:com.example:id/bar`
         *
         * Modifier prefixes on keys:
         *   `+key:value`  → requirePresent NodeMatcher
         *   `-key:value`  → requireAbsent  NodeMatcher
         *   `~key:value`  → matchChildren  NodeMatcher
         *
         * Returns null if the rule is blank or has no `pkg:` token.
         */
        fun parse(raw: String): ViewBlockerFilterRule? {
            if (raw.isBlank()) return null

            val tokens = mutableMapOf<String, String>()
            val requirePresent = mutableListOf<NodeMatcher>()
            val requireAbsent = mutableListOf<NodeMatcher>()
            val matchChildren = mutableListOf<NodeMatcher>()

            // Regex matches optional modifier prefix, an alphanumeric key (with optional trailing ~),
            // a colon, then either a quoted value or an unquoted non-whitespace value.
            val tokenRegex = Regex("""([+\-~]?[a-zA-Z0-9_]+~?):(?:([^"\s]+)|"([^"]*)")""")

            tokenRegex.findAll(raw).forEach { match ->
                val fullKey = match.groupValues[1]
                // Group 3 is set for quoted values; group 2 for unquoted.
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
            // desc token supports a single description value; pipe-separated multi-desc
            // is only available in the legacy ##-delimited format.
            val contentDescriptions = tokens["desc"]?.let { setOf(it) } ?: emptySet()

            val color = tokens["color"]?.let { colorStr ->
                try {
                    Color.parseColor(if (colorStr.startsWith("#")) colorStr else "#$colorStr")
                } catch (_: Exception) { Color.WHITE }
            } ?: Color.WHITE

            val action = when (tokens["action"]?.lowercase()) {
                "back" -> RuleAction.BACK
                else   -> RuleAction.OVERLAY
            }

            val blockTouches = parseBooleanToken(tokens["blocktouches"], default = true)
            val clickableFilter = tokens["clickable"]?.let { parseBooleanToken(it, default = false) }
            val maxPerScreen = tokens["max"]?.toIntOrNull() ?: 0

            val textRegex = tokens["text~"]?.let { try { Regex(it) } catch (_: Exception) { null } }
            val descRegex = tokens["desc~"]?.let { try { Regex(it) } catch (_: Exception) { null } }

            val parsedPath = targetPath?.let { ViewBlockerRuleParser.parsePath(it) }

            return ViewBlockerFilterRule(
                packageName = packageName,
                targetViewId = targetViewId,
                contentDescriptions = contentDescriptions,
                targetClassName = targetClassName,
                targetText = targetText,
                targetPath = targetPath,
                parsedPath = parsedPath,
                color = color,
                ruleString = raw,
                baseKey = "$packageName::$raw",
                blockTouches = blockTouches,
                requireAbsent = requireAbsent,
                requirePresent = requirePresent,
                matchChildren = matchChildren,
                action = action,
                textContains = tokens["textcontains"],
                descContains = tokens["desccontains"],
                textRegex = textRegex,
                descRegex = descRegex,
                clickableFilter = clickableFilter,
                maxPerScreen = maxPerScreen
            )
        }

        private fun parseBooleanToken(value: String?, default: Boolean): Boolean {
            return when (value?.lowercase()) {
                "true", "1", "yes" -> true
                "false", "0", "no" -> false
                else -> default
            }
        }
    }
}
