package neth.iecal.curbox.blockers.viewblocker

import android.graphics.Color

data class ViewBlockerFilterRule(
    val packageName: String,
    val targetViewId: String? = null,
    val contentDescriptions: Set<String> = emptySet(),
    val targetClassName: String? = null,
    val targetText: String? = null,
    val targetPath: String? = null,
    val color: Int = Color.WHITE,
    val description: String? = null,
    val ruleString: String,
    val blockTouches: Boolean = true,
    var enabled: Boolean = true,
    var isCustom: Boolean = false
) {
    fun matchesPackage(pkgName: CharSequence?): Boolean {
        return pkgName != null && packageName == pkgName.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ViewBlockerFilterRule) return false
        return ruleString == other.ruleString
    }

    override fun hashCode(): Int = ruleString.hashCode()
}
