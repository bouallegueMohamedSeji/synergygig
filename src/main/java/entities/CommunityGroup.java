package entities;

import java.sql.Timestamp;

/**
 * Represents a community group (like Facebook groups).
 * Users can create, join, and post within groups.
 */
public class CommunityGroup {

    public static final String PRIVACY_PUBLIC = "PUBLIC";
    public static final String PRIVACY_PRIVATE = "PRIVATE";

    private int id;
    private String name;
    private String description;
    private String imageBase64;
    private int creatorId;
    private String privacy;        // PUBLIC or PRIVATE
    private int memberCount;
    private Timestamp createdAt;

    public CommunityGroup() {
        this.privacy = PRIVACY_PUBLIC;
    }

    public CommunityGroup(String name, String description, int creatorId) {
        this.name = name;
        this.description = description;
        this.creatorId = creatorId;
        this.privacy = PRIVACY_PUBLIC;
    }

    public CommunityGroup(int id, String name, String description, String imageBase64,
                           int creatorId, String privacy, int memberCount, Timestamp createdAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.imageBase64 = imageBase64;
        this.creatorId = creatorId;
        this.privacy = privacy;
        this.memberCount = memberCount;
        this.createdAt = createdAt;
    }

    // Getters & Setters

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getImageBase64() { return imageBase64; }
    public void setImageBase64(String imageBase64) { this.imageBase64 = imageBase64; }

    public int getCreatorId() { return creatorId; }
    public void setCreatorId(int creatorId) { this.creatorId = creatorId; }

    public String getPrivacy() { return privacy; }
    public void setPrivacy(String privacy) { this.privacy = privacy; }

    public int getMemberCount() { return memberCount; }
    public void setMemberCount(int memberCount) { this.memberCount = memberCount; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "CommunityGroup{id=" + id + ", name='" + name + "', members=" + memberCount + "}";
    }
}
