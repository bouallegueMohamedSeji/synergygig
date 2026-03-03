package services;

import com.google.gson.*;
import entities.Contract;
import utils.ApiClient;
import utils.AppConfig;
import utils.MyDatabase;

import java.sql.*;
import java.util.*;

public class ServiceContract implements IService<Contract> {

    private final boolean useApi;

    public ServiceContract() {
        useApi = AppConfig.isApiMode();
    }

    // ==================== JSON helpers ====================

    private Contract jsonToContract(JsonObject obj) {
        Timestamp createdAt = null;
        if (obj.has("created_at") && !obj.get("created_at").isJsonNull()) {
            createdAt = Timestamp.valueOf(obj.get("created_at").getAsString().replace("T", " "));
        }
        Timestamp signedAt = null;
        if (obj.has("signed_at") && !obj.get("signed_at").isJsonNull()) {
            signedAt = Timestamp.valueOf(obj.get("signed_at").getAsString().replace("T", " "));
        }
        java.sql.Date startDate = null;
        if (obj.has("start_date") && !obj.get("start_date").isJsonNull()) {
            startDate = java.sql.Date.valueOf(obj.get("start_date").getAsString());
        }
        java.sql.Date endDate = null;
        if (obj.has("end_date") && !obj.get("end_date").isJsonNull()) {
            endDate = java.sql.Date.valueOf(obj.get("end_date").getAsString());
        }
        Integer riskScore = null;
        if (obj.has("risk_score") && !obj.get("risk_score").isJsonNull()) {
            riskScore = obj.get("risk_score").getAsInt();
        }

        Contract c = new Contract(
            obj.get("id").getAsInt(),
            obj.get("offer_id").getAsInt(),
            obj.get("applicant_id").getAsInt(),
            obj.get("owner_id").getAsInt(),
            obj.has("terms") && !obj.get("terms").isJsonNull() ? obj.get("terms").getAsString() : "",
            obj.has("amount") && !obj.get("amount").isJsonNull() ? obj.get("amount").getAsDouble() : 0,
            obj.has("currency") && !obj.get("currency").isJsonNull() ? obj.get("currency").getAsString() : "USD",
            obj.has("status") && !obj.get("status").isJsonNull() ? obj.get("status").getAsString() : "DRAFT",
            riskScore,
            obj.has("risk_factors") && !obj.get("risk_factors").isJsonNull() ? obj.get("risk_factors").getAsString() : null,
            obj.has("blockchain_hash") && !obj.get("blockchain_hash").isJsonNull() ? obj.get("blockchain_hash").getAsString() : null,
            obj.has("qr_code_url") && !obj.get("qr_code_url").isJsonNull() ? obj.get("qr_code_url").getAsString() : null,
            signedAt,
            startDate,
            endDate,
            createdAt
        );
        // Negotiation fields
        if (obj.has("counter_amount") && !obj.get("counter_amount").isJsonNull())
            c.setCounterAmount(obj.get("counter_amount").getAsDouble());
        if (obj.has("counter_terms") && !obj.get("counter_terms").isJsonNull())
            c.setCounterTerms(obj.get("counter_terms").getAsString());
        if (obj.has("negotiation_notes") && !obj.get("negotiation_notes").isJsonNull())
            c.setNegotiationNotes(obj.get("negotiation_notes").getAsString());
        if (obj.has("negotiation_round") && !obj.get("negotiation_round").isJsonNull())
            c.setNegotiationRound(obj.get("negotiation_round").getAsInt());
        return c;
    }

    private List<Contract> jsonArrayToContracts(JsonElement el) {
        List<Contract> contracts = new ArrayList<>();
        if (el != null && el.isJsonArray()) {
            for (JsonElement item : el.getAsJsonArray()) {
                contracts.add(jsonToContract(item.getAsJsonObject()));
            }
        }
        return contracts;
    }

    // ==================== CRUD ====================

