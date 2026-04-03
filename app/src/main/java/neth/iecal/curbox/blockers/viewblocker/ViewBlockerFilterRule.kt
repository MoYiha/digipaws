package neth.iecal.curbox.blockers.viewblocker

import android.graphics.Color

data class PathSegment(
    val className: String,
    val index: Int,
    val isWildcard: Boolean
)

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
    var isCustom: Boolean = false
) {
    val isRecursiveRule: Boolean
        get() = targetViewId.isNullOrEmpty() && targetPath.isNullOrEmpty()

    val needsViewIdLookup: Boolean
        get() = !targetViewId.isNullOrEmpty() && contentDescriptions.isEmpty()

    val needsViewIdWithDescLookup: Boolean
        get() = !targetViewId.isNullOrEmpty() && contentDescriptions.isNotEmpty()

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
