package services;

import entities.ChatRoom;
import utils.MyDatabase;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ServiceChatRoom implements IService<ChatRoom> {

    private Connection connection;

    public ServiceChatRoom() {
        connection = MyDatabase.getInstance().getConnection();
    }

    @Override
    public void ajouter(ChatRoom room) throws SQLException {
        String req = "INSERT INTO chat_rooms (name) VALUES (?)";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setString(1, room.getName());
        ps.executeUpdate();
        ps.close();
    }

    @Override
    public void modifier(ChatRoom room) throws SQLException {
        String req = "UPDATE chat_rooms SET name=? WHERE id=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setString(1, room.getName());
        ps.setInt(2, room.getId());
        ps.executeUpdate();
        ps.close();
    }

    @Override
    public void supprimer(int id) throws SQLException {
        String req = "DELETE FROM chat_rooms WHERE id=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setInt(1, id);
        ps.executeUpdate();
        ps.close();
    }

    @Override
    public List<ChatRoom> recuperer() throws SQLException {
        List<ChatRoom> rooms = new ArrayList<>();
        String req = "SELECT * FROM chat_rooms ORDER BY created_at DESC";
        PreparedStatement ps = connection.prepareStatement(req);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            rooms.add(new ChatRoom(
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getTimestamp("created_at")));
        }
        rs.close();
        ps.close();
        return rooms;
    }

    /**
     * Get or create a chat room by name (useful for direct messages or specific
     * topics)
     */
    public ChatRoom getOrCreateRoom(String name) throws SQLException {
        // Try to find existing
        String req = "SELECT * FROM chat_rooms WHERE name=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setString(1, name);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            ChatRoom room = new ChatRoom(
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getTimestamp("created_at"));
            rs.close();
            ps.close();
            return room;
        }

        // Create new if not found
        rs.close();
        ps.close();
        ajouter(new ChatRoom(name));
        return getOrCreateRoom(name); // Recursive call to fetch the newly created room
    }
}
