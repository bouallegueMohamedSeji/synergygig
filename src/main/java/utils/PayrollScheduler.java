package utils;

import entities.Attendance;
import entities.Payroll;
import entities.User;
import javafx.application.Platform;
import services.ServiceAttendance;
import services.ServicePayroll;
import services.ServiceUser;

import java.sql.Date;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Automatic payroll scheduler that generates payroll on the 1st of each month.
 * <p>
 * On every app startup (and daily thereafter), checks whether payroll for the
 * previous month has been generated. If not, it auto-generates PENDING payroll
 * entries for all non-ADMIN employees based on their attendance hours.
 * </p>
 */
public class PayrollScheduler {

    private static final double DEFAULT_HOURLY_RATE = 15.0;
    private static final String TAG = "[PayrollScheduler]";

    private static PayrollScheduler instance;
    private ScheduledExecutorService scheduler;

    private final ServicePayroll servicePayroll;
    private final ServiceAttendance serviceAttendance;
    private final ServiceUser serviceUser;

    /** Optional callback invoked on the FX thread after payroll is auto-generated. */
    private Runnable onPayrollGenerated;

    private PayrollScheduler() {
        this.servicePayroll = new ServicePayroll();
        this.serviceAttendance = new ServiceAttendance();
        this.serviceUser = new ServiceUser();
    }

    public static synchronized PayrollScheduler getInstance() {
        if (instance == null) {
            instance = new PayrollScheduler();
        }
        return instance;
    }

    /**
     * Set a callback that will be invoked on the JavaFX Application Thread
     * whenever automatic payroll generation completes. Useful for refreshing
     * the payroll list in the UI.
     */
    public void setOnPayrollGenerated(Runnable callback) {
        this.onPayrollGenerated = callback;
    }

