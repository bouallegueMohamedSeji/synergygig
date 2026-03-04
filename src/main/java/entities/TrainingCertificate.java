package entities;

import java.sql.Timestamp;

public class TrainingCertificate {

    private int id;
    private int enrollmentId;
    private int userId;
    private int courseId;
    private String certificateNumber;   // UUID string
    private Timestamp issuedAt;

    // ── Signature fields ──
    private int signedByUserId;             // user_id of the HR/Admin who signed (0 = unsigned)
    private String signatureData;           // base64 PNG of the drawn signature
    private Timestamp signedAt;             // when the signature was applied

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

    // ── Signature getters/setters ──
    public int getSignedByUserId() { return signedByUserId; }
    public void setSignedByUserId(int signedByUserId) { this.signedByUserId = signedByUserId; }

    public String getSignatureData() { return signatureData; }
    public void setSignatureData(String signatureData) { this.signatureData = signatureData; }

    public Timestamp getSignedAt() { return signedAt; }
    public void setSignedAt(Timestamp signedAt) { this.signedAt = signedAt; }

    /** Returns true if an HR/Admin has signed this certificate. */
    public boolean isSigned() { return signedByUserId > 0 && signatureData != null && !signatureData.isEmpty(); }

    @Override
    public String toString() {
        return "TrainingCertificate{userId=" + userId + ", courseId=" + courseId + ", cert='" + certificateNumber
                + "', signed=" + isSigned() + "}";
    }
}
