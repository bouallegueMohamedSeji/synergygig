package tn.esprit.synergygig.entities;

import tn.esprit.synergygig.entities.enums.ApplicationStatus;
import java.time.LocalDateTime;

public class Application {

    private int id;
    private int offerId;
    private int applicantId;
    private ApplicationStatus status;
    private LocalDateTime appliedAt;
    private String offerTitle;
    private String applicantName;

    // 🔹 constructeur insert
    public Application(int offerId, int applicantId) {
        this.offerId = offerId;
        this.applicantId = applicantId;
        this.status = ApplicationStatus.PENDING;
    }

    // 🔹 constructeur full
    public Application(int id, int offerId, int applicantId,
                       ApplicationStatus status, LocalDateTime appliedAt) {
        this.id = id;
        this.offerId = offerId;
        this.applicantId = applicantId;
        this.status = status;
        this.appliedAt = appliedAt;
    }

    // ===== GETTERS =====
    public int getId() {
        return id;
    }

    public int getOfferId() {
        return offerId;
    }

    public int getApplicantId() {
        return applicantId;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public LocalDateTime getAppliedAt() {
        return appliedAt;
    }

    // ===== SETTERS =====
    public void setId(int id) {
        this.id = id;
    }

    public void setOfferId(int offerId) {
        this.offerId = offerId;
    }

    public void setApplicantId(int applicantId) {
        this.applicantId = applicantId;
    }

    public void setStatus(ApplicationStatus status) {
        this.status = status;
    }

    public void setAppliedAt(LocalDateTime appliedAt) {
        this.appliedAt = appliedAt;
    }
    public String getOfferTitle() {
        return offerTitle;
    }

    public String getApplicantName() {
        return applicantName;
    }

    // setters
    public void setOfferTitle(String offerTitle) {
        this.offerTitle = offerTitle;
    }

    public void setApplicantName(String applicantName) {
        this.applicantName = applicantName;
    }
}
