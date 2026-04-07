package entities;

import java.sql.Timestamp;

public class TrainingEnrollment {

    private int id;
    private int courseId;
    private int userId;
    private String status;          // ENROLLED, IN_PROGRESS, COMPLETED, DROPPED
    private int progress;           // 0-100
    private Double score;           // nullable
    private Timestamp enrolledAt;
    private Timestamp completedAt;

    public TrainingEnrollment() {}

    public TrainingEnrollment(int courseId, int userId) {
        this.courseId = courseId;
        this.userId = userId;
        this.status = "ENROLLED";
        this.progress = 0;
    }

    public TrainingEnrollment(int id, int courseId, int userId, String status, int progress,
                              Double score, Timestamp enrolledAt, Timestamp completedAt) {
        this.id = id;
        this.courseId = courseId;
        this.userId = userId;
        this.status = status;
        this.progress = progress;
        this.score = score;
        this.enrolledAt = enrolledAt;
        this.completedAt = completedAt;
    }

    // Getters & Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getCourseId() { return courseId; }
    public void setCourseId(int courseId) { this.courseId = courseId; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }

    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }

    public Timestamp getEnrolledAt() { return enrolledAt; }
    public void setEnrolledAt(Timestamp enrolledAt) { this.enrolledAt = enrolledAt; }

    public Timestamp getCompletedAt() { return completedAt; }
    public void setCompletedAt(Timestamp completedAt) { this.completedAt = completedAt; }

    @Override
    public String toString() {
        return "TrainingEnrollment{courseId=" + courseId + ", userId=" + userId + ", status='" + status + "', progress=" + progress + "}";
    }
}
