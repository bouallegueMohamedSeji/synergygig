package services;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import entities.ChatRoomMember;
import utils.ApiClient;
import utils.AppConfig;
import utils.InMemoryCache;
import utils.MyDatabase;

import java.sql.*;
import java.util.*;

/**
 * Service for chat room membership. Supports API and JDBC modes.
 * Group rooms use explicit membership; DMs don't need this.
 */
public class ServiceChatRoomMember {

    private boolean useApi() { return AppConfig.isApiMode(); }

    // ═══════════════════════════════════════════
    //  ADD MEMBER
    // ═══════════════════════════════════════════

    public void addMember(int roomId, int userId, String role) throws SQLException {
        if (useApi()) {
            Map<String, Object> body = new HashMap<>();
            body.put("room_id", roomId);
            body.put("user_id", userId);
            body.put("role", role);
            ApiClient.post("/chat_room_members", body);
            InMemoryCache.evictByPrefix("room_members:");
            return;
        }
        String sql = "INSERT IGNORE INTO chat_room_members (room_id, user_id, role) VALUES (?, ?, ?)";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, roomId);
            ps.setInt(2, userId);
            ps.setString(3, role);
            ps.executeUpdate();
        }
        InMemoryCache.evictByPrefix("room_members:");
    }

    // ═══════════════════════════════════════════
    //  REMOVE MEMBER
    // ═══════════════════════════════════════════

    public void removeMember(int roomId, int userId) throws SQLException {
        if (useApi()) {
            ApiClient.delete("/chat_room_members/" + roomId + "/" + userId);
            InMemoryCache.evictByPrefix("room_members:");
            return;
        }
        String sql = "DELETE FROM chat_room_members WHERE room_id = ? AND user_id = ?";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, roomId);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
        InMemoryCache.evictByPrefix("room_members:");
    }

    // ═══════════════════════════════════════════
    //  GET MEMBERS BY ROOM
    // ═══════════════════════════════════════════

    public List<ChatRoomMember> getByRoom(int roomId) throws SQLException {
        String key = "room_members:room:" + roomId;
        if (useApi()) {
            return InMemoryCache.getOrLoad(key, 30, () -> {
                List<ChatRoomMember> list = new ArrayList<>();
                try {
                    JsonElement resp = ApiClient.get("/chat_room_members/room/" + roomId);
                    if (resp != null && resp.isJsonArray()) {
                        for (JsonElement el : resp.getAsJsonArray()) {
                            list.add(jsonToMember(el.getAsJsonObject()));
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Failed to get room members: " + e.getMessage());
                }
                return list;
            });
        }
        return InMemoryCache.getOrLoadChecked(key, 30, () -> getByRoomJdbc(roomId));
    }

    private List<ChatRoomMember> getByRoomJdbc(int roomId) throws SQLException {
        List<ChatRoomMember> list = new ArrayList<>();
        String sql = "SELECT * FROM chat_room_members WHERE room_id = ?";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, roomId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(rowToMember(rs));
            }
        }
        return list;
    }

    // ═══════════════════════════════════════════
    //  GET ROOMS FOR USER (which rooms the user is a member of)
    // ═══════════════════════════════════════════

    public Set<Integer> getRoomIdsForUser(int userId) throws SQLException {
        String key = "room_members:user:" + userId;
        if (useApi()) {
            return InMemoryCache.getOrLoad(key, 30, () -> {
                Set<Integer> ids = new HashSet<>();
                try {
                    JsonElement resp = ApiClient.get("/chat_room_members/user/" + userId);
                    if (resp != null && resp.isJsonArray()) {
                        for (JsonElement el : resp.getAsJsonArray()) {
                            ids.add(el.getAsJsonObject().get("room_id").getAsInt());
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Failed to get user rooms: " + e.getMessage());
                }
                return ids;
            });
        }
        return InMemoryCache.getOrLoadChecked(key, 30, () -> getRoomIdsForUserJdbc(userId));
    }

    private Set<Integer> getRoomIdsForUserJdbc(int userId) throws SQLException {
        Set<Integer> ids = new HashSet<>();
        String sql = "SELECT room_id FROM chat_room_members WHERE user_id = ?";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getInt("room_id"));
            }
        }
        return ids;
    }

    // ═══════════════════════════════════════════
    //  IS MEMBER CHECK
    // ═══════════════════════════════════════════

    public boolean isMember(int roomId, int userId) throws SQLException {
        return getRoomIdsForUser(userId).contains(roomId);
    }

    // ═══════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════

    private ChatRoomMember jsonToMember(JsonObject obj) {
        ChatRoomMember m = new ChatRoomMember();
        if (obj.has("id") && !obj.get("id").isJsonNull()) m.setId(obj.get("id").getAsInt());
        m.setRoomId(obj.get("room_id").getAsInt());
        m.setUserId(obj.get("user_id").getAsInt());
        if (obj.has("role") && !obj.get("role").isJsonNull()) m.setRole(obj.get("role").getAsString());
        if (obj.has("joined_at") && !obj.get("joined_at").isJsonNull()) {
            try { m.setJoinedAt(Timestamp.valueOf(obj.get("joined_at").getAsString().replace("T", " "))); } catch (Exception ignored) {}
        }
        return m;
    }

    private ChatRoomMember rowToMember(ResultSet rs) throws SQLException {
        ChatRoomMember m = new ChatRoomMember();
        m.setId(rs.getInt("id"));
        m.setRoomId(rs.getInt("room_id"));
        m.setUserId(rs.getInt("user_id"));
        m.setRole(rs.getString("role"));
        try { m.setJoinedAt(rs.getTimestamp("joined_at")); } catch (SQLException ignored) {}
        return m;
    }

    /**
     * Ensure the chat_room_members table exists. Called once on startup.
     */
    public void ensureTable() {
        // Create table via JDBC if a connection is available (skips silently in pure API mode)
        Connection conn = MyDatabase.getInstance().getConnection();
        if (conn == null) return;
        try (conn;
             Statement st = conn.createStatement()) {
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS chat_room_members (" +
                "  id INT AUTO_INCREMENT PRIMARY KEY," +
                "  room_id INT NOT NULL," +
                "  user_id INT NOT NULL," +
                "  role VARCHAR(20) DEFAULT 'MEMBER'," +
                "  joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "  UNIQUE KEY unique_room_user (room_id, user_id)," +
                "  INDEX idx_user (user_id)," +
                "  INDEX idx_room (room_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
        } catch (Exception e) {
            System.err.println("Could not ensure chat_room_members table: " + e.getMessage());
        }
    }
}
