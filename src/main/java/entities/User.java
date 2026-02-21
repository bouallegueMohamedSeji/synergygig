package entities;

import java.sql.Timestamp;

public class User {

    private int id;
    private String email;
    private String password;
    private String firstName;
    private String lastName;
    private String role; // ADMIN, HR_MANAGER, EMPLOYEE, PROJECT_OWNER, GIG_WORKER
    private Timestamp createdAt;
    private String avatarPath;
    private String faceEncoding;
    private boolean isOnline = false;
    private boolean isVerified = true; // default true for backward compat
    private boolean isActive = true;   // freeze/unfreeze support

    // HR fields
    private Integer departmentId;
    private double hourlyRate = 0.0;
    private double monthlySalary = 0.0;

    // Default constructor
    public User() {
    }

    // Constructor without id and createdAt (for registration)
    public User(String email, String password, String firstName, String lastName, String role) {
        this.email = email;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
        this.role = role;
    }

    // Full constructor (for fetching from DB)
    public User(int id, String email, String password, String firstName, String lastName, String role,
            Timestamp createdAt) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
        this.role = role;
        this.createdAt = createdAt;
    }

    // Full constructor with avatar
    public User(int id, String email, String password, String firstName, String lastName, String role,
            Timestamp createdAt, String avatarPath) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
        this.role = role;
        this.createdAt = createdAt;
        this.avatarPath = avatarPath;
    }

    // Full constructor with avatar + face encoding
    public User(int id, String email, String password, String firstName, String lastName, String role,
            Timestamp createdAt, String avatarPath, String faceEncoding) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
        this.role = role;
        this.createdAt = createdAt;
        this.avatarPath = avatarPath;
        this.faceEncoding = faceEncoding;
    }

    // Getters & Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
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

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public String getAvatarPath() {
        return avatarPath;
    }

    public void setAvatarPath(String avatarPath) {
        this.avatarPath = avatarPath;
    }

    public String getFaceEncoding() {
        return faceEncoding;
    }

    public void setFaceEncoding(String faceEncoding) {
        this.faceEncoding = faceEncoding;
    }

    public boolean isVerified() {
        return isVerified;
    }

    public void setVerified(boolean verified) {
        isVerified = verified;
    }

    public boolean isOnline() {
        return isOnline;
    }

    public void setOnline(boolean online) {
        isOnline = online;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    // HR field getters/setters
    public Integer getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Integer departmentId) {
        this.departmentId = departmentId;
    }

    public double getHourlyRate() {
        return hourlyRate;
    }

    public void setHourlyRate(double hourlyRate) {
        this.hourlyRate = hourlyRate;
    }

    public double getMonthlySalary() {
        return monthlySalary;
    }

    public void setMonthlySalary(double monthlySalary) {
        this.monthlySalary = monthlySalary;
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public boolean hasFaceEnrolled() {
        return faceEncoding != null && !faceEncoding.isEmpty();
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", email='" + email + '\'' +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", role='" + role + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
