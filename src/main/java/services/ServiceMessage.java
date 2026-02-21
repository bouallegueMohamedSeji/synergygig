package services;

import com.google.gson.*;
import entities.Message;
import utils.ApiClient;
import utils.AppConfig;
import utils.MyDatabase;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServiceMessage implements IService<Message> {

    private Connection connection;
    private final boolean useApi;

    public ServiceMessage() {
        useApi = AppConfig.isApiMode();
        if (!useApi) {
            connection = MyDatabase.getInstance().getConnection();
        }
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
            return;
        }
        String req = "INSERT INTO messages (sender_id, room_id, content) VALUES (?, ?, ?)";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setInt(1, message.getSenderId());
        ps.setInt(2, message.getRoomId());
        ps.setString(3, message.getContent());
        ps.executeUpdate();
        ps.close();
    }

    @Override
    public void modifier(Message message) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("content", message.getContent());
            ApiClient.put("/messages/" + message.getId(), body);
            return;
        }
        String req = "UPDATE messages SET content=? WHERE id=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setString(1, message.getContent());
        ps.setInt(2, message.getId());
        ps.executeUpdate();
        ps.close();
    }

    @Override
    public void supprimer(int id) throws SQLException {
        if (useApi) {
            ApiClient.delete("/messages/" + id);
            return;
        }
        String req = "DELETE FROM messages WHERE id=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setInt(1, id);
        ps.executeUpdate();
        ps.close();
    }

    @Override
    public List<Message> recuperer() throws SQLException {
        if (useApi) {
            return jsonArrayToMessages(ApiClient.get("/messages"));
        }
        List<Message> messages = new ArrayList<>();
        String req = "SELECT * FROM messages";
        PreparedStatement ps = connection.prepareStatement(req);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            Message msg = new Message(
                    rs.getInt("id"),
                    rs.getInt("sender_id"),
                    rs.getInt("room_id"),
                    rs.getString("content"),
                    rs.getTimestamp("timestamp"));
            messages.add(msg);
        }
        rs.close();
        ps.close();
        return messages;
    }

    public List<Message> getByRoom(int roomId) throws SQLException {
        if (useApi) {
            return jsonArrayToMessages(ApiClient.get("/messages/room/" + roomId));
        }
        List<Message> messages = new ArrayList<>();
        String req = "SELECT * FROM messages WHERE room_id=? ORDER BY timestamp ASC";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setInt(1, roomId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            Message msg = new Message(
                    rs.getInt("id"),
                    rs.getInt("sender_id"),
                    rs.getInt("room_id"),
                    rs.getString("content"),
                    rs.getTimestamp("timestamp"));
            messages.add(msg);
        }
        rs.close();
        ps.close();
        return messages;
    }
}
