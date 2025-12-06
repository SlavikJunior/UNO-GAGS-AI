# Wire Protocol Implementation

## Overview

This document describes the wire protocol implementation for the UNO game server. The protocol layer converts raw socket text into `NetworkMessage` objects and routes them to the game logic.

## Components

### 1. MessageParser (`uno_server.protocol.MessageParser`)

Handles JSON serialization and deserialization of `NetworkMessage` objects using Gson.

**Features:**
- Custom deserializer for `NetworkMessage` that handles polymorphic `Payload` types
- Based on the `Method` field, deserializes the payload to the correct DTO type
- Provides `toJson()` and `fromJson()` methods

**Payload Classes:**
- `EmptyPayload`: For methods that don't require data (PING, PONG, OK, etc.)
- `ErrorPayload`: For ERROR responses with message and code
- `RoomsListPayload`: For ROOMS_LIST responses containing available rooms

### 2. MessageRouter (`uno_server.protocol.MessageRouter`)

Main controller that routes incoming messages to appropriate handlers.

**State Management:**
- `rooms`: Map of roomId → Room (all active game rooms)
- `players`: Map of userId → PlayerConnection (all connected players)
- `connectionToUserId`: Map of Connection → userId (links sockets to users)

**Supported Methods:**

#### Lobby Methods
- **CREATE_ROOM**: Creates a new game room and assigns the creator
- **GET_ROOMS**: Returns list of available rooms
- **JOIN_ROOM**: Adds a player to an existing room (with password validation)
- **LEAVE_ROOM**: Removes a player from their current room
- **START_GAME**: Starts the game (must be room creator, needs 2+ players)
- **LOBBY_UPDATE**: Broadcasted automatically when room state changes
- **LOBBY_CHAT**: Broadcasts chat messages to all players in lobby

#### Game Methods
- **PLAY_CARD**: Validates and executes card plays
- **DRAW_CARD**: Makes the player draw a card
- **SAY_UNO**: Declares UNO for the player
- **GAME_STATE**: Broadcasted automatically after game actions
- **GAME_CHAT**: Broadcasts chat messages to all players in game

#### System Methods
- **PING**: Keep-alive request
- **PONG**: Keep-alive response
- **OK**: Generic success response
- **ERROR**: Standardized error responses

**Broadcasting:**
- Lobby updates are automatically broadcast to all players in a room
- Game state updates are automatically broadcast after each game action
- Chat messages are broadcast to all players in the room

**Connection Management:**
- Automatic user creation on first message (assigned userId and default username)
- Cleanup on disconnect (removes player from room, cleans up empty rooms)

### 3. Room (`uno_server.protocol.Room`)

Tracks game room state and metadata.

**Properties:**
- Room ID, name, password, max players
- Creator ID and player list
- Room status (WAITING, IN_PROGRESS, FINISHED)

**Features:**
- Thread-safe player management
- Password validation
- Ready status tracking
- Full/empty checks

### 4. PlayerConnection (`uno_server.protocol.PlayerConnection`)

Links user information with socket connections.

**Properties:**
- User ID and username
- Connection object (socket wrapper)
- Ready status
- Current room ID

### 5. Server Integration

The `Server` class has been updated to integrate with the protocol layer:
- Creates a `MessageRouter` instance
- Routes each incoming JSON line to the router
- Handles disconnections through the router
- Maintains clean separation between socket handling and business logic

## Protocol Flow

### Creating and Joining a Room

1. **Client 1**: Sends CREATE_ROOM request
   ```json
   {"id":1,"version":"V1","method":"CREATE_ROOM","payload":{"roomName":"My Room","maxPlayers":4}}
   ```

2. **Server**: Responds with ROOM_CREATED_SUCCESS and broadcasts LOBBY_UPDATE
   ```json
   {"id":2,"version":"V1","method":"ROOM_CREATED_SUCCESS","payload":{"roomId":1,"roomName":"My Room","isSuccessful":true}}
   {"id":3,"version":"V1","method":"LOBBY_UPDATE","payload":{"players":[...],"roomStatus":"WAITING"}}
   ```

3. **Client 2**: Sends JOIN_ROOM request
   ```json
   {"id":1,"version":"V1","method":"JOIN_ROOM","payload":{"roomId":1}}
   ```

4. **Server**: Responds with JOIN_ROOM_SUCCESS and broadcasts LOBBY_UPDATE to all players
   ```json
   {"id":4,"version":"V1","method":"JOIN_ROOM_SUCCESS","payload":{"roomId":1,"isSuccessful":true}}
   {"id":5,"version":"V1","method":"LOBBY_UPDATE","payload":{"players":[...],"roomStatus":"WAITING"}}
   ```

### Starting a Game

1. **Client 1** (room creator): Sends START_GAME request
   ```json
   {"id":2,"version":"V1","method":"START_GAME","payload":{}}
   ```

2. **Server**: Creates game session and broadcasts GAME_START with initial state
   ```json
   {"id":6,"version":"V1","method":"GAME_START","payload":{"roomId":1,"players":{...},"currentCard":{...}}}
   ```

### Playing Cards

1. **Client**: Sends PLAY_CARD request
   ```json
   {"id":3,"version":"V1","method":"PLAY_CARD","payload":{"cardIndex":0,"chosenColor":"RED"}}
   ```

2. **Server**: Validates and broadcasts updated GAME_STATE to all players
   ```json
   {"id":7,"version":"V1","method":"GAME_STATE","payload":{"roomId":1,"players":{...},"currentCard":{...}}}
   ```

3. **On error**: Server sends ERROR response
   ```json
   {"id":8,"version":"V1","method":"ERROR","payload":{"message":"Cannot play this card","code":"INVALID_PLAY"}}
   ```

## Testing

### Automated Test

Run the protocol test to verify all functionality:
```bash
./test_protocol.sh
```

This test demonstrates:
- Two clients connecting
- Creating and joining a room
- Starting a game
- Playing and drawing cards
- Error handling
- PING/PONG keepalive

### Manual Testing

Start the server:
```bash
mvn compile
mvn dependency:build-classpath -Dmdep.outputFile=cp.txt
java -cp "target/classes:$(cat cp.txt)" uno_server.ServerLauncher
```

Connect with a telnet-like client and send JSON messages. See README.md for detailed examples.

## Error Handling

All errors are returned using the ERROR method with descriptive messages and error codes:

**Common Error Codes:**
- `UNSUPPORTED_METHOD`: Unknown method
- `PROCESSING_ERROR`: General processing error
- `ROOM_NOT_FOUND`: Room doesn't exist
- `ROOM_FULL`: Room has reached max players
- `INVALID_PASSWORD`: Incorrect room password
- `NOT_IN_ROOM`: Player not in a room
- `NOT_CREATOR`: Action requires room creator
- `NOT_ENOUGH_PLAYERS`: Need more players to start
- `INVALID_PLAY`: Invalid card play
- `INVALID_DRAW`: Invalid draw action

## Dependencies

- **Gson 2.10.1**: JSON serialization/deserialization
- **Kotlin Standard Library**: For Kotlin DTO support
- Existing game engine classes (GameSessionManager, GameSession, etc.)

## Implementation Notes

- All DTOs now implement the `Payload` interface for proper serialization
- User IDs are automatically assigned on first connection
- Rooms are automatically cleaned up when empty
- Thread-safe collections are used for concurrent access
- Comprehensive logging for debugging
- Clear separation between protocol layer and game logic
