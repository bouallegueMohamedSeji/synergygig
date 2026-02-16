package tn.esprit.synergygig.dao;

import tn.esprit.synergygig.entities.Offer;
import tn.esprit.synergygig.utils.MyDBConnexion;
import tn.esprit.synergygig.entities.enums.OfferStatus;
import tn.esprit.synergygig.entities.enums.OfferType;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class OfferDAO implements CRUD<Offer> {

    private Connection cnx;

    public OfferDAO() {
        cnx = MyDBConnexion.getInstance().getCnx();
    }

    // ================== INSERT ==================
    @Override
    public void insertOne(Offer o) throws SQLException {
        String sql = """
    INSERT INTO offers (title, description, type, status, created_by, image_url)
    VALUES (?, ?, ?, ?, ?, ?)
""";

        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setString(1, o.getTitle());
        ps.setString(2, o.getDescription());
        ps.setString(3, o.getType().name());
        ps.setString(4, o.getStatus().name());

        ps.setInt(5, o.getCreatedBy());
        ps.setString(6, o.getImageUrl());   // 🔥 IMPORTANT

        ps.executeUpdate();
        System.out.println("✅ Offer insérée avec succès");
    }

    // ================== SELECT ALL ==================
    @Override
    public List<Offer> selectAll() throws SQLException {

        List<Offer> offers = new ArrayList<>();

        String sql = "SELECT * FROM offers";
        Statement st = cnx.createStatement();
        ResultSet rs = st.executeQuery(sql);

        while (rs.next()) {
            Offer o = new Offer(
                    rs.getInt("id"),
                    rs.getString("title"),
                    rs.getString("description"),
                    OfferType.valueOf(rs.getString("type")),
                    OfferStatus.valueOf(rs.getString("status")),
                    rs.getInt("created_by"),
                    rs.getTimestamp("created_at").toLocalDateTime(),
                    rs.getString("image_url") // ✅ AJOUT ICI
            );

            offers.add(o);
        }

        return offers;
    }

    // ================== UPDATE ==================
    @Override
    public void updateOne(Offer o) throws SQLException {

        String sql = "UPDATE offers SET title=?, description=?, type=?, status=? WHERE id=?";

        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setString(1, o.getTitle());
        ps.setString(2, o.getDescription());
        ps.setString(3, o.getType().name());
        ps.setString(4, o.getStatus().name());
        ps.setInt(5, o.getId());

        ps.executeUpdate();
        System.out.println("✏️ Offer mise à jour avec succès");
    }

    // ================== DELETE ==================
    @Override
    public void deleteOne(Offer o) throws SQLException {

        String sql = "DELETE FROM offers WHERE id=?";

        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setInt(1, o.getId());

        ps.executeUpdate();
        System.out.println("🗑️ Offer supprimée avec succès");
    }
    public int countAll() throws SQLException {
        String sql = "SELECT COUNT(*) FROM offers";
        Statement st = cnx.createStatement();
        ResultSet rs = st.executeQuery(sql);
        rs.next();
        return rs.getInt(1);
    }

    public int countByStatus(OfferStatus status) throws SQLException {
        String sql = "SELECT COUNT(*) FROM offers WHERE status = ?";
        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setString(1, status.name());
        ResultSet rs = ps.executeQuery();
        rs.next();
        return rs.getInt(1);
    }
    public void updateStatus(int offerId, OfferStatus status) throws SQLException {

        String sql = "UPDATE offers SET status=? WHERE id=?";
        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setString(1, status.name());
        ps.setInt(2, offerId);
        ps.executeUpdate();
    }
    public List<Offer> selectByUser(int userId) throws SQLException {

        List<Offer> offers = new ArrayList<>();

        String sql = "SELECT * FROM offers WHERE created_by = ?";
        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setInt(1, userId);

        ResultSet rs = ps.executeQuery();

        while (rs.next()) {
            Offer o = new Offer(
                    rs.getInt("id"),
                    rs.getString("title"),
                    rs.getString("description"),
                    OfferType.valueOf(rs.getString("type")),
                    OfferStatus.valueOf(rs.getString("status")),
                    rs.getInt("created_by"),
                    rs.getTimestamp("created_at").toLocalDateTime(),
                    rs.getString("image_url")
            );

            offers.add(o);
        }

        return offers;
    }


}
