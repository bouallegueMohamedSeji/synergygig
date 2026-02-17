package entities;

import java.sql.Timestamp;

public class ChatRoom {

    private int id;
    private String name;
    private Timestamp createdAt;

    public ChatRoom() {
    }

    public ChatRoom(String name) {
        this.name = name;
    }

    public ChatRoom(int id, String name, Timestamp createdAt) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
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

    @Override
    public String toString() {
        return name != null ? name : "Chat Room #" + id;
    }
}
