package services;

import entities.Interview;
import utils.MyDatabase;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ServiceInterview implements IService<Interview> {

    private Connection connection;

    public ServiceInterview() {
        connection = MyDatabase.getInstance().getConnection();
    }

    @Override
    public void ajouter(Interview interview) throws SQLException {
        String req = "INSERT INTO interviews (organizer_id, candidate_id, date_time, status, meet_link) VALUES (?, ?, ?, ?, ?)";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setInt(1, interview.getOrganizerId());
        ps.setInt(2, interview.getCandidateId());
        ps.setTimestamp(3, interview.getDateTime());
        ps.setString(4, interview.getStatus());
        ps.setString(5, interview.getMeetLink());
        ps.executeUpdate();
        ps.close();
    }

    @Override
    public void modifier(Interview interview) throws SQLException {
        String req = "UPDATE interviews SET date_time=?, status=?, meet_link=? WHERE id=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setTimestamp(1, interview.getDateTime());
        ps.setString(2, interview.getStatus());
        ps.setString(3, interview.getMeetLink());
        ps.setInt(4, interview.getId());
        ps.executeUpdate();
        ps.close();
    }

    @Override
    public void supprimer(int id) throws SQLException {
        String req = "DELETE FROM interviews WHERE id=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setInt(1, id);
        ps.executeUpdate();
        ps.close();
    }

    @Override
    public List<Interview> recuperer() throws SQLException {
        List<Interview> interviews = new ArrayList<>();
        String req = "SELECT * FROM interviews ORDER BY date_time DESC";
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

    /**
     * Get interviews organized by a specific user.
     */
    public List<Interview> getByOrganizer(int organizerId) throws SQLException {
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

    /**
     * Get interviews for a candidate.
     */
    public List<Interview> getByCandidate(int candidateId) throws SQLException {
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
