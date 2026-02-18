package tn.esprit.synergygig.dao;

import tn.esprit.synergygig.entities.Gig;
import tn.esprit.synergygig.entities.User;
import tn.esprit.synergygig.utils.MyDBConnexion;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class GigDAO implements CRUD<Gig> {

    private Connection cnx;

    public GigDAO() {
        cnx = MyDBConnexion.getInstance().getCnx();
    }

    @Override
    public void insertOne(Gig gig) throws SQLException {
        String req = "INSERT INTO gigs (title, description, user_id, status) VALUES (?, ?, ?, ?)";
        PreparedStatement ps = cnx.prepareStatement(req);
        ps.setString(1, gig.getTitle());
        ps.setString(2, gig.getDescription());
        if (gig.getUser() != null) {
            ps.setInt(3, gig.getUser().getId());
        } else {
            ps.setNull(3, Types.INTEGER);
        }
        ps.setString(4, gig.getStatus());
        ps.executeUpdate();
    }

    @Override
    public void updateOne(Gig gig) throws SQLException {
        String req = "UPDATE gigs SET title=?, description=?, user_id=?, status=? WHERE id=?";
        PreparedStatement ps = cnx.prepareStatement(req);
        ps.setString(1, gig.getTitle());
        ps.setString(2, gig.getDescription());
        if (gig.getUser() != null) {
            ps.setInt(3, gig.getUser().getId());
        } else {
            ps.setNull(3, Types.INTEGER);
        }
        ps.setString(4, gig.getStatus());
        ps.setInt(5, gig.getId());
        ps.executeUpdate();
    }

    @Override
    public void deleteOne(Gig gig) throws SQLException {
        String req = "DELETE FROM gigs WHERE id=?";
        PreparedStatement ps = cnx.prepareStatement(req);
        ps.setInt(1, gig.getId());
        ps.executeUpdate();
    }

    @Override
    public List<Gig> selectAll() throws SQLException {
        List<Gig> gigs = new ArrayList<>();
        String req = "SELECT * FROM gigs";
        Statement st = cnx.createStatement();
        ResultSet rs = st.executeQuery(req);
        while (rs.next()) {
            Gig g = new Gig();
            g.setId(rs.getInt("id"));
            g.setTitle(rs.getString("title"));
            g.setDescription(rs.getString("description"));
            g.setStatus(rs.getString("status"));
            g.setCreatedAt(rs.getTimestamp("created_at"));
            // Note: User is not fully loaded here to avoid circular dependency loop or N+1 if not needed.
            // If needed, we can fetch it.
            gigs.add(g);
        }
        return gigs;
    }
}
