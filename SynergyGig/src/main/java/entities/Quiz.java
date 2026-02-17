package entities;

public class Quiz {
    private int id;
    private int courseId;
    private String title;

    public Quiz() {
    }

    public Quiz(int id, int courseId, String title) {
        this.id = id;
        this.courseId = courseId;
        this.title = title;
    }

    public Quiz(int courseId, String title) {
        this.courseId = courseId;
        this.title = title;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getCourseId() {
        return courseId;
    }

    public void setCourseId(int courseId) {
        this.courseId = courseId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @Override
    public String toString() {
        return "Quiz{" +
                "id=" + id +
                ", courseId=" + courseId +
                ", title='" + title + '\'' +
                '}';
    }
}
