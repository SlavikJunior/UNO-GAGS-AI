package uno_proto.dto

data class GameState(
    val roomId: Long,
    val players: Map<Long, PlayerGameInfo>, // userId -> инфо
    val currentCard: Card?,
    val currentPlayerId: Long,
    val direction: GameDirection,
    val gamePhase: GamePhase
)

data class PlayerGameInfo(
    val username: String,
    val cardCount: Int,
    val hasUno: Boolean = false
)

// направление: по часовой и против часовой
enum class GameDirection {
    CLOCKWISE, COUNTER_CLOCKWISE
}

enum class GamePhase {
    WAITING_TURN, CHOOSING_COLOR, DRAWING_CARD, FINISHED
}

data class PlayCardRequest(
    val cardIndex: Int,           // Индекс карты в руке
    val chosenColor: CardColor? = null // Для WILD карт
)

data class ChatMessage(
    val senderId: Long,
    val senderName: String,
    val content: String,
    val messageType: ChatMessageType = ChatMessageType.TEXT,
    val emojiId: String? = null,  // Для смайлов
    val timestamp: Long = System.currentTimeMillis()
)

enum class ChatMessageType {
    TEXT, EMOJI, VOICE
}

// Карта (базовая модель)
data class Card(
    val id: String,
    val color: CardColor,
    val type: CardType,
    val number: Int? = null  // только для NUMBER
)

// карта: красная, синяя, зелёная, жёлтая, дикая
enum class CardColor { RED, BLUE, GREEN, YELLOW, WILD }
// карта: обычная, пропуск, разворот, +2, дикая, дикая+4
enum class CardType { NUMBER, SKIP, REVERSE, DRAW_TWO, WILD, WILD_DRAW_FOUR }