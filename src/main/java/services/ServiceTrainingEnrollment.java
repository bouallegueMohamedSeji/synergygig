package services;

import com.google.gson.*;
import entities.TrainingEnrollment;
import utils.ApiClient;
import utils.AppConfig;
import utils.MyDatabase;

import java.sql.*;
import java.util.*;

public class ServiceTrainingEnrollment implements IService<TrainingEnrollment> {

    private final boolean useApi;

    public ServiceTrainingEnrollment() {
        useApi = AppConfig.isApiMode();
    }

    // ==================== JSON helpers ====================

    private TrainingEnrollment jsonToEnrollment(JsonObject obj) {
        Timestamp enrolledAt = null;
        if (obj.has("enrolled_at") && !obj.get("enrolled_at").isJsonNull()) {
            enrolledAt = Timestamp.valueOf(obj.get("enrolled_at").getAsString().replace("T", " "));
        }
        Timestamp completedAt = null;
        if (obj.has("completed_at") && !obj.get("completed_at").isJsonNull()) {
            completedAt = Timestamp.valueOf(obj.get("completed_at").getAsString().replace("T", " "));
        }
        Double score = null;
        if (obj.has("score") && !obj.get("score").isJsonNull()) {
            score = obj.get("score").getAsDouble();
        }
        return new TrainingEnrollment(
                obj.get("id").getAsInt(),
                obj.has("course_id") ? obj.get("course_id").getAsInt() : 0,
                obj.has("user_id") ? obj.get("user_id").getAsInt() : 0,
                obj.has("status") && !obj.get("status").isJsonNull() ? obj.get("status").getAsString() : "ENROLLED",
                obj.has("progress") ? obj.get("progress").getAsInt() : 0,
                score, enrolledAt, completedAt
        );
    }

    private List<TrainingEnrollment> jsonArrayToList(JsonElement el) {
        List<TrainingEnrollment> list = new ArrayList<>();
        if (el != null && el.isJsonArray()) {
            for (JsonElement item : el.getAsJsonArray()) list.add(jsonToEnrollment(item.getAsJsonObject()));
        }
        return list;
    }

    // ==================== CRUD ====================

    @Override
    public void ajouter(TrainingEnrollment e) throws SQLException {
        if (useApi) {
            Map<String, Object> body = buildBody(e);
            JsonElement resp = ApiClient.post("/training_enrollments", body);
            if (resp != null && resp.isJsonObject()) e.setId(resp.getAsJsonObject().get("id").getAsInt());
            return;
        }
        String sql = "INSERT INTO training_enrollments (course_id, user_id, status, progress, score) VALUES (?,?,?,?,?)";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, e.getCourseId());
            ps.setInt(2, e.getUserId());
            ps.setString(3, e.getStatus());
            ps.setInt(4, e.getProgress());
            if (e.getScore() != null) ps.setDouble(5, e.getScore()); else ps.setNull(5, Types.DOUBLE);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) e.setId(keys.getInt(1));
            }
        }
    }

    @Override
    public void modifier(TrainingEnrollment e) throws SQLException {
        if (useApi) {
            ApiClient.put("/training_enrollments/" + e.getId(), buildBody(e));
            return;
        }
        String sql = "UPDATE training_enrollments SET status=?, progress=?, score=?, completed_at=? WHERE id=?";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, e.getStatus());
            ps.setInt(2, e.getProgress());
            if (e.getScore() != null) ps.setDouble(3, e.getScore()); else ps.setNull(3, Types.DOUBLE);
            if (e.getCompletedAt() != null) ps.setTimestamp(4, e.getCompletedAt()); else ps.setNull(4, Types.TIMESTAMP);
            ps.setInt(5, e.getId());
            ps.executeUpdate();
        }
    }

    @Override
    public void supprimer(int id) throws SQLException {
        if (useApi) { ApiClient.delete("/training_enrollments/" + id); return; }
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM training_enrollments WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    @Override
    public List<TrainingEnrollment> recuperer() throws SQLException {
        if (useApi) return jsonArrayToList(ApiClient.get("/training_enrollments"));
        List<TrainingEnrollment> list = new ArrayList<>();
        try (Connection conn = MyDatabase.getInstance().getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM training_enrollments ORDER BY enrolled_at DESC")) {
            while (rs.next()) list.add(rowToEnrollment(rs));
        }
        return list;
    }

    public List<TrainingEnrollment> getByUser(int userId) throws SQLException {
        if (useApi) return jsonArrayToList(ApiClient.get("/training_enrollments/user/" + userId));
        List<TrainingEnrollment> list = new ArrayList<>();
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM training_enrollments WHERE user_id=? ORDER BY enrolled_at DESC")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(rowToEnrollment(rs));
            }
        }
        return list;
    }

    public List<TrainingEnrollment> getByCourse(int courseId) throws SQLException {
        if (useApi) return jsonArrayToList(ApiClient.get("/training_enrollments/course/" + courseId));
        List<TrainingEnrollment> list = new ArrayList<>();
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM training_enrollments WHERE course_id=? ORDER BY enrolled_at DESC")) {
            ps.setInt(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(rowToEnrollment(rs));
            }
        }
        return list;
    }

    public void updateProgress(int enrollId, int progress) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("progress", progress);
            if (progress >= 100) {
                body.put("status", "COMPLETED");
                body.put("completed_at", new Timestamp(System.currentTimeMillis()).toString());
            } else {
                body.put("status", "IN_PROGRESS");
            }
            ApiClient.put("/training_enrollments/" + enrollId + "/progress", body);
            return;
        }
        try (Connection conn = MyDatabase.getInstance().getConnection()) {
            if (progress >= 100) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE training_enrollments SET progress=100, status='COMPLETED', completed_at=NOW() WHERE id=?")) {
                    ps.setInt(1, enrollId);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE training_enrollments SET progress=?, status='IN_PROGRESS' WHERE id=?")) {
                    ps.setInt(1, progress);
                    ps.setInt(2, enrollId);
                    ps.executeUpdate();
                }
            }
        }
    }

    private TrainingEnrollment rowToEnrollment(ResultSet rs) throws SQLException {
        Double score = rs.getDouble("score");
        if (rs.wasNull()) score = null;
        return new TrainingEnrollment(
                rs.getInt("id"), rs.getInt("course_id"), rs.getInt("user_id"),
                rs.getString("status"), rs.getInt("progress"), score,
                rs.getTimestamp("enrolled_at"), rs.getTimestamp("completed_at")
        );
    }

    private Map<String, Object> buildBody(TrainingEnrollment e) {
        Map<String, Object> body = new HashMap<>();
        body.put("course_id", e.getCourseId());
        body.put("user_id", e.getUserId());
        body.put("status", e.getStatus());
        body.put("progress", e.getProgress());
        body.put("score", e.getScore());
        body.put("completed_at", e.getCompletedAt() != null ? e.getCompletedAt().toString() : null);
        return body;
    }
}
