package tn.esprit.synergygig.services;

import tn.esprit.synergygig.dao.UserDAO;
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
}
