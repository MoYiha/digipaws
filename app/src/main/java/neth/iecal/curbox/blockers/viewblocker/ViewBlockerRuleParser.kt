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

            rules.add(
                ViewBlockerFilterRule(
                    packageName = packageName,
                    targetViewId = targetViewId,
                    contentDescriptions = descriptions,
                    targetClassName = targetClassName,
                    targetText = targetText,
                    targetPath = targetPath,
                    color = color,
                    description = currentComment,
                    ruleString = trimmed,
                    blockTouches = blockTouches
                )
            )
        }
        return rules
    }
}
