package tn.esprit.synergygig.services;

import tn.esprit.synergygig.dao.AttendanceDAO;
import tn.esprit.synergygig.entities.Attendance;
import tn.esprit.synergygig.entities.User;
import tn.esprit.synergygig.entities.enums.AttendanceStatus;

import java.sql.Date;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

public class AttendanceService {

    private AttendanceDAO attendanceDAO;
    private static final LocalTime WORK_START_TIME = LocalTime.of(9, 0); // 9:00 AM

    public AttendanceService() {
        attendanceDAO = new AttendanceDAO();
    }

    public boolean markCheckIn(User user) {
        try {
            Date today = Date.valueOf(LocalDate.now());
            // Efficient check using specific query
            Attendance existing = attendanceDAO.findByUserAndDate(user.getId(), today);

            if (existing != null) {
                System.out.println("User already checked in today.");
                return false; // Already checked in
            }

            Attendance attendance = new Attendance();
            attendance.setUser(user);
            attendance.setDate(today);
            LocalTime now = LocalTime.now();
            attendance.setCheckIn(Time.valueOf(now));
            
            if (now.isAfter(WORK_START_TIME)) {
                attendance.setStatus(AttendanceStatus.LATE);
            } else {
                attendance.setStatus(AttendanceStatus.PRESENT);
            }
            
            attendanceDAO.insertOne(attendance);
            return true;
            
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            return false;
        }
    }

    public boolean markCheckOut(User user) {
        try {
            Date today = Date.valueOf(LocalDate.now());
            Attendance existing = attendanceDAO.findByUserAndDate(user.getId(), today);

            if (existing == null) {
                System.out.println("No check-in record found for today.");
                return false; // Cannot check out without checking in
            }
            
            if (existing.getCheckOut() != null) {
                System.out.println("User already checked out today.");
                return false; // Already checked out
            }

            // Perform Check Out
            existing.setCheckOut(Time.valueOf(LocalTime.now()));
            attendanceDAO.updateOne(existing);
            return true;
            
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            return false;
        }
    }

    public List<Attendance> getAttendanceHistory(int userId) {
        try {
            return attendanceDAO.findByUserId(userId);
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<Attendance> getAllAttendance() {
        try {
            return attendanceDAO.selectAll();
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            return Collections.emptyList();
        }
    }
}
