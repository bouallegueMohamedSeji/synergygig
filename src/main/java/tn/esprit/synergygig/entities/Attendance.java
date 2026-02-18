package tn.esprit.synergygig.entities;

import tn.esprit.synergygig.entities.enums.AttendanceStatus;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;

public class Attendance {
    private int id;
    private User user;
    private Date date;
    private Time checkIn;
    private Time checkOut;
    private AttendanceStatus status;
    private Timestamp createdAt;

    public Attendance() {
    }

    public Attendance(User user, Date date, Time checkIn, Time checkOut, AttendanceStatus status) {
        this.user = user;
        this.date = date;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.status = status;
    }

    public Attendance(int id, User user, Date date, Time checkIn, Time checkOut, AttendanceStatus status, Timestamp createdAt) {
        this.id = id;
        this.user = user;
        this.date = date;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.status = status;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public Time getCheckIn() {
        return checkIn;
    }

    public void setCheckIn(Time checkIn) {
        this.checkIn = checkIn;
    }

    public Time getCheckOut() {
        return checkOut;
    }

    public void setCheckOut(Time checkOut) {
        this.checkOut = checkOut;
    }

    public AttendanceStatus getStatus() {
        return status;
    }

    public void setStatus(AttendanceStatus status) {
        this.status = status;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "Attendance{" +
                "id=" + id +
                ", user=" + user +
                ", date=" + date +
                ", checkIn=" + checkIn +
                ", checkOut=" + checkOut +
                ", status=" + status +
                ", createdAt=" + createdAt +
                '}';
    }
}
