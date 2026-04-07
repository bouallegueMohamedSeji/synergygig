package services;

import com.google.gson.*;
import entities.TrainingCourse;
import utils.ApiClient;
import utils.AppConfig;
import utils.MyDatabase;
import utils.InMemoryCache;
import java.sql.*;
import java.util.*;

public class ServiceTrainingCourse implements IService<TrainingCourse> {

    private final boolean useApi;

    private static final String CACHE_KEY = "courses:all";
    private static final int CACHE_TTL = 120;

    public ServiceTrainingCourse() {
        useApi = AppConfig.isApiMode();
    }

    // ==================== JSON helpers ====================

    private TrainingCourse jsonToCourse(JsonObject obj) {
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
        TrainingCourse tc = new TrainingCourse(
                obj.get("id").getAsInt(),
                obj.has("title") && !obj.get("title").isJsonNull() ? obj.get("title").getAsString() : "",
                obj.has("description") && !obj.get("description").isJsonNull() ? obj.get("description").getAsString() : "",
                obj.has("category") && !obj.get("category").isJsonNull() ? obj.get("category").getAsString() : "TECHNICAL",
                obj.has("difficulty") && !obj.get("difficulty").isJsonNull() ? obj.get("difficulty").getAsString() : "BEGINNER",
                obj.has("duration_hours") && !obj.get("duration_hours").isJsonNull() ? obj.get("duration_hours").getAsDouble() : 0,
                obj.has("instructor_name") && !obj.get("instructor_name").isJsonNull() ? obj.get("instructor_name").getAsString() : "",
                obj.has("mega_link") && !obj.get("mega_link").isJsonNull() ? obj.get("mega_link").getAsString() : "",
                obj.has("thumbnail_url") && !obj.get("thumbnail_url").isJsonNull() ? obj.get("thumbnail_url").getAsString() : "",
                obj.has("max_participants") && !obj.get("max_participants").isJsonNull() ? obj.get("max_participants").getAsInt() : 50,
                obj.has("status") && !obj.get("status").isJsonNull() ? obj.get("status").getAsString() : "DRAFT",
                startDate, endDate,
                obj.has("created_by") && !obj.get("created_by").isJsonNull() ? obj.get("created_by").getAsInt() : 0,
                createdAt
        );
        if (obj.has("quiz_timer_seconds") && !obj.get("quiz_timer_seconds").isJsonNull()) {
            tc.setQuizTimerSeconds(obj.get("quiz_timer_seconds").getAsInt());
        }
        return tc;
    }

    private List<TrainingCourse> jsonArrayToList(JsonElement el) {
        List<TrainingCourse> list = new ArrayList<>();
        if (el != null && el.isJsonArray()) {
            for (JsonElement item : el.getAsJsonArray()) list.add(jsonToCourse(item.getAsJsonObject()));
        }
        return list;
    }

    // ==================== CRUD ====================

