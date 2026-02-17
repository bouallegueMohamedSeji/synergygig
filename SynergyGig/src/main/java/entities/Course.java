package entities;

public class Course {
    private int id;
    private String title;
    private String description;
    private int instructorId;
    private int skillId;
    private String skillLevel; // Added for Skill Levels

    public Course() {
    }

    public Course(int id, String title, String description, int instructorId, int skillId, String skillLevel) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.instructorId = instructorId;
        this.skillId = skillId;
        this.skillLevel = skillLevel;
    }

    public Course(String title, String description, int instructorId, int skillId, String skillLevel) {
        this.title = title;
        this.description = description;
        this.instructorId = instructorId;
        this.skillId = skillId;
        this.skillLevel = skillLevel;
    }

    // Keep constructors for backward compatibility but default level to "Beginner"
    public Course(String title, String description, int instructorId, int skillId) {
        this(title, description, instructorId, skillId, "Beginner");
    }

    public Course(String title, String description, int instructorId) {
        this(title, description, instructorId, 0, "Beginner");
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

    public String getSkillLevel() {
        return skillLevel;
    }

    public void setSkillLevel(String skillLevel) {
        this.skillLevel = skillLevel;
    }

    @Override
    public String toString() {
        return "Course{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", instructorId=" + instructorId +
                ", skillId=" + skillId +
                ", skillLevel='" + skillLevel + '\'' +
                '}';
    }
}
