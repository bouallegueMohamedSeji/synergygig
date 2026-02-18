package tn.esprit.synergygig.dao;

import tn.esprit.synergygig.entities.Leave;
import tn.esprit.synergygig.entities.enums.LeaveStatus;
import tn.esprit.synergygig.utils.MyDBConnexion;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class LeaveDAO implements CRUD<Leave> {

    private Connection cnx;
    private UserDAO userDAO;

    public LeaveDAO() {
        cnx = MyDBConnexion.getInstance().getCnx();
        userDAO = new UserDAO();
    }

    @Override
    public void insertOne(Leave leave) throws SQLException {
        String req = "INSERT INTO leaves (user_id, start_date, end_date, reason, status) VALUES (?, ?, ?, ?, ?)";
        PreparedStatement ps = cnx.prepareStatement(req);
        ps.setInt(1, leave.getUser().getId());
        ps.setDate(2, leave.getStartDate());
        ps.setDate(3, leave.getEndDate());
        ps.setString(4, leave.getReason());
        ps.setString(5, leave.getStatus().toString());
        ps.executeUpdate();
    }

    @Override
    public void updateOne(Leave leave) throws SQLException {
        String req = "UPDATE leaves SET user_id=?, start_date=?, end_date=?, reason=?, status=? WHERE id=?";
        PreparedStatement ps = cnx.prepareStatement(req);
        ps.setInt(1, leave.getUser().getId());
        ps.setDate(2, leave.getStartDate());
        ps.setDate(3, leave.getEndDate());
        ps.setString(4, leave.getReason());
        ps.setString(5, leave.getStatus().toString());
        ps.setInt(6, leave.getId());
        ps.executeUpdate();
    }

    @Override
    public void deleteOne(Leave leave) throws SQLException {
        String req = "DELETE FROM leaves WHERE id=?";
        PreparedStatement ps = cnx.prepareStatement(req);
        ps.setInt(1, leave.getId());
        ps.executeUpdate();
    }

    @Override
    public List<Leave> selectAll() throws SQLException {
        List<Leave> leaves = new ArrayList<>();
        String req = "SELECT * FROM leaves";
        Statement st = cnx.createStatement();
        ResultSet rs = st.executeQuery(req);
        while (rs.next()) {
            Leave l = new Leave();
            l.setId(rs.getInt("id"));
            l.setStartDate(rs.getDate("start_date"));
            l.setEndDate(rs.getDate("end_date"));
            l.setReason(rs.getString("reason"));
            l.setStatus(LeaveStatus.valueOf(rs.getString("status")));
            l.setCreatedAt(rs.getTimestamp("created_at"));

            int userId = rs.getInt("user_id");
            l.setUser(userDAO.findById(userId));
            
            leaves.add(l);
        }
        return leaves;
    }
    
    public List<Leave> findByUserId(int userId) throws SQLException {
        List<Leave> leaves = new ArrayList<>();
        String req = "SELECT * FROM leaves WHERE user_id = ?";
        PreparedStatement ps = cnx.prepareStatement(req);
        ps.setInt(1, userId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            Leave l = new Leave();
            l.setId(rs.getInt("id"));
            l.setStartDate(rs.getDate("start_date"));
            l.setEndDate(rs.getDate("end_date"));
            l.setReason(rs.getString("reason"));
            l.setStatus(LeaveStatus.valueOf(rs.getString("status")));
            l.setCreatedAt(rs.getTimestamp("created_at"));
            
            l.setUser(userDAO.findById(userId));
            
            leaves.add(l);
        }
        return leaves;
    }
    
    public List<Leave> findByStatus(LeaveStatus status) throws SQLException {
        List<Leave> leaves = new ArrayList<>();
        String req = "SELECT * FROM leaves WHERE status = ?";
        PreparedStatement ps = cnx.prepareStatement(req);
        ps.setString(1, status.toString());
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
             Leave l = new Leave();
            l.setId(rs.getInt("id"));
            l.setStartDate(rs.getDate("start_date"));
            l.setEndDate(rs.getDate("end_date"));
            l.setReason(rs.getString("reason"));
            l.setStatus(LeaveStatus.valueOf(rs.getString("status")));
            l.setCreatedAt(rs.getTimestamp("created_at"));
            
            int userId = rs.getInt("user_id");
            l.setUser(userDAO.findById(userId));
            
            leaves.add(l);
        }
        return leaves;
    }
}
