package entities;

import java.sql.Date;
import java.sql.Timestamp;

public class Leave {

    private int id;
    private int userId;
    private String type;      // SICK, VACATION, UNPAID
    private Date startDate;
    private Date endDate;
    private String reason;
    private String status;    // PENDING, APPROVED, REJECTED
    private Timestamp createdAt;

    public Leave() {}

    public Leave(int userId, String type, Date startDate, Date endDate, String reason) {
        this.userId = userId;
        this.type = type;
        this.startDate = startDate;
        this.endDate = endDate;
        this.reason = reason;
        this.status = "PENDING";
    }

    public Leave(int id, int userId, String type, Date startDate, Date endDate, String reason, String status, Timestamp createdAt) {
        this.id = id;
        this.userId = userId;
        this.type = type;
        this.startDate = startDate;
        this.endDate = endDate;
        this.reason = reason;
        this.status = status;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Date getStartDate() { return startDate; }
    public void setStartDate(Date startDate) { this.startDate = startDate; }

    public Date getEndDate() { return endDate; }
    public void setEndDate(Date endDate) { this.endDate = endDate; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    /** Calculate the number of days for this leave. */
    public long getDays() {
        if (startDate != null && endDate != null) {
            long diffMs = endDate.getTime() - startDate.getTime();
            return (diffMs / (1000 * 60 * 60 * 24)) + 1;
        }
        return 0;
    }

    @Override
    public String toString() {
        return "Leave{" + "userId=" + userId + ", type=" + type + ", " + startDate + " to " + endDate + ", status=" + status + "}";
    }
}
