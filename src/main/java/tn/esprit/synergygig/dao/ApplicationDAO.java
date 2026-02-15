package tn.esprit.synergygig.dao;

import tn.esprit.synergygig.entities.Application;
import tn.esprit.synergygig.entities.enums.ApplicationStatus;
import tn.esprit.synergygig.utils.MyDBConnexion;
import java.util.List;
import java.util.ArrayList;
import tn.esprit.synergygig.entities.enums.ApplicationStatus;

import java.sql.*;

public class ApplicationDAO {

    private final Connection cnx;

    public ApplicationDAO() {
        cnx = MyDBConnexion.getInstance().getCnx();
    }

    // ✅ INSERT
    public void insert(Application app) throws SQLException {
        String sql = """
            INSERT INTO applications (offer_id, applicant_id, status)
            VALUES (?, ?, ?)
        """;

        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setInt(1, app.getOfferId());
        ps.setInt(2, app.getApplicantId());
        ps.setString(3, app.getStatus().name());

        ps.executeUpdate();
    }

    // 🔒 empêcher double postulation
    public boolean exists(int offerId, int userId) throws SQLException {
        String sql = """
            SELECT COUNT(*) FROM applications
            WHERE offer_id = ? AND applicant_id = ?
        """;

        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setInt(1, offerId);
        ps.setInt(2, userId);

        ResultSet rs = ps.executeQuery();
        rs.next();
        return rs.getInt(1) > 0;
    }
    public void updateStatus(int applicationId, ApplicationStatus status) throws SQLException {

        String sql = "UPDATE applications SET status = ? WHERE id = ?";

        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setString(1, status.name());
        ps.setInt(2, applicationId);

        ps.executeUpdate();
    }
    public List<Application> selectAll() throws SQLException {

        List<Application> apps = new ArrayList<>();

        String sql = """
        SELECT 
            a.id,
            a.offer_id,
            a.applicant_id,
            a.status,
            a.applied_at,
            o.title AS offer_title,
            u.full_name AS applicant_name
        FROM applications a
        JOIN offers o ON a.offer_id = o.id
        JOIN users u ON a.applicant_id = u.id
        ORDER BY a.applied_at DESC
    """;

        Statement st = cnx.createStatement();
        ResultSet rs = st.executeQuery(sql);

        while (rs.next()) {
            Application app = new Application(
                    rs.getInt("id"),
                    rs.getInt("offer_id"),
                    rs.getInt("applicant_id"),
                    ApplicationStatus.valueOf(rs.getString("status")),
                    rs.getTimestamp("applied_at").toLocalDateTime()
            );

            // 🔥 données JOIN
            app.setOfferTitle(rs.getString("offer_title"));
            app.setApplicantName(rs.getString("applicant_name"));

            apps.add(app);
        }

        return apps;
    }


}
