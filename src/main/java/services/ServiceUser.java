package services;

import entities.User;
import utils.MyDatabase;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ServiceUser implements IService<User> {

    private Connection connection;

    public ServiceUser() {
        connection = MyDatabase.getInstance().getConnection();
    }

    @Override
    public void ajouter(User user) throws SQLException {
        String req = "INSERT INTO users (email, password, first_name, last_name, role) VALUES (?, ?, ?, ?, ?)";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setString(1, user.getEmail());
        ps.setString(2, user.getPassword());
        ps.setString(3, user.getFirstName());
        ps.setString(4, user.getLastName());
        ps.setString(5, user.getRole());
        ps.executeUpdate();
        ps.close();
        System.out.println("✅ User added: " + user.getEmail());
    }

    @Override
    public void modifier(User user) throws SQLException {
        String req = "UPDATE users SET email=?, password=?, first_name=?, last_name=?, role=?, avatar_path=? WHERE id=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setString(1, user.getEmail());
        ps.setString(2, user.getPassword());
        ps.setString(3, user.getFirstName());
        ps.setString(4, user.getLastName());
        ps.setString(5, user.getRole());
        ps.setString(6, user.getAvatarPath());
        ps.setInt(7, user.getId());
        ps.executeUpdate();
        ps.close();
        System.out.println("✅ User updated: " + user.getEmail());
    }

    /**
     * Update only the avatar path for a user.
     */
    public void updateAvatar(int userId, String avatarPath) throws SQLException {
        String req = "UPDATE users SET avatar_path=? WHERE id=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setString(1, avatarPath);
        ps.setInt(2, userId);
        ps.executeUpdate();
        ps.close();
        System.out.println("✅ Avatar updated for user id=" + userId);
    }

    @Override
    public void supprimer(int id) throws SQLException {
        String req = "DELETE FROM users WHERE id=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setInt(1, id);
        ps.executeUpdate();
        ps.close();
        System.out.println("✅ User deleted: id=" + id);
    }

    @Override
    public List<User> recuperer() throws SQLException {
        List<User> users = new ArrayList<>();
        String req = "SELECT * FROM users";
        PreparedStatement ps = connection.prepareStatement(req);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            User user = new User(
                    rs.getInt("id"),
                    rs.getString("email"),
                    rs.getString("password"),
                    rs.getString("first_name"),
                    rs.getString("last_name"),
                    rs.getString("role"),
                    rs.getTimestamp("created_at"),
                    rs.getString("avatar_path"),
                    rs.getString("face_encoding"));
            users.add(user);
        }
        rs.close();
        ps.close();
        return users;
    }

    /**
     * Authenticate a user by email and password.
     * 
     * @return User object if credentials match, null otherwise.
     */
    public User login(String email, String password) throws SQLException {
        String req = "SELECT * FROM users WHERE email=? AND password=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setString(1, email);
        ps.setString(2, password);
        ResultSet rs = ps.executeQuery();
        User user = null;
        if (rs.next()) {
            user = new User(
                    rs.getInt("id"),
                    rs.getString("email"),
                    rs.getString("password"),
                    rs.getString("first_name"),
                    rs.getString("last_name"),
                    rs.getString("role"),
                    rs.getTimestamp("created_at"),
                    rs.getString("avatar_path"),
                    rs.getString("face_encoding"));
        }
        rs.close();
        ps.close();
        return user;
    }

    /**
     * Check if an email already exists in the database.
     */
    public boolean emailExists(String email) throws SQLException {
        String req = "SELECT COUNT(*) FROM users WHERE email=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setString(1, email);
        ResultSet rs = ps.executeQuery();
        boolean exists = false;
        if (rs.next()) {
            exists = rs.getInt(1) > 0;
        }
        rs.close();
        ps.close();
        return exists;
    }

    /**
     * Update a user's role (Admin privilege).
     */
    public void updateRole(int userId, String newRole) throws SQLException {
        String req = "UPDATE users SET role=? WHERE id=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setString(1, newRole);
        ps.setInt(2, userId);
        ps.executeUpdate();
        ps.close();
        System.out.println("✅ Role updated for user id=" + userId + " → " + newRole);
    }

    /**
     * Get all users filtered by role.
     */
    public List<User> getByRole(String role) throws SQLException {
        List<User> users = new ArrayList<>();
        String req = "SELECT * FROM users WHERE role=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setString(1, role);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            User user = new User(
                    rs.getInt("id"),
                    rs.getString("email"),
                    rs.getString("password"),
                    rs.getString("first_name"),
                    rs.getString("last_name"),
                    rs.getString("role"),
                    rs.getTimestamp("created_at"),
                    rs.getString("avatar_path"),
                    rs.getString("face_encoding"));
            users.add(user);
        }
        rs.close();
        ps.close();
        return users;
    }

    /**
     * Update face encoding for a user.
     */
    public void updateFaceEncoding(int userId, String faceEncoding) throws SQLException {
        String req = "UPDATE users SET face_encoding=? WHERE id=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setString(1, faceEncoding);
        ps.setInt(2, userId);
        ps.executeUpdate();
        ps.close();
        System.out.println("\u2705 Face encoding updated for user id=" + userId);
    }

    /**
     * Get a user by ID.
     */
    public User getById(int userId) throws SQLException {
        String req = "SELECT * FROM users WHERE id=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setInt(1, userId);
        ResultSet rs = ps.executeQuery();
        User user = null;
        if (rs.next()) {
            user = new User(
                    rs.getInt("id"),
                    rs.getString("email"),
                    rs.getString("password"),
                    rs.getString("first_name"),
                    rs.getString("last_name"),
                    rs.getString("role"),
                    rs.getTimestamp("created_at"),
                    rs.getString("avatar_path"),
                    rs.getString("face_encoding"));
        }
        rs.close();
        ps.close();
        return user;
    }
}
