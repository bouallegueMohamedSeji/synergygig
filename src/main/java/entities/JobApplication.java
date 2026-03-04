package entities;

import java.sql.Timestamp;

public class JobApplication {

    // ── Status constants ──
    public static final String STATUS_PENDING     = "PENDING";
    public static final String STATUS_REVIEWED     = "REVIEWED";
    public static final String STATUS_SHORTLISTED  = "SHORTLISTED";
    public static final String STATUS_ACCEPTED     = "ACCEPTED";
    public static final String STATUS_REJECTED     = "REJECTED";
    public static final String STATUS_WITHDRAWN    = "WITHDRAWN";

    private int id;
    private int offerId;
    private int applicantId;
    private String coverLetter;
    private String status;       // PENDING, REVIEWED, SHORTLISTED, ACCEPTED, REJECTED, WITHDRAWN
    private Integer aiScore;
    private String aiFeedback;
    private Timestamp appliedAt;
    private Timestamp reviewedAt;

    public JobApplication() {}

    /** For creating a new application (no id / appliedAt yet). */
    public JobApplication(int offerId, int applicantId, String coverLetter, String status) {
        this.offerId = offerId;
        this.applicantId = applicantId;
        this.coverLetter = coverLetter;
        this.status = status;
    }

    /** Full constructor (from DB / API). */
    public JobApplication(int id, int offerId, int applicantId, String coverLetter, String status,
                          Integer aiScore, String aiFeedback, Timestamp appliedAt, Timestamp reviewedAt) {
        this.id = id;
        this.offerId = offerId;
        this.applicantId = applicantId;
        this.coverLetter = coverLetter;
        this.status = status;
        this.aiScore = aiScore;
        this.aiFeedback = aiFeedback;
        this.appliedAt = appliedAt;
        this.reviewedAt = reviewedAt;
    }

    // ── Getters & Setters ──

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getOfferId() { return offerId; }
    public void setOfferId(int offerId) { this.offerId = offerId; }

    public int getApplicantId() { return applicantId; }
    public void setApplicantId(int applicantId) { this.applicantId = applicantId; }

    public String getCoverLetter() { return coverLetter; }
    public void setCoverLetter(String coverLetter) { this.coverLetter = coverLetter; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getAiScore() { return aiScore; }
    public void setAiScore(Integer aiScore) { this.aiScore = aiScore; }

    public String getAiFeedback() { return aiFeedback; }
    public void setAiFeedback(String aiFeedback) { this.aiFeedback = aiFeedback; }

    public Timestamp getAppliedAt() { return appliedAt; }
    public void setAppliedAt(Timestamp appliedAt) { this.appliedAt = appliedAt; }

    public Timestamp getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Timestamp reviewedAt) { this.reviewedAt = reviewedAt; }

    @Override
    public String toString() {
        return "JobApplication{id=" + id + ", offerId=" + offerId + ", applicantId=" + applicantId + ", status=" + status + "}";
    }
}
