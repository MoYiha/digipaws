package neth.iecal.curbox.blockers.viewblocker

import android.graphics.Color
import neth.iecal.curbox.data.models.ViewBlockerRule

object ViewBlockerRuleParser {

    /**
     * Parse a list of raw rule strings into [ViewBlockerFilterRule] objects.
     *
     * Two formats are supported, determined by whether the line contains `##`:
     *
     * 1. **Token-based** (no `##`) — the preferred format for custom rules, documented in
     *    `view_blocker_rule.md`. Delegated to [ViewBlockerFilterRule.parse].
     *    Example: `pkg:com.example id:com.example:id/foo +viewId:com.example:id/bar`
     *
     * 2. **Legacy `##`-delimited** — used for backwards compatibility with existing stored
     *    custom rules. Key-value pairs separated by `##`.
     *    Example: `com.example##viewId=com.example:id/foo##requirePresent=viewId:com.example:id/bar`
     *
     * Lines that are blank, start with `//` (comment), or start with `!DISABLED!` are skipped.
     * A `//` comment line sets the description for the rule that immediately follows it.
     */
    fun parseRules(rawLines: List<String>): List<ViewBlockerFilterRule> {
        val rules = mutableListOf<ViewBlockerFilterRule>()
        var pendingDescription: String? = null

        for (line in rawLines) {
            if (line.isBlank()) continue
            val trimmed = line.trim()

            if (trimmed.startsWith("!DISABLED!")) {
                pendingDescription = null
                continue
            }

            // A comment line sets the description for the very next rule.
            if (trimmed.startsWith("//")) {
                pendingDescription = trimmed.removePrefix("//").trim()
                continue
            }

            if (!trimmed.contains("##")) {
                // Token-based format — delegate to ViewBlockerFilterRule.parse().
                val rule = ViewBlockerFilterRule.parse(trimmed)
                if (rule != null) {
                    rules.add(if (pendingDescription != null) rule.copy(description = pendingDescription) else rule)
                    pendingDescription = null
                }
                continue
            }

            // Legacy ##-delimited format — parse inline.
            val rule = parseLegacyRule(trimmed, pendingDescription)
            if (rule != null) {
                rules.add(rule)
                pendingDescription = null
            }
        }
        return rules
    }

