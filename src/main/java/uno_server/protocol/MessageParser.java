package uno_server.protocol;

import com.google.gson.*;
import uno_proto.common.*;
import uno_proto.dto.*;

import java.lang.reflect.Type;

/**
 * MessageParser handles serialization and deserialization of NetworkMessage objects.
 * It uses Gson to convert between JSON strings and NetworkMessage objects,
 * with special handling for polymorphic Payload types based on the Method field.
 */
public class MessageParser {

    private final Gson gson;

    public MessageParser() {
        // Create Gson instance with custom deserializer for NetworkMessage
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(NetworkMessage.class, new NetworkMessageDeserializer());
        this.gson = builder.create();
    }

    /**
     * Converts a NetworkMessage to a JSON string.
     *
     * @param message The NetworkMessage to serialize
     * @return JSON string representation
     */
    public String toJson(NetworkMessage message) {
        return gson.toJson(message);
    }

    /**
     * Converts a JSON string to a NetworkMessage.
     * The Payload field is deserialized to the appropriate DTO type based on the Method.
     *
     * @param json The JSON string to deserialize
     * @return The deserialized NetworkMessage
     * @throws JsonSyntaxException if the JSON is invalid
     */
    public NetworkMessage fromJson(String json) throws JsonSyntaxException {
        return gson.fromJson(json, NetworkMessage.class);
    }

    /**
     * Custom deserializer for NetworkMessage that handles polymorphic Payload types.
     * Based on the Method field, it deserializes the payload to the correct DTO type.
     */
    private static class NetworkMessageDeserializer implements JsonDeserializer<NetworkMessage> {

        @Override
        public NetworkMessage deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            
            JsonObject jsonObject = json.getAsJsonObject();

            // Extract basic fields
            long id = jsonObject.get("id").getAsLong();
            Version version = context.deserialize(jsonObject.get("version"), Version.class);
            Method method = context.deserialize(jsonObject.get("method"), Method.class);
            long timestamp = jsonObject.has("timestamp") ? jsonObject.get("timestamp").getAsLong() : System.currentTimeMillis();

            // Deserialize payload based on method type
            Payload payload = deserializePayload(jsonObject.get("payload"), method, context);

            // Create NetworkMessage using Kotlin constructor
            return new NetworkMessage(id, version, method, payload, timestamp);
        }

        /**
         * Deserializes the payload field to the appropriate DTO type based on the method.
         *
         * @param payloadElement The JSON element containing the payload
         * @param method The method that determines the payload type
         * @param context The deserialization context
         * @return The deserialized Payload object
         */
        private Payload deserializePayload(JsonElement payloadElement, Method method, JsonDeserializationContext context) {
            if (payloadElement == null || payloadElement.isJsonNull()) {
                return new EmptyPayload();
            }

            // Map method to payload DTO type
            switch (method) {
                // Lobby methods
                case CREATE_ROOM:
                    return context.deserialize(payloadElement, CreateRoomRequest.class);
                case ROOM_CREATED_SUCCESS:
                case ROOM_CREATED_ERROR:
                    return context.deserialize(payloadElement, CreateRoomResponse.class);
                case GET_ROOMS:
                    return new EmptyPayload();
                case ROOMS_LIST:
                    return context.deserialize(payloadElement, RoomsListPayload.class);
                case JOIN_ROOM:
                    return context.deserialize(payloadElement, JoinRoomRequest.class);
                case JOIN_ROOM_SUCCESS:
                case JOIN_ROOM_ERROR:
                    return context.deserialize(payloadElement, JoinRoomResponse.class);
                case LOBBY_UPDATE:
                    return context.deserialize(payloadElement, LobbyUpdate.class);
                case LOBBY_CHAT:
                case GAME_CHAT:
                    return context.deserialize(payloadElement, ChatMessage.class);

                // Game methods
                case START_GAME:
                case GAME_START:
                case GAME_STATE:
                    return context.deserialize(payloadElement, GameState.class);
                case PLAY_CARD:
                    return context.deserialize(payloadElement, PlayCardRequest.class);
                case DRAW_CARD:
                case SAY_UNO:
                    return new EmptyPayload();

                // System methods
                case PING:
                case PONG:
                case OK:
                    return new EmptyPayload();
                case ERROR:
                    return context.deserialize(payloadElement, ErrorPayload.class);

                default:
                    return new EmptyPayload();
            }
        }
    }

    /**
     * Empty payload implementation for methods that don't require data.
     */
    public static class EmptyPayload implements Payload {
        private static final long serialVersionUID = 1L;
    }

    /**
     * Error payload for ERROR method responses.
     */
    public static class ErrorPayload implements Payload {
        private static final long serialVersionUID = 1L;
        private final String message;
        private final String code;

        public ErrorPayload(String message, String code) {
            this.message = message;
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public String getCode() {
            return code;
        }
    }

    /**
     * Payload for ROOMS_LIST method containing a list of available rooms.
     */
    public static class RoomsListPayload implements Payload {
        private static final long serialVersionUID = 1L;
        private final java.util.List<RoomInfo> rooms;

        public RoomsListPayload(java.util.List<RoomInfo> rooms) {
            this.rooms = rooms;
        }

        public java.util.List<RoomInfo> getRooms() {
            return rooms;
        }
    }
}
