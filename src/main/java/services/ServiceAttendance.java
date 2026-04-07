package services;

import com.google.gson.*;
import entities.Attendance;
import utils.ApiClient;
import utils.AppConfig;
import utils.MyDatabase;
import utils.InMemoryCache;
import java.sql.*;
import java.util.*;

public class ServiceAttendance implements IService<Attendance> {

    private final boolean useApi;

    private static final String CACHE_KEY = "attendance:all";
    private static final int CACHE_TTL = 60;

    public ServiceAttendance() {
        useApi = AppConfig.isApiMode();
    }

    // ==================== JSON helpers ====================

    /**
     * Parse a TIME value that may arrive as "HH:mm:ss", "HH:mm", or seconds-float (timedelta serialization).
     */
    private Time parseTime(JsonElement el) {
        try {
            // Try as number first (Python timedelta serialized as total seconds)
            if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) {
                long totalSeconds = (long) el.getAsDouble();
                long h = totalSeconds / 3600;
                long m = (totalSeconds % 3600) / 60;
                long s = totalSeconds % 60;
                return Time.valueOf(String.format("%02d:%02d:%02d", h, m, s));
            }
            // Try as string
            String str = el.getAsString().trim();
            // Handle "69064.0" style strings (float-as-string)
            if (str.matches("\\d+\\.\\d+")) {
                long totalSeconds = (long) Double.parseDouble(str);
                long h = totalSeconds / 3600;
                long m = (totalSeconds % 3600) / 60;
                long s = totalSeconds % 60;
                return Time.valueOf(String.format("%02d:%02d:%02d", h, m, s));
            }
            // Normal "HH:mm:ss" or "HH:mm"
            return Time.valueOf(str.length() == 5 ? str + ":00" : str);
        } catch (Exception e) {
            System.err.println("⚠ Failed to parse time: " + el + " — " + e.getMessage());
            return null;
        }
    }

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
            checkIn = parseTime(obj.get("check_in"));
        }
        Time checkOut = null;
        if (obj.has("check_out") && !obj.get("check_out").isJsonNull()) {
            checkOut = parseTime(obj.get("check_out"));
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
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, a.getUserId());
            ps.setDate(2, a.getDate());
            ps.setTime(3, a.getCheckIn());
            ps.setTime(4, a.getCheckOut());
            ps.setString(5, a.getStatus());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) a.setId(keys.getInt(1));
            }
        }
        InMemoryCache.evictByPrefix("attendance:");
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
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, a.getUserId());
            ps.setDate(2, a.getDate());
            ps.setTime(3, a.getCheckIn());
            ps.setTime(4, a.getCheckOut());
            ps.setString(5, a.getStatus());
            ps.setInt(6, a.getId());
            ps.executeUpdate();
        }
        InMemoryCache.evictByPrefix("attendance:");
    }

    @Override
    public void supprimer(int id) throws SQLException {
        if (useApi) {
            ApiClient.delete("/attendance/" + id);
            return;
        }
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM attendance WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
        InMemoryCache.evictByPrefix("attendance:");
    }

    @Override
    public List<Attendance> recuperer() throws SQLException {
        if (useApi) {
            return InMemoryCache.getOrLoad(CACHE_KEY, CACHE_TTL,
                    () -> jsonArrayToList(ApiClient.get("/attendance")));
        }
        return InMemoryCache.getOrLoadChecked(CACHE_KEY, CACHE_TTL,
                () -> recupererFromDb());
    }

    private List<Attendance> recupererFromDb() throws SQLException {
        List<Attendance> list = new ArrayList<>();
        try (Connection conn = MyDatabase.getInstance().getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM attendance ORDER BY date DESC")) {
            while (rs.next()) list.add(rowToAttendance(rs));
        }
        return list;
    }

    public List<Attendance> getByUser(int userId) throws SQLException {
        if (useApi) {
            return jsonArrayToList(ApiClient.get("/attendance/user/" + userId));
        }
        List<Attendance> list = new ArrayList<>();
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM attendance WHERE user_id=? ORDER BY date DESC")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(rowToAttendance(rs));
            }
        }
        return list;
    }

    public List<Attendance> getByDate(java.sql.Date date) throws SQLException {
        if (useApi) {
            return jsonArrayToList(ApiClient.get("/attendance/date/" + date.toString()));
        }
        List<Attendance> list = new ArrayList<>();
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM attendance WHERE date=? ORDER BY user_id")) {
            ps.setDate(1, date);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(rowToAttendance(rs));
            }
        }
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
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM attendance WHERE user_id=? AND date=?")) {
            ps.setInt(1, userId);
            ps.setDate(2, today);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rowToAttendance(rs);
            }
        }
        return null;
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
