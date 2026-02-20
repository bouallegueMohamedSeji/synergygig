package entities;

import java.sql.Timestamp;

public class Call {
    private int id;
    private int callerId;
    private int calleeId;
    private int roomId;
    private String status; // ringing, active, ended, rejected, missed
    private Timestamp startedAt;
    private Timestamp endedAt;
    private Timestamp createdAt;

    public Call() {}

    public Call(int callerId, int calleeId, int roomId) {
        this.callerId = callerId;
        this.calleeId = calleeId;
        this.roomId = roomId;
        this.status = "ringing";
    }

    public Call(int id, int callerId, int calleeId, int roomId, String status,
                Timestamp startedAt, Timestamp endedAt, Timestamp createdAt) {
        this.id = id;
        this.callerId = callerId;
        this.calleeId = calleeId;
        this.roomId = roomId;
        this.status = status;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.createdAt = createdAt;
    }

    // Getters & Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getCallerId() { return callerId; }
    public void setCallerId(int callerId) { this.callerId = callerId; }

    public int getCalleeId() { return calleeId; }
    public void setCalleeId(int calleeId) { this.calleeId = calleeId; }

    public int getRoomId() { return roomId; }
    public void setRoomId(int roomId) { this.roomId = roomId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getStartedAt() { return startedAt; }
    public void setStartedAt(Timestamp startedAt) { this.startedAt = startedAt; }

    public Timestamp getEndedAt() { return endedAt; }
    public void setEndedAt(Timestamp endedAt) { this.endedAt = endedAt; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public boolean isRinging() { return "ringing".equals(status); }
    public boolean isActive()  { return "active".equals(status); }
    public boolean isEnded()   { return "ended".equals(status) || "rejected".equals(status) || "missed".equals(status); }
}
