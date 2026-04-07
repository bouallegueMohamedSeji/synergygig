package entities;

import java.sql.Timestamp;

/**
 * Represents membership in a community group.
 */
public class GroupMember {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_MEMBER = "MEMBER";

    private int id;
    private int groupId;
    private int userId;
    private String role;           // ADMIN or MEMBER
    private Timestamp joinedAt;

    public GroupMember() {
        this.role = ROLE_MEMBER;
    }

    public GroupMember(int groupId, int userId, String role) {
        this.groupId = groupId;
        this.userId = userId;
        this.role = role;
    }

    public GroupMember(int id, int groupId, int userId, String role, Timestamp joinedAt) {
        this.id = id;
        this.groupId = groupId;
        this.userId = userId;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    // Getters & Setters

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getGroupId() { return groupId; }
    public void setGroupId(int groupId) { this.groupId = groupId; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public Timestamp getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Timestamp joinedAt) { this.joinedAt = joinedAt; }

    @Override
    public String toString() {
        return "GroupMember{groupId=" + groupId + ", userId=" + userId + ", role='" + role + "'}";
    }
}
