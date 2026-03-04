package entities;

import java.sql.Timestamp;

public class Department {

    private int id;
    private String name;
    private String description;
    private Integer managerId;
    private double allocatedBudget;
    private Timestamp createdAt;

    public Department() {}

    public Department(String name, String description, Integer managerId, double allocatedBudget) {
        this.name = name;
        this.description = description;
        this.managerId = managerId;
        this.allocatedBudget = allocatedBudget;
    }

    public Department(int id, String name, String description, Integer managerId, double allocatedBudget, Timestamp createdAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.managerId = managerId;
        this.allocatedBudget = allocatedBudget;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getManagerId() { return managerId; }
    public void setManagerId(Integer managerId) { this.managerId = managerId; }

    public double getAllocatedBudget() { return allocatedBudget; }
    public void setAllocatedBudget(double allocatedBudget) { this.allocatedBudget = allocatedBudget; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return name;
    }
}
