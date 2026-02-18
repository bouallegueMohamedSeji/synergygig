package tn.esprit.synergygig.dao;

import tn.esprit.synergygig.entities.Payroll;
import tn.esprit.synergygig.utils.MyDBConnexion;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PayrollDAO implements CRUD<Payroll> {

    private Connection cnx;
    private UserDAO userDAO;

    public PayrollDAO() {
        cnx = MyDBConnexion.getInstance().getCnx();
        userDAO = new UserDAO();
    }

    @Override
    public void insertOne(Payroll payroll) throws SQLException {
        String req = "INSERT INTO payrolls (user_id, month, year, base_salary, bonus, deductions, net_salary, total_hours_worked, hourly_rate) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        PreparedStatement ps = cnx.prepareStatement(req);
        ps.setInt(1, payroll.getUser().getId());
        ps.setString(2, payroll.getMonth());
        ps.setInt(3, payroll.getYear());
        ps.setDouble(4, payroll.getBaseSalary());
        ps.setDouble(5, payroll.getBonus());
        ps.setDouble(6, payroll.getDeductions());
        ps.setDouble(7, payroll.getNetSalary());
        ps.setDouble(8, payroll.getTotalHoursWorked());
        ps.setDouble(9, payroll.getHourlyRate());
        ps.executeUpdate();
    }

    @Override
    public void updateOne(Payroll payroll) throws SQLException {
        String req = "UPDATE payrolls SET user_id=?, month=?, year=?, base_salary=?, bonus=?, deductions=?, net_salary=?, total_hours_worked=?, hourly_rate=? WHERE id=?";
        PreparedStatement ps = cnx.prepareStatement(req);
        ps.setInt(1, payroll.getUser().getId());
        ps.setString(2, payroll.getMonth());
        ps.setInt(3, payroll.getYear());
        ps.setDouble(4, payroll.getBaseSalary());
        ps.setDouble(5, payroll.getBonus());
        ps.setDouble(6, payroll.getDeductions());
        ps.setDouble(7, payroll.getNetSalary());
        ps.setDouble(8, payroll.getTotalHoursWorked());
        ps.setDouble(9, payroll.getHourlyRate());
        ps.setInt(10, payroll.getId());
        ps.executeUpdate();
    }

    @Override
    public void deleteOne(Payroll payroll) throws SQLException {
        String req = "DELETE FROM payrolls WHERE id=?";
        PreparedStatement ps = cnx.prepareStatement(req);
        ps.setInt(1, payroll.getId());
        ps.executeUpdate();
    }

    @Override
    public List<Payroll> selectAll() throws SQLException {
        List<Payroll> payrolls = new ArrayList<>();
        String req = "SELECT * FROM payrolls";
        Statement st = cnx.createStatement();
        ResultSet rs = st.executeQuery(req);
        while (rs.next()) {
            Payroll p = new Payroll();
            p.setId(rs.getInt("id"));
            p.setMonth(rs.getString("month"));
            p.setYear(rs.getInt("year"));
            p.setBaseSalary(rs.getDouble("base_salary"));
            p.setBonus(rs.getDouble("bonus"));
            p.setDeductions(rs.getDouble("deductions"));
            p.setNetSalary(rs.getDouble("net_salary"));
            p.setTotalHoursWorked(rs.getDouble("total_hours_worked"));
            p.setHourlyRate(rs.getDouble("hourly_rate"));
            p.setGeneratedAt(rs.getTimestamp("generated_at"));

            int userId = rs.getInt("user_id");
            p.setUser(userDAO.findById(userId));
            
            payrolls.add(p);
        }
        return payrolls;
    }
    
    public List<Payroll> findByUserId(int userId) throws SQLException {
        List<Payroll> payrolls = new ArrayList<>();
        String req = "SELECT * FROM payrolls WHERE user_id = ?";
        PreparedStatement ps = cnx.prepareStatement(req);
        ps.setInt(1, userId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
             Payroll p = new Payroll();
            p.setId(rs.getInt("id"));
            p.setMonth(rs.getString("month"));
            p.setYear(rs.getInt("year"));
            p.setBaseSalary(rs.getDouble("base_salary"));
            p.setBonus(rs.getDouble("bonus"));
            p.setDeductions(rs.getDouble("deductions"));
            p.setNetSalary(rs.getDouble("net_salary"));
            p.setTotalHoursWorked(rs.getDouble("total_hours_worked"));
            p.setHourlyRate(rs.getDouble("hourly_rate"));
            p.setGeneratedAt(rs.getTimestamp("generated_at"));
            
            p.setUser(userDAO.findById(userId));
            
            payrolls.add(p);
        }
        return payrolls;
    }

    public void deleteByUserIdAndMonth(int userId, String month, int year) throws SQLException {
        String req = "DELETE FROM payrolls WHERE user_id=? AND month=? AND year=?";
        PreparedStatement ps = cnx.prepareStatement(req);
        ps.setInt(1, userId);
        ps.setString(2, month);
        ps.setInt(3, year);
        ps.executeUpdate();
    }
}
