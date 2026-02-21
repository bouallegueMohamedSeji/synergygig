package tn.esprit.synergygig.services;

import tn.esprit.synergygig.dao.UserDAO;
import tn.esprit.synergygig.entities.User;

public class UserService {

    private final UserDAO userDAO;

    public UserService() {
        userDAO = new UserDAO();
    }

    public User getById(int id) throws Exception {
        return userDAO.selectById(id);
    }
}
