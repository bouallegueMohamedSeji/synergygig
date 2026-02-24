package entities;

import java.sql.Timestamp;

public class TrainingCertificate {

    private int id;
    private int enrollmentId;
    private int userId;
    private int courseId;
    private String certificateNumber;   // UUID string
    private Timestamp issuedAt;

    public TrainingCertificate() {}

    public TrainingCertificate(int enrollmentId, int userId, int courseId, String certificateNumber) {
        this.enrollmentId = enrollmentId;
        this.userId = userId;
        this.courseId = courseId;
        this.certificateNumber = certificateNumber;
    }

    public TrainingCertificate(int id, int enrollmentId, int userId, int courseId,
                               String certificateNumber, Timestamp issuedAt) {
        this.id = id;
        this.enrollmentId = enrollmentId;
        this.userId = userId;
        this.courseId = courseId;
        this.certificateNumber = certificateNumber;
        this.issuedAt = issuedAt;
    }

    // Getters & Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getEnrollmentId() { return enrollmentId; }
    public void setEnrollmentId(int enrollmentId) { this.enrollmentId = enrollmentId; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public int getCourseId() { return courseId; }
    public void setCourseId(int courseId) { this.courseId = courseId; }

    public String getCertificateNumber() { return certificateNumber; }
    public void setCertificateNumber(String certificateNumber) { this.certificateNumber = certificateNumber; }

    public Timestamp getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Timestamp issuedAt) { this.issuedAt = issuedAt; }

    @Override
    public String toString() {
        return "TrainingCertificate{userId=" + userId + ", courseId=" + courseId + ", cert='" + certificateNumber + "'}";
    }
}