    /**
     * Parse a single line in the legacy `##`-delimited format.
     *
     * The first segment is the package name; subsequent segments are `key=value` pairs.
     * Returns null if the line is malformed.
     */
    private fun parseLegacyRule(trimmed: String, pendingDescription: String?): ViewBlockerFilterRule? {
        val parts = trimmed.split("##")
        if (parts.size < 2) return null

        val packageName = parts[0].trim()
        if (packageName.isEmpty()) return null

        var targetViewId: String? = null
        val descriptions = mutableSetOf<String>()
        var targetClassName: String? = null
        var targetText: String? = null
        var targetPath: String? = null
        var color = Color.WHITE
        var blockTouches = true
        val requireAbsent = mutableListOf<NodeMatcher>()
        val requirePresent = mutableListOf<NodeMatcher>()
        var blockLayoutMatcher: NodeMatcher? = null
        val excludeFromLayoutMatchers = mutableListOf<NodeMatcher>()
        val matchChildren = mutableListOf<NodeMatcher>()
        var action = RuleAction.OVERLAY
        var textContains: String? = null
        var descContains: String? = null
        var textRegex: Regex? = null
        var descRegex: Regex? = null
        var clickableFilter: Boolean? = null
        var maxPerScreen = 0
        var comment: String? = pendingDescription

        for (i in 1 until parts.size) {
            val part = parts[i]
            if (!part.contains("=")) continue

            val kv = part.split("=", limit = 2)
            val key = kv[0].trim()
            val value = kv[1].trim()

            when (key) {
                "viewId"           -> targetViewId = value
                "desc"             -> value.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                                          .forEach { descriptions.add(it) }
                "color"            -> try {
                                          color = Color.parseColor(if (value.startsWith("#")) value else "#$value")
                                      } catch (_: IllegalArgumentException) {}
                "blockTouches"     -> blockTouches = value.toBoolean()
                "className"        -> targetClassName = value
                "text"             -> targetText = value
                "path"             -> targetPath = value
                "comment"          -> comment = value
                "requireAbsent"    -> requireAbsent.addAll(NodeMatcher.parseList(value))
                "requirePresent"   -> requirePresent.addAll(NodeMatcher.parseList(value))
                "blockLayout"      -> blockLayoutMatcher = NodeMatcher.parse(value)
                "excludeFromLayout"-> excludeFromLayoutMatchers.addAll(NodeMatcher.parseList(value))
                "matchChildren"    -> matchChildren.addAll(NodeMatcher.parseList(value))
                "action"           -> action = if (value.lowercase() == "back") RuleAction.BACK else RuleAction.OVERLAY
                "textContains"     -> textContains = value
                "descContains"     -> descContains = value
                "textRegex"        -> try { textRegex = Regex(value) } catch (_: Exception) {}
                "descRegex"        -> try { descRegex = Regex(value) } catch (_: Exception) {}
                "clickable"        -> clickableFilter = value.toBoolean()
                "maxPerScreen"     -> maxPerScreen = value.toIntOrNull() ?: 0
            }
        }

        return ViewBlockerFilterRule(
            packageName = packageName,
            targetViewId = targetViewId,
            contentDescriptions = descriptions,
            targetClassName = targetClassName,
            targetText = targetText,
            targetPath = targetPath,
            parsedPath = targetPath?.let { parsePath(it) },
            color = color,
            description = comment,
            ruleString = trimmed,
            baseKey = "$packageName::$trimmed",
            blockTouches = blockTouches,
            requireAbsent = requireAbsent,
            requirePresent = requirePresent,
            blockLayoutMatcher = blockLayoutMatcher,
            excludeFromLayoutMatchers = excludeFromLayoutMatchers,
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

    /**
     * Parse a path string such as `"FrameLayout[0]>TextView[1]>ImageView[*]"` into a
     * list of [PathSegment] objects.
     *
     * Segments are separated by `>`. Each segment is `ClassName` (index defaults to 0),
     * `ClassName[n]` (match the nth child of that class), or `ClassName[*]` (wildcard —
     * match all children of that class).
     */
    fun parsePath(path: String): List<PathSegment> {
        if (path.isEmpty()) return emptyList()
        return path.split(">").map { segment ->
            val bracketStart = segment.indexOf('[')
            if (bracketStart >= 0) {
                val className = segment.substring(0, bracketStart)
                val indexStr = segment.substring(bracketStart + 1, segment.indexOf(']'))
                if (indexStr == "*") {
                    PathSegment(className, -1, isWildcard = true)
                } else {
                    PathSegment(className, indexStr.toIntOrNull() ?: 0, isWildcard = false)
                }
            } else {
                PathSegment(segment, 0, isWildcard = false)
            }
        }
    }
}

/**
 * Convert a [ViewBlockerRule] (the UI/storage model, which may have an [ViewBlockerRule.isEnabled]
 * flag and a human-readable [ViewBlockerRule.label]) directly into a [ViewBlockerFilterRule]
 * (the runtime matching model) without any intermediate string serialization.
 *
 * This is used by [neth.iecal.curbox.blockers.viewblocker.ViewBlocker.rebuildParsedRules] to
 * activate built-in rules that the user has toggled on.
 */
fun ViewBlockerRule.toFilterRule(): ViewBlockerFilterRule {
    val contentDescriptions = desc
        ?.split("|")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.toSet()
        ?: emptySet()

    val parsedPath = path?.let { ViewBlockerRuleParser.parsePath(it) }

    // Build a stable identity string. We include "color=" only when the color was explicitly
    // set so that ViewBlocker can distinguish "default white" from "explicitly white" when
    // deciding whether to swap to black in dark mode.
    val ruleString = if (color != android.graphics.Color.WHITE) "$id color=#${Integer.toHexString(color)}" else id

    return ViewBlockerFilterRule(
        packageName = packageName,
        targetViewId = viewId,
        contentDescriptions = contentDescriptions,
        targetClassName = className,
        targetText = text,
        targetPath = path,
        parsedPath = parsedPath,
        color = color,
        description = label,
        ruleString = ruleString,
        baseKey = "$packageName::$id",
        blockTouches = blockTouches,
        enabled = isEnabled,
        requireAbsent = requireAbsent.mapNotNull { NodeMatcher.parse(it) },
        requirePresent = requirePresent.mapNotNull { NodeMatcher.parse(it) },
        blockLayoutMatcher = blockLayout?.let { NodeMatcher.parse(it) },
        excludeFromLayoutMatchers = excludeFromLayout?.let { NodeMatcher.parseList(it) } ?: emptyList(),
        matchChildren = matchChildren?.let { NodeMatcher.parseList(it) } ?: emptyList(),
        action = when (action?.lowercase()) { "back" -> RuleAction.BACK else -> RuleAction.OVERLAY },
        textContains = textContains,
        descContains = descContains,
        textRegex = textRegex?.let { try { Regex(it) } catch (_: Exception) { null } },
        descRegex = descRegex?.let { try { Regex(it) } catch (_: Exception) { null } },
        clickableFilter = clickable?.let { v ->
            when (v.lowercase()) {
                "true", "1", "yes" -> true
                "false", "0", "no" -> false
                else               -> null
            }
        },
        maxPerScreen = maxPerScreen
    )
}
