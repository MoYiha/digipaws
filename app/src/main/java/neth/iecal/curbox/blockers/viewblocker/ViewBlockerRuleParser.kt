package neth.iecal.curbox.blockers.viewblocker

import android.graphics.Color

object ViewBlockerRuleParser {

    fun parseRules(rawLines: List<String>): List<ViewBlockerFilterRule> {
        val rules = mutableListOf<ViewBlockerFilterRule>()
        var currentComment: String? = null

        for (line in rawLines) {
            if (line.isBlank()) continue

            val trimmed = line.trim()
            if (trimmed.startsWith("//")) {
                currentComment = trimmed.removePrefix("//").trim()
                continue
            }

            val parts = trimmed.split("##")
            if (parts.size < 2) continue

            val packageName = parts[0].trim()
            if (packageName.isEmpty()) continue

            var targetViewId: String? = null
            val descriptions = mutableSetOf<String>()
            var targetClassName: String? = null
            var targetText: String? = null
            var targetPath: String? = null
            var color = Color.WHITE
            var blockTouches = true

            for (i in 1 until parts.size) {
                val part = parts[i]
                if (!part.contains("=")) continue

                val kv = part.split("=", limit = 2)
                val key = kv[0].trim()
                val value = kv[1].trim()

                when (key) {
                    "viewId" -> targetViewId = value
                    "desc" -> {
                        value.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                            .forEach { descriptions.add(it) }
                    }
                    "color" -> {
                        try {
                            color = Color.parseColor(if (value.startsWith("#")) value else "#$value")
                        } catch (_: IllegalArgumentException) {}
                    }
                    "blockTouches" -> blockTouches = value.toBoolean()
                    "className" -> targetClassName = value
                    "text" -> targetText = value
                    "path" -> targetPath = value
                    "comment" -> currentComment = value
                }
            }

            val parsedPath = targetPath?.let { parsePath(it) }
            val baseKey = "$packageName::$trimmed"

            rules.add(
                ViewBlockerFilterRule(
                    packageName = packageName,
                    targetViewId = targetViewId,
                    contentDescriptions = descriptions,
                    targetClassName = targetClassName,
                    targetText = targetText,
                    targetPath = targetPath,
                    parsedPath = parsedPath,
                    color = color,
                    description = currentComment,
                    ruleString = trimmed,
                    baseKey = baseKey,
                    blockTouches = blockTouches
                )
            )
        }
        return rules
    }

    private fun parsePath(path: String): List<PathSegment> {
        if (path.isEmpty()) return emptyList()
        return path.split(">").map { segment ->
            val bracketStart = segment.indexOf('[')
            if (bracketStart >= 0) {
                val className = segment.substring(0, bracketStart)
                val indexStr = segment.substring(bracketStart + 1, segment.indexOf(']'))
                if (indexStr == "*") {
                    PathSegment(className, -1, true)
                } else {
                    PathSegment(className, indexStr.toIntOrNull() ?: 0, false)
                }
            } else {
                PathSegment(segment, 0, false)
            }
        }
    }
}
