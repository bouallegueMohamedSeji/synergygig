package tn.esprit.synergygig.utils;

import tn.esprit.synergygig.entities.User;

public class UserSession {

    private static UserSession instance;

    private User user;

    private UserSession(User user) {
        this.user = user;
    }

    public static UserSession getInstance(User user) {
        if (instance == null) {
            instance = new UserSession(user);
        } else {
            instance.setUser(user);
        }
        return instance;
    }

    public static UserSession getInstance() {
        return instance;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public void cleanUserSession() {
        user = null;
        instance = null;
    }

    @Override
    public String toString() {
        return "UserSession{" +
                "user=" + user +
                '}';
    }
}
