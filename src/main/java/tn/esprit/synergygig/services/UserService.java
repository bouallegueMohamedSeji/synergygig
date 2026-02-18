package tn.esprit.synergygig.services;

import tn.esprit.synergygig.dao.UserDAO;
import tn.esprit.synergygig.entities.Department;
import tn.esprit.synergygig.entities.User;

import java.sql.SQLException;

public class UserService {

    private final UserDAO userDAO;

    public UserService() {
        this.userDAO = new UserDAO();
    }

    public boolean register(User user) {
        try {
            if (userDAO.findByEmail(user.getEmail()) != null) {
                System.out.println("User with this email already exists.");
                return false;
            }
            userDAO.insertOne(user);
            return true;
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            return false;
        }
    }

    public User authenticate(String email, String password) {
        try {
            User user = userDAO.findByEmail(email);
            if (user != null && user.getPassword().equals(password)) {
                return user;
            }
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
        return null;
    }

    public User getUserById(int id) {
        try {
            return userDAO.findById(id);
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
        return null;
    }
    
    public java.util.List<User> getAllUsers() {
        try {
            return userDAO.selectAll();
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    public void delete(int id) {
        try {
            userDAO.delete(id);
        } catch (SQLException e) {
            System.err.println("Error deleting user: " + e.getMessage());
        }
    }

    public void update(User user) {
        try {
            userDAO.updateOne(user);
        } catch (SQLException e) {
            System.err.println("Error updating user: " + e.getMessage());
        }
    }

    public boolean assignUserToDepartment(User user, Department department) {
        try {
            userDAO.updateUserDepartment(user.getId(), department.getId());
            return true;
        } catch (SQLException e) {
            System.err.println("Error assigning user to department: " + e.getMessage());
            return false;
        }
    }

    public boolean removeUserFromDepartment(int userId) {
        try {
            userDAO.removeUserFromDepartment(userId);
            return true;
        } catch (SQLException e) {
            System.err.println("Error removing user from department: " + e.getMessage());
            return false;
        }
    }

    public java.util.List<User> getUsersByDepartment(int deptId) {
        try {
            return userDAO.findUsersByDepartment(deptId);
        } catch (SQLException e) {
            System.err.println("Error fetching users by department: " + e.getMessage());
            return java.util.Collections.emptyList();
        }
    }
}