    @Override
    public void ajouter(TrainingCourse c) throws SQLException {
        if (useApi) {
            Map<String, Object> body = buildBody(c);
            JsonElement resp = ApiClient.post("/training_courses", body);
            if (resp != null && resp.isJsonObject()) c.setId(resp.getAsJsonObject().get("id").getAsInt());
            return;
        }
        String sql = "INSERT INTO training_courses (title, description, category, difficulty, duration_hours, " +
                "instructor_name, mega_link, thumbnail_url, max_participants, status, start_date, end_date, created_by) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, c.getTitle());
            ps.setString(2, c.getDescription());
            ps.setString(3, c.getCategory());
            ps.setString(4, c.getDifficulty());
            ps.setDouble(5, c.getDurationHours());
            ps.setString(6, c.getInstructorName());
            ps.setString(7, c.getMegaLink());
            ps.setString(8, c.getThumbnailUrl());
            ps.setInt(9, c.getMaxParticipants());
            ps.setString(10, c.getStatus());
            ps.setDate(11, c.getStartDate());
            ps.setDate(12, c.getEndDate());
            ps.setInt(13, c.getCreatedBy());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) c.setId(keys.getInt(1));
            }
        }
        InMemoryCache.evictByPrefix("courses:");
    }

    @Override
    public void modifier(TrainingCourse c) throws SQLException {
        if (useApi) {
            ApiClient.put("/training_courses/" + c.getId(), buildBody(c));
            return;
        }
        String sql = "UPDATE training_courses SET title=?, description=?, category=?, difficulty=?, duration_hours=?, " +
                "instructor_name=?, mega_link=?, thumbnail_url=?, max_participants=?, status=?, start_date=?, end_date=? WHERE id=?";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, c.getTitle());
            ps.setString(2, c.getDescription());
            ps.setString(3, c.getCategory());
            ps.setString(4, c.getDifficulty());
            ps.setDouble(5, c.getDurationHours());
            ps.setString(6, c.getInstructorName());
            ps.setString(7, c.getMegaLink());
            ps.setString(8, c.getThumbnailUrl());
            ps.setInt(9, c.getMaxParticipants());
            ps.setString(10, c.getStatus());
            ps.setDate(11, c.getStartDate());
            ps.setDate(12, c.getEndDate());
            ps.setInt(13, c.getId());
            ps.executeUpdate();
        }
        InMemoryCache.evictByPrefix("courses:");
    }

    @Override
    public void supprimer(int id) throws SQLException {
        if (useApi) { ApiClient.delete("/training_courses/" + id); return; }
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM training_courses WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
        InMemoryCache.evictByPrefix("courses:");
    }

    @Override
    public List<TrainingCourse> recuperer() throws SQLException {
        if (useApi) {
            return InMemoryCache.getOrLoad(CACHE_KEY, CACHE_TTL,
                    () -> jsonArrayToList(ApiClient.get("/training_courses")));
        }
        return InMemoryCache.getOrLoadChecked(CACHE_KEY, CACHE_TTL,
                () -> recupererFromDb());
    }

    private List<TrainingCourse> recupererFromDb() throws SQLException {
        List<TrainingCourse> list = new ArrayList<>();
        try (Connection conn = MyDatabase.getInstance().getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM training_courses ORDER BY created_at DESC")) {
            while (rs.next()) list.add(rowToCourse(rs));
        }
        return list;
    }

    public List<TrainingCourse> getActive() throws SQLException {
        if (useApi) return jsonArrayToList(ApiClient.get("/training_courses/status/ACTIVE"));
        List<TrainingCourse> list = new ArrayList<>();
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM training_courses WHERE status='ACTIVE' ORDER BY created_at DESC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(rowToCourse(rs));
        }
        return list;
    }

    public List<TrainingCourse> getByCategory(String category) throws SQLException {
        if (useApi) return jsonArrayToList(ApiClient.get("/training_courses/category/" + category));
        List<TrainingCourse> list = new ArrayList<>();
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM training_courses WHERE category=? ORDER BY created_at DESC")) {
            ps.setString(1, category);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(rowToCourse(rs));
            }
        }
        return list;
    }

    private TrainingCourse rowToCourse(ResultSet rs) throws SQLException {
        return new TrainingCourse(
                rs.getInt("id"), rs.getString("title"), rs.getString("description"),
                rs.getString("category"), rs.getString("difficulty"), rs.getDouble("duration_hours"),
                rs.getString("instructor_name"), rs.getString("mega_link"), rs.getString("thumbnail_url"),
                rs.getInt("max_participants"), rs.getString("status"),
                rs.getDate("start_date"), rs.getDate("end_date"),
                rs.getInt("created_by"), rs.getTimestamp("created_at")
        );
    }

    private Map<String, Object> buildBody(TrainingCourse c) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", c.getTitle());
        body.put("description", c.getDescription());
        body.put("category", c.getCategory());
        body.put("difficulty", c.getDifficulty());
        body.put("duration_hours", c.getDurationHours());
        body.put("instructor_name", c.getInstructorName());
        body.put("mega_link", c.getMegaLink());
        body.put("thumbnail_url", c.getThumbnailUrl());
        body.put("max_participants", c.getMaxParticipants());
        body.put("status", c.getStatus());
        body.put("start_date", c.getStartDate() != null ? c.getStartDate().toString() : null);
        body.put("end_date", c.getEndDate() != null ? c.getEndDate().toString() : null);
        body.put("created_by", c.getCreatedBy());
        body.put("quiz_timer_seconds", c.getQuizTimerSeconds());
        return body;
    }
}
