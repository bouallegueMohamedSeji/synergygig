package services;

import entities.Message;
import utils.MyDatabase;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ServiceMessage implements IService<Message> {

    private Connection connection;

    public ServiceMessage() {
        connection = MyDatabase.getInstance().getConnection();
    }

    @Override
    public void ajouter(Message message) throws SQLException {
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
        String req = "UPDATE messages SET content=? WHERE id=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setString(1, message.getContent());
        ps.setInt(2, message.getId());
        ps.executeUpdate();
        ps.close();
    }

    @Override
    public void supprimer(int id) throws SQLException {
        String req = "DELETE FROM messages WHERE id=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setInt(1, id);
        ps.executeUpdate();
        ps.close();
    }

    @Override
    public List<Message> recuperer() throws SQLException {
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

    /**
     * Get messages by room ID (for chat).
     */
    public List<Message> getByRoom(int roomId) throws SQLException {
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
