package entities;

public class Course {
    private int id;
    private String title;
    private String description;
    private int instructorId;
    private int skillId; // Added for Skill Integration

    public Course() {
    }

    public Course(int id, String title, String description, int instructorId, int skillId) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.instructorId = instructorId;
        this.skillId = skillId;
    }

    public Course(String title, String description, int instructorId, int skillId) {
        this.title = title;
        this.description = description;
        this.instructorId = instructorId;
        this.skillId = skillId;
    }

    // Keep constructors for backward compatibility if needed, or update callers
    public Course(String title, String description, int instructorId) {
        this.title = title;
        this.description = description;
        this.instructorId = instructorId;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getInstructorId() {
        return instructorId;
    }

    public void setInstructorId(int instructorId) {
        this.instructorId = instructorId;
    }

    public int getSkillId() {
        return skillId;
    }

    public void setSkillId(int skillId) {
        this.skillId = skillId;
    }

    @Override
    public String toString() {
        return "Course{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", instructorId=" + instructorId +
                ", skillId=" + skillId +
                '}';
    }
}
