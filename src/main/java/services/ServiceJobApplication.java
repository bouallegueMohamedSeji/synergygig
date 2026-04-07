package services;

import com.google.gson.*;
import entities.JobApplication;
import utils.ApiClient;
import utils.AppConfig;
import utils.MyDatabase;

import java.sql.*;
import java.util.*;

public class ServiceJobApplication implements IService<JobApplication> {

    private final boolean useApi;

    public ServiceJobApplication() {
        useApi = AppConfig.isApiMode();
    }

    // ==================== JSON helpers ====================

    private JobApplication jsonToApplication(JsonObject obj) {
        Timestamp appliedAt = null;
        if (obj.has("applied_at") && !obj.get("applied_at").isJsonNull()) {
            appliedAt = Timestamp.valueOf(obj.get("applied_at").getAsString().replace("T", " "));
        }
        Timestamp reviewedAt = null;
        if (obj.has("reviewed_at") && !obj.get("reviewed_at").isJsonNull()) {
            reviewedAt = Timestamp.valueOf(obj.get("reviewed_at").getAsString().replace("T", " "));
        }
        Integer aiScore = null;
        if (obj.has("ai_score") && !obj.get("ai_score").isJsonNull()) {
            aiScore = obj.get("ai_score").getAsInt();
        }

        return new JobApplication(
            obj.get("id").getAsInt(),
            obj.get("offer_id").getAsInt(),
            obj.get("applicant_id").getAsInt(),
            obj.has("cover_letter") && !obj.get("cover_letter").isJsonNull() ? obj.get("cover_letter").getAsString() : "",
            obj.has("status") && !obj.get("status").isJsonNull() ? obj.get("status").getAsString() : "PENDING",
            aiScore,
            obj.has("ai_feedback") && !obj.get("ai_feedback").isJsonNull() ? obj.get("ai_feedback").getAsString() : null,
            appliedAt,
            reviewedAt
        );
    }

    private List<JobApplication> jsonArrayToApplications(JsonElement el) {
        List<JobApplication> applications = new ArrayList<>();
        if (el != null && el.isJsonArray()) {
            for (JsonElement item : el.getAsJsonArray()) {
                applications.add(jsonToApplication(item.getAsJsonObject()));
            }
        }
        return applications;
    }

    // ==================== CRUD ====================

    @Override
    public void ajouter(JobApplication a) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("offer_id", a.getOfferId());
            body.put("applicant_id", a.getApplicantId());
            body.put("cover_letter", a.getCoverLetter());
            body.put("status", a.getStatus());
            JsonElement resp = ApiClient.post("/applications", body);
            if (resp != null && resp.isJsonObject()) {
                a.setId(resp.getAsJsonObject().get("id").getAsInt());
            }
            return;
        }
        String sql = "INSERT INTO job_applications (offer_id, applicant_id, cover_letter, status) VALUES (?, ?, ?, ?)";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, a.getOfferId());
            ps.setInt(2, a.getApplicantId());
            ps.setString(3, a.getCoverLetter());
            ps.setString(4, a.getStatus());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) a.setId(keys.getInt(1));
            }
        }
    }

    @Override
    public void modifier(JobApplication a) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("offer_id", a.getOfferId());
            body.put("applicant_id", a.getApplicantId());
            body.put("cover_letter", a.getCoverLetter());
            body.put("status", a.getStatus());
            body.put("ai_score", a.getAiScore());
            body.put("ai_feedback", a.getAiFeedback());
            ApiClient.put("/applications/" + a.getId(), body);
            return;
        }
        String sql = "UPDATE job_applications SET offer_id=?, applicant_id=?, cover_letter=?, status=?, ai_score=?, ai_feedback=?, reviewed_at=NOW() WHERE id=?";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, a.getOfferId());
            ps.setInt(2, a.getApplicantId());
            ps.setString(3, a.getCoverLetter());
            ps.setString(4, a.getStatus());
            if (a.getAiScore() != null) ps.setInt(5, a.getAiScore());
            else ps.setNull(5, Types.INTEGER);
            ps.setString(6, a.getAiFeedback());
            ps.setInt(7, a.getId());
            ps.executeUpdate();
        }
    }

    @Override
    public void supprimer(int id) throws SQLException {
        if (useApi) {
            ApiClient.delete("/applications/" + id);
            return;
        }
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM job_applications WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    @Override
    public List<JobApplication> recuperer() throws SQLException {
        if (useApi) {
            JsonElement el = ApiClient.get("/applications");
            return jsonArrayToApplications(el);
        }
        List<JobApplication> list = new ArrayList<>();
        try (Connection conn = MyDatabase.getInstance().getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM job_applications ORDER BY applied_at DESC")) {
            while (rs.next()) list.add(rowToApplication(rs));
        }
        return list;
    }

    /** Get applications for a specific offer. */
    public List<JobApplication> getByOffer(int offerId) throws SQLException {
        if (useApi) {
            JsonElement el = ApiClient.get("/applications/offer/" + offerId);
            return jsonArrayToApplications(el);
        }
        List<JobApplication> list = new ArrayList<>();
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM job_applications WHERE offer_id = ? ORDER BY applied_at DESC")) {
            ps.setInt(1, offerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(rowToApplication(rs));
            }
        }
        return list;
    }

    /** Get applications by a specific user. */
    public List<JobApplication> getByUser(int userId) throws SQLException {
        if (useApi) {
            JsonElement el = ApiClient.get("/applications/user/" + userId);
            return jsonArrayToApplications(el);
        }
        List<JobApplication> list = new ArrayList<>();
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM job_applications WHERE applicant_id = ? ORDER BY applied_at DESC")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(rowToApplication(rs));
            }
        }
        return list;
    }

    private JobApplication rowToApplication(ResultSet rs) throws SQLException {
        Integer aiScore = rs.getInt("ai_score");
        if (rs.wasNull()) aiScore = null;
        return new JobApplication(
            rs.getInt("id"),
            rs.getInt("offer_id"),
            rs.getInt("applicant_id"),
            rs.getString("cover_letter"),
            rs.getString("status"),
            aiScore,
            rs.getString("ai_feedback"),
            rs.getTimestamp("applied_at"),
            rs.getTimestamp("reviewed_at")
        );
    }
}
