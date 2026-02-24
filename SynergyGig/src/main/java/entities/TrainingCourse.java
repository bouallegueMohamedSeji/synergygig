package entities;

import java.sql.Date;
import java.sql.Timestamp;

public class TrainingCourse {

    private int id;
    private String title;
    private String description;
    private String category;       // TECHNICAL, SOFT_SKILLS, COMPLIANCE, ONBOARDING, LEADERSHIP
    private String difficulty;     // BEGINNER, INTERMEDIATE, ADVANCED
    private double durationHours;
    private String instructorName;
    private String megaLink;       // Mega.nz folder/file share link
    private String thumbnailUrl;   // optional thumbnail
    private int maxParticipants;
    private String status;         // DRAFT, ACTIVE, ARCHIVED
    private Date startDate;
    private Date endDate;
    private int createdBy;         // user_id of creator
    private Timestamp createdAt;

    public TrainingCourse() {}

    public TrainingCourse(String title, String description, String category, String difficulty,
                          double durationHours, String instructorName, String megaLink,
                          int maxParticipants, String status, Date startDate, Date endDate, int createdBy) {
        this.title = title;
        this.description = description;
        this.category = category;
        this.difficulty = difficulty;
        this.durationHours = durationHours;
        this.instructorName = instructorName;
        this.megaLink = megaLink;
        this.maxParticipants = maxParticipants;
        this.status = status;
        this.startDate = startDate;
        this.endDate = endDate;
        this.createdBy = createdBy;
    }

    public TrainingCourse(int id, String title, String description, String category, String difficulty,
                          double durationHours, String instructorName, String megaLink, String thumbnailUrl,
                          int maxParticipants, String status, Date startDate, Date endDate,
                          int createdBy, Timestamp createdAt) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.category = category;
        this.difficulty = difficulty;
        this.durationHours = durationHours;
        this.instructorName = instructorName;
        this.megaLink = megaLink;
        this.thumbnailUrl = thumbnailUrl;
        this.maxParticipants = maxParticipants;
        this.status = status;
        this.startDate = startDate;
        this.endDate = endDate;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    // Getters & Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    public double getDurationHours() { return durationHours; }
    public void setDurationHours(double durationHours) { this.durationHours = durationHours; }

    public String getInstructorName() { return instructorName; }
    public void setInstructorName(String instructorName) { this.instructorName = instructorName; }

    public String getMegaLink() { return megaLink; }
    public void setMegaLink(String megaLink) { this.megaLink = megaLink; }

    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }

    public int getMaxParticipants() { return maxParticipants; }
    public void setMaxParticipants(int maxParticipants) { this.maxParticipants = maxParticipants; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Date getStartDate() { return startDate; }
    public void setStartDate(Date startDate) { this.startDate = startDate; }

    public Date getEndDate() { return endDate; }
    public void setEndDate(Date endDate) { this.endDate = endDate; }

    public int getCreatedBy() { return createdBy; }
    public void setCreatedBy(int createdBy) { this.createdBy = createdBy; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "TrainingCourse{" + "id=" + id + ", title='" + title + "', category='" + category + "', status='" + status + "'}";
    }
}
