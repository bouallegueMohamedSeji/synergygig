package tn.esprit.synergygig.entities;

import java.sql.Timestamp;

public class Gig {
    private int id;
    private String title;
    private String description;
    private User user;
    private String status;
    private Timestamp createdAt;

    public Gig() {
    }

    public Gig(int id, String title, String description, User user, String status, Timestamp createdAt) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.user = user;
        this.status = status;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
    
    @Override
    public String toString() {
        return title;
    }
}
