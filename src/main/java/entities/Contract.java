package entities;

import java.sql.Date;
import java.sql.Timestamp;

public class Contract {

    // ── Status constants ──
    public static final String STATUS_DRAFT              = "DRAFT";
    public static final String STATUS_PENDING_REVIEW     = "PENDING_REVIEW";     // sent to applicant for review
    public static final String STATUS_COUNTER_PROPOSED   = "COUNTER_PROPOSED";   // applicant counter-offered
    public static final String STATUS_PENDING_SIGNATURE  = "PENDING_SIGNATURE";
    public static final String STATUS_ACTIVE             = "ACTIVE";
    public static final String STATUS_COMPLETED          = "COMPLETED";
    public static final String STATUS_TERMINATED         = "TERMINATED";
    public static final String STATUS_DISPUTED           = "DISPUTED";

    private int id;
    private int offerId;
    private int applicantId;
    private int ownerId;
    private String terms;
    private double amount;
    private String currency;
    private String status;
    private Integer riskScore;
    private String riskFactors;
    private String blockchainHash;
    private String qrCodeUrl;
    private Timestamp signedAt;
    private Date startDate;
    private Date endDate;
    private Timestamp createdAt;

    // ── Signature fields ──
    private String signatureData;       // base64-encoded PNG of drawn signature
    private int signedByUserId;         // userId of the person who signed

    // ── Negotiation fields ──
    private Double counterAmount;       // applicant's counter-proposed amount
    private String counterTerms;        // applicant's proposed term changes
    private String negotiationNotes;    // free-text history/comments
    private int negotiationRound;       // how many rounds of negotiation

    public Contract() {}

    /** For creating a new contract (no id / createdAt yet). */
    public Contract(int offerId, int applicantId, int ownerId, String terms,
                    double amount, String currency, String status, Date startDate, Date endDate) {
        this.offerId = offerId;
        this.applicantId = applicantId;
        this.ownerId = ownerId;
        this.terms = terms;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /** Full constructor (from DB / API). */
    public Contract(int id, int offerId, int applicantId, int ownerId, String terms,
                    double amount, String currency, String status, Integer riskScore,
                    String riskFactors, String blockchainHash, String qrCodeUrl,
                    Timestamp signedAt, Date startDate, Date endDate, Timestamp createdAt) {
        this.id = id;
        this.offerId = offerId;
        this.applicantId = applicantId;
        this.ownerId = ownerId;
        this.terms = terms;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.riskScore = riskScore;
        this.riskFactors = riskFactors;
        this.blockchainHash = blockchainHash;
        this.qrCodeUrl = qrCodeUrl;
        this.signedAt = signedAt;
        this.startDate = startDate;
        this.endDate = endDate;
        this.createdAt = createdAt;
    }

    // ── Getters & Setters ──

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getOfferId() { return offerId; }
    public void setOfferId(int offerId) { this.offerId = offerId; }

    public int getApplicantId() { return applicantId; }
    public void setApplicantId(int applicantId) { this.applicantId = applicantId; }

    public int getOwnerId() { return ownerId; }
    public void setOwnerId(int ownerId) { this.ownerId = ownerId; }

    public String getTerms() { return terms; }
    public void setTerms(String terms) { this.terms = terms; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getRiskScore() { return riskScore; }
    public void setRiskScore(Integer riskScore) { this.riskScore = riskScore; }

    public String getRiskFactors() { return riskFactors; }
    public void setRiskFactors(String riskFactors) { this.riskFactors = riskFactors; }

    public String getBlockchainHash() { return blockchainHash; }
    public void setBlockchainHash(String blockchainHash) { this.blockchainHash = blockchainHash; }

    public String getQrCodeUrl() { return qrCodeUrl; }
    public void setQrCodeUrl(String qrCodeUrl) { this.qrCodeUrl = qrCodeUrl; }

    public Timestamp getSignedAt() { return signedAt; }
    public void setSignedAt(Timestamp signedAt) { this.signedAt = signedAt; }

    public Date getStartDate() { return startDate; }
    public void setStartDate(Date startDate) { this.startDate = startDate; }

    public Date getEndDate() { return endDate; }
    public void setEndDate(Date endDate) { this.endDate = endDate; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    // ── Signature getters/setters ──

    public String getSignatureData() { return signatureData; }
    public void setSignatureData(String signatureData) { this.signatureData = signatureData; }

    public int getSignedByUserId() { return signedByUserId; }
    public void setSignedByUserId(int signedByUserId) { this.signedByUserId = signedByUserId; }

    public boolean isSigned() { return signatureData != null && !signatureData.isEmpty(); }

    // ── Negotiation getters/setters ──

    public Double getCounterAmount() { return counterAmount; }
    public void setCounterAmount(Double counterAmount) { this.counterAmount = counterAmount; }

    public String getCounterTerms() { return counterTerms; }
    public void setCounterTerms(String counterTerms) { this.counterTerms = counterTerms; }

    public String getNegotiationNotes() { return negotiationNotes; }
    public void setNegotiationNotes(String negotiationNotes) { this.negotiationNotes = negotiationNotes; }

    public int getNegotiationRound() { return negotiationRound; }
    public void setNegotiationRound(int negotiationRound) { this.negotiationRound = negotiationRound; }

    @Override
    public String toString() {
        return "Contract{id=" + id + ", offerId=" + offerId + ", status=" + status + ", amount=" + amount + "}";
    }
}
