package entities;

import java.sql.Timestamp;

/**
 * Represents a follow relationship between two users.
 * followerId follows followedId.
 */
public class UserFollow {

    public static final String STATUS_ACCEPTED  = "friend_accepted";
    public static final String STATUS_PENDING   = "friend_pending";
    public static final String STATUS_FOLLOWING = "following";

    private int id;
    private int followerId;
    private int followedId;
    private Timestamp createdAt;
    private String status = STATUS_FOLLOWING; // friend_pending | friend_accepted | following

    public UserFollow() {}

    public UserFollow(int followerId, int followedId) {
        this.followerId = followerId;
        this.followedId = followedId;
    }

    public UserFollow(int followerId, int followedId, String status) {
        this.followerId = followerId;
        this.followedId = followedId;
        this.status = status;
    }

    public UserFollow(int id, int followerId, int followedId, Timestamp createdAt) {
        this.id = id;
        this.followerId = followerId;
        this.followedId = followedId;
        this.createdAt = createdAt;
    }

    // Getters & Setters

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getFollowerId() { return followerId; }
    public void setFollowerId(int followerId) { this.followerId = followerId; }

    public int getFollowedId() { return followedId; }
    public void setFollowedId(int followedId) { this.followedId = followedId; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @Override
    public String toString() {
        return "UserFollow{follower=" + followerId + " -> followed=" + followedId + "}";
    }
}
