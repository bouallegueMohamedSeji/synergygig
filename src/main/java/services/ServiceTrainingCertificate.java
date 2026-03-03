package services;

import com.google.gson.*;
import entities.TrainingCertificate;
import utils.ApiClient;
import utils.AppConfig;
import utils.MyDatabase;

import java.sql.*;
import java.util.*;

public class ServiceTrainingCertificate implements IService<TrainingCertificate> {

    private final boolean useApi;

    public ServiceTrainingCertificate() {
        useApi = AppConfig.isApiMode();
    }

    // ==================== JSON helpers ====================

    private TrainingCertificate jsonToCertificate(JsonObject obj) {
        Timestamp issuedAt = null;
        if (obj.has("issued_at") && !obj.get("issued_at").isJsonNull()) {
            issuedAt = Timestamp.valueOf(obj.get("issued_at").getAsString().replace("T", " "));
        }
        TrainingCertificate cert = new TrainingCertificate(
                obj.get("id").getAsInt(),
                obj.has("enrollment_id") ? obj.get("enrollment_id").getAsInt() : 0,
                obj.has("user_id") ? obj.get("user_id").getAsInt() : 0,
                obj.has("course_id") ? obj.get("course_id").getAsInt() : 0,
                obj.has("certificate_number") && !obj.get("certificate_number").isJsonNull() ? obj.get("certificate_number").getAsString() : "",
                issuedAt
        );
        // Signature fields
        if (obj.has("signed_by_user_id") && !obj.get("signed_by_user_id").isJsonNull())
            cert.setSignedByUserId(obj.get("signed_by_user_id").getAsInt());
        if (obj.has("signature_data") && !obj.get("signature_data").isJsonNull())
            cert.setSignatureData(obj.get("signature_data").getAsString());
        if (obj.has("signed_at") && !obj.get("signed_at").isJsonNull())
            cert.setSignedAt(Timestamp.valueOf(obj.get("signed_at").getAsString().replace("T", " ")));
        return cert;
    }

    private List<TrainingCertificate> jsonArrayToList(JsonElement el) {
        List<TrainingCertificate> list = new ArrayList<>();
        if (el != null && el.isJsonArray()) {
            for (JsonElement item : el.getAsJsonArray()) list.add(jsonToCertificate(item.getAsJsonObject()));
        }
        return list;
    }

    // ==================== CRUD ====================

    @Override
    public void ajouter(TrainingCertificate c) throws SQLException {
        if (useApi) {
            Map<String, Object> body = buildBody(c);
            JsonElement resp = ApiClient.post("/training_certificates", body);
            if (resp != null && resp.isJsonObject()) c.setId(resp.getAsJsonObject().get("id").getAsInt());
            return;
        }
        String sql = "INSERT INTO training_certificates (enrollment_id, user_id, course_id, certificate_number) VALUES (?,?,?,?)";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, c.getEnrollmentId());
            ps.setInt(2, c.getUserId());
            ps.setInt(3, c.getCourseId());
            ps.setString(4, c.getCertificateNumber());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) c.setId(keys.getInt(1));
            }
        }
    }

    @Override
    public void modifier(TrainingCertificate c) throws SQLException {
        if (useApi) {
            ApiClient.put("/training_certificates/" + c.getId(), buildBody(c));
            return;
        }
        String sql = "UPDATE training_certificates SET enrollment_id=?, user_id=?, course_id=?, certificate_number=? WHERE id=?";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, c.getEnrollmentId());
            ps.setInt(2, c.getUserId());
            ps.setInt(3, c.getCourseId());
            ps.setString(4, c.getCertificateNumber());
            ps.setInt(5, c.getId());
            ps.executeUpdate();
        }
    }

    @Override
    public void supprimer(int id) throws SQLException {
        if (useApi) { ApiClient.delete("/training_certificates/" + id); return; }
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM training_certificates WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    @Override
    public List<TrainingCertificate> recuperer() throws SQLException {
        if (useApi) return jsonArrayToList(ApiClient.get("/training_certificates"));
        List<TrainingCertificate> list = new ArrayList<>();
        try (Connection conn = MyDatabase.getInstance().getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM training_certificates ORDER BY issued_at DESC")) {
            while (rs.next()) list.add(rowToCertificate(rs));
        }
        return list;
    }

    public List<TrainingCertificate> getByUser(int userId) throws SQLException {
        if (useApi) return jsonArrayToList(ApiClient.get("/training_certificates/user/" + userId));
        List<TrainingCertificate> list = new ArrayList<>();
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM training_certificates WHERE user_id=? ORDER BY issued_at DESC")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(rowToCertificate(rs));
            }
        }
        return list;
    }

    private TrainingCertificate rowToCertificate(ResultSet rs) throws SQLException {
        TrainingCertificate cert = new TrainingCertificate(
                rs.getInt("id"), rs.getInt("enrollment_id"), rs.getInt("user_id"),
                rs.getInt("course_id"), rs.getString("certificate_number"), rs.getTimestamp("issued_at")
        );
        // Signature fields (columns may not exist yet — guard with try/catch)
        try {
            cert.setSignedByUserId(rs.getInt("signed_by_user_id"));
            cert.setSignatureData(rs.getString("signature_data"));
            cert.setSignedAt(rs.getTimestamp("signed_at"));
        } catch (SQLException ignored) { /* columns not yet added — that's fine */ }
        return cert;
    }

    private Map<String, Object> buildBody(TrainingCertificate c) {
        Map<String, Object> body = new HashMap<>();
        body.put("enrollment_id", c.getEnrollmentId());
        body.put("user_id", c.getUserId());
        body.put("course_id", c.getCourseId());
        body.put("certificate_number", c.getCertificateNumber());
        if (c.getSignedByUserId() > 0) body.put("signed_by_user_id", c.getSignedByUserId());
        if (c.getSignatureData() != null) body.put("signature_data", c.getSignatureData());
        return body;
    }

    /**
     * Sign a certificate: store the drawn signature image (base64 PNG) and the signer's user id.
     * Uses API first; falls back to direct JDBC if the API rejects the new signature fields.
     */
    public void signCertificate(int certId, int signerUserId, String signatureBase64) throws SQLException {
        if (useApi) {
            try {
                Map<String, Object> body = new HashMap<>();
                body.put("signed_by_user_id", signerUserId);
                body.put("signature_data", signatureBase64);
                ApiClient.put("/training_certificates/" + certId, body);
                return;
            } catch (Exception apiEx) {
                System.out.println("⚠ API sign failed (" + apiEx.getMessage() + "), falling back to JDBC…");
            }
        }
        String sql = "UPDATE training_certificates SET signed_by_user_id=?, signature_data=?, signed_at=NOW() WHERE id=?";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, signerUserId);
            ps.setString(2, signatureBase64);
            ps.setInt(3, certId);
            ps.executeUpdate();
        }
    }
}