    @Override
    public void ajouter(Contract c) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("offer_id", c.getOfferId());
            body.put("applicant_id", c.getApplicantId());
            body.put("owner_id", c.getOwnerId());
            body.put("terms", c.getTerms());
            body.put("amount", c.getAmount());
            body.put("currency", c.getCurrency());
            body.put("status", c.getStatus());
            body.put("risk_score", c.getRiskScore());
            body.put("risk_factors", c.getRiskFactors());
            body.put("blockchain_hash", c.getBlockchainHash());
            body.put("qr_code_url", c.getQrCodeUrl());
            body.put("start_date", c.getStartDate() != null ? c.getStartDate().toString() : null);
            body.put("end_date", c.getEndDate() != null ? c.getEndDate().toString() : null);
            if (c.getCounterAmount() != null) body.put("counter_amount", c.getCounterAmount());
            body.put("counter_terms", c.getCounterTerms());
            body.put("negotiation_notes", c.getNegotiationNotes());
            body.put("negotiation_round", c.getNegotiationRound());
            JsonElement resp = ApiClient.post("/contracts", body);
            if (resp == null) {
                throw new SQLException("API error: failed to create contract (server returned error)");
            }
            if (resp.isJsonObject()) {
                c.setId(resp.getAsJsonObject().get("id").getAsInt());
            }
            return;
        }
        String sql = "INSERT INTO contracts (offer_id, applicant_id, owner_id, terms, amount, currency, status, risk_score, risk_factors, blockchain_hash, qr_code_url, start_date, end_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, c.getOfferId());
            ps.setInt(2, c.getApplicantId());
            ps.setInt(3, c.getOwnerId());
            ps.setString(4, c.getTerms());
            ps.setDouble(5, c.getAmount());
            ps.setString(6, c.getCurrency());
            ps.setString(7, c.getStatus());
            if (c.getRiskScore() != null) ps.setInt(8, c.getRiskScore());
            else ps.setNull(8, Types.INTEGER);
            ps.setString(9, c.getRiskFactors());
            ps.setString(10, c.getBlockchainHash());
            ps.setString(11, c.getQrCodeUrl());
            ps.setDate(12, c.getStartDate());
            ps.setDate(13, c.getEndDate());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) c.setId(keys.getInt(1));
            }
        }
    }

    @Override
    public void modifier(Contract c) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("offer_id", c.getOfferId());
            body.put("applicant_id", c.getApplicantId());
            body.put("owner_id", c.getOwnerId());
            body.put("terms", c.getTerms());
            body.put("amount", c.getAmount());
            body.put("currency", c.getCurrency());
            body.put("status", c.getStatus());
            body.put("risk_score", c.getRiskScore());
            body.put("risk_factors", c.getRiskFactors());
            body.put("blockchain_hash", c.getBlockchainHash());
            body.put("qr_code_url", c.getQrCodeUrl());
            body.put("start_date", c.getStartDate() != null ? c.getStartDate().toString() : null);
            body.put("end_date", c.getEndDate() != null ? c.getEndDate().toString() : null);
            if (c.getCounterAmount() != null) body.put("counter_amount", c.getCounterAmount());
            body.put("counter_terms", c.getCounterTerms());
            body.put("negotiation_notes", c.getNegotiationNotes());
            body.put("negotiation_round", c.getNegotiationRound());
            ApiClient.put("/contracts/" + c.getId(), body);
            return;
        }
        String sql = "UPDATE contracts SET offer_id=?, applicant_id=?, owner_id=?, terms=?, amount=?, currency=?, status=?, risk_score=?, risk_factors=?, blockchain_hash=?, qr_code_url=?, start_date=?, end_date=? WHERE id=?";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, c.getOfferId());
            ps.setInt(2, c.getApplicantId());
            ps.setInt(3, c.getOwnerId());
            ps.setString(4, c.getTerms());
            ps.setDouble(5, c.getAmount());
            ps.setString(6, c.getCurrency());
            ps.setString(7, c.getStatus());
            if (c.getRiskScore() != null) ps.setInt(8, c.getRiskScore());
            else ps.setNull(8, Types.INTEGER);
            ps.setString(9, c.getRiskFactors());
            ps.setString(10, c.getBlockchainHash());
            ps.setString(11, c.getQrCodeUrl());
            ps.setDate(12, c.getStartDate());
            ps.setDate(13, c.getEndDate());
            ps.setInt(14, c.getId());
            ps.executeUpdate();
        }
    }

    @Override
    public void supprimer(int id) throws SQLException {
        if (useApi) {
            ApiClient.delete("/contracts/" + id);
            return;
        }
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM contracts WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    @Override
    public List<Contract> recuperer() throws SQLException {
        if (useApi) {
            JsonElement el = ApiClient.get("/contracts");
            return jsonArrayToContracts(el);
        }
        List<Contract> list = new ArrayList<>();
        try (Connection conn = MyDatabase.getInstance().getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM contracts ORDER BY created_at DESC")) {
            while (rs.next()) list.add(rowToContract(rs));
        }
        return list;
    }

    /** Get contracts owned by a specific user. */
    public List<Contract> getByOwner(int ownerId) throws SQLException {
        if (useApi) {
            JsonElement el = ApiClient.get("/contracts/owner/" + ownerId);
            return jsonArrayToContracts(el);
        }
        List<Contract> list = new ArrayList<>();
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM contracts WHERE owner_id = ? ORDER BY created_at DESC")) {
            ps.setInt(1, ownerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(rowToContract(rs));
            }
        }
        return list;
    }

    /** Get contracts for a specific applicant/contractor. */
    public List<Contract> getByApplicant(int applicantId) throws SQLException {
        if (useApi) {
            JsonElement el = ApiClient.get("/contracts/applicant/" + applicantId);
            return jsonArrayToContracts(el);
        }
        List<Contract> list = new ArrayList<>();
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM contracts WHERE applicant_id = ? ORDER BY created_at DESC")) {
            ps.setInt(1, applicantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(rowToContract(rs));
            }
        }
        return list;
    }

    private Contract rowToContract(ResultSet rs) throws SQLException {
        Integer riskScore = rs.getInt("risk_score");
        if (rs.wasNull()) riskScore = null;
        Contract c = new Contract(
            rs.getInt("id"),
            rs.getInt("offer_id"),
            rs.getInt("applicant_id"),
            rs.getInt("owner_id"),
            rs.getString("terms"),
            rs.getDouble("amount"),
            rs.getString("currency"),
            rs.getString("status"),
            riskScore,
            rs.getString("risk_factors"),
            rs.getString("blockchain_hash"),
            rs.getString("qr_code_url"),
            rs.getTimestamp("signed_at"),
            rs.getDate("start_date"),
            rs.getDate("end_date"),
            rs.getTimestamp("created_at")
        );
        // Negotiation fields — backward-compatible with old schema
        try {
            double ca = rs.getDouble("counter_amount");
            if (!rs.wasNull()) c.setCounterAmount(ca);
            c.setCounterTerms(rs.getString("counter_terms"));
            c.setNegotiationNotes(rs.getString("negotiation_notes"));
            c.setNegotiationRound(rs.getInt("negotiation_round"));
        } catch (SQLException ignored) { /* columns may not exist in older schemas */ }
        return c;
    }
}
