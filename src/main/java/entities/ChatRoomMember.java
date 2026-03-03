package entities;

import java.sql.Timestamp;

/**
 * Represents membership of a user in a chat room.
 * Only relevant for group rooms — DMs use naming convention.
 */
public class ChatRoomMember {

    private int id;
    private int roomId;
    private int userId;
    private String role;     // OWNER, ADMIN, MEMBER
    private Timestamp joinedAt;

    public ChatRoomMember() {}

    public ChatRoomMember(int roomId, int userId, String role) {
        this.roomId = roomId;
        this.userId = userId;
        this.role = role;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getRoomId() { return roomId; }
    public void setRoomId(int roomId) { this.roomId = roomId; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public Timestamp getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Timestamp joinedAt) { this.joinedAt = joinedAt; }

    @Override
    public String toString() {
        return "ChatRoomMember{roomId=" + roomId + ", userId=" + userId + ", role=" + role + "}";
    }
}
