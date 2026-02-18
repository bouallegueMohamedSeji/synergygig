package tn.esprit.synergygig.dao;

import tn.esprit.synergygig.entities.Department;
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
        String req;
        if (user.getDepartment() != null) {
            req = "INSERT INTO users (full_name, email, password, role, hourly_rate, monthly_salary, department_id) VALUES (?, ?, ?, ?, ?, ?, ?)";
        } else {
            req = "INSERT INTO users (full_name, email, password, role, hourly_rate, monthly_salary) VALUES (?, ?, ?, ?, ?, ?)";
        }
        
        PreparedStatement ps = cnx.prepareStatement(req);
        ps.setString(1, user.getFullName());
        ps.setString(2, user.getEmail());
        ps.setString(3, user.getPassword());
        ps.setString(4, user.getRole().toString());
        ps.setDouble(5, user.getHourlyRate());
        ps.setDouble(6, user.getMonthlySalary());
        
        if (user.getDepartment() != null) {
            ps.setInt(7, user.getDepartment().getId());
        }
        
        ps.executeUpdate();
    }

    @Override
    public void updateOne(User user) throws SQLException {
        String req = "UPDATE users SET full_name=?, email=?, password=?, role=?, hourly_rate=?, monthly_salary=? WHERE id=?";
        PreparedStatement ps = cnx.prepareStatement(req);
        ps.setString(1, user.getFullName());
        ps.setString(2, user.getEmail());
        ps.setString(3, user.getPassword());
        ps.setString(4, user.getRole().toString());
        ps.setDouble(5, user.getHourlyRate());
        ps.setDouble(6, user.getMonthlySalary());
        ps.setInt(7, user.getId());
        ps.executeUpdate();
    }

    @Override
    public void deleteOne(User user) throws SQLException {
        delete(user.getId());
    }

    public void delete(int id) throws SQLException {
        String req = "DELETE FROM users WHERE id=?";
        PreparedStatement ps = cnx.prepareStatement(req);
        ps.setInt(1, id);
        ps.executeUpdate();
    }

    @Override
    public List<User> selectAll() throws SQLException {
        List<User> users = new ArrayList<>();
        String req = "SELECT u.*, d.name as dept_name, GROUP_CONCAT(g.title SEPARATOR ', ') as active_gigs " +
                     "FROM users u " +
                     "LEFT JOIN departments d ON u.department_id = d.id " +
                     "LEFT JOIN gigs g ON u.id = g.user_id AND g.status = 'OPEN' " +
                     "GROUP BY u.id";
        Statement st = cnx.createStatement();
        ResultSet rs = st.executeQuery(req);
        while (rs.next()) {
            User u = new User();
            u.setId(rs.getInt("id"));
            u.setFullName(rs.getString("full_name"));
            u.setEmail(rs.getString("email"));
            u.setPassword(rs.getString("password"));
            u.setRole(Role.valueOf(rs.getString("role")));
            u.setHourlyRate(rs.getDouble("hourly_rate"));
            try {
                u.setMonthlySalary(rs.getDouble("monthly_salary"));
            } catch (SQLException e) {
                // Column might not exist yet if script hasn't run
                u.setMonthlySalary(0.0);
            }
            u.setCreatedAt(rs.getTimestamp("created_at"));
            
            String deptName = rs.getString("dept_name");
            if (deptName != null) {
                Department d = new Department();
                d.setName(deptName);
                u.setDepartment(d);
            }
            
            u.setActiveGigsSummary(rs.getString("active_gigs"));
            
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
            u.setHourlyRate(rs.getDouble("hourly_rate"));
            try {
                u.setMonthlySalary(rs.getDouble("monthly_salary"));
            } catch (SQLException e) {
                u.setMonthlySalary(0.0);
            }
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
            u.setHourlyRate(rs.getDouble("hourly_rate"));
            try {
                u.setMonthlySalary(rs.getDouble("monthly_salary"));
            } catch (SQLException e) {
                u.setMonthlySalary(0.0);
            }
            u.setCreatedAt(rs.getTimestamp("created_at"));
            return u;
        }
        return null;
    }

    public void updateUserDepartment(int userId, int deptId) throws SQLException {
        String req = "UPDATE users SET department_id = ? WHERE id = ?";
        PreparedStatement ps = cnx.prepareStatement(req);
        ps.setInt(1, deptId);
        ps.setInt(2, userId);
        ps.executeUpdate();
    }

    public void removeUserFromDepartment(int userId) throws SQLException {
        String req = "UPDATE users SET department_id = NULL WHERE id = ?";
        PreparedStatement ps = cnx.prepareStatement(req);
        ps.setInt(1, userId);
        ps.executeUpdate();
    }

    public List<User> findUsersByDepartment(int deptId) throws SQLException {
        List<User> users = new ArrayList<>();
        String req = "SELECT * FROM users WHERE department_id = ?";
        PreparedStatement ps = cnx.prepareStatement(req);
        ps.setInt(1, deptId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
             User u = new User();
            u.setId(rs.getInt("id"));
            u.setFullName(rs.getString("full_name"));
            u.setEmail(rs.getString("email"));
            // Password omitted for list views
            u.setRole(Role.valueOf(rs.getString("role")));
            u.setHourlyRate(rs.getDouble("hourly_rate"));
            u.setCreatedAt(rs.getTimestamp("created_at"));
            users.add(u);
        }
        return users;
    }
}
