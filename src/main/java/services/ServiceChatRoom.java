package services;

import com.google.gson.*;
import entities.ChatRoom;
import utils.ApiClient;
import utils.AppConfig;
import utils.MyDatabase;
import utils.InMemoryCache;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServiceChatRoom implements IService<ChatRoom> {

    private final boolean useApi;

    private static final String CACHE_KEY = "chatrooms:all";
    private static final int CACHE_TTL = 60;

    public ServiceChatRoom() {
        useApi = AppConfig.isApiMode();
    }

    // ==================== JSON helpers ====================

    private ChatRoom jsonToChatRoom(JsonObject obj) {
        Timestamp createdAt = null;
        if (obj.has("created_at") && !obj.get("created_at").isJsonNull()) {
            createdAt = Timestamp.valueOf(obj.get("created_at").getAsString().replace("T", " "));
        }
        String type = obj.has("type") && !obj.get("type").isJsonNull() ? obj.get("type").getAsString() : "group";
        int createdBy = obj.has("created_by") && !obj.get("created_by").isJsonNull() ? obj.get("created_by").getAsInt() : 0;
        return new ChatRoom(
                obj.get("id").getAsInt(),
                obj.get("name").getAsString(),
                createdAt,
                type,
                createdBy
        );
    }

    private List<ChatRoom> jsonArrayToRooms(JsonElement el) {
        List<ChatRoom> rooms = new ArrayList<>();
        if (el != null && el.isJsonArray()) {
            for (JsonElement item : el.getAsJsonArray()) {
                rooms.add(jsonToChatRoom(item.getAsJsonObject()));
            }
        }
        return rooms;
    }

    // ==================== CRUD ====================

    @Override
    public void ajouter(ChatRoom room) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("name", room.getName());
            body.put("type", room.getType() != null ? room.getType() : "group");
            body.put("created_by", room.getCreatedBy() > 0 ? room.getCreatedBy() : null);
            JsonElement resp = ApiClient.post("/chatrooms", body);
            if (resp != null && resp.isJsonObject() && resp.getAsJsonObject().has("id")) {
                room.setId(resp.getAsJsonObject().get("id").getAsInt());
            }
            return;
        }
        String req = "INSERT INTO chat_rooms (name, type, created_by) VALUES (?, ?, ?)";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(req, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, room.getName());
            ps.setString(2, room.getType() != null ? room.getType() : "group");
            if (room.getCreatedBy() > 0) ps.setInt(3, room.getCreatedBy());
            else ps.setNull(3, java.sql.Types.INTEGER);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) room.setId(keys.getInt(1));
            }
        }
        InMemoryCache.evictByPrefix("chatrooms:");
    }

    @Override
    public void modifier(ChatRoom room) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("name", room.getName());
            ApiClient.put("/chatrooms/" + room.getId(), body);
            return;
        }
        String req = "UPDATE chat_rooms SET name=? WHERE id=?";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(req)) {
            ps.setString(1, room.getName());
            ps.setInt(2, room.getId());
            ps.executeUpdate();
        }
        InMemoryCache.evictByPrefix("chatrooms:");
    }

    @Override
    public void supprimer(int id) throws SQLException {
        if (useApi) {
            ApiClient.delete("/chatrooms/" + id);
            return;
        }
        String req = "DELETE FROM chat_rooms WHERE id=?";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(req)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
        InMemoryCache.evictByPrefix("chatrooms:");
    }

    @Override
    public List<ChatRoom> recuperer() throws SQLException {
        if (useApi) {
            return InMemoryCache.getOrLoad(CACHE_KEY, CACHE_TTL,
                    () -> jsonArrayToRooms(ApiClient.get("/chatrooms")));
        }
        return InMemoryCache.getOrLoadChecked(CACHE_KEY, CACHE_TTL,
                () -> recupererFromDb());
    }

    private List<ChatRoom> recupererFromDb() throws SQLException {
        List<ChatRoom> rooms = new ArrayList<>();
        String req = "SELECT * FROM chat_rooms ORDER BY created_at DESC";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(req);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rooms.add(new ChatRoom(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getTimestamp("created_at"),
                        rs.getString("type"),
                        rs.getInt("created_by")));
            }
        }
        return rooms;
    }

    public ChatRoom getOrCreateRoom(String name) throws SQLException {
        boolean isPrivate = name.startsWith("dm_");
        if (useApi) {
            // Try to find existing
            JsonElement resp = ApiClient.get("/chatrooms/by-name/" + name);
            if (resp != null && resp.isJsonObject()) {
                return jsonToChatRoom(resp.getAsJsonObject());
            }
            // Create new
            ChatRoom newRoom = new ChatRoom(name, isPrivate ? "DIRECT" : "group", 0);
            ajouter(newRoom);
            // Fetch again
            resp = ApiClient.get("/chatrooms/by-name/" + name);
            if (resp != null && resp.isJsonObject()) {
                return jsonToChatRoom(resp.getAsJsonObject());
            }
            return null;
        }
        // Try to find existing
        String req = "SELECT * FROM chat_rooms WHERE name=?";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(req)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new ChatRoom(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getTimestamp("created_at"),
                            rs.getString("type"),
                            rs.getInt("created_by"));
                }
            }
        }
        // Create new if not found
        ajouter(new ChatRoom(name, isPrivate ? "DIRECT" : "group", 0));
        return getOrCreateRoom(name);
    }

    /** Returns total chat room count without loading all rows. */
    public int count() throws SQLException {
        return InMemoryCache.getOrLoadChecked("chatrooms:count", 30, () -> {
            if (useApi) {
                return recuperer().size();
            }
            try (Connection conn = MyDatabase.getInstance().getConnection();
                 Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM chat_rooms")) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        });
    }
}
