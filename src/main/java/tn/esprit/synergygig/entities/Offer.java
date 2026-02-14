package tn.esprit.synergygig.entities;

import tn.esprit.synergygig.entities.enums.OfferStatus;
import tn.esprit.synergygig.entities.enums.OfferType;

import java.time.LocalDateTime;

public class Offer {

    private int id;
    private String title;
    private String description;
    private OfferType type;
    private OfferStatus status;
    private int createdBy;
    private LocalDateTime createdAt;
    private String imageUrl;

    // 🔹 Constructeur vide (OBLIGATOIRE pour JDBC)
    public Offer() {}

    // 🔹 Constructeur pour INSERT
    public Offer(String title, String description,
                 OfferType type, int createdBy, String imageUrl) {

        this.title = title;
        this.description = description;
        this.type = type;
        this.status = OfferStatus.DRAFT;
        this.createdBy = createdBy;
        this.imageUrl = imageUrl;
    }
    // 🔹 Constructeur pour SELECT
    public Offer(int id, String title, String description,
                 OfferType type, OfferStatus status,
                 int createdBy, LocalDateTime createdAt, String imageUrl) {

        this.id = id;
        this.title = title;
        this.description = description;
        this.type = type;
        this.status = status;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.imageUrl = imageUrl;   }

    // ===== GETTERS & SETTERS =====

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

    public OfferType getType() {
        return type;
    }

    public void setType(OfferType type) {
        this.type = type;
    }

    public OfferStatus getStatus() {
        return status;
    }

    public void setStatus(OfferStatus status) {
        this.status = status;
    }
    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public int getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(int createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    // 🔹 Pour debug / affichage
    @Override
    public String toString() {
        return "Offer{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", type=" + type +
                ", status=" + status +
                '}';
    }
}
