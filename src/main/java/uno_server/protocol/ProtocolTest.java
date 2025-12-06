package uno_server.protocol;

import uno_proto.common.*;
import uno_proto.dto.*;

import java.io.*;
import java.net.Socket;

/**
 * Simple test class to demonstrate the wire protocol.
 * This simulates two clients creating a room, joining, starting a game,
 * and playing cards.
 */
public class ProtocolTest {

    private static final String HOST = "localhost";
    private static final int PORT = 9090;

    public static void main(String[] args) {
        System.out.println("Starting Protocol Test...");
        System.out.println("Make sure the server is running first!");
        System.out.println();

        try {
            Thread.sleep(1000);
            
            // Start two client threads
            Thread client1 = new Thread(() -> runClient1(), "Client-1");
            Thread client2 = new Thread(() -> runClient2(), "Client-2");

            client1.start();
            Thread.sleep(2000); // Let client 1 create room first
            client2.start();

            client1.join();
            client2.join();

            System.out.println("\nProtocol test completed!");
        } catch (Exception e) {
            System.err.println("Test error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void runClient1() {
        try (Socket socket = new Socket(HOST, PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            MessageParser parser = new MessageParser();

            System.out.println("[Client 1] Connected to server");

            // Test 1: PING
            System.out.println("[Client 1] Sending PING...");
            NetworkMessage ping = new NetworkMessage(1, Version.V1, Method.PING, new MessageParser.EmptyPayload(), System.currentTimeMillis());
            out.println(parser.toJson(ping));
            String response = in.readLine();
            System.out.println("[Client 1] Received: " + response);

            // Test 2: CREATE_ROOM
            System.out.println("[Client 1] Creating room...");
            CreateRoomRequest createRequest = new CreateRoomRequest("Test Room", null, 2, false);
            NetworkMessage createMsg = new NetworkMessage(2, Version.V1, Method.CREATE_ROOM, createRequest, System.currentTimeMillis());
            out.println(parser.toJson(createMsg));
            
            // Read response
            response = in.readLine();
            System.out.println("[Client 1] Received: " + response);
            
            // Read lobby update
            response = in.readLine();
            System.out.println("[Client 1] Received: " + response);

            // Wait for client 2 to join
            Thread.sleep(3000);

            // Read lobby update when client 2 joins
            response = in.readLine();
            System.out.println("[Client 1] Received: " + response);

            // Test 3: START_GAME
            System.out.println("[Client 1] Starting game...");
            NetworkMessage startMsg = new NetworkMessage(3, Version.V1, Method.START_GAME, new MessageParser.EmptyPayload(), System.currentTimeMillis());
            out.println(parser.toJson(startMsg));
            
            // Read game start state
            response = in.readLine();
            System.out.println("[Client 1] Received game state: " + response);

            // Test 4: PLAY_CARD (try to play first card)
            Thread.sleep(1000);
            System.out.println("[Client 1] Attempting to play card...");
            PlayCardRequest playRequest = new PlayCardRequest(0, null);
            NetworkMessage playMsg = new NetworkMessage(4, Version.V1, Method.PLAY_CARD, playRequest, System.currentTimeMillis());
            out.println(parser.toJson(playMsg));
            
            // Read response
            response = in.readLine();
            System.out.println("[Client 1] Received: " + response);

            // Keep reading for a bit to see any state updates
            Thread.sleep(2000);
            while (in.ready()) {
                response = in.readLine();
                if (response != null) {
                    System.out.println("[Client 1] Received: " + response);
                }
            }

        } catch (Exception e) {
            System.err.println("[Client 1] Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void runClient2() {
        try (Socket socket = new Socket(HOST, PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            MessageParser parser = new MessageParser();

            System.out.println("[Client 2] Connected to server");

            // Test 1: GET_ROOMS
            System.out.println("[Client 2] Getting rooms list...");
            NetworkMessage getRoomsMsg = new NetworkMessage(1, Version.V1, Method.GET_ROOMS, new MessageParser.EmptyPayload(), System.currentTimeMillis());
            out.println(parser.toJson(getRoomsMsg));
            String response = in.readLine();
            System.out.println("[Client 2] Received: " + response);

            // Test 2: JOIN_ROOM (room ID 1)
            System.out.println("[Client 2] Joining room 1...");
            JoinRoomRequest joinRequest = new JoinRoomRequest(1L, null);
            NetworkMessage joinMsg = new NetworkMessage(2, Version.V1, Method.JOIN_ROOM, joinRequest, System.currentTimeMillis());
            out.println(parser.toJson(joinMsg));
            
            // Read response
            response = in.readLine();
            System.out.println("[Client 2] Received: " + response);
            
            // Read lobby update
            response = in.readLine();
            System.out.println("[Client 2] Received: " + response);

            // Wait for game to start
            Thread.sleep(2000);

            // Read game start state
            response = in.readLine();
            System.out.println("[Client 2] Received game state: " + response);

            // Test 3: DRAW_CARD
            Thread.sleep(2000);
            System.out.println("[Client 2] Drawing a card...");
            NetworkMessage drawMsg = new NetworkMessage(3, Version.V1, Method.DRAW_CARD, new MessageParser.EmptyPayload(), System.currentTimeMillis());
            out.println(parser.toJson(drawMsg));
            
            // Read response
            response = in.readLine();
            System.out.println("[Client 2] Received: " + response);

            // Keep reading for a bit to see any state updates
            Thread.sleep(2000);
            while (in.ready()) {
                response = in.readLine();
                if (response != null) {
                    System.out.println("[Client 2] Received: " + response);
                }
            }

        } catch (Exception e) {
            System.err.println("[Client 2] Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
