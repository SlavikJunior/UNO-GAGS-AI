package uno_proto.dto

data class CreateRoomRequest(
    val roomName: String,
    val password: String? = null,
    val maxPlayers: Int = 4,
    val rules: GameRules = GameRules()
)

data class CreateRoomResponse(
    val roomId: Long,
    val roomName: String,
    val isSuccessful: Boolean
)

data class GameRules(
    val allowStackingPlusTwo: Boolean = false,
    val allowStackingPlusFour: Boolean = false,
    val sevenZeroRule: Boolean = false,
    val jumpInRule: Boolean = false
)

data class JoinRoomRequest(
    val roomId: Long,
    val password: String? = null
)

data class JoinRoomResponse(
    val roomId: Long,
    val isSuccessful: Boolean
)

data class RoomInfo(
    val roomId: Long,
    val roomName: String,
    val hasPassword: Boolean,
    val maxPlayers: Int,
    val currentPlayers: Int,
    val status: RoomStatus,
    val creatorName: String
)

enum class RoomStatus {
    WAITING, IN_PROGRESS, FINISHED
}

data class LobbyUpdate(
    val players: List<PlayerInfo>,
    val roomStatus: RoomStatus
)

data class PlayerInfo(
    val userId: Long,
    val username: String,
    val isOwner: Boolean,
    val isReady: Boolean
)