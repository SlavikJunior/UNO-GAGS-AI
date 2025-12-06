# UNO Game - Quick Start Guide

## For Users Who Just Want to Play

### Step 1: Start the Server
Open a terminal and run:
```bash
cd /path/to/UNO-GAGS
mvn compile
java -cp "target/classes:$(cat cp.txt)" uno_server.ServerLauncher
```

Keep this terminal open - the server is now running on port 9090.

### Step 2: Launch the UI Client
Open a NEW terminal and run:
```bash
cd /path/to/UNO-GAGS
./launch_ui.sh
```

A window will open showing the UNO game client.

### Step 3: Create or Join a Game

**First player (creates the game):**
1. Click "Create Room"
2. Enter a room name (e.g., "My Game")
3. Click OK
4. Note the Room ID shown in the status bar

**Other players (join the game):**
1. Click "Join Room"
2. Enter the Room ID (usually 1)
3. Click OK

### Step 4: Start Playing

1. Once at least 2 players have joined, the room creator clicks "Start Game"
2. Cards are dealt to all players
3. Players take turns:
   - Click a card in your hand to select it
   - Click "Play Selected Card" to play it
   - Or click "Draw Card" if you can't play
4. When you have one card left, click "Say UNO!"
5. Use the chat box to talk with other players

## Common Issues

**"Connection Failed"**
- Make sure the server is running (Step 1)
- Check that you see "Server started on port 9090" in the server terminal

**"Can't start game"**
- You need at least 2 players
- Only the room creator can start the game

**Cards don't update**
- The current version shows placeholder cards
- The networking works fine; this is just a visual simplification
- Check the console output to see actual game state

## For Developers

See these files for more details:
- `README.md` - Complete project documentation
- `UI_CLIENT_GUIDE.md` - Detailed client architecture
- `IMPLEMENTATION_SUMMARY.md` - What was built and how

## Architecture Overview

```
┌─────────────────┐
│   MainApp.java  │  ← JavaFX UI (buttons, labels, etc.)
│   (JavaFX UI)   │
└────────┬────────┘
         │
         ↓
┌─────────────────┐
│GameController   │  ← Game logic and state management
│  .java          │
└────────┬────────┘
         │
         ↓
┌─────────────────┐
│NetworkClient    │  ← TCP socket communication
│  .java          │     (background threads)
└────────┬────────┘
         │
         ↓ JSON over TCP
┌─────────────────┐
│   UNO Server    │  ← Server on port 9090
│  (ServerLauncher)│
└─────────────────┘
```

## File Locations

### UI Client Code
- `src/main/java/uno_ui/MainApp.java` - Main UI
- `src/main/java/uno_ui/GameController.java` - Game logic
- `src/main/java/uno_ui/NetworkClient.java` - Networking
- `src/main/java/uno_ui/ClientTest.java` - Testing

### Server Code
- `src/main/java/uno_server/` - Server implementation
- `src/main/java/uno_proto/` - Protocol definitions

### Configuration
- `pom.xml` - Maven build configuration (includes JavaFX)

## Testing Without UI

To test the networking without launching the full UI:
```bash
mvn compile
java -cp "target/classes:$(cat cp.txt)" uno_ui.ClientTest
```

This will connect to the server and test basic operations.

## Next Steps

Want to enhance the client? Check out these ideas:
1. Display real cards instead of placeholders
2. Add a color picker dialog for WILD cards
3. Show error messages in the UI (not just console)
4. Add a room list view
5. Implement animations for card plays
6. Add sound effects
7. Use card images instead of text buttons

See `UI_CLIENT_GUIDE.md` for more enhancement ideas.

## Help

**Documentation:**
- Basic usage: This file
- Complete guide: `UI_CLIENT_GUIDE.md`
- Technical details: `IMPLEMENTATION_SUMMARY.md`
- Server protocol: `README.md`

**Testing:**
- Test client: `uno_ui.ClientTest`
- Test protocol: `./test_protocol.sh`

Enjoy playing UNO!