    /**
     * Start the scheduler. Runs an initial check after a short delay (5 seconds),
     * then checks daily at midnight.
     */
    public void start() {
        if (scheduler != null && !scheduler.isShutdown()) {
            System.out.println(TAG + " Already running.");
            return;
        }

        scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "payroll-scheduler");
            t.setDaemon(true);
            return t;
        });

        // Initial check after 5 seconds (let the app finish loading)
        scheduler.schedule(this::checkAndGenerate, 5, TimeUnit.SECONDS);

        // Schedule daily check — calculate delay until next midnight
        long minutesUntilMidnight = minutesUntilNextMidnight();
        scheduler.scheduleAtFixedRate(
                this::checkAndGenerate,
                minutesUntilMidnight,
                TimeUnit.DAYS.toMinutes(1),
                TimeUnit.MINUTES
        );

        System.out.println(TAG + " Started. Next daily check in " + minutesUntilMidnight + " minutes.");
    }

    /**
     * Stop the scheduler gracefully.
     */
    public void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            System.out.println(TAG + " Stopped.");
        }
    }

    /**
     * Core logic: check if payroll for the previous month needs to be generated.
     * Called automatically by the scheduler.
     */
    private void checkAndGenerate() {
        try {
            LocalDate today = LocalDate.now();
            // Target: the previous month (generate payroll for work done last month)
            LocalDate previousMonth = today.minusMonths(1).withDayOfMonth(1);

            System.out.println(TAG + " Checking payroll for " +
                    previousMonth.getMonth() + " " + previousMonth.getYear() + "...");

            List<User> allUsers = serviceUser.recuperer();
            if (allUsers == null || allUsers.isEmpty()) {
                System.out.println(TAG + " No users found. Skipping.");
                return;
            }

            // Get existing payrolls to skip already-generated entries
            Set<Integer> existingUserIds = new HashSet<>();
            List<Payroll> existingPayrolls = servicePayroll.recuperer();
            for (Payroll ep : existingPayrolls) {
                if (ep.getMonth() != null &&
                        ep.getMonth().toLocalDate().getMonth() == previousMonth.getMonth() &&
                        ep.getMonth().toLocalDate().getYear() == previousMonth.getYear()) {
                    existingUserIds.add(ep.getUserId());
                }
            }

            int generated = 0;
            int skipped = 0;

            for (User u : allUsers) {
                // Skip admin users
                if ("ADMIN".equals(u.getRole())) continue;

                // Skip if payroll already exists
                if (existingUserIds.contains(u.getId())) {
                    skipped++;
                    continue;
                }

                try {
                    // Calculate attendance hours for the previous month
                    List<Attendance> userAtt = serviceAttendance.getByUser(u.getId());
                    double totalHours = userAtt.stream()
                            .filter(a -> a.getDate() != null &&
                                    a.getDate().toLocalDate().getMonth() == previousMonth.getMonth() &&
                                    a.getDate().toLocalDate().getYear() == previousMonth.getYear())
                            .mapToDouble(Attendance::getHoursWorked)
                            .sum();

                    double hourlyRate = u.getHourlyRate() > 0 ? u.getHourlyRate() : DEFAULT_HOURLY_RATE;
                    double baseSalary;
                    if (u.getMonthlySalary() > 0) {
                        baseSalary = u.getMonthlySalary();
                    } else {
                        baseSalary = totalHours * hourlyRate;
                    }

                    // Calculate deductions from absent days
                    long absentDays = userAtt.stream()
                            .filter(a -> a.getDate() != null &&
                                    a.getDate().toLocalDate().getMonth() == previousMonth.getMonth() &&
                                    a.getDate().toLocalDate().getYear() == previousMonth.getYear() &&
                                    "ABSENT".equals(a.getStatus()))
                            .count();
                    double dailyRate = baseSalary > 0 ? baseSalary / 22.0 : 0;
                    double deductions = absentDays * dailyRate;
                    double netSalary = baseSalary - deductions;

                    Payroll p = new Payroll();
                    p.setUserId(u.getId());
                    p.setMonth(Date.valueOf(previousMonth));
                    p.setYear(previousMonth.getYear());
                    p.setBaseSalary(baseSalary);
                    p.setBonus(0);
                    p.setDeductions(deductions);
                    p.setNetSalary(netSalary);
                    p.setAmount(netSalary);
                    p.setTotalHoursWorked(totalHours);
                    p.setHourlyRate(hourlyRate);
                    p.setStatus("PENDING");

                    servicePayroll.ajouter(p);
                    generated++;
                } catch (SQLException e) {
                    System.err.println(TAG + " Failed for user " + u.getId() + ": " + e.getMessage());
                }
            }

            if (generated > 0) {
                System.out.println(TAG + " Auto-generated payroll for " + generated + " employees. " +
                        skipped + " skipped (already existed).");
                // Notify UI on FX thread
                if (onPayrollGenerated != null) {
                    Platform.runLater(onPayrollGenerated);
                }
            } else {
                System.out.println(TAG + " No new payroll to generate. " +
                        skipped + " employees already have payroll for " +
                        previousMonth.getMonth() + " " + previousMonth.getYear() + ".");
            }

        } catch (SQLException e) {
            System.err.println(TAG + " Error: " + e.getMessage());
        } catch (Exception e) {
            System.err.println(TAG + " Unexpected error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Manually trigger payroll generation for a specific month.
     * Runs on a background thread and notifies the UI when done.
     *
     * @param targetMonth the 1st day of the month to generate payroll for
     */
    public void generateForMonth(LocalDate targetMonth) {
        AppThreadPool.io(() -> {
            try {
                LocalDate monthStart = targetMonth.withDayOfMonth(1);
                System.out.println(TAG + " Manual trigger for " + monthStart.getMonth() + " " + monthStart.getYear());

                List<User> allUsers = serviceUser.recuperer();
                Set<Integer> existingUserIds = new HashSet<>();
                List<Payroll> existingPayrolls = servicePayroll.recuperer();
                for (Payroll ep : existingPayrolls) {
                    if (ep.getMonth() != null &&
                            ep.getMonth().toLocalDate().getMonth() == monthStart.getMonth() &&
                            ep.getMonth().toLocalDate().getYear() == monthStart.getYear()) {
                        existingUserIds.add(ep.getUserId());
                    }
                }

                int generated = 0;
                for (User u : allUsers) {
                    if ("ADMIN".equals(u.getRole())) continue;
                    if (existingUserIds.contains(u.getId())) continue;

                    List<Attendance> userAtt = serviceAttendance.getByUser(u.getId());
                    double totalHours = userAtt.stream()
                            .filter(a -> a.getDate() != null &&
                                    a.getDate().toLocalDate().getMonth() == monthStart.getMonth() &&
                                    a.getDate().toLocalDate().getYear() == monthStart.getYear())
                            .mapToDouble(Attendance::getHoursWorked)
                            .sum();

                    double hourlyRate = u.getHourlyRate() > 0 ? u.getHourlyRate() : DEFAULT_HOURLY_RATE;
                    double baseSalary = u.getMonthlySalary() > 0 ? u.getMonthlySalary() : totalHours * hourlyRate;

                    long absentDays = userAtt.stream()
                            .filter(a -> a.getDate() != null &&
                                    a.getDate().toLocalDate().getMonth() == monthStart.getMonth() &&
                                    a.getDate().toLocalDate().getYear() == monthStart.getYear() &&
                                    "ABSENT".equals(a.getStatus()))
                            .count();
                    double dailyRate = baseSalary > 0 ? baseSalary / 22.0 : 0;
                    double deductions = absentDays * dailyRate;
                    double netSalary = baseSalary - deductions;

                    Payroll p = new Payroll();
                    p.setUserId(u.getId());
                    p.setMonth(Date.valueOf(monthStart));
                    p.setYear(monthStart.getYear());
                    p.setBaseSalary(baseSalary);
                    p.setBonus(0);
                    p.setDeductions(deductions);
                    p.setNetSalary(netSalary);
                    p.setAmount(netSalary);
                    p.setTotalHoursWorked(totalHours);
                    p.setHourlyRate(hourlyRate);
                    p.setStatus("PENDING");

                    servicePayroll.ajouter(p);
                    generated++;
                }

                final int gen = generated;
                System.out.println(TAG + " Generated " + gen + " payroll entries for " +
                        monthStart.getMonth() + " " + monthStart.getYear());
                if (gen > 0 && onPayrollGenerated != null) {
                    Platform.runLater(onPayrollGenerated);
                }
            } catch (SQLException e) {
                System.err.println(TAG + " Manual generation failed: " + e.getMessage());
            }
        });
    }

    private long minutesUntilNextMidnight() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextMidnight = now.toLocalDate().plusDays(1).atTime(LocalTime.MIDNIGHT);
        return ChronoUnit.MINUTES.between(now, nextMidnight);
    }
}
