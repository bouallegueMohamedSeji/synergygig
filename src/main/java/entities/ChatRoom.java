package entities;

import java.sql.Timestamp;

public class ChatRoom {

    private int id;
    private String name;
    private Timestamp createdAt;
    private String type;      // "group" or "private"
    private int createdBy;    // user id who created the room

    public ChatRoom() {
    }

    public ChatRoom(String name) {
        this.name = name;
        this.type = "group";
    }

    public ChatRoom(String name, String type, int createdBy) {
        this.name = name;
        this.type = type;
        this.createdBy = createdBy;
    }

    public ChatRoom(int id, String name, Timestamp createdAt) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
        this.type = "group";
    }

    public ChatRoom(int id, String name, Timestamp createdAt, String type, int createdBy) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
        this.type = type != null ? type : "group";
        this.createdBy = createdBy;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(int createdBy) {
        this.createdBy = createdBy;
    }

    public boolean isPrivate() {
        return "private".equals(type) || (name != null && name.startsWith("dm_"));
    }

    @Override
    public String toString() {
        return name != null ? name : "Chat Room #" + id;
    }
}
