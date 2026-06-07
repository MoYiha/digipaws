package neth.iecal.curbox.data.models

data class KeywordBlocker(
    val isActive: Boolean = false,
    val keywordGroups: List<KeywordGroup> = emptyList(),
    val blockAllExceptSupported: Boolean = false
)

data class KeywordGroup(
    val id: String = "",
    val name: String = "name",
    val selectedKeywords: List<String> = listOf(),
    val blockingType: AppBlockingType = AppBlockingType.Usage,
    val isActive: Boolean = false,
    val setting: String = "", // JSON for AppTimeConfig or AppUsageConfig
    val unlockBehavior: KeywordUnlockBehavior = KeywordUnlockBehavior.Redirection,
    val redirectUrl: String = "https://curbox.life",
    val warningScreenConfig: AppBlockerWarningScreenConfig = AppBlockerWarningScreenConfig()
)

enum class KeywordUnlockBehavior {
    Redirection, WarningScreen
}
