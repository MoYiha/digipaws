package nethical.digipaws.data.models

data class AppGroup(
    val id: String = "",
    val name: String = "name",
    val selectedPackages: List<String> = listOf(),
    val blockingType: AppBlockingType = AppBlockingType.Usage, // "USAGE" or "TIME"
    val isActive: Boolean = false,
    val setting:String = ""
)

enum class AppBlockingType{
    Usage, Timed
}