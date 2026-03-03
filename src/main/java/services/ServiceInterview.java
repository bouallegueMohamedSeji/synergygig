package services;

import com.google.gson.*;
import entities.Interview;
import utils.ApiClient;
import utils.AppConfig;
import utils.MyDatabase;
import utils.InMemoryCache;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServiceInterview implements IService<Interview> {

    private final boolean useApi;

    private static final String CACHE_KEY = "interviews:all";
    private static final int CACHE_TTL = 60;

    public ServiceInterview() {
        useApi = AppConfig.isApiMode();
    }

    // ==================== JSON helpers ====================

    private Interview jsonToInterview(JsonObject obj) {
        Timestamp dateTime = null;
        if (obj.has("date_time") && !obj.get("date_time").isJsonNull()) {
            dateTime = Timestamp.valueOf(obj.get("date_time").getAsString().replace("T", " "));
        }
        String meetLink = null;
        if (obj.has("meet_link") && !obj.get("meet_link").isJsonNull()) {
            meetLink = obj.get("meet_link").getAsString();
        }
        int applicationId = 0;
        if (obj.has("application_id") && !obj.get("application_id").isJsonNull()) {
            applicationId = obj.get("application_id").getAsInt();
        }
        int offerId = 0;
        if (obj.has("offer_id") && !obj.get("offer_id").isJsonNull()) {
            offerId = obj.get("offer_id").getAsInt();
        }
        return new Interview(
                obj.get("id").getAsInt(),
                obj.get("organizer_id").getAsInt(),
                obj.get("candidate_id").getAsInt(),
                dateTime,
                obj.get("status").getAsString(),
                meetLink,
                applicationId,
                offerId
        );
    }

    private List<Interview> jsonArrayToInterviews(JsonElement el) {
        List<Interview> interviews = new ArrayList<>();
        if (el != null && el.isJsonArray()) {
            for (JsonElement item : el.getAsJsonArray()) {
                interviews.add(jsonToInterview(item.getAsJsonObject()));
            }
        }
        return interviews;
    }

    // ==================== CRUD ====================

    @Override
    public void ajouter(Interview interview) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("organizer_id", interview.getOrganizerId());
            body.put("candidate_id", interview.getCandidateId());
            body.put("date_time", interview.getDateTime() != null ? interview.getDateTime().toString() : null);
            body.put("meet_link", interview.getMeetLink());
            body.put("application_id", interview.getApplicationId());
            body.put("offer_id", interview.getOfferId());
            JsonElement resp = ApiClient.post("/interviews", body);
            if (resp != null && resp.isJsonObject()) {
                interview.setId(resp.getAsJsonObject().get("id").getAsInt());
            }
            return;
        }
        String req = "INSERT INTO interviews (organizer_id, candidate_id, date_time, status, meet_link, application_id, offer_id) VALUES (?, ?, ?, 'PENDING', ?, ?, ?)";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(req, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, interview.getOrganizerId());
            ps.setInt(2, interview.getCandidateId());
            ps.setTimestamp(3, interview.getDateTime());
            ps.setString(4, interview.getMeetLink());
            ps.setInt(5, interview.getApplicationId());
            ps.setInt(6, interview.getOfferId());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) interview.setId(keys.getInt(1));
            }
        }
        InMemoryCache.evictByPrefix("interviews:");
    }

    @Override
    public void modifier(Interview interview) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("organizer_id", interview.getOrganizerId());
            body.put("candidate_id", interview.getCandidateId());
            body.put("date_time", interview.getDateTime() != null ? interview.getDateTime().toString() : null);
            body.put("status", interview.getStatus());
            body.put("meet_link", interview.getMeetLink());
            body.put("application_id", interview.getApplicationId());
            body.put("offer_id", interview.getOfferId());
            ApiClient.put("/interviews/" + interview.getId(), body);
            return;
        }
        String req = "UPDATE interviews SET organizer_id=?, candidate_id=?, date_time=?, status=?, meet_link=?, application_id=?, offer_id=? WHERE id=?";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(req)) {
            ps.setInt(1, interview.getOrganizerId());
            ps.setInt(2, interview.getCandidateId());
            ps.setTimestamp(3, interview.getDateTime());
            ps.setString(4, interview.getStatus());
            ps.setString(5, interview.getMeetLink());
            ps.setInt(6, interview.getApplicationId());
            ps.setInt(7, interview.getOfferId());
            ps.setInt(8, interview.getId());
            ps.executeUpdate();
        }
        InMemoryCache.evictByPrefix("interviews:");
    }

    @Override
    public void supprimer(int id) throws SQLException {
        if (useApi) {
            ApiClient.delete("/interviews/" + id);
            return;
        }
        String req = "DELETE FROM interviews WHERE id=?";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(req)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
        InMemoryCache.evictByPrefix("interviews:");
    }

    @Override
    public List<Interview> recuperer() throws SQLException {
        if (useApi) {
            return InMemoryCache.getOrLoad(CACHE_KEY, CACHE_TTL,
                    () -> jsonArrayToInterviews(ApiClient.get("/interviews")));
        }
        return InMemoryCache.getOrLoadChecked(CACHE_KEY, CACHE_TTL,
                () -> recupererFromDb());
    }

    private List<Interview> recupererFromDb() throws SQLException {
        List<Interview> interviews = new ArrayList<>();
        String req = "SELECT * FROM interviews";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(req);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                interviews.add(rowToInterview(rs));
            }
        }
        return interviews;
    }

    public List<Interview> getByOrganizer(int organizerId) throws SQLException {
        if (useApi) {
            return jsonArrayToInterviews(ApiClient.get("/interviews/organizer/" + organizerId));
        }
        List<Interview> interviews = new ArrayList<>();
        String req = "SELECT * FROM interviews WHERE organizer_id=? ORDER BY date_time DESC";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(req)) {
            ps.setInt(1, organizerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    interviews.add(rowToInterview(rs));
                }
            }
        }
        return interviews;
    }

    public List<Interview> getByCandidate(int candidateId) throws SQLException {
        if (useApi) {
            return jsonArrayToInterviews(ApiClient.get("/interviews/candidate/" + candidateId));
        }
        List<Interview> interviews = new ArrayList<>();
        String req = "SELECT * FROM interviews WHERE candidate_id=? ORDER BY date_time DESC";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(req)) {
            ps.setInt(1, candidateId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    interviews.add(rowToInterview(rs));
                }
            }
        }
        return interviews;
    }

    /** Get all interviews linked to a specific job application. */
    public List<Interview> getByApplication(int applicationId) throws SQLException {
        if (useApi) {
            return jsonArrayToInterviews(ApiClient.get("/interviews/application/" + applicationId));
        }
        List<Interview> interviews = new ArrayList<>();
        String req = "SELECT * FROM interviews WHERE application_id=? ORDER BY date_time DESC";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(req)) {
            ps.setInt(1, applicationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    interviews.add(rowToInterview(rs));
                }
            }
        }
        return interviews;
    }

    private Interview rowToInterview(ResultSet rs) throws SQLException {
        int appId = 0;
        try { appId = rs.getInt("application_id"); if (rs.wasNull()) appId = 0; } catch (Exception ignored) {}
        int ofrId = 0;
        try { ofrId = rs.getInt("offer_id"); if (rs.wasNull()) ofrId = 0; } catch (Exception ignored) {}
        return new Interview(
                rs.getInt("id"),
                rs.getInt("organizer_id"),
                rs.getInt("candidate_id"),
                rs.getTimestamp("date_time"),
                rs.getString("status"),
                rs.getString("meet_link"),
                appId,
                ofrId);
    }
}
