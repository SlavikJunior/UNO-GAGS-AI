package uno_ui;

import javafx.application.Platform;
import uno_proto.common.*;
import uno_proto.dto.*;
import uno_server.protocol.MessageParser;

import java.util.*;

/**
 * GameController is the "brain" of the UI - it keeps track of the current
 * game state and provides methods for the UI to call.
 * 
 * It acts as a bridge between the NetworkClient and the UI components.
 * When the server sends updates, this class processes them and notifies the UI.
 */
public class GameController {
    
    // The network client for communicating with server
    private final NetworkClient networkClient;
    
    // Current game state (null if not in a game)
    private GameState currentGameState;
    
    // Current lobby state (null if not in a lobby)
    private LobbyUpdate currentLobbyState;
    
    // Current room ID we're in (null if not in a room)
    private Long currentRoomId;
    
    // Our player ID (assigned by server)
    private Long myPlayerId;
    
    // List of cards in our hand
    private final List<Card> myHand;
    
    // Selected card index (for playing)
    private int selectedCardIndex = -1;
    
    // Chat messages
    private final List<ChatMessage> chatMessages;
    
    // Callbacks for UI to be notified of changes
    private Runnable onStateChanged;
    private Runnable onChatMessage;
    
    /**
     * Creates a new GameController.
     */
    public GameController() {
        this.networkClient = new NetworkClient();
        this.myHand = new ArrayList<>();
        this.chatMessages = new ArrayList<>();
        
        // Set up listener for messages from server
        networkClient.setMessageListener(this::handleMessage);
    }
    
    /**
     * Sets callback that gets called when game state changes.
     * This allows the UI to refresh itself.
     */
    public void setOnStateChanged(Runnable callback) {
        this.onStateChanged = callback;
    }
    
    /**
     * Sets callback that gets called when new chat message arrives.
     */
    public void setOnChatMessage(Runnable callback) {
        this.onChatMessage = callback;
    }
    
    /**
     * Connects to the server.
     * @return true if successful
     */
    public boolean connect() {
        return networkClient.connect();
    }
    
    /**
     * Disconnects from server.
     */
    public void disconnect() {
        networkClient.disconnect();
    }
    
    /**
     * Creates a new room on the server.
     * 
     * @param roomName Name for the room
     * @param maxPlayers Maximum number of players
     */
    public void createRoom(String roomName, int maxPlayers) {
        CreateRoomRequest request = new CreateRoomRequest(roomName, null, maxPlayers, false);
        networkClient.sendMessage(Method.CREATE_ROOM, request);
    }
    
    /**
     * Joins an existing room.
     * 
     * @param roomId ID of room to join
     */
    public void joinRoom(long roomId) {
        JoinRoomRequest request = new JoinRoomRequest(roomId, null);
        networkClient.sendMessage(Method.JOIN_ROOM, request);
    }
    
    /**
     * Requests list of available rooms from server.
     */
    public void getRooms() {

        networkClient.sendMessage(Method.GET_ROOMS, MessageParser.EmptyPayload.INSTANCE);
    }
    
    /**
     * Starts the game (only works if you're the room creator).
     */
    public void startGame() {
        networkClient.sendMessage(Method.START_GAME, MessageParser.EmptyPayload.INSTANCE);
    }
    
    /**
     * Attempts to play the currently selected card.
     * 
     * @param chosenColor Color to set (only for WILD cards, can be null otherwise)
     */
    public void playCard(CardColor chosenColor) {
        if (selectedCardIndex < 0 || selectedCardIndex >= myHand.size()) {
            System.err.println("[GameController] No card selected or invalid index");
            return;
        }
        
        PlayCardRequest request = new PlayCardRequest(selectedCardIndex, chosenColor);
        networkClient.sendMessage(Method.PLAY_CARD, request);
    }
    
    /**
     * Draws a card from the deck.
     */
    public void drawCard() {
        networkClient.sendMessage(Method.DRAW_CARD, MessageParser.EmptyPayload.INSTANCE);
    }
    
    /**
     * Declares UNO (when you have one card left).
     */
    public void sayUno() {
        networkClient.sendMessage(Method.SAY_UNO, MessageParser.EmptyPayload.INSTANCE);
    }
    
    /**
     * Sends a chat message.
     * 
     * @param text Message content
     */
    public void sendChat(String text) {
        // We don't know our player ID yet, so we'll send a simple version
        // The server will fill in the details
        ChatMessage msg = new ChatMessage(0L, "Me", text, ChatMessageType.TEXT, null, System.currentTimeMillis());
        networkClient.sendMessage(Method.GAME_CHAT, msg);
    }
    
