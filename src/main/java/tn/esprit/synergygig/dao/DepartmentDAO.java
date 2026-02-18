package tn.esprit.synergygig.dao;

import tn.esprit.synergygig.entities.Department;
import tn.esprit.synergygig.entities.User;
import tn.esprit.synergygig.utils.MyDBConnexion;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DepartmentDAO implements CRUD<Department> {

    private Connection cnx;
    private UserDAO userDAO;

    public DepartmentDAO() {
        cnx = MyDBConnexion.getInstance().getCnx();
        userDAO = new UserDAO();
    }

    @Override
    public void insertOne(Department department) throws SQLException {
        String req = "INSERT INTO departments (name, description, manager_id, allocated_budget) VALUES (?, ?, ?, ?)";
        PreparedStatement ps = cnx.prepareStatement(req);
        ps.setString(1, department.getName());
        ps.setString(2, department.getDescription());
        if (department.getManager() != null) {
            ps.setInt(3, department.getManager().getId());
        } else {
            ps.setNull(3, Types.INTEGER);
        }
        ps.setDouble(4, department.getAllocatedBudget());
        ps.executeUpdate();
    }

    @Override
    public void updateOne(Department department) throws SQLException {
        String req = "UPDATE departments SET name=?, description=?, manager_id=?, allocated_budget=? WHERE id=?";
        PreparedStatement ps = cnx.prepareStatement(req);
        ps.setString(1, department.getName());
        ps.setString(2, department.getDescription());
        if (department.getManager() != null) {
            ps.setInt(3, department.getManager().getId());
        } else {
            ps.setNull(3, Types.INTEGER);
        }
        ps.setDouble(4, department.getAllocatedBudget());
        ps.setInt(5, department.getId());
        ps.executeUpdate();
    }

    @Override
    public void deleteOne(Department department) throws SQLException {
        String req = "DELETE FROM departments WHERE id=?";
        PreparedStatement ps = cnx.prepareStatement(req);
        ps.setInt(1, department.getId());
        ps.executeUpdate();
    }

    @Override
    public List<Department> selectAll() throws SQLException {
        List<Department> departments = new ArrayList<>();
        String req = "SELECT * FROM departments";
        Statement st = cnx.createStatement();
        ResultSet rs = st.executeQuery(req);
        while (rs.next()) {
            Department d = new Department();
            d.setId(rs.getInt("id"));
            d.setName(rs.getString("name"));
            d.setDescription(rs.getString("description"));
            d.setAllocatedBudget(rs.getDouble("allocated_budget"));
            d.setCreatedAt(rs.getTimestamp("created_at"));
            
            int managerId = rs.getInt("manager_id");
            if (!rs.wasNull()) {
                d.setManager(userDAO.findById(managerId));
            }
            departments.add(d);
        }
        return departments;
    }
}
