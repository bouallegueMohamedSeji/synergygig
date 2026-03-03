package services;

import com.google.gson.*;
import entities.Leave;
import utils.ApiClient;
import utils.AppConfig;
import utils.MyDatabase;
import utils.InMemoryCache;

import java.sql.*;
import java.util.*;

public class ServiceLeave implements IService<Leave> {

    private final boolean useApi;

    private static final String CACHE_KEY = "leaves:all";
    private static final int CACHE_TTL = 120;

    public ServiceLeave() {
        useApi = AppConfig.isApiMode();
    }

    // ==================== JSON helpers ====================

    private Leave jsonToLeave(JsonObject obj) {
        Timestamp createdAt = null;
        if (obj.has("created_at") && !obj.get("created_at").isJsonNull()) {
            createdAt = Timestamp.valueOf(obj.get("created_at").getAsString().replace("T", " "));
        }
        java.sql.Date startDate = null;
        if (obj.has("start_date") && !obj.get("start_date").isJsonNull()) {
            startDate = java.sql.Date.valueOf(obj.get("start_date").getAsString());
        }
        java.sql.Date endDate = null;
        if (obj.has("end_date") && !obj.get("end_date").isJsonNull()) {
            endDate = java.sql.Date.valueOf(obj.get("end_date").getAsString());
        }
        Leave l = new Leave(
                obj.get("id").getAsInt(),
                obj.get("user_id").getAsInt(),
                obj.has("type") && !obj.get("type").isJsonNull() ? obj.get("type").getAsString() : "VACATION",
                startDate,
                endDate,
                obj.has("reason") && !obj.get("reason").isJsonNull() ? obj.get("reason").getAsString() : "",
                obj.has("status") && !obj.get("status").isJsonNull() ? obj.get("status").getAsString() : "PENDING",
                createdAt
        );
        if (obj.has("rejection_reason") && !obj.get("rejection_reason").isJsonNull())
            l.setRejectionReason(obj.get("rejection_reason").getAsString());
        return l;
    }

    private List<Leave> jsonArrayToList(JsonElement el) {
        List<Leave> list = new ArrayList<>();
        if (el != null && el.isJsonArray()) {
            for (JsonElement item : el.getAsJsonArray()) {
                list.add(jsonToLeave(item.getAsJsonObject()));
            }
        }
        return list;
    }

    // ==================== CRUD ====================

    @Override
    public void ajouter(Leave l) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("user_id", l.getUserId());
            body.put("type", l.getType());
            body.put("start_date", l.getStartDate() != null ? l.getStartDate().toString() : null);
            body.put("end_date", l.getEndDate() != null ? l.getEndDate().toString() : null);
            body.put("reason", l.getReason());
            body.put("status", l.getStatus());
            body.put("rejection_reason", l.getRejectionReason());
            JsonElement resp = ApiClient.post("/leaves", body);
            if (resp != null && resp.isJsonObject()) {
                l.setId(resp.getAsJsonObject().get("id").getAsInt());
            }
            return;
        }
        String sql = "INSERT INTO leaves (user_id, type, start_date, end_date, reason, status, rejection_reason) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, l.getUserId());
            ps.setString(2, l.getType());
            ps.setDate(3, l.getStartDate());
            ps.setDate(4, l.getEndDate());
            ps.setString(5, l.getReason());
            ps.setString(6, l.getStatus());
            ps.setString(7, l.getRejectionReason());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) l.setId(keys.getInt(1));
            }
        }
        InMemoryCache.evictByPrefix("leaves:");
    }

    @Override
    public void modifier(Leave l) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("user_id", l.getUserId());
            body.put("type", l.getType());
            body.put("start_date", l.getStartDate() != null ? l.getStartDate().toString() : null);
            body.put("end_date", l.getEndDate() != null ? l.getEndDate().toString() : null);
            body.put("reason", l.getReason());
            body.put("status", l.getStatus());
            body.put("rejection_reason", l.getRejectionReason());
            ApiClient.put("/leaves/" + l.getId(), body);
            return;
        }
        String sql = "UPDATE leaves SET user_id=?, type=?, start_date=?, end_date=?, reason=?, status=?, rejection_reason=? WHERE id=?";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, l.getUserId());
            ps.setString(2, l.getType());
            ps.setDate(3, l.getStartDate());
            ps.setDate(4, l.getEndDate());
            ps.setString(5, l.getReason());
            ps.setString(6, l.getStatus());
            ps.setString(7, l.getRejectionReason());
            ps.setInt(8, l.getId());
            ps.executeUpdate();
        }
        InMemoryCache.evictByPrefix("leaves:");
    }

    @Override
    public void supprimer(int id) throws SQLException {
        if (useApi) {
            ApiClient.delete("/leaves/" + id);
            return;
        }
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM leaves WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
        InMemoryCache.evictByPrefix("leaves:");
    }

    @Override
    public List<Leave> recuperer() throws SQLException {
        if (useApi) {
            return InMemoryCache.getOrLoad(CACHE_KEY, CACHE_TTL,
                    () -> jsonArrayToList(ApiClient.get("/leaves")));
        }
        return InMemoryCache.getOrLoadChecked(CACHE_KEY, CACHE_TTL,
                () -> recupererFromDb());
    }

    private List<Leave> recupererFromDb() throws SQLException {
        List<Leave> list = new ArrayList<>();
        try (Connection conn = MyDatabase.getInstance().getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM leaves ORDER BY start_date DESC")) {
            while (rs.next()) list.add(rowToLeave(rs));
        }
        return list;
    }

    public List<Leave> getByUser(int userId) throws SQLException {
        if (useApi) {
            return jsonArrayToList(ApiClient.get("/leaves/user/" + userId));
        }
        List<Leave> list = new ArrayList<>();
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM leaves WHERE user_id=? ORDER BY start_date DESC")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(rowToLeave(rs));
            }
        }
        return list;
    }

    public List<Leave> getByStatus(String status) throws SQLException {
        if (useApi) {
            return jsonArrayToList(ApiClient.get("/leaves/status/" + status));
        }
        List<Leave> list = new ArrayList<>();
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM leaves WHERE status=? ORDER BY start_date DESC")) {
            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(rowToLeave(rs));
            }
        }
        return list;
    }

    private Leave rowToLeave(ResultSet rs) throws SQLException {
        Leave l = new Leave(
                rs.getInt("id"),
                rs.getInt("user_id"),
                rs.getString("type"),
                rs.getDate("start_date"),
                rs.getDate("end_date"),
                rs.getString("reason"),
                rs.getString("status"),
                rs.getTimestamp("created_at")
        );
        try { l.setRejectionReason(rs.getString("rejection_reason")); } catch (SQLException ignored) {}
        return l;
    }
}