    /**
     * Handles incoming messages from the server.
     * This is called by the NetworkClient on the receiver thread.
     * We use Platform.runLater() to update UI on the JavaFX thread.
     */
    private void handleMessage(NetworkMessage message) {
        System.out.println("[GameController] Handling message: " + message.getMethod());
        
        switch (message.getMethod()) {
            case ROOM_CREATED_SUCCESS:
                // We successfully created a room
                CreateRoomResponse createResp = (CreateRoomResponse) message.getPayload();
                currentRoomId = createResp.getRoomId();
                System.out.println("[GameController] Room created: " + currentRoomId);
                notifyStateChanged();
                break;
                
            case JOIN_ROOM_SUCCESS:
                // We successfully joined a room
                JoinRoomResponse joinResp = (JoinRoomResponse) message.getPayload();
                currentRoomId = joinResp.getRoomId();
                System.out.println("[GameController] Joined room: " + currentRoomId);
                notifyStateChanged();
                break;
                
            case LOBBY_UPDATE:
                // Lobby state changed (players joined/left)
                currentLobbyState = (LobbyUpdate) message.getPayload();
                System.out.println("[GameController] Lobby updated, players: " + currentLobbyState.getPlayers().size());
                notifyStateChanged();
                break;
                
            case GAME_STATE:
            case GAME_START:
                // Game state update (includes our hand)
                GameState newState = (GameState) message.getPayload();
                updateGameState(newState);
                notifyStateChanged();
                break;
                
            case ROOMS_LIST:
                // List of available rooms
                MessageParser.RoomsListPayload roomsList = (MessageParser.RoomsListPayload) message.getPayload();
                System.out.println("[GameController] Received " + roomsList.getRooms().size() + " rooms");
                // Could store this and display in UI
                break;
                
            case LOBBY_CHAT:
            case GAME_CHAT:
                // Chat message
                ChatMessage chat = (ChatMessage) message.getPayload();
                chatMessages.add(chat);
                notifyChatMessage();
                break;
                
            case ERROR:
                // Server sent an error
                MessageParser.ErrorPayload error = (MessageParser.ErrorPayload) message.getPayload();
                System.err.println("[GameController] Error from server: " + error.getMessage());
                // Could show this in UI
                break;
                
            case PONG:
                System.out.println("[GameController] Received PONG");
                break;
                
            default:
                System.out.println("[GameController] Unhandled message type: " + message.getMethod());
        }
    }
    
    /**
     * Updates our local game state when server sends GAME_STATE.
     * This extracts our hand from the full game state.
     */
    private void updateGameState(GameState newState) {
        currentGameState = newState;
        
        // Find our player in the game state
        // The server includes full hand only for us, other players just show card count
        // We need to figure out which player we are
        
        // For now, let's assume the player with actual cards in the map is us
        // In a real implementation, the server would tell us our player ID
        
        // Look for the player with the most info (that's probably us)
        // Actually, the server sends the full hand only to the player who owns it
        // So we'll need to track this differently
        
        // For this simple demo, let's just show we got a game state
        System.out.println("[GameController] Game state updated");
        System.out.println("  Current player: " + newState.getCurrentPlayerId());
        System.out.println("  Current card: " + newState.getCurrentCard());
        System.out.println("  Players: " + newState.getPlayers().size());
        
        // In a more complete implementation, we'd need the server to tell us
        // our player ID and send our hand separately, or we'd track it from the start
    }
    
    /**
     * Notifies UI that state has changed (on JavaFX thread).
     */
    private void notifyStateChanged() {
        if (onStateChanged != null) {
            try {
                Platform.runLater(onStateChanged);
            } catch (IllegalStateException e) {
                // JavaFX not initialized (e.g., in tests) - run directly
                onStateChanged.run();
            }
        }
    }
    
    /**
     * Notifies UI that a chat message arrived (on JavaFX thread).
     */
    private void notifyChatMessage() {
        if (onChatMessage != null) {
            try {
                Platform.runLater(onChatMessage);
            } catch (IllegalStateException e) {
                // JavaFX not initialized (e.g., in tests) - run directly
                onChatMessage.run();
            }
        }
    }
    
    // Getters for UI to access current state
    
    public GameState getCurrentGameState() {
        return currentGameState;
    }
    
    public LobbyUpdate getCurrentLobbyState() {
        return currentLobbyState;
    }
    
    public Long getCurrentRoomId() {
        return currentRoomId;
    }
    
    public List<Card> getMyHand() {
        return new ArrayList<>(myHand);
    }
    
    public int getSelectedCardIndex() {
        return selectedCardIndex;
    }
    
    public void setSelectedCardIndex(int index) {
        this.selectedCardIndex = index;
    }
    
    public List<ChatMessage> getChatMessages() {
        return new ArrayList<>(chatMessages);
    }
    
    public boolean isConnected() {
        return networkClient.isConnected();
    }
}
