# Прочитай это!!!!

## Здесь будут некоторые заметки по проекту, пока не наладим таск-треккер

# Главные правила леса:
### Не забывай спулить!
### Главное - общаться!
### Читай код!
### Если что то случилось гитом и всё полетело, сразу сообщаем друг другу и решаем вместе

# Коротко о структуре:
#### UNO-CLIENT - модуль клиента (JavaFX UI в uno_ui пакете)
#### UNO-SERVER - модуль сервера
#### UNO-PROTO - модуль протокола

#### Запусти код в Playground для проверки как работает вообще всё

## Running the JavaFX UI Client

The project now includes a simple JavaFX graphical client that connects to the UNO server.

### Prerequisites

1. Make sure the server is running first:
```bash
mvn compile
java -cp target/classes uno_server.ServerLauncher
```

2. In a separate terminal, launch the UI client:
```bash
mvn exec:java -Dexec.mainClass=uno_ui.MainApp
```

### Using the UI

The client window provides:
- **Status bar** - Shows connection status, room info, and current game state
- **Room controls** - Buttons to create room, join room, or get room list
- **Current card display** - Shows the card currently on the table
- **Your hand** - Displays your cards as clickable buttons (placeholder cards shown initially)
- **Action buttons**:
  - **Play Selected Card** - Plays the card you've selected
  - **Draw Card** - Draws a card from the deck
  - **Say UNO!** - Declares UNO when you have one card left
  - **Start Game** - Starts the game (only works if you're the room creator)
- **Chat area** - Shows chat messages and allows you to send messages

### Typical Workflow

1. Start the server in one terminal
2. Launch one or more UI clients
3. In the first client, click "Create Room" and enter a room name
4. In other clients, click "Join Room" and enter the room ID (usually 1)
5. In the room creator's client, click "Start Game"
6. Players take turns clicking cards to play them or clicking "Draw Card"
7. When you have one card left, click "Say UNO!"

### Technical Notes

- The UI uses a background thread for network communication to avoid blocking
- All server messages are logged to console for debugging
- The client uses the same JSON protocol and DTOs as the server
- Error messages from the server are logged but not yet displayed in UI (can be enhanced)

## Wire Protocol Testing

The server now supports JSON-based wire protocol communication on port 9090. You can test the protocol manually using telnet or netcat.

### Starting the Server

```bash
# Start the UNO server (it will listen on port 9090)
mvn compile
java -cp target/classes uno_server.ServerLauncher
```

### Manual Testing with netcat/telnet

Connect to the server using netcat (nc) or telnet:
```bash
nc localhost 9090
# or
telnet localhost 9090
```

### Message Format

All messages are JSON objects with the following structure:
```json
{
  "id": 1,
  "version": "V1",
  "method": "METHOD_NAME",
  "payload": { ... },
  "timestamp": 1234567890
}
```

### Example Test Flow

#### 1. Test PING/PONG
```json
{"id":1,"version":"V1","method":"PING","payload":{}}
```
Expected response:
```json
{"id":1,"version":"V1","method":"PONG","payload":{}}
```

#### 2. Create a Room (Client 1)
```json
{"id":2,"version":"V1","method":"CREATE_ROOM","payload":{"roomName":"Test Room","maxPlayers":4,"allowStuck":false}}
```
Expected response:
```json
{"id":2,"version":"V1","method":"ROOM_CREATED_SUCCESS","payload":{"roomId":1,"roomName":"Test Room","isSuccessful":true}}
{"id":3,"version":"V1","method":"LOBBY_UPDATE","payload":{"players":[{"userId":1,"username":"Player1","isOwner":true,"isReady":false}],"roomStatus":"WAITING"}}
```

#### 3. Get Available Rooms
```json
{"id":3,"version":"V1","method":"GET_ROOMS","payload":{}}
```
Expected response:
```json
{"id":4,"version":"V1","method":"ROOMS_LIST","payload":{"rooms":[{"roomId":1,"roomName":"Test Room","hasPassword":false,"maxPlayers":4,"currentPlayers":1,"status":"WAITING","creatorName":"Player1"}]}}
```

#### 4. Join Room (Client 2)
```json
{"id":4,"version":"V1","method":"JOIN_ROOM","payload":{"roomId":1}}
```
Expected response:
```json
{"id":5,"version":"V1","method":"JOIN_ROOM_SUCCESS","payload":{"roomId":1,"isSuccessful":true}}
{"id":6,"version":"V1","method":"LOBBY_UPDATE","payload":{"players":[{"userId":1,"username":"Player1","isOwner":true,"isReady":false},{"userId":2,"username":"Player2","isOwner":false,"isReady":false}],"roomStatus":"WAITING"}}
```

#### 5. Start Game (Client 1 - room creator)
```json
{"id":5,"version":"V1","method":"START_GAME","payload":{}}
```
Expected response: GAME_START with initial game state including dealt cards

#### 6. Play a Card
```json
{"id":6,"version":"V1","method":"PLAY_CARD","payload":{"cardIndex":0}}
```
Expected response: GAME_STATE with updated game state

#### 7. Draw a Card
```json
{"id":7,"version":"V1","method":"DRAW_CARD","payload":{}}
```
Expected response: GAME_STATE with updated game state

#### 8. Declare UNO
```json
{"id":8,"version":"V1","method":"SAY_UNO","payload":{}}
```
Expected response: GAME_STATE with UNO status updated

### Two-Socket Test Script

Open two terminals and connect both to the server:

**Terminal 1 (Player 1):**
```bash
nc localhost 9090
{"id":1,"version":"V1","method":"CREATE_ROOM","payload":{"roomName":"Game Room","maxPlayers":2,"allowStuck":false}}
```

**Terminal 2 (Player 2):**
```bash
nc localhost 9090
{"id":1,"version":"V1","method":"JOIN_ROOM","payload":{"roomId":1}}
```

**Terminal 1:**
```bash
{"id":2,"version":"V1","method":"START_GAME","payload":{}}
```

Now both clients will receive GAME_STATE updates as players take turns playing cards and drawing.

### Error Handling

If a request fails, the server will respond with an ERROR message:
```json
{"id":n,"version":"V1","method":"ERROR","payload":{"message":"Error description","code":"ERROR_CODE"}}
```