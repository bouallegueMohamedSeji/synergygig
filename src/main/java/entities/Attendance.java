package entities;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;

public class Attendance {

    private int id;
    private int userId;
    private Date date;
    private Time checkIn;
    private Time checkOut;
    private String status; // PRESENT, ABSENT, LATE, EXCUSED
    private Timestamp createdAt;

    public Attendance() {}

    public Attendance(int userId, Date date, Time checkIn, Time checkOut, String status) {
        this.userId = userId;
        this.date = date;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.status = status;
    }

    public Attendance(int id, int userId, Date date, Time checkIn, Time checkOut, String status, Timestamp createdAt) {
        this.id = id;
        this.userId = userId;
        this.date = date;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.status = status;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public Date getDate() { return date; }
    public void setDate(Date date) { this.date = date; }

    public Time getCheckIn() { return checkIn; }
    public void setCheckIn(Time checkIn) { this.checkIn = checkIn; }

    public Time getCheckOut() { return checkOut; }
    public void setCheckOut(Time checkOut) { this.checkOut = checkOut; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    /** Calculate hours worked from check-in and check-out times. */
    public double getHoursWorked() {
        if (checkIn != null && checkOut != null) {
            long diffMs = checkOut.getTime() - checkIn.getTime();
            return diffMs / (1000.0 * 60 * 60);
        }
        return 0.0;
    }

    @Override
    public String toString() {
        return "Attendance{" + "userId=" + userId + ", date=" + date + ", status=" + status + "}";
    }
}
