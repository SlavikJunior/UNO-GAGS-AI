package uno_server.game;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages multiple UNO game sessions.
 * Provides methods to create, access, and manage game sessions by room ID.
 * This is the main interface that the server will use to interact with game logic.
 */
public class GameSessionManager {
    
    // Thread-safe map to store active game sessions
    private final Map<Long, GameSession> activeSessions;
    
    public GameSessionManager() {
        this.activeSessions = new ConcurrentHashMap<>();
    }
    
    /**
     * Creates a new game session for the specified room.
     * 
     * @param roomId The unique identifier for the room
     * @param players The list of players who will participate in the game
     * @return The newly created GameSession
     * @throws IllegalArgumentException if a session already exists for the room
     */
    public GameSession createSession(long roomId, java.util.List<PlayerState> players) {
        if (activeSessions.containsKey(roomId)) {
            throw new IllegalArgumentException("Game session already exists for room: " + roomId);
        }
        
        GameSession session = new GameSession(roomId, players);
        activeSessions.put(roomId, session);
        
        return session;
    }

    /**
     * Removes and ends a game session.
     *
     * @param roomId The room ID
     */
    public void removeSession(long roomId) {
        activeSessions.remove(roomId);
    }

    /**
     * Plays a card in the specified game session.
     * 
     * @param roomId The room ID
     * @param playerId The player attempting to play the card
     * @param cardIndex The index of the card in the player's hand
     * @param chosenColor The color chosen for wild cards (null for non-wild cards)
     * @return The updated game state
     * @throws IllegalArgumentException if session not found
     */
    public uno_proto.dto.GameState playCard(long roomId, long playerId, int cardIndex, uno_proto.dto.CardColor chosenColor) {
        GameSession session = activeSessions.get(roomId);
        if (session == null) {
            throw new IllegalArgumentException("No game session found for room: " + roomId);
        }
        
        session.playCard(playerId, cardIndex, chosenColor);
        return session.getGameState();
    }
    
    /**
     * Makes the current player draw a card.
     * 
     * @param roomId The room ID
     * @param playerId The player attempting to draw
     * @return The updated game state
     * @throws IllegalArgumentException if session not found
     */
    public uno_proto.dto.GameState drawCard(long roomId, long playerId) {
        GameSession session = activeSessions.get(roomId);
        if (session == null) {
            throw new IllegalArgumentException("No game session found for room: " + roomId);
        }
        
        session.drawCard(playerId);
        return session.getGameState();
    }
    
    /**
     * Player declares UNO.
     * 
     * @param roomId The room ID
     * @param playerId The player declaring UNO
     * @return The updated game state
     * @throws IllegalArgumentException if session not found
     */
    public uno_proto.dto.GameState sayUno(long roomId, long playerId) {
        GameSession session = activeSessions.get(roomId);
        if (session == null) {
            throw new IllegalArgumentException("No game session found for room: " + roomId);
        }
        
        session.sayUno(playerId);
        return session.getGameState();
    }
}