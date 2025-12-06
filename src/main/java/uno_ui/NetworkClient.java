package uno_ui;

import uno_proto.common.*;
import uno_proto.dto.*;
import uno_server.protocol.MessageParser;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

/**
 * NetworkClient handles all communication with the UNO server.
 * It runs on a background thread to avoid blocking the UI.
 * 
 * This class:
 * - Connects to the server via TCP socket
 * - Sends NetworkMessage objects as JSON
 * - Receives and parses JSON responses
 * - Notifies listeners when messages arrive
 */
public class NetworkClient {
    
    // Server connection details
    private static final String HOST = "localhost";
    private static final int PORT = 9090;
    
    // Socket and I/O streams for communicating with server
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    
    // Parser for converting between JSON and NetworkMessage objects
    private final MessageParser parser;
    
    // Message counter for giving each message a unique ID
    private long messageIdCounter = 1;
    
    // Queue for messages we want to send (thread-safe)
    private final BlockingQueue<NetworkMessage> outgoingMessages;
    
    // Callback that gets called when we receive a message from server
    private Consumer<NetworkMessage> messageListener;
    
    // Background threads for sending and receiving
    private Thread senderThread;
    private Thread receiverThread;
    
    // Flag to control when threads should stop
    private volatile boolean running = false;
    
    /**
     * Creates a new NetworkClient.
     * Note: This doesn't connect yet - call connect() to start.
     */
    public NetworkClient() {
        this.parser = new MessageParser();
        this.outgoingMessages = new LinkedBlockingQueue<>();
    }
    
    /**
     * Sets the callback that will be invoked when messages arrive from server.
     * This callback will be called from the receiver thread, so if you need to
     * update UI, make sure to use Platform.runLater()!
     * 
     * @param listener Function that receives NetworkMessage objects
     */
    public void setMessageListener(Consumer<NetworkMessage> listener) {
        this.messageListener = listener;
    }
    
    /**
     * Connects to the server and starts background threads.
     * 
     * @return true if connection successful, false otherwise
     */
    public boolean connect() {
        try {
            // Try to connect to server
            System.out.println("[NetworkClient] Connecting to " + HOST + ":" + PORT + "...");
            socket = new Socket(HOST, PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            System.out.println("[NetworkClient] Connected!");
            
            // Start background threads
            running = true;
            startSenderThread();
            startReceiverThread();
            
            return true;
        } catch (IOException e) {
            System.err.println("[NetworkClient] Failed to connect: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Disconnects from server and stops all threads.
     */
    public void disconnect() {
        System.out.println("[NetworkClient] Disconnecting...");
        running = false;
        
        // Close socket (this will also cause threads to exit)
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("[NetworkClient] Error closing socket: " + e.getMessage());
        }
        
        // Wait for threads to finish
        try {
            if (senderThread != null) {
                senderThread.join(1000);
            }
            if (receiverThread != null) {
                receiverThread.join(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.println("[NetworkClient] Disconnected");
    }
    
    /**
     * Sends a message to the server.
     * The message is queued and will be sent by the sender thread.
     * 
     * @param method The protocol method to invoke
     * @param payload The data to send (use MessageParser.EmptyPayload() if none)
     */
    public void sendMessage(Method method, Payload payload) {
        // Create a NetworkMessage with auto-incrementing ID
        NetworkMessage message = new NetworkMessage(
            messageIdCounter++,
            Version.V1,
            method,
            payload,
            System.currentTimeMillis()
        );
        
        // Add to queue - sender thread will pick it up
        outgoingMessages.offer(message);
    }
    
    /**
     * Starts the sender thread which sends queued messages to server.
     */
    private void startSenderThread() {
        senderThread = new Thread(() -> {
            System.out.println("[Sender] Thread started");
            
            while (running) {
                try {
                    // Wait for a message to send (blocks until one is available)
                    // Timeout after 1 second to check if we should still be running
                    NetworkMessage message = outgoingMessages.poll(1, java.util.concurrent.TimeUnit.SECONDS);
                    
                    if (message != null) {
                        // Convert to JSON and send
                        String json = parser.toJson(message);
                        System.out.println("[Sender] Sending: " + json);
                        out.println(json);
                    }
                } catch (InterruptedException e) {
                    // Thread interrupted - exit loop
                    break;
                } catch (Exception e) {
                    System.err.println("[Sender] Error sending message: " + e.getMessage());
                }
            }
            
            System.out.println("[Sender] Thread stopped");
        }, "NetworkClient-Sender");
        
        senderThread.setDaemon(true);
        senderThread.start();
    }
    
    /**
     * Starts the receiver thread which reads messages from server.
     */
    private void startReceiverThread() {
        receiverThread = new Thread(() -> {
            System.out.println("[Receiver] Thread started");
            
            while (running) {
                try {
                    // Read one line of JSON from server
                    String line = in.readLine();
                    
                    if (line == null) {
                        // Server closed connection
                        System.out.println("[Receiver] Server closed connection");
                        break;
                    }
                    
                    System.out.println("[Receiver] Received: " + line);
                    
                    // Parse JSON into NetworkMessage
                    NetworkMessage message = parser.fromJson(line);
                    
                    // Notify listener if one is registered
                    if (messageListener != null) {
                        messageListener.accept(message);
                    }
                    
                } catch (IOException e) {
                    if (running) {
                        System.err.println("[Receiver] Error reading from server: " + e.getMessage());
                    }
                    break;
                } catch (Exception e) {
                    System.err.println("[Receiver] Error parsing message: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            System.out.println("[Receiver] Thread stopped");
        }, "NetworkClient-Receiver");
        
        receiverThread.setDaemon(true);
        receiverThread.start();
    }
    
    /**
     * Checks if we're currently connected to server.
     * 
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        return socket != null && !socket.isClosed() && running;
    }
}
