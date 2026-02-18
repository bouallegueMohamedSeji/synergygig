package tn.esprit.synergygig.services;

import tn.esprit.synergygig.dao.LeaveDAO;
import tn.esprit.synergygig.entities.Leave;
import tn.esprit.synergygig.entities.User;
import tn.esprit.synergygig.entities.enums.LeaveStatus;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

public class LeaveService {

    private LeaveDAO leaveDAO;

    public LeaveService() {
        leaveDAO = new LeaveDAO();
    }

    public void requestLeave(Leave leave) {
        try {
            // Check if end date is after start date
            if (leave.getStartDate().after(leave.getEndDate())) {
                System.out.println("End date must be after start date.");
                return;
            }
            leave.setStatus(LeaveStatus.PENDING);
            leaveDAO.insertOne(leave);
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
    }

    public void updateLeaveStatus(Leave leave, LeaveStatus status) {
        try {
            leave.setStatus(status);
            leaveDAO.updateOne(leave);
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
    }

    public List<Leave> getMyLeaves(int userId) {
        try {
            return leaveDAO.findByUserId(userId);
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<Leave> getAllLeaves() {
         try {
            return leaveDAO.selectAll();
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<Leave> getPendingLeaves() {
        try {
            return leaveDAO.findByStatus(LeaveStatus.PENDING);
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            return Collections.emptyList();
        }
    }
}
