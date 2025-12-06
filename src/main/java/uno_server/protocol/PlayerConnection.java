package uno_server.protocol;

import uno_server.common.Connection;

/**
 * Represents a player's connection and metadata.
 * Links a Connection object with user information.
 */
public class PlayerConnection {
    private final long userId;
    private final String username;
    private final Connection connection;
    private boolean isReady;
    private Long currentRoomId;

    public PlayerConnection(long userId, String username, Connection connection) {
        this.userId = userId;
        this.username = username;
        this.connection = connection;
        this.isReady = false;
        this.currentRoomId = null;
    }

    public long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public Connection getConnection() {
        return connection;
    }

    public boolean isReady() {
        return isReady;
    }

    public void setReady(boolean ready) {
        isReady = ready;
    }

    public Long getCurrentRoomId() {
        return currentRoomId;
    }

    public void setCurrentRoomId(Long roomId) {
        this.currentRoomId = roomId;
    }
}
