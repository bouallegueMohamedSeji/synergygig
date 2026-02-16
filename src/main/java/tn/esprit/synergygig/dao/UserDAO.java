package tn.esprit.synergygig.dao;

import tn.esprit.synergygig.entities.User;
import tn.esprit.synergygig.entities.enums.Role;
import tn.esprit.synergygig.utils.MyDBConnexion;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UserDAO implements CRUD<User> {

    private Connection cnx;

    public UserDAO() {
        cnx = MyDBConnexion.getInstance().getCnx();
    }

    @Override
    public void insertOne(User user) throws SQLException {
        String req = "INSERT INTO users (full_name, email, password, role) VALUES (?, ?, ?, ?)";
        PreparedStatement ps = cnx.prepareStatement(req);
        ps.setString(1, user.getFullName());
        ps.setString(2, user.getEmail());
        ps.setString(3, user.getPassword()); // In a real app, hash this!
        ps.setString(4, user.getRole().toString());
        ps.executeUpdate();
    }

    @Override
    public void updateOne(User user) throws SQLException {
        String req = "UPDATE users SET full_name=?, email=?, password=?, role=? WHERE id=?";
        PreparedStatement ps = cnx.prepareStatement(req);
        ps.setString(1, user.getFullName());
        ps.setString(2, user.getEmail());
        ps.setString(3, user.getPassword());
        ps.setString(4, user.getRole().toString());
        ps.setInt(5, user.getId());
        ps.executeUpdate();
    }

    @Override
    public void deleteOne(User user) throws SQLException {
        String req = "DELETE FROM users WHERE id=?";
        PreparedStatement ps = cnx.prepareStatement(req);
        ps.setInt(1, user.getId());
        ps.executeUpdate();
    }

    @Override
    public List<User> selectAll() throws SQLException {
        List<User> users = new ArrayList<>();
        String req = "SELECT * FROM users";
        Statement st = cnx.createStatement();
        ResultSet rs = st.executeQuery(req);
        while (rs.next()) {
            User u = new User();
            u.setId(rs.getInt("id"));
            u.setFullName(rs.getString("full_name"));
            u.setEmail(rs.getString("email"));
            u.setPassword(rs.getString("password"));
            u.setRole(Role.valueOf(rs.getString("role")));
            u.setCreatedAt(rs.getTimestamp("created_at"));
            users.add(u);
        }
        return users;
    }

    public User findByEmail(String email) throws SQLException {
        String req = "SELECT * FROM users WHERE email = ?";
        PreparedStatement ps = cnx.prepareStatement(req);
        ps.setString(1, email);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            User u = new User();
            u.setId(rs.getInt("id"));
            u.setFullName(rs.getString("full_name"));
            u.setEmail(rs.getString("email"));
            u.setPassword(rs.getString("password"));
            u.setRole(Role.valueOf(rs.getString("role")));
            u.setCreatedAt(rs.getTimestamp("created_at"));
            return u;
        }
        return null;
    }

    public User findById(int id) throws SQLException {
        String req = "SELECT * FROM users WHERE id = ?";
        PreparedStatement ps = cnx.prepareStatement(req);
        ps.setInt(1, id);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            User u = new User();
            u.setId(rs.getInt("id"));
            u.setFullName(rs.getString("full_name"));
            u.setEmail(rs.getString("email"));
            u.setPassword(rs.getString("password"));
            u.setRole(Role.valueOf(rs.getString("role")));
            u.setCreatedAt(rs.getTimestamp("created_at"));
            return u;
        }
        return null;
    }
}
