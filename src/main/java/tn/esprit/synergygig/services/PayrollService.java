package tn.esprit.synergygig.services;

import tn.esprit.synergygig.dao.AttendanceDAO;
import tn.esprit.synergygig.dao.PayrollDAO;
import tn.esprit.synergygig.entities.Attendance;
import tn.esprit.synergygig.entities.Payroll;
import tn.esprit.synergygig.entities.User;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

public class PayrollService {

    private PayrollDAO payrollDAO;
    private AttendanceDAO attendanceDAO;
    private static final int WORKING_DAYS_PER_MONTH = 22;

    public PayrollService() {
        payrollDAO = new PayrollDAO();
        attendanceDAO = new AttendanceDAO();
    }

    public Payroll generatePayroll(User user, String month, int year, double baseSalary, double manualBonus) {
        // 1. Calculate Total Working Hours
        double totalHours = calculateTotalHours(user.getId(), month, year);
        
        // 2. Calculate Overtime Bonus
        // Threshold: 160 hours
        // Rate: 1.5x Hourly Rate
        double overtimeBonus = 0.0;
        double hourlyRate = user.getHourlyRate() != null ? user.getHourlyRate() : 0.0;
        
        if (totalHours > 160) {
            double overtimeHours = totalHours - 160;
            overtimeBonus = overtimeHours * (hourlyRate * 1.5);
        }
        
        double totalBonus = manualBonus + overtimeBonus;

        // 3. Calculate Deductions (Absent Days)
        // Logic: If they work less than expected days, we deduct.
        // For simplicity, let's keep existing logic based on "days present" count for now, 
        // but ideally this should also differ based on hours. 
        // Let's stick to the previous day-based deduction for consistency with legacy, 
        // or we could switch to hour-based deduction. 
        // Let's keep day-based for now to strictly follow "Overtime" request.
        
        int daysPresent = getDaysPresent(user.getId(), month, year);
        int absentDays = Math.max(0, WORKING_DAYS_PER_MONTH - daysPresent);
        double dailySalary = baseSalary / WORKING_DAYS_PER_MONTH;
        double deductions = absentDays * dailySalary;
        
        // Round to 2 decimals
        deductions = Math.round(deductions * 100.0) / 100.0;
        totalBonus = Math.round(totalBonus * 100.0) / 100.0;

        Payroll payroll = new Payroll(user, month, year, baseSalary, totalBonus, deductions, totalHours, hourlyRate);
        
        try {
            payrollDAO.insertOne(payroll);
        } catch (SQLException e) {
             System.err.println("Error saving payroll: " + e.getMessage());
        }
        
        return payroll;
    }

    private double calculateTotalHours(int userId, String month, int year) {
        double totalHours = 0.0;
        try {
            List<Attendance> history = attendanceDAO.findByUserId(userId);
            for (Attendance a : history) {
                LocalDate date = a.getDate().toLocalDate();
                if (date.getMonth().toString().equalsIgnoreCase(month) && date.getYear() == year) {
                    if (a.getCheckIn() != null && a.getCheckOut() != null) {
                        long diffMs = a.getCheckOut().getTime() - a.getCheckIn().getTime();
                        double hours = (diffMs / (1000.0 * 60 * 60));
                        totalHours += hours;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error calculating hours: " + e.getMessage());
        }
        return Math.round(totalHours * 100.0) / 100.0;
    }

    private int getDaysPresent(int userId, String month, int year) {
        try {
            List<Attendance> history = attendanceDAO.findByUserId(userId);
            return (int) history.stream()
                    .filter(a -> {
                        LocalDate date = a.getDate().toLocalDate();
                        return date.getMonth().toString().equalsIgnoreCase(month) && date.getYear() == year;
                    })
                    .count();
        } catch (SQLException e) {
            return 0;
        }
    }

    public List<Payroll> getAllPayrolls() {
        try {
            return payrollDAO.selectAll();
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            return Collections.emptyList();
        }
    }
    
    public List<Payroll> getMyPayrolls(int userId) {
        try {
            return payrollDAO.findByUserId(userId);
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            return Collections.emptyList();
        }
    }

    public void deletePayroll(int userId, String month, int year) {
        try {
            payrollDAO.deleteByUserIdAndMonth(userId, month, year);
        } catch (SQLException e) {
            System.err.println("Error deleting payroll: " + e.getMessage());
        }
    }
    public void updatePayroll(Payroll payroll) {
        try {
            // Recalculate net salary before updating
            payroll.setNetSalary(payroll.calculateNetSalary());
            payrollDAO.updateOne(payroll);
        } catch (SQLException e) {
            System.err.println("Error updating payroll: " + e.getMessage());
        }
    }
}
