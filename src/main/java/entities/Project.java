package entities;

import java.sql.Date;
import java.sql.Timestamp;

public class Project {

    private int id;
    private String name;
    private String description;
    private int managerId;       // DB column: owner_id
    private Date startDate;
    private Date deadline;
    private String status;       // PLANNING, IN_PROGRESS, ON_HOLD, COMPLETED, CANCELLED
    private Timestamp createdAt;
    private Integer departmentId;  // optional: assign project to a department

    public Project() {}

    /** For creating a new project (no id / createdAt yet). */
    public Project(String name, String description, int managerId,
                   Date startDate, Date deadline, String status) {
        this.name = name;
        this.description = description;
        this.managerId = managerId;
        this.startDate = startDate;
        this.deadline = deadline;
        this.status = status;
    }

    /** Full constructor (from DB / API). */
    public Project(int id, String name, String description, int managerId,
                   Date startDate, Date deadline, String status, Timestamp createdAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.managerId = managerId;
        this.startDate = startDate;
        this.deadline = deadline;
        this.status = status;
        this.createdAt = createdAt;
    }

    // ── Getters & Setters ──

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getManagerId() { return managerId; }
    public void setManagerId(int managerId) { this.managerId = managerId; }

    public Date getStartDate() { return startDate; }
    public void setStartDate(Date startDate) { this.startDate = startDate; }

    public Date getDeadline() { return deadline; }
    public void setDeadline(Date deadline) { this.deadline = deadline; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Integer getDepartmentId() { return departmentId; }
    public void setDepartmentId(Integer departmentId) { this.departmentId = departmentId; }

    @Override
    public String toString() {
        return "Project{id=" + id + ", name='" + name + "', status=" + status + "}";
    }
}
