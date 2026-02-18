package tn.esprit.synergygig.entities;

import tn.esprit.synergygig.entities.enums.Role;

import java.sql.Timestamp;

public class User {
    private int id;
    private String fullName;
    private String email;
    private String password;
    private Role role;
    private Department department;
    private String activeGigsSummary;
    private Timestamp createdAt;

    private Double hourlyRate = 0.0;
    private Double monthlySalary = 0.0;

    public User() {
    }

    public User(String fullName, String email, String password, Role role) {
        this(fullName, email, password, role, 0.0, 0.0);
    }

    public User(String fullName, String email, String password, Role role, Double hourlyRate) {
        this(fullName, email, password, role, hourlyRate, 0.0);
    }

    public User(String fullName, String email, String password, Role role, Double hourlyRate, Double monthlySalary) {
        this.fullName = fullName;
        this.email = email;
        this.password = password;
        this.role = role;
        this.hourlyRate = hourlyRate;
        this.monthlySalary = monthlySalary;
    }

    public User(int id, String fullName, String email, String password, Role role, Timestamp createdAt) {
        this(id, fullName, email, password, role, createdAt, 0.0, 0.0);
    }

    public User(int id, String fullName, String email, String password, Role role, Timestamp createdAt, Double hourlyRate, Double monthlySalary) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.password = password;
        this.role = role;
        this.createdAt = createdAt;
        this.hourlyRate = hourlyRate;
        this.monthlySalary = monthlySalary;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    public String getActiveGigsSummary() {
        return activeGigsSummary;
    }

    public void setActiveGigsSummary(String activeGigsSummary) {
        this.activeGigsSummary = activeGigsSummary;
    }

    public Double getHourlyRate() {
        return hourlyRate;
    }

    public void setHourlyRate(Double hourlyRate) {
        this.hourlyRate = hourlyRate;
    }

    public Double getMonthlySalary() {
        return monthlySalary;
    }

    public void setMonthlySalary(Double monthlySalary) {
        this.monthlySalary = monthlySalary;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", fullName='" + fullName + '\'' +
                ", email='" + email + '\'' +
                ", role=" + role +
                ", createdAt=" + createdAt +
                '}';
    }
}
