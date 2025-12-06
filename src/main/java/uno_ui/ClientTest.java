package uno_ui;

import uno_proto.common.Method;
import uno_proto.dto.CreateRoomRequest;
import uno_server.protocol.MessageParser;

/**
 * Simple test to verify the UI client code can connect and communicate.
 * This doesn't test the JavaFX UI itself (which requires a display),
 * but verifies the networking logic works.
 */
public class ClientTest {
    
    public static void main(String[] args) {
        System.out.println("=== UNO Client Test ===");
        System.out.println();
        
        // Create a controller
        GameController controller = new GameController();
        
        // Set up listeners
        controller.setOnStateChanged(() -> {
            System.out.println("[Test] State changed!");
            System.out.println("  Room ID: " + controller.getCurrentRoomId());
            System.out.println("  Lobby State: " + controller.getCurrentLobbyState());
            System.out.println("  Game State: " + controller.getCurrentGameState());
        });
        
        controller.setOnChatMessage(() -> {
            System.out.println("[Test] New chat message!");
            System.out.println("  Messages: " + controller.getChatMessages().size());
        });
        
        // Try to connect
        System.out.println("[Test] Connecting to server...");
        boolean connected = controller.connect();
        
        if (!connected) {
            System.err.println("[Test] Failed to connect! Is server running?");
            System.exit(1);
        }
        
        System.out.println("[Test] Connected successfully!");
        System.out.println();
        
        // Wait a bit for connection to stabilize
        sleep(1000);
        
        // Create a room
        System.out.println("[Test] Creating a room...");
        controller.createRoom("Test Room", 2);
        
        // Wait for response
        sleep(2000);
        
        // Check if we're in a room
        if (controller.getCurrentRoomId() != null) {
            System.out.println("[Test] SUCCESS: Joined room " + controller.getCurrentRoomId());
        } else {
            System.out.println("[Test] WARNING: No room ID yet");
        }
        
        System.out.println();
        
        // Try to start game (this should fail since we're alone)
        System.out.println("[Test] Attempting to start game...");
        controller.startGame();
        
        // Wait for response
        sleep(2000);
        
        // Send a chat message
        System.out.println("[Test] Sending chat message...");
        controller.sendChat("Hello from test client!");
        
        // Wait for response
        sleep(1000);
        
        // Disconnect
        System.out.println();
        System.out.println("[Test] Disconnecting...");
        controller.disconnect();
        
        System.out.println("[Test] Test complete!");
        System.out.println();
        System.out.println("If you saw 'Connected successfully' and a room ID, the client works!");
    }
    
    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
