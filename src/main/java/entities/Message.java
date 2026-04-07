package entities;

import java.sql.Timestamp;

public class Message {

    private int id;
    private int senderId;
    private int roomId;
    private String content;
    private Timestamp timestamp;

    // Default constructor
    public Message() {
    }

    // Constructor for creating new messages
    public Message(int senderId, int roomId, String content) {
        this.senderId = senderId;
        this.roomId = roomId;
        this.content = content;
    }

    // Full constructor (from DB)
    public Message(int id, int senderId, int roomId, String content, Timestamp timestamp) {
        this.id = id;
        this.senderId = senderId;
        this.roomId = roomId;
        this.content = content;
        this.timestamp = timestamp;
    }

    // Getters & Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getSenderId() {
        return senderId;
    }

    public void setSenderId(int senderId) {
        this.senderId = senderId;
    }

    public int getRoomId() {
        return roomId;
    }

    public void setRoomId(int roomId) {
        this.roomId = roomId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "Message{" +
                "id=" + id +
                ", senderId=" + senderId +
                ", roomId=" + roomId +
                ", content='" + content + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
