package entities;

import java.sql.Date;
import java.sql.Timestamp;

public class Task {

    private int id;
    private int projectId;
    private int assigneeId;       // DB column: assigned_to  (0 = unassigned)
    private String title;
    private String description;
    private String status;        // TODO, IN_PROGRESS, DONE
    private String priority;      // LOW, MEDIUM, HIGH
    private Date dueDate;
    private Timestamp createdAt;

    public Task() {}

    /** For creating a new task (no id / createdAt yet). */
    public Task(int projectId, int assigneeId, String title, String description,
                String status, String priority, Date dueDate) {
        this.projectId = projectId;
        this.assigneeId = assigneeId;
        this.title = title;
        this.description = description;
        this.status = status;
        this.priority = priority;
        this.dueDate = dueDate;
    }

    /** Full constructor (from DB / API). */
    public Task(int id, int projectId, int assigneeId, String title, String description,
                String status, String priority, Date dueDate, Timestamp createdAt) {
        this.id = id;
        this.projectId = projectId;
        this.assigneeId = assigneeId;
        this.title = title;
        this.description = description;
        this.status = status;
        this.priority = priority;
        this.dueDate = dueDate;
        this.createdAt = createdAt;
    }

    // ── Getters & Setters ──

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getProjectId() { return projectId; }
    public void setProjectId(int projectId) { this.projectId = projectId; }

    public int getAssigneeId() { return assigneeId; }
    public void setAssigneeId(int assigneeId) { this.assigneeId = assigneeId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public Date getDueDate() { return dueDate; }
    public void setDueDate(Date dueDate) { this.dueDate = dueDate; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "Task{id=" + id + ", title='" + title + "', status=" + status + ", priority=" + priority + "}";
    }
}
