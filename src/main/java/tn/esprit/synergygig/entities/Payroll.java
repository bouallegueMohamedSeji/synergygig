package tn.esprit.synergygig.entities;

import java.sql.Timestamp;

public class Payroll {
    private int id;
    private User user;
    private String month;
    private int year;
    private double baseSalary;
    private double bonus;
    private double deductions;
    private double netSalary;
    private double totalHoursWorked;
    private double hourlyRate;
    private Timestamp generatedAt;

    public Payroll() {
    }

    public Payroll(User user, String month, int year, double baseSalary, double bonus, double deductions, double totalHoursWorked, double hourlyRate) {
        this.user = user;
        this.month = month;
        this.year = year;
        this.baseSalary = baseSalary;
        this.bonus = bonus;
        this.deductions = deductions;
        this.totalHoursWorked = totalHoursWorked;
        this.hourlyRate = hourlyRate;
        this.netSalary = calculateNetSalary();
    }

    public Payroll(int id, User user, String month, int year, double baseSalary, double bonus, double deductions, double netSalary, double totalHoursWorked, double hourlyRate, Timestamp generatedAt) {
        this.id = id;
        this.user = user;
        this.month = month;
        this.year = year;
        this.baseSalary = baseSalary;
        this.bonus = bonus;
        this.deductions = deductions;
        this.netSalary = netSalary;
        this.totalHoursWorked = totalHoursWorked;
        this.hourlyRate = hourlyRate;
        this.generatedAt = generatedAt;
    }

    public double calculateNetSalary() {
        return this.baseSalary + this.bonus - this.deductions;
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

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public double getBaseSalary() {
        return baseSalary;
    }

    public void setBaseSalary(double baseSalary) {
        this.baseSalary = baseSalary;
        this.bonus = bonus; // Just to be safe if setter called individually
        this.netSalary = calculateNetSalary();
    }

    public double getBonus() {
        return bonus;
    }

    public void setBonus(double bonus) {
        this.bonus = bonus;
        this.netSalary = calculateNetSalary();
    }

    public double getDeductions() {
        return deductions;
    }

    public void setDeductions(double deductions) {
        this.deductions = deductions;
        this.netSalary = calculateNetSalary();
    }

    public double getNetSalary() {
        return netSalary;
    }

    public void setNetSalary(double netSalary) {
        this.netSalary = netSalary;
    }

    public double getTotalHoursWorked() {
        return totalHoursWorked;
    }

    public void setTotalHoursWorked(double totalHoursWorked) {
        this.totalHoursWorked = totalHoursWorked;
    }

    public double getHourlyRate() {
        return hourlyRate;
    }

    public void setHourlyRate(double hourlyRate) {
        this.hourlyRate = hourlyRate;
    }

    public Timestamp getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Timestamp generatedAt) {
        this.generatedAt = generatedAt;
    }

    @Override
    public String toString() {
        return "Payroll{" +
                "id=" + id +
                ", user=" + user +
                ", month='" + month + '\'' +
                ", year=" + year +
                ", baseSalary=" + baseSalary +
                ", bonus=" + bonus +
                ", deductions=" + deductions +
                ", netSalary=" + netSalary +
                ", generatedAt=" + generatedAt +
                '}';
    }
}
