package services;

import com.google.gson.*;
import entities.ChatRoom;
import utils.ApiClient;
import utils.AppConfig;
import utils.MyDatabase;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServiceChatRoom implements IService<ChatRoom> {

    private Connection connection;
    private final boolean useApi;

    public ServiceChatRoom() {
        useApi = AppConfig.isApiMode();
        if (!useApi) {
            connection = MyDatabase.getInstance().getConnection();
        }
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
            body.put("created_by", room.getCreatedBy());
            ApiClient.post("/chatrooms", body);
            return;
        }
        String req = "INSERT INTO chat_rooms (name, type, created_by) VALUES (?, ?, ?)";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setString(1, room.getName());
        ps.setString(2, room.getType() != null ? room.getType() : "group");
        ps.setInt(3, room.getCreatedBy());
        ps.executeUpdate();
        ps.close();
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
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setString(1, room.getName());
        ps.setInt(2, room.getId());
        ps.executeUpdate();
        ps.close();
    }

    @Override
    public void supprimer(int id) throws SQLException {
        if (useApi) {
            ApiClient.delete("/chatrooms/" + id);
            return;
        }
        String req = "DELETE FROM chat_rooms WHERE id=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setInt(1, id);
        ps.executeUpdate();
        ps.close();
    }

    @Override
    public List<ChatRoom> recuperer() throws SQLException {
        if (useApi) {
            return jsonArrayToRooms(ApiClient.get("/chatrooms"));
        }
        List<ChatRoom> rooms = new ArrayList<>();
        String req = "SELECT * FROM chat_rooms ORDER BY created_at DESC";
        PreparedStatement ps = connection.prepareStatement(req);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            rooms.add(new ChatRoom(
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getTimestamp("created_at"),
                    rs.getString("type"),
                    rs.getInt("created_by")));
        }
        rs.close();
        ps.close();
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
            ChatRoom newRoom = new ChatRoom(name, isPrivate ? "private" : "group", 0);
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
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setString(1, name);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            ChatRoom room = new ChatRoom(
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getTimestamp("created_at"),
                    rs.getString("type"),
                    rs.getInt("created_by"));
            rs.close();
            ps.close();
            return room;
        }

        // Create new if not found
        rs.close();
        ps.close();
        ajouter(new ChatRoom(name, isPrivate ? "private" : "group", 0));
        return getOrCreateRoom(name);
    }
}
