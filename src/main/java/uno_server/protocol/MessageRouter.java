package uno_server.protocol;

import uno_proto.common.*;
import uno_proto.dto.*;
import uno_server.common.Connection;
import uno_server.game.GameSession;
import uno_server.game.GameSessionManager;
import uno_server.game.PlayerState;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * MessageRouter handles all incoming network messages and routes them to appropriate handlers.
 * It maintains the state of all rooms, players, and connections in the server.
 * 
 * This is the main controller that integrates the protocol layer with the game logic.
 */
public class MessageRouter {

    private static final Logger logger = Logger.getLogger(MessageRouter.class.getName());

    private final MessageParser parser;
    private final GameSessionManager gameManager;
    
    // In-memory storage
    private final Map<Long, Room> rooms;                          // roomId -> Room
    private final Map<Long, PlayerConnection> players;            // userId -> PlayerConnection
    private final Map<Connection, Long> connectionToUserId;       // Connection -> userId
    private final AtomicLong roomIdGenerator;
    private final AtomicLong userIdGenerator;
    private final AtomicLong messageIdGenerator;

    public MessageRouter() {
        this.parser = new MessageParser();
        this.gameManager = new GameSessionManager();
        this.rooms = new ConcurrentHashMap<>();
        this.players = new ConcurrentHashMap<>();
        this.connectionToUserId = new ConcurrentHashMap<>();
        this.roomIdGenerator = new AtomicLong(1);
        this.userIdGenerator = new AtomicLong(1);
        this.messageIdGenerator = new AtomicLong(1);
    }

