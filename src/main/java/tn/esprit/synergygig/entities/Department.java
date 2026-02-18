package tn.esprit.synergygig.entities;

import java.sql.Timestamp;

public class Department {
    private int id;
    private String name;
    private String description;
    private User manager;
    private Timestamp createdAt;

    private Double allocatedBudget = 0.0;

    public Department() {
    }

    public Department(String name, String description, User manager, Double allocatedBudget) {
        this.name = name;
        this.description = description;
        this.manager = manager;
        this.allocatedBudget = allocatedBudget;
    }

    public Department(int id, String name, String description, User manager, Timestamp createdAt, Double allocatedBudget) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.manager = manager;
        this.createdAt = createdAt;
        this.allocatedBudget = allocatedBudget;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public User getManager() {
        return manager;
    }

    public void setManager(User manager) {
        this.manager = manager;
    }

    public Double getAllocatedBudget() {
        return allocatedBudget;
    }

    public void setAllocatedBudget(Double allocatedBudget) {
        this.allocatedBudget = allocatedBudget;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "Department{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", manager=" + manager +
                ", createdAt=" + createdAt +
                '}';
    }
}
