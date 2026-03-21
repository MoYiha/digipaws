package nethical.digipaws.data.models

data class KeywordBlocker(
    val isActive: Boolean = false,
    val blockedKeywords: List<String> = emptyList(),
    val redirectUrl: String = "",
    val searchRecursively: Boolean = false,
    val blockAllExceptSupported: Boolean = false,
    val ignoredApps: List<String> = emptyList()
)