    /**
     * Routes an incoming message to the appropriate handler.
     * 
     * @param connection The connection that sent the message
     * @param rawMessage The raw JSON message string
     */
    public void routeMessage(Connection connection, String rawMessage) {
        try {
            // Parse the incoming message
            NetworkMessage message = parser.fromJson(rawMessage);
            logger.log(Level.INFO, "Received message: " + message.getMethod() + " from " + connection.getRemoteAddress());

            // Get or create user ID for this connection
            Long userId = connectionToUserId.get(connection);

            // Route based on method
            switch (message.getMethod()) {
                case CREATE_ROOM:
                    handleCreateRoom(connection, message, userId);
                    break;
                case GET_ROOMS:
                    handleGetRooms(connection, message);
                    break;
                case JOIN_ROOM:
                    handleJoinRoom(connection, message, userId);
                    break;
                case LEAVE_ROOM:
                    handleLeaveRoom(connection, message, userId);
                    break;
                case START_GAME:
                    handleStartGame(connection, message, userId);
                    break;
                case PLAY_CARD:
                    handlePlayCard(connection, message, userId);
                    break;
                case DRAW_CARD:
                    handleDrawCard(connection, message, userId);
                    break;
                case SAY_UNO:
                    handleSayUno(connection, message, userId);
                    break;
                case PING:
                    handlePing(connection, message);
                    break;
                case LOBBY_CHAT:
                    handleLobbyChat(connection, message, userId);
                    break;
                case GAME_CHAT:
                    handleGameChat(connection, message, userId);
                    break;
                default:
                    sendError(connection, "Unsupported method: " + message.getMethod(), "UNSUPPORTED_METHOD");
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error routing message", e);
            sendError(connection, "Error processing message: " + e.getMessage(), "PROCESSING_ERROR");
        }
    }

    /**
     * Handles CREATE_ROOM requests.
     * Creates a new game room and assigns the requesting user as the creator.
     */
    private void handleCreateRoom(Connection connection, NetworkMessage message, Long userId) {
        try {
            CreateRoomRequest request = (CreateRoomRequest) message.getPayload();
            
            // Create user if not exists
            if (userId == null) {
                userId = userIdGenerator.getAndIncrement();
                String username = "Player" + userId;
                PlayerConnection player = new PlayerConnection(userId, username, connection);
                players.put(userId, player);
                connectionToUserId.put(connection, userId);
            }

            // Create the room
            long roomId = roomIdGenerator.getAndIncrement();
            Room room = new Room(
                roomId,
                request.getRoomName(),
                request.getPassword(),
                request.getMaxPlayers(),
                request.getAllowStuck(),
                userId
            );
            rooms.put(roomId, room);

            // Add creator to the room
            PlayerConnection creator = players.get(userId);
            room.addPlayer(creator);
            creator.setCurrentRoomId(roomId);

            // Send success response
            CreateRoomResponse response = new CreateRoomResponse(roomId, request.getRoomName(), true);
            sendMessage(connection, Method.ROOM_CREATED_SUCCESS, response);

            // Send initial lobby update
            broadcastLobbyUpdate(roomId);

            logger.log(Level.INFO, "Room created: " + roomId + " by user " + userId);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error creating room", e);
            sendError(connection, "Failed to create room: " + e.getMessage(), "CREATE_ROOM_ERROR");
        }
    }

    /**
     * Handles GET_ROOMS requests.
     * Returns a list of all available rooms.
     */
    private void handleGetRooms(Connection connection, NetworkMessage message) {
        try {
            List<RoomInfo> roomList = rooms.values().stream()
                .map(room -> {
                    PlayerConnection creator = players.get(room.getCreatorId());
                    String creatorName = creator != null ? creator.getUsername() : "Unknown";
                    return new RoomInfo(
                        room.getRoomId(),
                        room.getRoomName(),
                        room.hasPassword(),
                        room.getMaxPlayers(),
                        room.getCurrentPlayerCount(),
                        room.getStatus(),
                        creatorName
                    );
                })
                .collect(Collectors.toList());

            MessageParser.RoomsListPayload payload = new MessageParser.RoomsListPayload(roomList);
            sendMessage(connection, Method.ROOMS_LIST, payload);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error getting rooms", e);
            sendError(connection, "Failed to get rooms: " + e.getMessage(), "GET_ROOMS_ERROR");
        }
    }

    /**
     * Handles JOIN_ROOM requests.
     * Adds a player to an existing room.
     */
    private void handleJoinRoom(Connection connection, NetworkMessage message, Long userId) {
        try {
            JoinRoomRequest request = (JoinRoomRequest) message.getPayload();
            Room room = rooms.get(request.getRoomId());

            if (room == null) {
                sendError(connection, "Room not found", "ROOM_NOT_FOUND");
                return;
            }

            // Check if room is full
            if (room.isFull()) {
                sendError(connection, "Room is full", "ROOM_FULL");
                return;
            }

            // Check password
            if (room.hasPassword() && !room.getPassword().equals(request.getPassword())) {
                sendError(connection, "Invalid password", "INVALID_PASSWORD");
                return;
            }

            // Create user if not exists
            if (userId == null) {
                userId = userIdGenerator.getAndIncrement();
                String username = "Player" + userId;
                PlayerConnection player = new PlayerConnection(userId, username, connection);
                players.put(userId, player);
                connectionToUserId.put(connection, userId);
            }

            // Add player to room
            PlayerConnection player = players.get(userId);
            if (room.addPlayer(player)) {
                player.setCurrentRoomId(request.getRoomId());

                // Send success response
                JoinRoomResponse response = new JoinRoomResponse(request.getRoomId(), true);
                sendMessage(connection, Method.JOIN_ROOM_SUCCESS, response);

                // Broadcast lobby update to all players in room
                broadcastLobbyUpdate(request.getRoomId());

                logger.log(Level.INFO, "User " + userId + " joined room " + request.getRoomId());
            } else {
                sendError(connection, "Failed to join room", "JOIN_FAILED");
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error joining room", e);
            sendError(connection, "Failed to join room: " + e.getMessage(), "JOIN_ROOM_ERROR");
        }
    }

    /**
     * Handles LEAVE_ROOM requests.
     * Removes a player from their current room.
     */
    private void handleLeaveRoom(Connection connection, NetworkMessage message, Long userId) {
        if (userId == null) {
            sendError(connection, "User not found", "USER_NOT_FOUND");
            return;
        }

        try {
            PlayerConnection player = players.get(userId);
            Long roomId = player.getCurrentRoomId();

            if (roomId == null) {
                sendError(connection, "Not in a room", "NOT_IN_ROOM");
                return;
            }

            Room room = rooms.get(roomId);
            if (room != null) {
                room.removePlayer(userId);
                player.setCurrentRoomId(null);
                player.setReady(false);

                // If room is empty, remove it
                if (room.getCurrentPlayerCount() == 0) {
                    rooms.remove(roomId);
                    gameManager.removeSession(roomId);
                } else {
                    // Broadcast updated lobby state
                    broadcastLobbyUpdate(roomId);
                }

                sendMessage(connection, Method.OK, MessageParser.EmptyPayload.INSTANCE);
                logger.log(Level.INFO, "User " + userId + " left room " + roomId);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error leaving room", e);
            sendError(connection, "Failed to leave room: " + e.getMessage(), "LEAVE_ROOM_ERROR");
        }
    }

    /**
     * Handles START_GAME requests.
     * Starts the game if all players are ready.
     */
    private void handleStartGame(Connection connection, NetworkMessage message, Long userId) {
        if (userId == null) {
            sendError(connection, "User not found", "USER_NOT_FOUND");
            return;
        }

        try {
            PlayerConnection player = players.get(userId);
            Long roomId = player.getCurrentRoomId();

            if (roomId == null) {
                sendError(connection, "Not in a room", "NOT_IN_ROOM");
                return;
            }

            Room room = rooms.get(roomId);
            if (room == null) {
                sendError(connection, "Room not found", "ROOM_NOT_FOUND");
                return;
            }

            // Only room creator can start the game
            if (room.getCreatorId() != userId) {
                sendError(connection, "Only room creator can start the game", "NOT_CREATOR");
                return;
            }

            // Need at least 2 players
            if (room.getCurrentPlayerCount() < 2) {
                sendError(connection, "Need at least 2 players to start", "NOT_ENOUGH_PLAYERS");
                return;
            }

            // Create game session
            List<PlayerState> gamePlayers = room.getPlayers().stream()
                .map(p -> new PlayerState(p.getUserId(), p.getUsername()))
                .collect(Collectors.toList());

            GameSession session = gameManager.createSession(roomId, gamePlayers);
            room.setStatus(RoomStatus.IN_PROGRESS);

            // Broadcast game state to all players in room
            GameState gameState = session.getGameState();
            broadcastToRoom(roomId, Method.GAME_START, gameState);

            logger.log(Level.INFO, "Game started in room " + roomId);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error starting game", e);
            sendError(connection, "Failed to start game: " + e.getMessage(), "START_GAME_ERROR");
        }
    }

    /**
     * Handles PLAY_CARD requests.
     * Validates and executes a card play action.
     */
    private void handlePlayCard(Connection connection, NetworkMessage message, Long userId) {
        if (userId == null) {
            sendError(connection, "User not found", "USER_NOT_FOUND");
            return;
        }

        try {
            PlayCardRequest request = (PlayCardRequest) message.getPayload();
            PlayerConnection player = players.get(userId);
            Long roomId = player.getCurrentRoomId();

            if (roomId == null) {
                sendError(connection, "Not in a room", "NOT_IN_ROOM");
                return;
            }

            // Play the card
            GameState gameState = gameManager.playCard(roomId, userId, request.getCardIndex(), request.getChosenColor());

            // Broadcast updated game state to all players in room
            broadcastToRoom(roomId, Method.GAME_STATE, gameState);

            logger.log(Level.INFO, "User " + userId + " played card in room " + roomId);
        } catch (IllegalStateException | IllegalArgumentException e) {
            logger.log(Level.WARNING, "Invalid card play", e);
            sendError(connection, e.getMessage(), "INVALID_PLAY");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error playing card", e);
            sendError(connection, "Failed to play card: " + e.getMessage(), "PLAY_CARD_ERROR");
        }
    }

    /**
     * Handles DRAW_CARD requests.
     * Makes the player draw a card.
     */
    private void handleDrawCard(Connection connection, NetworkMessage message, Long userId) {
        if (userId == null) {
            sendError(connection, "User not found", "USER_NOT_FOUND");
            return;
        }

        try {
            PlayerConnection player = players.get(userId);
            Long roomId = player.getCurrentRoomId();

            if (roomId == null) {
                sendError(connection, "Not in a room", "NOT_IN_ROOM");
                return;
            }

            // Draw a card
            GameState gameState = gameManager.drawCard(roomId, userId);

            // Broadcast updated game state to all players in room
            broadcastToRoom(roomId, Method.GAME_STATE, gameState);

            logger.log(Level.INFO, "User " + userId + " drew a card in room " + roomId);
        } catch (IllegalStateException | IllegalArgumentException e) {
            logger.log(Level.WARNING, "Invalid draw", e);
            sendError(connection, e.getMessage(), "INVALID_DRAW");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error drawing card", e);
            sendError(connection, "Failed to draw card: " + e.getMessage(), "DRAW_CARD_ERROR");
        }
    }

    /**
     * Handles SAY_UNO requests.
     * Declares UNO for the player.
     */
    private void handleSayUno(Connection connection, NetworkMessage message, Long userId) {
        if (userId == null) {
            sendError(connection, "User not found", "USER_NOT_FOUND");
            return;
        }

        try {
            PlayerConnection player = players.get(userId);
            Long roomId = player.getCurrentRoomId();

            if (roomId == null) {
                sendError(connection, "Not in a room", "NOT_IN_ROOM");
                return;
            }

            // Say UNO
            GameState gameState = gameManager.sayUno(roomId, userId);

            // Broadcast updated game state to all players in room
            broadcastToRoom(roomId, Method.GAME_STATE, gameState);

            logger.log(Level.INFO, "User " + userId + " said UNO in room " + roomId);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error saying UNO", e);
            sendError(connection, "Failed to say UNO: " + e.getMessage(), "SAY_UNO_ERROR");
        }
    }

    /**
     * Handles PING requests.
     * Responds with PONG.
     */
    private void handlePing(Connection connection, NetworkMessage message) {
        sendMessage(connection, Method.PONG, MessageParser.EmptyPayload.INSTANCE);
    }

    /**
     * Handles LOBBY_CHAT requests.
     * Broadcasts chat messages to all players in the lobby.
     */
    private void handleLobbyChat(Connection connection, NetworkMessage message, Long userId) {
        if (userId == null) {
            sendError(connection, "User not found", "USER_NOT_FOUND");
            return;
        }

        try {
            ChatMessage chatMessage = (ChatMessage) message.getPayload();
            PlayerConnection player = players.get(userId);
            Long roomId = player.getCurrentRoomId();

            if (roomId != null) {
                broadcastToRoom(roomId, Method.LOBBY_CHAT, chatMessage);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error handling lobby chat", e);
        }
    }

    /**
     * Handles GAME_CHAT requests.
     * Broadcasts chat messages to all players in the game.
     */
    private void handleGameChat(Connection connection, NetworkMessage message, Long userId) {
        if (userId == null) {
            sendError(connection, "User not found", "USER_NOT_FOUND");
            return;
        }

        try {
            ChatMessage chatMessage = (ChatMessage) message.getPayload();
            PlayerConnection player = players.get(userId);
            Long roomId = player.getCurrentRoomId();

            if (roomId != null) {
                broadcastToRoom(roomId, Method.GAME_CHAT, chatMessage);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error handling game chat", e);
        }
    }

    /**
     * Broadcasts a lobby update to all players in a room.
     */
    private void broadcastLobbyUpdate(long roomId) {
        Room room = rooms.get(roomId);
        if (room == null) return;

        List<PlayerInfo> playerInfos = room.getPlayers().stream()
            .map(p -> new PlayerInfo(
                p.getUserId(),
                p.getUsername(),
                p.getUserId() == room.getCreatorId(),
                p.isReady()
            ))
            .collect(Collectors.toList());

        LobbyUpdate update = new LobbyUpdate(playerInfos, room.getStatus());
        broadcastToRoom(roomId, Method.LOBBY_UPDATE, update);
    }

    /**
     * Broadcasts a message to all players in a room.
     */
    private void broadcastToRoom(long roomId, Method method, Payload payload) {
        Room room = rooms.get(roomId);
        if (room == null) return;

        for (PlayerConnection player : room.getPlayers()) {
            sendMessage(player.getConnection(), method, payload);
        }
    }

    /**
     * Sends a message to a connection.
     */
    private void sendMessage(Connection connection, Method method, Payload payload) {
        try {
            NetworkMessage message = new NetworkMessage(
                messageIdGenerator.getAndIncrement(),
                Version.V1,
                method,
                payload,
                System.currentTimeMillis()
            );
            String json = parser.toJson(message);
            connection.sendLine(json);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error sending message", e);
        }
    }

    /**
     * Sends an error message to a connection.
     */
    private void sendError(Connection connection, String errorMessage, String errorCode) {
        MessageParser.ErrorPayload payload = new MessageParser.ErrorPayload(errorMessage, errorCode);
        sendMessage(connection, Method.ERROR, payload);
    }

    /**
     * Handles connection disconnection.
     * Removes the player from their room and cleans up resources.
     */
    public void handleDisconnect(Connection connection) {
        Long userId = connectionToUserId.remove(connection);
        if (userId != null) {
            PlayerConnection player = players.remove(userId);
            if (player != null) {
                Long roomId = player.getCurrentRoomId();
                if (roomId != null) {
                    Room room = rooms.get(roomId);
                    if (room != null) {
                        room.removePlayer(userId);
                        
                        // If room is empty, remove it
                        if (room.getCurrentPlayerCount() == 0) {
                            rooms.remove(roomId);
                            gameManager.removeSession(roomId);
                        } else {
                            // Broadcast updated lobby state
                            broadcastLobbyUpdate(roomId);
                        }
                    }
                }
            }
            logger.log(Level.INFO, "User " + userId + " disconnected and cleaned up");
        }
    }
}
