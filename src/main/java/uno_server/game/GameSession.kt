package uno_server.game

import uno_proto.dto.*
import uno_server.game.DeckBuilder.DeckPiles

/**
 * Represents a complete UNO game session.
 * Manages game state, player turns, card play validation, and scoring.
 */
class GameSession(// Getters for game properties
    val roomId: Long, initialPlayers: MutableList<PlayerState>
) {
    private val players = HashMap<Long?, PlayerState>()
    private val deckPiles: DeckPiles = DeckBuilder.createDeckPiles()

    var direction: GameDirection = GameDirection.CLOCKWISE
        private set
    private var currentPlayerIndex: Int = 0
    private val playerOrder: MutableList<Long?>
    var gamePhase: GamePhase = GamePhase.WAITING_TURN
        private set
    private var chosenColor: CardColor? = null // For when a wild card is played

    init {
        // Initialize players
        for (player in initialPlayers) {
            players.put(player.playerId, player)
        }

        // Set player order
        this.playerOrder = ArrayList<Long?>(players.keys)

        // Deal initial cards (7 per player)
        dealInitialCards()
    }

    /**
     * Deals 7 cards to each player from the draw pile.
     */
    private fun dealInitialCards() {
        for (player in players.values) {
            for (i in 0..6) {
                player.addCard(deckPiles.drawCard())
            }
        }
    }

    val currentPlayerId: Long
        /**
         * Gets the current player's ID.
         */
        get() = playerOrder[currentPlayerIndex]!!

    val currentCard: Card
        /**
         * Gets the current top card of the discard pile.
         */
        get() = deckPiles.getTopCard()

    /**
     * Validates if a card can be played on the current card.
     * A card can be played if:
     * - It matches the color of the current card
     * - It matches the number of the current card
     * - It matches the type of the current card
     * - It's a wild card
     * - A color has been chosen and the card matches that color
     */
    fun canPlayCard(card: Card): Boolean {
        val currentCard = this.currentCard

        // Wild cards can always be played
        if (card.type == CardType.WILD || card.type == CardType.WILD_DRAW_FOUR) {
            return true
        }

        // If a color was chosen from a wild card, match against that color
        if (chosenColor != null && card.color == chosenColor) {
            return true
        }

        // If current card is wild and no color chosen, any card can be played
        if ((currentCard.type == CardType.WILD || currentCard.type == CardType.WILD_DRAW_FOUR)
            && chosenColor == null
        ) {
            return true
        }

        // Match against the actual current card's color
        if (card.color == currentCard.color) {
            return true
        }

        // Match against the current card's type (for action cards)
        if (card.type == currentCard.type) {
            return true
        }

        // Match against the current card's number (for number cards)
        if (card.type == CardType.NUMBER && currentCard.type == CardType.NUMBER &&
            card.number == currentCard.number
        ) return true else return false
    }

    /**
     * Plays a card from the current player's hand.
     * Applies card effects and updates game state.
     */
    fun playCard(playerId: Long, cardIndex: Int, chosenColor: CardColor?) {
        // Validate it's the player's turn
        check(playerId == this.currentPlayerId) { "Not your turn" }

        val player: PlayerState = players.get(playerId)!!
        requireNotNull(player) { "Player not found: $playerId" }


        // Get the card to play
        val hand = player.hand
        require(!(cardIndex < 0 || cardIndex >= hand.size)) { "Invalid card index: $cardIndex" }

        val card = hand[cardIndex]


        // Validate the card can be played
        check(canPlayCard(card)) { "Cannot play this card" }


        // Check UNO rules if player has 2 cards after playing
        val willHaveOneCardLeft = hand.size == 2
        if (willHaveOneCardLeft && !player.hasDeclaredUno()) {
            // Player forgot to say UNO - penalty of 2 cards
            player.addCard(deckPiles.drawCard())
            player.addCard(deckPiles.drawCard())
        }


        // Remove and play the card
        val playedCard = player.removeCard(cardIndex)
        deckPiles.playCard(playedCard)


        // Handle wild card color choice
        if (card.type == CardType.WILD || card.type == CardType.WILD_DRAW_FOUR) {
            if (chosenColor == null) {
                this.gamePhase = GamePhase.CHOOSING_COLOR
                return  // Wait for color choice
            }
            this.chosenColor = chosenColor
        } else {
            this.chosenColor = null // Reset color choice for non-wild cards
        }


        // Apply card effects
        applyCardEffect(playedCard)


        // Check for win condition
        if (player.cardCount == 0) {
            gamePhase = GamePhase.FINISHED
            return
        }


        // Move to next player
        moveToNextPlayer()
        gamePhase = GamePhase.WAITING_TURN
    }

    /**
     * Sets the chosen color after playing a wild card.
     */
    fun setChosenColor(color: CardColor?) {
        check(gamePhase == GamePhase.CHOOSING_COLOR) { "Not waiting for color choice" }

        this.chosenColor = color


        // Apply the wild card effect and move to next player
        val currentCard = this.currentCard
        applyCardEffect(currentCard)
        moveToNextPlayer()
        gamePhase = GamePhase.WAITING_TURN
    }

    /**
     * Applies the effect of the played card.
     */
    private fun applyCardEffect(card: Card) {
        when (card.type) {
            CardType.SKIP ->                 // Skip the next player
                moveToNextPlayer()

            CardType.REVERSE -> {
                // Reverse direction
                direction =
                    if (direction == GameDirection.CLOCKWISE) GameDirection.COUNTER_CLOCKWISE else GameDirection.CLOCKWISE
                // In 2-player game, reverse acts like skip
                if (players.size == 2) {
                    moveToNextPlayer()
                }
            }

            CardType.DRAW_TWO -> {
                // Next player draws 2 cards and loses their turn
                moveToNextPlayer()
                val drawTwoTarget: PlayerState = players.get(this.currentPlayerId)!!
                drawTwoTarget.addCard(deckPiles.drawCard())
                drawTwoTarget.addCard(deckPiles.drawCard())
            }

            CardType.WILD_DRAW_FOUR -> {
                // Next player draws 4 cards and loses their turn
                moveToNextPlayer()
                val drawFourTarget: PlayerState = players.get(this.currentPlayerId)!!
                var i = 0
                while (i < 4) {
                    drawFourTarget.addCard(deckPiles.drawCard())
                    i++
                }
            }

            CardType.WILD, CardType.NUMBER -> {}
        }
    }

    /**
     * Moves to the next player based on current direction.
     */
    private fun moveToNextPlayer() {
        currentPlayerIndex = if (direction == GameDirection.CLOCKWISE)
            (currentPlayerIndex + 1) % playerOrder.size
        else
            (currentPlayerIndex - 1 + playerOrder.size) % playerOrder.size
    }

    /**
     * Current player draws a card and ends their turn.
     */
    fun drawCard(playerId: Long) {
        check(playerId == this.currentPlayerId) { "Not your turn" }

        val player: PlayerState = players.get(playerId)!!
        player.addCard(deckPiles.drawCard())

        moveToNextPlayer()
        gamePhase = GamePhase.WAITING_TURN
    }

    /**
     * Player declares UNO (must have exactly 2 cards).
     */
    fun sayUno(playerId: Long) {
        val player: PlayerState = players.get(playerId)!!
        requireNotNull(player) { "Player not found: " + playerId }

        player.declareUno()
    }

    /**
     * Calculates scores for all players when the game ends.
     * The winner gets points equal to the sum of all cards in other players' hands.
     */
    fun calculateScores(): MutableMap<Long?, Int?> {
        val scores: MutableMap<Long?, Int?> = HashMap<Long?, Int?>()

        if (gamePhase != GamePhase.FINISHED) {
            return scores // Game not finished yet
        }

        // Find the winner (player with 0 cards)
        var winner: PlayerState? = null
        for (player in players.values) {
            if (player.cardCount == 0) {
                winner = player
                break
            }
        }

        if (winner == null) {
            return scores // No winner found
        }

        // Calculate total points from all other players
        var totalPoints = 0
        for (player in players.values) {
            if (player.playerId != winner.playerId) {
                totalPoints += player.calculateHandScore()
            }
        }

        // Winner gets all the points
        scores.put(winner.playerId, totalPoints)

        return scores
    }

    val gameState: GameState
        /**
         * Gets the current game state as a DTO for transmission to clients.
         */
        get() {
            val playerInfos: MutableMap<Long, PlayerGameInfo> = HashMap()

            for (player in players.values) {
                val info = PlayerGameInfo(
                    player.username,
                    player.cardCount,
                    player.hasDeclaredUno()
                )
                playerInfos.put(player.playerId, info)
            }

            return GameState(
                roomId,
                playerInfos,
                this.currentCard,
                this.currentPlayerId,
                direction,
                gamePhase
            )
        }

    fun getPlayers(): MutableMap<Long?, PlayerState?> {
        return HashMap(players)
    }
}