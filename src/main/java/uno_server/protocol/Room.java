package uno_server.protocol;

import uno_proto.dto.RoomStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Represents a game room in the server.
 * Tracks room metadata, players, and connection information.
 */
public class Room {
    private final long roomId;
    private final String roomName;
    private final String password;
    private final int maxPlayers;
    private final boolean allowStuck;
    private final long creatorId;
    private final List<PlayerConnection> players;
    private RoomStatus status;

    public Room(long roomId, String roomName, String password, int maxPlayers, boolean allowStuck, long creatorId) {
        this.roomId = roomId;
        this.roomName = roomName;
        this.password = password;
        this.maxPlayers = maxPlayers;
        this.allowStuck = allowStuck;
        this.creatorId = creatorId;
        this.players = new CopyOnWriteArrayList<>();
        this.status = RoomStatus.WAITING;
    }

    /**
     * Adds a player to the room.
     *
     * @param player The player connection to add
     * @return true if added successfully, false if room is full
     */
    public boolean addPlayer(PlayerConnection player) {
        if (players.size() >= maxPlayers) {
            return false;
        }
        players.add(player);
        return true;
    }

    /**
     * Removes a player from the room.
     *
     * @param userId The user ID to remove
     * @return true if removed, false if not found
     */
    public boolean removePlayer(long userId) {
        return players.removeIf(p -> p.getUserId() == userId);
    }

    /**
     * Gets a player by user ID.
     *
     * @param userId The user ID to find
     * @return The PlayerConnection or null if not found
     */
    public PlayerConnection getPlayer(long userId) {
        return players.stream()
                .filter(p -> p.getUserId() == userId)
                .findFirst()
                .orElse(null);
    }

    /**
     * Checks if a player is in the room.
     *
     * @param userId The user ID to check
     * @return true if player is in room, false otherwise
     */
    public boolean hasPlayer(long userId) {
        return players.stream().anyMatch(p -> p.getUserId() == userId);
    }

    /**
     * Gets all players in the room.
     *
     * @return List of player connections
     */
    public List<PlayerConnection> getPlayers() {
        return new ArrayList<>(players);
    }

    /**
     * Sets a player's ready status.
     *
     * @param userId The user ID
     * @param ready The ready status
     */
    public void setPlayerReady(long userId, boolean ready) {
        PlayerConnection player = getPlayer(userId);
        if (player != null) {
            player.setReady(ready);
        }
    }

    /**
     * Checks if all players are ready.
     *
     * @return true if all players are ready, false otherwise
     */
    public boolean allPlayersReady() {
        return !players.isEmpty() && players.stream().allMatch(PlayerConnection::isReady);
    }

    // Getters
    public long getRoomId() {
        return roomId;
    }

    public String getRoomName() {
        return roomName;
    }

    public String getPassword() {
        return password;
    }

    public boolean hasPassword() {
        return password != null && !password.isEmpty();
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public boolean isAllowStuck() {
        return allowStuck;
    }

    public long getCreatorId() {
        return creatorId;
    }

    public int getCurrentPlayerCount() {
        return players.size();
    }

    public RoomStatus getStatus() {
        return status;
    }

    public void setStatus(RoomStatus status) {
        this.status = status;
    }

    public boolean isFull() {
        return players.size() >= maxPlayers;
    }
}
