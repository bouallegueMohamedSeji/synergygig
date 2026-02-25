package services;

import com.google.gson.*;
import entities.Message;
import utils.ApiClient;
import utils.AppConfig;
import utils.InMemoryCache;
import utils.MyDatabase;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ServiceMessage implements IService<Message> {

    private final boolean useApi;

    public ServiceMessage() {
        useApi = AppConfig.isApiMode();
    }

    // ==================== JSON helpers ====================

    private Message jsonToMessage(JsonObject obj) {
        Timestamp timestamp = null;
        if (obj.has("timestamp") && !obj.get("timestamp").isJsonNull()) {
            timestamp = Timestamp.valueOf(obj.get("timestamp").getAsString().replace("T", " "));
        }
        return new Message(
                obj.get("id").getAsInt(),
                obj.get("sender_id").getAsInt(),
                obj.get("room_id").getAsInt(),
                obj.get("content").getAsString(),
                timestamp
        );
    }

    private List<Message> jsonArrayToMessages(JsonElement el) {
        List<Message> messages = new ArrayList<>();
        if (el != null && el.isJsonArray()) {
            for (JsonElement item : el.getAsJsonArray()) {
                messages.add(jsonToMessage(item.getAsJsonObject()));
            }
        }
        return messages;
    }

    // ==================== CRUD ====================

    @Override
    public void ajouter(Message message) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("sender_id", message.getSenderId());
            body.put("room_id", message.getRoomId());
            body.put("content", message.getContent());
            ApiClient.post("/messages", body);
            InMemoryCache.evictByPrefix("messages:");
            return;
        }
        String req = "INSERT INTO messages (sender_id, room_id, content) VALUES (?, ?, ?)";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(req)) {
            ps.setInt(1, message.getSenderId());
            ps.setInt(2, message.getRoomId());
            ps.setString(3, message.getContent());
            ps.executeUpdate();
        }
        InMemoryCache.evictByPrefix("messages:");
    }

    @Override
    public void modifier(Message message) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("content", message.getContent());
            ApiClient.put("/messages/" + message.getId(), body);
            InMemoryCache.evictByPrefix("messages:");
            return;
        }
        String req = "UPDATE messages SET content=? WHERE id=?";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(req)) {
            ps.setString(1, message.getContent());
            ps.setInt(2, message.getId());
            ps.executeUpdate();
        }
        InMemoryCache.evictByPrefix("messages:");
    }

    @Override
    public void supprimer(int id) throws SQLException {
        if (useApi) {
            ApiClient.delete("/messages/" + id);
            InMemoryCache.evictByPrefix("messages:");
            return;
        }
        String req = "DELETE FROM messages WHERE id=?";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(req)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
        InMemoryCache.evictByPrefix("messages:");
    }

    @Override
    public List<Message> recuperer() throws SQLException {
        if (useApi) {
            return jsonArrayToMessages(ApiClient.get("/messages"));
        }
        List<Message> messages = new ArrayList<>();
        String req = "SELECT * FROM messages";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(req);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Message msg = new Message(
                        rs.getInt("id"),
                        rs.getInt("sender_id"),
                        rs.getInt("room_id"),
                        rs.getString("content"),
                        rs.getTimestamp("timestamp"));
                messages.add(msg);
            }
        }
        return messages;
    }

    public List<Message> getByRoom(int roomId) throws SQLException {
        String cacheKey = "messages:room:" + roomId;
        if (useApi) {
            return InMemoryCache.getOrLoad(cacheKey, 3,
                    () -> jsonArrayToMessages(ApiClient.get("/messages/room/" + roomId)));
        }
        return InMemoryCache.getOrLoadChecked(cacheKey, 3, () -> {
            List<Message> messages = new ArrayList<>();
            String req = "SELECT * FROM messages WHERE room_id=? ORDER BY timestamp ASC";
            try (Connection conn = MyDatabase.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(req)) {
                ps.setInt(1, roomId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        messages.add(new Message(
                                rs.getInt("id"),
                                rs.getInt("sender_id"),
                                rs.getInt("room_id"),
                                rs.getString("content"),
                                rs.getTimestamp("timestamp")));
                    }
                }
            }
            return messages;
        });
    }

    /**
     * Returns the latest message timestamp for each room in a single query (JDBC)
     * or with per-room API calls (API mode — only fetches last element from each response).
     */
    public Map<Integer, java.sql.Timestamp> getLatestTimestamps(List<Integer> roomIds) throws SQLException {
        Map<Integer, java.sql.Timestamp> result = new HashMap<>();
        if (roomIds == null || roomIds.isEmpty()) return result;

        if (useApi) {
            // API mode: fire all room fetches in parallel
            Map<Integer, CompletableFuture<java.sql.Timestamp>> futures = new HashMap<>();
            for (int roomId : roomIds) {
                futures.put(roomId, CompletableFuture.supplyAsync(() -> {
                    try {
                        List<Message> msgs = getByRoom(roomId);
                        if (!msgs.isEmpty()) {
                            Message last = msgs.get(msgs.size() - 1);
                            return last.getTimestamp();
                        }
                    } catch (Exception ignored) {}
                    return null;
                }));
            }
            CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0])).join();
            for (var entry : futures.entrySet()) {
                java.sql.Timestamp ts = entry.getValue().join();
                if (ts != null) result.put(entry.getKey(), ts);
            }
            return result;
        }

        // JDBC mode: single GROUP BY query
        String sql = "SELECT room_id, MAX(timestamp) AS latest FROM messages WHERE room_id IN ("
                + String.join(",", java.util.Collections.nCopies(roomIds.size(), "?"))
                + ") GROUP BY room_id";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < roomIds.size(); i++) {
                ps.setInt(i + 1, roomIds.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getInt("room_id"), rs.getTimestamp("latest"));
                }
            }
        }
        return result;
    }

    /** Returns total message count without loading all rows. */
    public int count() throws SQLException {
        return InMemoryCache.getOrLoadChecked("messages:count", 30, () -> {
            if (useApi) {
                return recuperer().size();
            }
            try (Connection conn = MyDatabase.getInstance().getConnection();
                 Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM messages")) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        });
    }
}
