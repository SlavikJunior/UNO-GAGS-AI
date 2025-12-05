package uno_server.common;

import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class Server implements Closeable {

    private static final int port = 9090;
    private static final List<Socket> sockets = new CopyOnWriteArrayList<Socket>();

    private static ServerSocket instance;
    private static AtomicInteger index = new AtomicInteger(0);

    static {
        try {
            instance = new ServerSocket(port);
            instance.setReuseAddress(true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    private Server() {}

    public static void handle() {

    }

    public void close() throws IOException {

    }
}
