package entities;

import java.sql.Date;
import java.sql.Timestamp;

public class Offer {

    // ── Enums as string constants ──
    public static final String TYPE_FULL_TIME   = "FULL_TIME";
    public static final String TYPE_PART_TIME   = "PART_TIME";
    public static final String TYPE_FREELANCE   = "FREELANCE";
    public static final String TYPE_INTERNSHIP  = "INTERNSHIP";
    public static final String TYPE_CONTRACT    = "CONTRACT";

    public static final String STATUS_DRAFT      = "DRAFT";
    public static final String STATUS_PUBLISHED  = "PUBLISHED";
    public static final String STATUS_COMPLETED  = "COMPLETED";
    public static final String STATUS_CANCELLED  = "CANCELLED";
    // legacy aliases
    public static final String STATUS_OPEN       = "OPEN";
    public static final String STATUS_CLOSED     = "CLOSED";

    private int id;
    private String title;
    private String description;
    private String offerType;        // FULL_TIME, PART_TIME, FREELANCE, INTERNSHIP, CONTRACT
    private String status;           // DRAFT, OPEN, CLOSED, CANCELLED
    private String requiredSkills;
    private String location;
    private double amount;
    private String currency;
    private int ownerId;
    private Integer departmentId;
    private Date startDate;
    private Date endDate;
    private Timestamp createdAt;

    public Offer() {}

    /** For creating a new offer (no id / createdAt yet). */
    public Offer(String title, String description, String offerType, String status,
                 String requiredSkills, String location, double amount, String currency,
                 int ownerId, Integer departmentId, Date startDate, Date endDate) {
        this.title = title;
        this.description = description;
        this.offerType = offerType;
        this.status = status;
        this.requiredSkills = requiredSkills;
        this.location = location;
        this.amount = amount;
        this.currency = currency;
        this.ownerId = ownerId;
        this.departmentId = departmentId;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /** Full constructor (from DB / API). */
    public Offer(int id, String title, String description, String offerType, String status,
                 String requiredSkills, String location, double amount, String currency,
                 int ownerId, Integer departmentId, Date startDate, Date endDate, Timestamp createdAt) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.offerType = offerType;
        this.status = status;
        this.requiredSkills = requiredSkills;
        this.location = location;
        this.amount = amount;
        this.currency = currency;
        this.ownerId = ownerId;
        this.departmentId = departmentId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.createdAt = createdAt;
    }

    // ── Getters & Setters ──

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getOfferType() { return offerType; }
    public void setOfferType(String offerType) { this.offerType = offerType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRequiredSkills() { return requiredSkills; }
    public void setRequiredSkills(String requiredSkills) { this.requiredSkills = requiredSkills; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public int getOwnerId() { return ownerId; }
    public void setOwnerId(int ownerId) { this.ownerId = ownerId; }

    public Integer getDepartmentId() { return departmentId; }
    public void setDepartmentId(Integer departmentId) { this.departmentId = departmentId; }

    public Date getStartDate() { return startDate; }
    public void setStartDate(Date startDate) { this.startDate = startDate; }

    public Date getEndDate() { return endDate; }
    public void setEndDate(Date endDate) { this.endDate = endDate; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "Offer{id=" + id + ", title='" + title + "', type=" + offerType + ", status=" + status + "}";
    }
}
