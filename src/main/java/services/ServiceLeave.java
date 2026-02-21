package services;

import com.google.gson.*;
import entities.Leave;
import utils.ApiClient;
import utils.AppConfig;
import utils.MyDatabase;

import java.sql.*;
import java.util.*;

public class ServiceLeave implements IService<Leave> {

    private Connection connection;
    private final boolean useApi;

    public ServiceLeave() {
        useApi = AppConfig.isApiMode();
        if (!useApi) {
            connection = MyDatabase.getInstance().getConnection();
        }
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
        return new Leave(
                obj.get("id").getAsInt(),
                obj.get("user_id").getAsInt(),
                obj.has("type") && !obj.get("type").isJsonNull() ? obj.get("type").getAsString() : "VACATION",
                startDate,
                endDate,
                obj.has("reason") && !obj.get("reason").isJsonNull() ? obj.get("reason").getAsString() : "",
                obj.has("status") && !obj.get("status").isJsonNull() ? obj.get("status").getAsString() : "PENDING",
                createdAt
        );
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
            JsonElement resp = ApiClient.post("/leaves", body);
            if (resp != null && resp.isJsonObject()) {
                l.setId(resp.getAsJsonObject().get("id").getAsInt());
            }
            return;
        }
        String sql = "INSERT INTO leaves (user_id, type, start_date, end_date, reason, status) VALUES (?, ?, ?, ?, ?, ?)";
        PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        ps.setInt(1, l.getUserId());
        ps.setString(2, l.getType());
        ps.setDate(3, l.getStartDate());
        ps.setDate(4, l.getEndDate());
        ps.setString(5, l.getReason());
        ps.setString(6, l.getStatus());
        ps.executeUpdate();
        ResultSet keys = ps.getGeneratedKeys();
        if (keys.next()) l.setId(keys.getInt(1));
        keys.close();
        ps.close();
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
            ApiClient.put("/leaves/" + l.getId(), body);
            return;
        }
        String sql = "UPDATE leaves SET user_id=?, type=?, start_date=?, end_date=?, reason=?, status=? WHERE id=?";
        PreparedStatement ps = connection.prepareStatement(sql);
        ps.setInt(1, l.getUserId());
        ps.setString(2, l.getType());
        ps.setDate(3, l.getStartDate());
        ps.setDate(4, l.getEndDate());
        ps.setString(5, l.getReason());
        ps.setString(6, l.getStatus());
        ps.setInt(7, l.getId());
        ps.executeUpdate();
        ps.close();
    }

    @Override
    public void supprimer(int id) throws SQLException {
        if (useApi) {
            ApiClient.delete("/leaves/" + id);
            return;
        }
        PreparedStatement ps = connection.prepareStatement("DELETE FROM leaves WHERE id=?");
        ps.setInt(1, id);
        ps.executeUpdate();
        ps.close();
    }

    @Override
    public List<Leave> recuperer() throws SQLException {
        if (useApi) {
            return jsonArrayToList(ApiClient.get("/leaves"));
        }
        List<Leave> list = new ArrayList<>();
        Statement st = connection.createStatement();
        ResultSet rs = st.executeQuery("SELECT * FROM leaves ORDER BY start_date DESC");
        while (rs.next()) list.add(rowToLeave(rs));
        rs.close();
        st.close();
        return list;
    }

    public List<Leave> getByUser(int userId) throws SQLException {
        if (useApi) {
            return jsonArrayToList(ApiClient.get("/leaves/user/" + userId));
        }
        List<Leave> list = new ArrayList<>();
        PreparedStatement ps = connection.prepareStatement("SELECT * FROM leaves WHERE user_id=? ORDER BY start_date DESC");
        ps.setInt(1, userId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) list.add(rowToLeave(rs));
        rs.close();
        ps.close();
        return list;
    }

    public List<Leave> getByStatus(String status) throws SQLException {
        if (useApi) {
            return jsonArrayToList(ApiClient.get("/leaves/status/" + status));
        }
        List<Leave> list = new ArrayList<>();
        PreparedStatement ps = connection.prepareStatement("SELECT * FROM leaves WHERE status=? ORDER BY start_date DESC");
        ps.setString(1, status);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) list.add(rowToLeave(rs));
        rs.close();
        ps.close();
        return list;
    }

    private Leave rowToLeave(ResultSet rs) throws SQLException {
        return new Leave(
                rs.getInt("id"),
                rs.getInt("user_id"),
                rs.getString("type"),
                rs.getDate("start_date"),
                rs.getDate("end_date"),
                rs.getString("reason"),
                rs.getString("status"),
                rs.getTimestamp("created_at")
        );
    }
}
