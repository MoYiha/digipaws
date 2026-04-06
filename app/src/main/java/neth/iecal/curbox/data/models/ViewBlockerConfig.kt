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
    val isEnabled: Boolean = false,
    val requireAbsent: List<String> = emptyList(),
    val requirePresent: List<String> = emptyList(),
    val action: String? = null,
    val textContains: String? = null,
    val descContains: String? = null,
    val textRegex: String? = null,
    val descRegex: String? = null,
    val matchChildren: String? = null,
    val blockLayout: String? = null,
    val excludeFromLayout: String? = null,
    val clickable: String? = null,
    val maxPerScreen: Int = 0
)

data class ViewBlockerConfig(
    val isActive: Boolean = false,
    val rules: List<ViewBlockerRule> = emptyList(),
    val customRules: List<String> = emptyList()
)
