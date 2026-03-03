package entities;

import java.sql.Timestamp;

public class Reaction {

    private int id;
    private int postId;
    private int userId;
    private String type;   // "heart", "like", "fire", "laugh", etc.
    private Timestamp createdAt;

    public Reaction() {}

    public Reaction(int postId, int userId, String type) {
        this.postId = postId;
        this.userId = userId;
        this.type = type;
    }

    public Reaction(int id, int postId, int userId, String type, Timestamp createdAt) {
        this.id = id;
        this.postId = postId;
        this.userId = userId;
        this.type = type;
        this.createdAt = createdAt;
    }

    // Getters & Setters

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getPostId() { return postId; }
    public void setPostId(int postId) { this.postId = postId; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "Reaction{id=" + id + ", postId=" + postId + ", userId=" + userId + ", type='" + type + "'}";
    }
}
