package services;

import com.google.gson.*;
import entities.Attendance;
import utils.ApiClient;
import utils.AppConfig;
import utils.MyDatabase;

import java.sql.*;
import java.util.*;

public class ServiceAttendance implements IService<Attendance> {

    private Connection connection;
    private final boolean useApi;

    public ServiceAttendance() {
        useApi = AppConfig.isApiMode();
        if (!useApi) {
            connection = MyDatabase.getInstance().getConnection();
        }
    }

    // ==================== JSON helpers ====================

    private Attendance jsonToAttendance(JsonObject obj) {
        Timestamp createdAt = null;
        if (obj.has("created_at") && !obj.get("created_at").isJsonNull()) {
            createdAt = Timestamp.valueOf(obj.get("created_at").getAsString().replace("T", " "));
        }
        java.sql.Date date = null;
        if (obj.has("date") && !obj.get("date").isJsonNull()) {
            date = java.sql.Date.valueOf(obj.get("date").getAsString());
        }
        Time checkIn = null;
        if (obj.has("check_in") && !obj.get("check_in").isJsonNull()) {
            String ci = obj.get("check_in").getAsString();
            checkIn = Time.valueOf(ci.length() == 5 ? ci + ":00" : ci);
        }
        Time checkOut = null;
        if (obj.has("check_out") && !obj.get("check_out").isJsonNull()) {
            String co = obj.get("check_out").getAsString();
            checkOut = Time.valueOf(co.length() == 5 ? co + ":00" : co);
        }
        return new Attendance(
                obj.get("id").getAsInt(),
                obj.get("user_id").getAsInt(),
                date,
                checkIn,
                checkOut,
                obj.has("status") && !obj.get("status").isJsonNull() ? obj.get("status").getAsString() : "PRESENT",
                createdAt
        );
    }

    private List<Attendance> jsonArrayToList(JsonElement el) {
        List<Attendance> list = new ArrayList<>();
        if (el != null && el.isJsonArray()) {
            for (JsonElement item : el.getAsJsonArray()) {
                list.add(jsonToAttendance(item.getAsJsonObject()));
            }
        }
        return list;
    }

    // ==================== CRUD ====================

