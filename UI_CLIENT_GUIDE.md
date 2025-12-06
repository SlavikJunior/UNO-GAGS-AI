# UNO JavaFX Client Guide

This document provides detailed information about the JavaFX graphical client for the UNO game.

## Overview

The JavaFX UI client provides a graphical interface for playing UNO. It connects to the UNO server via TCP sockets and uses the same JSON protocol as the command-line clients.

## Architecture

The UI client is organized into three main classes:

### 1. NetworkClient
- **Purpose**: Handles all network communication with the server
- **Features**:
  - Runs on background threads to avoid blocking the UI
  - Sends and receives JSON messages
  - Uses blocking queues for thread-safe message passing
  - Provides callback mechanism for incoming messages
- **Location**: `src/main/java/uno_ui/NetworkClient.java`

### 2. GameController
- **Purpose**: Acts as the "brain" of the application
- **Features**:
  - Manages game state (lobby, game, chat)
  - Provides high-level methods for UI actions
  - Bridges NetworkClient and UI components
  - Processes server messages and updates state
  - Thread-safe notification of UI via callbacks
- **Location**: `src/main/java/uno_ui/GameController.java`

### 3. MainApp
- **Purpose**: The JavaFX Application that creates the UI
- **Features**:
  - Builds the complete UI layout
  - Handles user interactions (button clicks, text input)
  - Updates display based on controller state
  - Shows dialogs for user input
- **Location**: `src/main/java/uno_ui/MainApp.java`

## UI Components

### Status Bar
- Shows connection status
- Displays current room ID and player count
- Shows game phase and whose turn it is

### Room Controls
- **Create Room**: Opens dialog to create a new game room
- **Join Room**: Opens dialog to join an existing room by ID
- **Get Rooms**: Requests list of available rooms from server

### Current Card Display
- Shows the card currently on the table
- Color-coded based on card color
- Updates automatically when cards are played

### Player Hand
- Displays your cards as clickable buttons
- Click a card to select it (highlights in yellow)
- Only one card can be selected at a time
- Initially shows placeholder cards for demonstration

### Action Buttons
- **Play Selected Card**: Plays the currently selected card
  - Disabled until you select a card
  - For WILD cards, should prompt for color (future enhancement)
- **Draw Card**: Draws a card from the deck
  - Use when you can't play or choose not to play
- **Say UNO!**: Declares UNO when you have one card left
  - Important to avoid penalties!
- **Start Game**: Starts the game (green button)
  - Only works if you're the room creator
  - Requires at least 2 players

### Chat Area
- **Message display**: Shows all chat messages
- **Input field**: Type your message here
- **Send button**: Sends your message (or press Enter)

## How to Use

### Prerequisites
1. Java 8 or higher installed
2. Maven installed
3. Server must be running

### Starting the Server
```bash
# Terminal 1: Start the server
cd /path/to/project
mvn compile
java -cp "target/classes:$(cat cp.txt)" uno_server.ServerLauncher
```

The server will start on port 9090.

### Starting the UI Client
```bash
# Terminal 2: Start the UI
cd /path/to/project
mvn exec:java -Dexec.mainClass=uno_ui.MainApp

# OR use the launcher script:
./launch_ui.sh
```

### Playing a Game

1. **First player creates a room**:
   - Click "Create Room"
   - Enter a room name
   - Click OK
   - Status bar will show your room ID

2. **Other players join**:
   - Click "Join Room"
   - Enter the room ID (usually 1)
   - Click OK
   - Status bar will show player count

3. **Start the game** (room creator only):
   - Wait for at least 2 players
   - Click "Start Game"
   - Cards will be dealt to all players

4. **Take your turn**:
   - Click one of your cards to select it
   - If it's valid, click "Play Selected Card"
   - If you can't play, click "Draw Card"
   - Watch the current card display to see what's played

5. **Say UNO**:
   - When you have one card left, click "Say UNO!"
   - Don't forget or you may be penalized

6. **Chat with players**:
   - Type a message in the chat input
   - Press Enter or click Send

## Technical Details

### Threading Model
- **Main Thread**: JavaFX UI thread (handles all UI updates)
- **Sender Thread**: Sends queued messages to server
- **Receiver Thread**: Receives and parses messages from server

The NetworkClient uses callbacks to notify the GameController, which uses `Platform.runLater()` to safely update the UI from the receiver thread.

### Message Flow
1. User clicks button in UI
2. MainApp calls method on GameController
3. GameController creates NetworkMessage
4. NetworkClient queues message for sending
5. Sender thread sends JSON to server
6. Server processes and responds
7. Receiver thread receives JSON response
8. NetworkClient parses and notifies GameController
9. GameController updates state and notifies UI
10. MainApp updates display on JavaFX thread

### Protocol
The client uses the same JSON protocol as documented in the main README:
- Each message is a JSON object with `id`, `version`, `method`, `payload`, `timestamp`
- Messages are sent line-by-line (one JSON object per line)
- The MessageParser class handles serialization/deserialization

### Error Handling
- Connection failures are shown in alert dialogs
- Server errors are logged to console
- Invalid actions (e.g., play card without selection) show warning dialogs

## Testing Without UI

You can test the networking logic without launching the full UI:

```bash
mvn compile
java -cp "target/classes:$(cat cp.txt)" uno_ui.ClientTest
```

This runs a simple test that connects, creates a room, and sends messages.

## Future Enhancements

Potential improvements for the UI:

1. **Display actual hand**: Currently shows placeholder cards; should show real cards from GameState
2. **Color picker for WILD cards**: Add dialog to choose color when playing WILD
3. **Better error display**: Show server errors in UI instead of just console
4. **Room list view**: Display available rooms in a table/list
5. **Player list**: Show all players and their card counts
6. **Animation**: Animate card plays and draws
7. **Sound effects**: Add sounds for actions
8. **Better card graphics**: Use images instead of text buttons
9. **Game history**: Show log of recent plays
10. **Disconnection handling**: Better handling when server goes down

## Troubleshooting

### "Connection Failed" error
- Make sure the server is running on localhost:9090
- Check firewall settings
- Verify server log shows "Server started on port 9090"

### UI doesn't launch
- Make sure JavaFX dependencies are properly installed
- Try running `mvn clean compile` first
- Check Java version is 8 or higher

### Cards don't update
- This is expected in the current version (shows placeholder cards)
- The networking works; the UI just needs enhancement to display real cards
- Check console output to verify messages are being received

### "Toolkit not initialized" error
- This only occurs in ClientTest, not in actual UI
- It's handled gracefully with try/catch
- No action needed

## Code Style

The code is written with students in mind:
- **Extensive comments**: Every method and class is documented
- **Simple patterns**: No complex frameworks or patterns
- **Linear flow**: Easy to follow execution path
- **Educational**: Shows good practices for threading, networking, and UI

Feel free to modify and enhance the code as a learning exercise!
