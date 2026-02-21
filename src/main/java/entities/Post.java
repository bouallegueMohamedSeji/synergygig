package entities;

import java.sql.Timestamp;

public class Post {

    private int id;
    private int authorId;
    private String content;
    private String imageBase64;   // optional embedded image
    private int likesCount;
    private int commentsCount;
    private Timestamp createdAt;

    public Post() {}

    public Post(int authorId, String content) {
        this.authorId = authorId;
        this.content = content;
    }

    public Post(int authorId, String content, String imageBase64) {
        this.authorId = authorId;
        this.content = content;
        this.imageBase64 = imageBase64;
    }

    public Post(int id, int authorId, String content, String imageBase64,
                int likesCount, int commentsCount, Timestamp createdAt) {
        this.id = id;
        this.authorId = authorId;
        this.content = content;
        this.imageBase64 = imageBase64;
        this.likesCount = likesCount;
        this.commentsCount = commentsCount;
        this.createdAt = createdAt;
    }

    // Getters & Setters

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getAuthorId() { return authorId; }
    public void setAuthorId(int authorId) { this.authorId = authorId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getImageBase64() { return imageBase64; }
    public void setImageBase64(String imageBase64) { this.imageBase64 = imageBase64; }

    public int getLikesCount() { return likesCount; }
    public void setLikesCount(int likesCount) { this.likesCount = likesCount; }

    public int getCommentsCount() { return commentsCount; }
    public void setCommentsCount(int commentsCount) { this.commentsCount = commentsCount; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "Post{id=" + id + ", authorId=" + authorId + ", content='" +
                (content != null && content.length() > 50 ? content.substring(0, 50) + "..." : content) + "'}";
    }
}
