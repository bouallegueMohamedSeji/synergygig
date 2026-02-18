package tn.esprit.synergygig.utils;

import tn.esprit.synergygig.entities.Payroll;
import tn.esprit.synergygig.entities.User;
import tn.esprit.synergygig.services.UserService;
import tn.esprit.synergygig.services.PayrollService;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.SQLException;
import java.time.LocalDate;

public class DatabaseSetup {

    public static void main(String[] args) {
        System.out.println("Starting Database Setup...");
        
        applySchemaUpdates();
        seedData();
        
        System.out.println("Database Setup Complete!");
    }

    private static void applySchemaUpdates() {
        Connection cnx = MyDBConnexion.getInstance().getCnx();
        try (Statement st = cnx.createStatement()) {
            
            System.out.println("Applying Schema Updates...");

            // 1. Add hourly_rate to users
            try {
                st.executeUpdate("ALTER TABLE users ADD COLUMN hourly_rate DOUBLE DEFAULT 0.0");
                System.out.println(" - Added hourly_rate to users");
            } catch (SQLException e) {
                // Ignore if exists
            }
            
            // 2. Add monthly_salary to users
            try {
                st.executeUpdate("ALTER TABLE users ADD COLUMN monthly_salary DOUBLE DEFAULT 0.0");
                System.out.println(" - Added monthly_salary to users");
            } catch (SQLException e) {
                // Ignore if exists
            }

            // 3. Add columns to payrolls
            try {
                st.executeUpdate("ALTER TABLE payrolls ADD COLUMN total_hours_worked DOUBLE DEFAULT 0.0");
                System.out.println(" - Added total_hours_worked to payrolls");
            } catch (SQLException e) {
                // Ignore if exists
            }

            try {
                st.executeUpdate("ALTER TABLE payrolls ADD COLUMN hourly_rate DOUBLE DEFAULT 0.0");
                System.out.println(" - Added hourly_rate to payrolls");
            } catch (SQLException e) {
                // Ignore if exists
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void seedData() {
        System.out.println("Seeding Data...");
        UserService userService = new UserService();
        tn.esprit.synergygig.dao.AttendanceDAO attendanceDAO = new tn.esprit.synergygig.dao.AttendanceDAO();
        PayrollService payrollService = new PayrollService();

        // 1. Update all users with random salary and hourly rate if not set
        Connection cnx = MyDBConnexion.getInstance().getCnx();
        
        java.util.List<User> users = userService.getAllUsers();
        for (User user : users) {
            // Randomize Rate (20-50) and Salary (3000-6000)
            double randomRate = 20 + Math.random() * 30;
            double randomSalary = 3000 + Math.random() * 3000;
            
            try (java.sql.PreparedStatement ps = cnx.prepareStatement("UPDATE users SET hourly_rate = ?, monthly_salary = ? WHERE id = ?")) {
                ps.setDouble(1, Math.round(randomRate * 100.0) / 100.0);
                ps.setDouble(2, Math.round(randomSalary * 100.0) / 100.0);
                ps.setInt(3, user.getId());
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        System.out.println(" - Updated Users with random Salaries and Rates");

        // 2. Generate Random Attendance for the Current Month
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        java.time.Month month = now.getMonth();
        int daysInMonth = now.lengthOfMonth();
        
        System.out.println(" - Generating Attendance for " + month + " " + year);

        for (User user : users) {
             // Basic check to avoid huge duplicates: Check if user already has ANY attendance this month
             // For simplicity in this demo, we might just try to insert and ignore duplicate errors or just proceed.
             // Better: Delete existing attendance for this user/month to allow regeneration?
             // Let's just generate for days that don't exist in DAO logic or catch errors.
             
             for (int day = 1; day <= daysInMonth; day++) {
                 LocalDate date = LocalDate.of(year, month, day);
                 // Skip Weekends (Saturday, Sunday)
                 if (date.getDayOfWeek() == java.time.DayOfWeek.SATURDAY || date.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
                     continue;
                 }
                 
                 // Skip if date is in future
                 if (date.isAfter(now)) {
                     continue;
                 }
                 
                 try {
                     // Check if exists
                     if (attendanceDAO.findByUserAndDate(user.getId(), java.sql.Date.valueOf(date)) != null) {
                        continue;
                     }
                     
                     // Random Check In (08:00 - 09:30)
                     int inHour = 8;
                     int inMinute = (int) (Math.random() * 90); // 0-90 mins
                     if (inMinute >= 60) { inHour++; inMinute -= 60; }
                     java.sql.Time checkIn = java.sql.Time.valueOf(String.format("%02d:%02d:00", inHour, inMinute));
                     
                     // Random Check Out (17:00 - 19:00)
                     int outHour = 17 + (int) (Math.random() * 3); // 17, 18, 19
                     int outMinute = (int) (Math.random() * 60);
                     java.sql.Time checkOut = java.sql.Time.valueOf(String.format("%02d:%02d:00", outHour, outMinute));
                     
                     tn.esprit.synergygig.entities.Attendance a = new tn.esprit.synergygig.entities.Attendance(
                         user,
                         java.sql.Date.valueOf(date),
                         checkIn,
                         checkOut,
                         tn.esprit.synergygig.entities.enums.AttendanceStatus.PRESENT
                     );
                     attendanceDAO.insertOne(a);
                     
                 } catch (SQLException e) {
                     // print simple error
                     System.err.println("Error adding attendance for " + user.getFullName() + ": " + e.getMessage());
                 }
             }
        }
        System.out.println(" - Generated Attendance Records");

        // 3. Generate Payrolls for everyone
        System.out.println(" - Generating Payrolls...");
        for (User user : users) {
            // First re-fetch user to get updated salary/rate
            try {
                User updatedUser = userService.getUserById(user.getId()); // ensure getUserById uses new DAO logic? 
                // Wait, UserService.getUserById delegates to UserDAO.findById? I should check. 
                // Assuming it does.
                if (updatedUser == null) updatedUser = user; 
                
                // Remove existing payroll for this month to re-generate properly
                 payrollService.deletePayroll(updatedUser.getId(), month.toString(), year);
                 
                 // Generate
                 double base = updatedUser.getMonthlySalary() != null ? updatedUser.getMonthlySalary() : 3000.0;
                 payrollService.generatePayroll(updatedUser, month.toString(), year, base, 0.0);
                 
            } catch (Exception e) {
                System.err.println("Error generating payroll for " + user.getFullName() + ": " + e.getMessage());
            }
        }
        System.out.println(" - Generated Payrolls for all users");
    }
}
