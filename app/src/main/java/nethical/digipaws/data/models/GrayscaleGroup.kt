package nethical.digipaws.data.models

import java.util.UUID

data class GrayscaleGroup(
val groupId: String = UUID.randomUUID().toString(),
val groupName: String,
val packages: HashSet<String>,
var dailyIntervals: MutableMap<Int, MutableList<TimeInterval>> = mutableMapOf(),
var isActive: Boolean = true
) {
    override fun toString(): String {
        return "$groupName (${packages.size} apps)"
    }
}