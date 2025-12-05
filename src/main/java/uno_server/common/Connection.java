package uno_server.common;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;

public class Connection {

    private Socket socket;
    private final int socketTimeoutMs = 30000;
    private InputStreamReader inputStream;
    private OutputStreamWriter outputStream;
    private BufferedReader reader;
    private BufferedWriter writer;

    public Connection(Socket socket) {
        this.socket = socket;
        initialize();
    }

    private void initialize() {
        isAlive();
        try {
            socket.setSoTimeout(socketTimeoutMs);
            inputStream = new InputStreamReader(socket.getInputStream());
            outputStream = new OutputStreamWriter(socket.getOutputStream());
            reader = new BufferedReader(inputStream);
            writer = new BufferedWriter(outputStream);
        } catch (SocketException e) {
            // handling ...
        } catch (IOException e) {
            // handling ...
        }

    }

    private void isAlive() {
        if (socket.isClosed()) {
            throw new RuntimeException("Socket is closed");
        }
    }
}
