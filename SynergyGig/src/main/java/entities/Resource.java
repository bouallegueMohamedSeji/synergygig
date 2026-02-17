package entities;

public class Resource {
    private int id;
    private int courseId;
    private String type; // VIDEO, PDF
    private String url;

    public Resource() {
    }

    public Resource(int id, int courseId, String type, String url) {
        this.id = id;
        this.courseId = courseId;
        this.type = type;
        this.url = url;
    }

    public Resource(int courseId, String type, String url) {
        this.courseId = courseId;
        this.type = type;
        this.url = url;
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public String toString() {
        return "Resource{" +
                "id=" + id +
                ", courseId=" + courseId +
                ", type='" + type + '\'' +
                ", url='" + url + '\'' +
                '}';
    }
}
