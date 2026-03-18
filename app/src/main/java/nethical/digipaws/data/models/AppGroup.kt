package nethical.digipaws.data.models

import nethical.digipaws.blockers.AppBlockerWarningScreenConfig

data class AppGroup(
    val id: String = "",
    val name: String = "name",
    val selectedPackages: List<String> = listOf(),
    val blockingType: AppBlockingType = AppBlockingType.Usage, // "USAGE" or "TIME"
    val isActive: Boolean = false,
    val setting:String = "",
    val warningScreenConfig : AppBlockerWarningScreenConfig
)

enum class AppBlockingType{
    Usage, Timed
}