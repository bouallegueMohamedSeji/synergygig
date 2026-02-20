package tn.esprit.synergygig.entities;

public class Milestone {

    private int id;
    private int contractId;
    private String title;
    private double amount;
    private String status;

    // 🔹 Constructeur principal (création)
    public Milestone(int contractId, String title, double amount) {
        this.contractId = contractId;
        this.title = title;
        this.amount = amount;
        this.status = "PENDING";
    }

    // 🔹 Constructeur vide (optionnel mais utile)
    public Milestone() {
    }

    // ================= GETTERS =================

    public int getId() {
        return id;
    }

    public int getContractId() {
        return contractId;
    }

    public String getTitle() {
        return title;
    }

    public double getAmount() {
        return amount;
    }

    public String getStatus() {
        return status;
    }

    // ================= SETTERS =================

    public void setId(int id) {
        this.id = id;
    }

    public void setContractId(int contractId) {
        this.contractId = contractId;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}