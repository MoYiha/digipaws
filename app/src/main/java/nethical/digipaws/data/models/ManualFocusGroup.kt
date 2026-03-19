package nethical.digipaws.data.models

import java.util.UUID

data class ManualFocusGroup(
    val groupId: String = UUID.randomUUID().toString(),
    val groupName: String,
    val packages: HashSet<String>,
    val blockMode: FocusBlockMode,
){
    override fun toString(): String {
        return "$groupName (${packages.size} ${
            if(blockMode == FocusBlockMode.BLOCK_SELECTED) "included" else "excluded"
        } apps)"
    }

}
