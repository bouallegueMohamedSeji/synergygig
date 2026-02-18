package tn.esprit.synergygig.dao;

import tn.esprit.synergygig.entities.Attendance;
import tn.esprit.synergygig.entities.enums.AttendanceStatus;
import tn.esprit.synergygig.utils.MyDBConnexion;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AttendanceDAO implements CRUD<Attendance> {

    private Connection cnx;
    private UserDAO userDAO;

    public AttendanceDAO() {
        cnx = MyDBConnexion.getInstance().getCnx();
        userDAO = new UserDAO();
    }

    @Override
    public void insertOne(Attendance attendance) throws SQLException {
        String req = "INSERT INTO attendance (user_id, date, check_in, check_out, status) VALUES (?, ?, ?, ?, ?)";
        PreparedStatement ps = cnx.prepareStatement(req);
        ps.setInt(1, attendance.getUser().getId());
        ps.setDate(2, attendance.getDate());
        ps.setTime(3, attendance.getCheckIn());
        ps.setTime(4, attendance.getCheckOut());
        ps.setString(5, attendance.getStatus().toString());
        ps.executeUpdate();
    }

    @Override
    public void updateOne(Attendance attendance) throws SQLException {
        String req = "UPDATE attendance SET user_id=?, date=?, check_in=?, check_out=?, status=? WHERE id=?";
        PreparedStatement ps = cnx.prepareStatement(req);
        ps.setInt(1, attendance.getUser().getId());
        ps.setDate(2, attendance.getDate());
        ps.setTime(3, attendance.getCheckIn());
        ps.setTime(4, attendance.getCheckOut());
        ps.setString(5, attendance.getStatus().toString());
        ps.setInt(6, attendance.getId());
        ps.executeUpdate();
    }

    @Override
    public void deleteOne(Attendance attendance) throws SQLException {
        String req = "DELETE FROM attendance WHERE id=?";
        PreparedStatement ps = cnx.prepareStatement(req);
        ps.setInt(1, attendance.getId());
        ps.executeUpdate();
    }

    @Override
    public List<Attendance> selectAll() throws SQLException {
        List<Attendance> attendances = new ArrayList<>();
        String req = "SELECT * FROM attendance";
        Statement st = cnx.createStatement();
        ResultSet rs = st.executeQuery(req);
        while (rs.next()) {
            Attendance a = new Attendance();
            a.setId(rs.getInt("id"));
            a.setDate(rs.getDate("date"));
            a.setCheckIn(rs.getTime("check_in"));
            a.setCheckOut(rs.getTime("check_out"));
            a.setStatus(AttendanceStatus.valueOf(rs.getString("status")));
            a.setCreatedAt(rs.getTimestamp("created_at"));

            int userId = rs.getInt("user_id");
            a.setUser(userDAO.findById(userId));
            
            attendances.add(a);
        }
        return attendances;
    }
    
    public List<Attendance> findByUserId(int userId) throws SQLException {
        List<Attendance> attendances = new ArrayList<>();
        String req = "SELECT * FROM attendance WHERE user_id = ?";
        PreparedStatement ps = cnx.prepareStatement(req);
        ps.setInt(1, userId);
        ResultSet rs = ps.executeQuery();
         while (rs.next()) {
            Attendance a = new Attendance();
            a.setId(rs.getInt("id"));
            a.setDate(rs.getDate("date"));
            a.setCheckIn(rs.getTime("check_in"));
            a.setCheckOut(rs.getTime("check_out"));
            a.setStatus(AttendanceStatus.valueOf(rs.getString("status")));
            a.setCreatedAt(rs.getTimestamp("created_at"));
            
            a.setUser(userDAO.findById(userId));
            
            attendances.add(a);
        }
        return attendances;
    }
    public Attendance findByUserAndDate(int userId, Date date) throws SQLException {
        String req = "SELECT * FROM attendance WHERE user_id = ? AND date = ?";
        PreparedStatement ps = cnx.prepareStatement(req);
        ps.setInt(1, userId);
        ps.setDate(2, date);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            Attendance a = new Attendance();
            a.setId(rs.getInt("id"));
            a.setDate(rs.getDate("date"));
            a.setCheckIn(rs.getTime("check_in"));
            a.setCheckOut(rs.getTime("check_out"));
            a.setStatus(AttendanceStatus.valueOf(rs.getString("status")));
            a.setCreatedAt(rs.getTimestamp("created_at"));
            
            a.setUser(userDAO.findById(userId));
            return a;
        }
        return null;
    }
}
