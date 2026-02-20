package services;

import com.google.gson.*;
import entities.Interview;
import utils.ApiClient;
import utils.AppConfig;
import utils.MyDatabase;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServiceInterview implements IService<Interview> {

    private Connection connection;
    private final boolean useApi;

    public ServiceInterview() {
        useApi = AppConfig.isApiMode();
        if (!useApi) {
            connection = MyDatabase.getInstance().getConnection();
        }
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
        return new Interview(
                obj.get("id").getAsInt(),
                obj.get("organizer_id").getAsInt(),
                obj.get("candidate_id").getAsInt(),
                dateTime,
                obj.get("status").getAsString(),
                meetLink
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
            ApiClient.post("/interviews", body);
            return;
        }
        String req = "INSERT INTO interviews (organizer_id, candidate_id, date_time, status, meet_link) VALUES (?, ?, ?, 'PENDING', ?)";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setInt(1, interview.getOrganizerId());
        ps.setInt(2, interview.getCandidateId());
        ps.setTimestamp(3, interview.getDateTime());
        ps.setString(4, interview.getMeetLink());
        ps.executeUpdate();
        ps.close();
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
            ApiClient.put("/interviews/" + interview.getId(), body);
            return;
        }
        String req = "UPDATE interviews SET organizer_id=?, candidate_id=?, date_time=?, status=?, meet_link=? WHERE id=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setInt(1, interview.getOrganizerId());
        ps.setInt(2, interview.getCandidateId());
        ps.setTimestamp(3, interview.getDateTime());
        ps.setString(4, interview.getStatus());
        ps.setString(5, interview.getMeetLink());
        ps.setInt(6, interview.getId());
        ps.executeUpdate();
        ps.close();
    }

    @Override
    public void supprimer(int id) throws SQLException {
        if (useApi) {
            ApiClient.delete("/interviews/" + id);
            return;
        }
        String req = "DELETE FROM interviews WHERE id=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setInt(1, id);
        ps.executeUpdate();
        ps.close();
    }

    @Override
    public List<Interview> recuperer() throws SQLException {
        if (useApi) {
            return jsonArrayToInterviews(ApiClient.get("/interviews"));
        }
        List<Interview> interviews = new ArrayList<>();
        String req = "SELECT * FROM interviews";
        PreparedStatement ps = connection.prepareStatement(req);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            interviews.add(new Interview(
                    rs.getInt("id"),
                    rs.getInt("organizer_id"),
                    rs.getInt("candidate_id"),
                    rs.getTimestamp("date_time"),
                    rs.getString("status"),
                    rs.getString("meet_link")));
        }
        rs.close();
        ps.close();
        return interviews;
    }

    public List<Interview> getByOrganizer(int organizerId) throws SQLException {
        if (useApi) {
            return jsonArrayToInterviews(ApiClient.get("/interviews/organizer/" + organizerId));
        }
        List<Interview> interviews = new ArrayList<>();
        String req = "SELECT * FROM interviews WHERE organizer_id=? ORDER BY date_time DESC";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setInt(1, organizerId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            interviews.add(new Interview(
                    rs.getInt("id"),
                    rs.getInt("organizer_id"),
                    rs.getInt("candidate_id"),
                    rs.getTimestamp("date_time"),
                    rs.getString("status"),
                    rs.getString("meet_link")));
        }
        rs.close();
        ps.close();
        return interviews;
    }

    public List<Interview> getByCandidate(int candidateId) throws SQLException {
        if (useApi) {
            return jsonArrayToInterviews(ApiClient.get("/interviews/candidate/" + candidateId));
        }
        List<Interview> interviews = new ArrayList<>();
        String req = "SELECT * FROM interviews WHERE candidate_id=? ORDER BY date_time DESC";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setInt(1, candidateId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            interviews.add(new Interview(
                    rs.getInt("id"),
                    rs.getInt("organizer_id"),
                    rs.getInt("candidate_id"),
                    rs.getTimestamp("date_time"),
                    rs.getString("status"),
                    rs.getString("meet_link")));
        }
        rs.close();
        ps.close();
        return interviews;
    }
}
