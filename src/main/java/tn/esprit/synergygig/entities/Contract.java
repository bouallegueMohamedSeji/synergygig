package tn.esprit.synergygig.entities;

import tn.esprit.synergygig.entities.enums.ContractStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Contract {

    private int id;
    private int applicationId;

    private LocalDate startDate;
    private LocalDate endDate;

    private double amount;
    private String terms;

    private ContractStatus status;

    private String blockchainHash;
    private double riskScore;
    private LocalDateTime createdAt;

    // ================= CONSTRUCTORS =================

    public Contract() {}

    public Contract(int applicationId,
                    LocalDate startDate,
                    LocalDate endDate,
                    double amount,
                    String terms) {

        this.applicationId = applicationId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.amount = amount;
        this.terms = terms;
        this.status = ContractStatus.GENERATED;
        this.riskScore = 0.0;
    }

    public Contract(int id,
                    int applicationId,
                    LocalDate startDate,
                    LocalDate endDate,
                    double amount,
                    String terms,
                    ContractStatus status,
                    String blockchainHash,
                    double riskScore,
                    LocalDateTime createdAt) {

        this.id = id;
        this.applicationId = applicationId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.amount = amount;
        this.terms = terms;
        this.status = status;
        this.blockchainHash = blockchainHash;
        this.riskScore = riskScore;
        this.createdAt = createdAt;
    }

    // ================= GETTERS =================

    public int getId() { return id; }
    public int getApplicationId() { return applicationId; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public double getAmount() { return amount; }
    public String getTerms() { return terms; }
    public ContractStatus getStatus() { return status; }
    public String getBlockchainHash() { return blockchainHash; }
    public double getRiskScore() { return riskScore; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    // ================= SETTERS =================

    public void setId(int id) { this.id = id; }
    public void setApplicationId(int applicationId) { this.applicationId = applicationId; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public void setAmount(double amount) { this.amount = amount; }
    public void setTerms(String terms) { this.terms = terms; }
    public void setStatus(ContractStatus status) { this.status = status; }
    public void setBlockchainHash(String blockchainHash) { this.blockchainHash = blockchainHash; }
    public void setRiskScore(double riskScore) { this.riskScore = riskScore; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