    @Override
    public void ajouter(Attendance a) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("user_id", a.getUserId());
            body.put("date", a.getDate() != null ? a.getDate().toString() : null);
            body.put("check_in", a.getCheckIn() != null ? a.getCheckIn().toString() : null);
            body.put("check_out", a.getCheckOut() != null ? a.getCheckOut().toString() : null);
            body.put("status", a.getStatus());
            JsonElement resp = ApiClient.post("/attendance", body);
            if (resp != null && resp.isJsonObject()) {
                a.setId(resp.getAsJsonObject().get("id").getAsInt());
            }
            return;
        }
        String sql = "INSERT INTO attendance (user_id, date, check_in, check_out, status) VALUES (?, ?, ?, ?, ?)";
        PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        ps.setInt(1, a.getUserId());
        ps.setDate(2, a.getDate());
        ps.setTime(3, a.getCheckIn());
        ps.setTime(4, a.getCheckOut());
        ps.setString(5, a.getStatus());
        ps.executeUpdate();
        ResultSet keys = ps.getGeneratedKeys();
        if (keys.next()) a.setId(keys.getInt(1));
        keys.close();
        ps.close();
    }

    @Override
    public void modifier(Attendance a) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("user_id", a.getUserId());
            body.put("date", a.getDate() != null ? a.getDate().toString() : null);
            body.put("check_in", a.getCheckIn() != null ? a.getCheckIn().toString() : null);
            body.put("check_out", a.getCheckOut() != null ? a.getCheckOut().toString() : null);
            body.put("status", a.getStatus());
            ApiClient.put("/attendance/" + a.getId(), body);
            return;
        }
        String sql = "UPDATE attendance SET user_id=?, date=?, check_in=?, check_out=?, status=? WHERE id=?";
        PreparedStatement ps = connection.prepareStatement(sql);
        ps.setInt(1, a.getUserId());
        ps.setDate(2, a.getDate());
        ps.setTime(3, a.getCheckIn());
        ps.setTime(4, a.getCheckOut());
        ps.setString(5, a.getStatus());
        ps.setInt(6, a.getId());
        ps.executeUpdate();
        ps.close();
    }

    @Override
    public void supprimer(int id) throws SQLException {
        if (useApi) {
            ApiClient.delete("/attendance/" + id);
            return;
        }
        PreparedStatement ps = connection.prepareStatement("DELETE FROM attendance WHERE id=?");
        ps.setInt(1, id);
        ps.executeUpdate();
        ps.close();
    }

    @Override
    public List<Attendance> recuperer() throws SQLException {
        if (useApi) {
            return jsonArrayToList(ApiClient.get("/attendance"));
        }
        List<Attendance> list = new ArrayList<>();
        Statement st = connection.createStatement();
        ResultSet rs = st.executeQuery("SELECT * FROM attendance ORDER BY date DESC");
        while (rs.next()) list.add(rowToAttendance(rs));
        rs.close();
        st.close();
        return list;
    }

    public List<Attendance> getByUser(int userId) throws SQLException {
        if (useApi) {
            return jsonArrayToList(ApiClient.get("/attendance/user/" + userId));
        }
        List<Attendance> list = new ArrayList<>();
        PreparedStatement ps = connection.prepareStatement("SELECT * FROM attendance WHERE user_id=? ORDER BY date DESC");
        ps.setInt(1, userId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) list.add(rowToAttendance(rs));
        rs.close();
        ps.close();
        return list;
    }

    public List<Attendance> getByDate(java.sql.Date date) throws SQLException {
        if (useApi) {
            return jsonArrayToList(ApiClient.get("/attendance/date/" + date.toString()));
        }
        List<Attendance> list = new ArrayList<>();
        PreparedStatement ps = connection.prepareStatement("SELECT * FROM attendance WHERE date=? ORDER BY user_id");
        ps.setDate(1, date);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) list.add(rowToAttendance(rs));
        rs.close();
        ps.close();
        return list;
    }

    /**
     * Get today's attendance record for a specific user (if any).
     */
    public Attendance getTodayForUser(int userId) throws SQLException {
        java.sql.Date today = java.sql.Date.valueOf(java.time.LocalDate.now());
        if (useApi) {
            List<Attendance> todayList = jsonArrayToList(ApiClient.get("/attendance/date/" + today.toString()));
            return todayList.stream().filter(a -> a.getUserId() == userId).findFirst().orElse(null);
        }
        PreparedStatement ps = connection.prepareStatement("SELECT * FROM attendance WHERE user_id=? AND date=?");
        ps.setInt(1, userId);
        ps.setDate(2, today);
        ResultSet rs = ps.executeQuery();
        Attendance a = null;
        if (rs.next()) a = rowToAttendance(rs);
        rs.close();
        ps.close();
        return a;
    }

    /**
     * Auto check-in: creates a PRESENT record with current time. Called on login.
     */
    public void autoCheckIn(int userId) {
        try {
            Attendance existing = getTodayForUser(userId);
            if (existing != null) return; // already checked in today
            java.time.LocalTime now = java.time.LocalTime.now();
            String status = now.isAfter(java.time.LocalTime.of(9, 15)) ? "LATE" : "PRESENT";
            Attendance a = new Attendance(
                    userId,
                    java.sql.Date.valueOf(java.time.LocalDate.now()),
                    java.sql.Time.valueOf(now),
                    null,
                    status
            );
            ajouter(a);
            System.out.println("✅ Auto check-in for user " + userId + " at " + now + " (" + status + ")");
        } catch (SQLException e) {
            System.err.println("⚠ Auto check-in failed: " + e.getMessage());
        }
    }

    /**
     * Auto check-out: updates today's record with current time. Called on logout.
     */
    public void autoCheckOut(int userId) {
        try {
            Attendance existing = getTodayForUser(userId);
            if (existing == null || existing.getCheckOut() != null) return; // no record or already checked out
            existing.setCheckOut(java.sql.Time.valueOf(java.time.LocalTime.now()));
            modifier(existing);
            System.out.println("✅ Auto check-out for user " + userId + " at " + existing.getCheckOut());
        } catch (SQLException e) {
            System.err.println("⚠ Auto check-out failed: " + e.getMessage());
        }
    }

    private Attendance rowToAttendance(ResultSet rs) throws SQLException {
        return new Attendance(
                rs.getInt("id"),
                rs.getInt("user_id"),
                rs.getDate("date"),
                rs.getTime("check_in"),
                rs.getTime("check_out"),
                rs.getString("status"),
                rs.getTimestamp("created_at")
        );
    }
}
