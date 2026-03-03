package entities;

import java.sql.Timestamp;

public class Interview {

    private int id;
    private int organizerId;
    private int candidateId;
    private Timestamp dateTime;
    private String status; // PENDING, ACCEPTED, REJECTED
    private String meetLink;
    private int applicationId;  // linked JobApplication (0 = standalone)
    private int offerId;        // linked Offer (0 = standalone)

    public Interview() {
    }

    public Interview(int organizerId, int candidateId, Timestamp dateTime, String meetLink) {
        this.organizerId = organizerId;
        this.candidateId = candidateId;
        this.dateTime = dateTime;
        this.meetLink = meetLink;
        this.status = "PENDING";
    }

    public Interview(int id, int organizerId, int candidateId, Timestamp dateTime, String status, String meetLink) {
        this.id = id;
        this.organizerId = organizerId;
        this.candidateId = candidateId;
        this.dateTime = dateTime;
        this.status = status;
        this.meetLink = meetLink;
    }

    public Interview(int id, int organizerId, int candidateId, Timestamp dateTime, String status, String meetLink, int applicationId, int offerId) {
        this.id = id;
        this.organizerId = organizerId;
        this.candidateId = candidateId;
        this.dateTime = dateTime;
        this.status = status;
        this.meetLink = meetLink;
        this.applicationId = applicationId;
        this.offerId = offerId;
    }

    // Getters & Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getOrganizerId() {
        return organizerId;
    }

    public void setOrganizerId(int organizerId) {
        this.organizerId = organizerId;
    }

    public int getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(int candidateId) {
        this.candidateId = candidateId;
    }

    public Timestamp getDateTime() {
        return dateTime;
    }

    public void setDateTime(Timestamp dateTime) {
        this.dateTime = dateTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMeetLink() {
        return meetLink;
    }

    public void setMeetLink(String meetLink) {
        this.meetLink = meetLink;
    }

    public int getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(int applicationId) {
        this.applicationId = applicationId;
    }

    public int getOfferId() {
        return offerId;
    }

    public void setOfferId(int offerId) {
        this.offerId = offerId;
    }
}
