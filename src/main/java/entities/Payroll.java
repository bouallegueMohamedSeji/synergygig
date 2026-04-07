package entities;

import java.sql.Date;
import java.sql.Timestamp;

public class Payroll {

    private int id;
    private int userId;
    private Date month;
    private Integer year;
    private double amount;
    private String status;          // PAID, PENDING
    private double baseSalary;
    private double bonus;
    private double deductions;
    private double netSalary;
    private double totalHoursWorked;
    private double hourlyRate;
    private Timestamp generatedAt;

    public Payroll() {}

    public Payroll(int userId, Date month, Integer year, double baseSalary, double bonus, double deductions,
                   double netSalary, double totalHoursWorked, double hourlyRate) {
        this.userId = userId;
        this.month = month;
        this.year = year;
        this.baseSalary = baseSalary;
        this.bonus = bonus;
        this.deductions = deductions;
        this.netSalary = netSalary;
        this.amount = netSalary;
        this.totalHoursWorked = totalHoursWorked;
        this.hourlyRate = hourlyRate;
        this.status = "PENDING";
    }

    public Payroll(int id, int userId, Date month, Integer year, double amount, String status,
                   double baseSalary, double bonus, double deductions, double netSalary,
                   double totalHoursWorked, double hourlyRate, Timestamp generatedAt) {
        this.id = id;
        this.userId = userId;
        this.month = month;
        this.year = year;
        this.amount = amount;
        this.status = status;
        this.baseSalary = baseSalary;
        this.bonus = bonus;
        this.deductions = deductions;
        this.netSalary = netSalary;
        this.totalHoursWorked = totalHoursWorked;
        this.hourlyRate = hourlyRate;
        this.generatedAt = generatedAt;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public Date getMonth() { return month; }
    public void setMonth(Date month) { this.month = month; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public double getBaseSalary() { return baseSalary; }
    public void setBaseSalary(double baseSalary) { this.baseSalary = baseSalary; }

    public double getBonus() { return bonus; }
    public void setBonus(double bonus) { this.bonus = bonus; }

    public double getDeductions() { return deductions; }
    public void setDeductions(double deductions) { this.deductions = deductions; }

    public double getNetSalary() { return netSalary; }
    public void setNetSalary(double netSalary) { this.netSalary = netSalary; }

    public double getTotalHoursWorked() { return totalHoursWorked; }
    public void setTotalHoursWorked(double totalHoursWorked) { this.totalHoursWorked = totalHoursWorked; }

    public double getHourlyRate() { return hourlyRate; }
    public void setHourlyRate(double hourlyRate) { this.hourlyRate = hourlyRate; }

    public Timestamp getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Timestamp generatedAt) { this.generatedAt = generatedAt; }

    @Override
    public String toString() {
        return "Payroll{" + "userId=" + userId + ", month=" + month + ", netSalary=" + netSalary + ", status=" + status + "}";
    }
}
