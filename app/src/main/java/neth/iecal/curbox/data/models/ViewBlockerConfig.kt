package neth.iecal.curbox.data.models

import android.graphics.Color

data class ViewBlockerRule(
    val id: String,
    val packageName: String,
    val label: String,
    val viewId: String? = null,
    val desc: String? = null,
    val path: String? = null,
    val className: String? = null,
    val text: String? = null,
    val color: Int = Color.WHITE,
    val blockTouches: Boolean = true,
    val isEnabled: Boolean = false
)

data class ViewBlockerConfig(
    val isActive: Boolean = false,
    val rules: List<ViewBlockerRule> = emptyList(),
    val customRules: List<String> = emptyList()
)
