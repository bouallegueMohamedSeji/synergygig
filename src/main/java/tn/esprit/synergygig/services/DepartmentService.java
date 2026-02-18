package tn.esprit.synergygig.services;

import tn.esprit.synergygig.dao.DepartmentDAO;
import tn.esprit.synergygig.entities.Department;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

public class DepartmentService {

    private DepartmentDAO departmentDAO;

    public DepartmentService() {
        departmentDAO = new DepartmentDAO();
    }

    public void addDepartment(Department department) {
        try {
            departmentDAO.insertOne(department);
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
    }

    public void updateDepartment(Department department) {
        try {
            departmentDAO.updateOne(department);
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
    }

    public void deleteDepartment(Department department) {
        try {
            departmentDAO.deleteOne(department);
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
    }

    public List<Department> getAllDepartments() {
        try {
            return departmentDAO.selectAll();
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            return Collections.emptyList();
        }
    }

    public boolean existsByName(String name) {
        return getAllDepartments().stream()
                .anyMatch(d -> d.getName().equalsIgnoreCase(name));
    }
}
