package tn.esprit.synergygig.entities;

import java.sql.Timestamp;

public class ForumComment {
    private int id;
    private int forumId;
    private String content;
    private int createdBy;
    private Timestamp createdAt;

    public ForumComment() {
    }

    public ForumComment(int forumId, String content, int createdBy) {
        this.forumId = forumId;
        this.content = content;
        this.createdBy = createdBy;
    }

    public ForumComment(int id, int forumId, String content, int createdBy, Timestamp createdAt) {
        this.id = id;
        this.forumId = forumId;
        this.content = content;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getForumId() {
        return forumId;
    }

    public void setForumId(int forumId) {
        this.forumId = forumId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(int createdBy) {
        this.createdBy = createdBy;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "ForumComment{" +
                "id=" + id +
                ", forumId=" + forumId +
                ", content='" + content + '\'' +
                ", createdBy=" + createdBy +
                ", createdAt=" + createdAt +
                '}';
    }
}
