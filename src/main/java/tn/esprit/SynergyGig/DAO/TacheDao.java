package tn.esprit.SynergyGig.DAO;
import tn.esprit.SynergyGig.models.Tache;
import tn.esprit.synergygig.utils.DatabaseConnection;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TacheDao {
    private final Connection cnx;

    public TacheDao() {
        cnx = DatabaseConnection.getInstance().getConnection();
    }

    public void ajouterTache(Tache t) {
        String sql = "INSERT INTO tache (titre, description, projet_id, employe_id, statut, priorite, date_debut, date_fin) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setString(1, t.getTitre());
            ps.setString(2, t.getDescription());
            ps.setInt(3, t.getProjetId());
            ps.setInt(4, t.getEmployeId());
            ps.setString(5, t.getStatut());
            ps.setString(6, t.getPriorite());
            ps.setDate(7, Date.valueOf(t.getDateDebut()));
            ps.setDate(8, Date.valueOf(t.getDateFin()));
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public List<Tache> afficherTaches() {
        List<Tache> taches = new ArrayList<>();
        String sql = "SELECT * FROM tache ORDER BY id DESC";
        try (Statement st = cnx.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) { taches.add(mapResultSet(rs)); }
        } catch (SQLException e) { e.printStackTrace(); }
        return taches;
    }

    public List<Tache> afficherTachesParProjet(int projetId) {
        List<Tache> taches = new ArrayList<>();
        String sql = "SELECT * FROM tache WHERE projet_id = ?";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, projetId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) { taches.add(mapResultSet(rs)); }
        } catch (SQLException e) { e.printStackTrace(); }
        return taches;
    }

    public void modifierTache(Tache t) {
        String sql = "UPDATE tache SET titre=?, description=?, projet_id=?, employe_id=?, statut=?, priorite=?, date_debut=?, date_fin=? WHERE id=?";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setString(1, t.getTitre());
            ps.setString(2, t.getDescription());
            ps.setInt(3, t.getProjetId());
            ps.setInt(4, t.getEmployeId());
            ps.setString(5, t.getStatut());
            ps.setString(6, t.getPriorite());
            ps.setDate(7, Date.valueOf(t.getDateDebut()));
            ps.setDate(8, Date.valueOf(t.getDateFin()));
            ps.setInt(9, t.getId());
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public void supprimerTache(int id) {
        String sql = "DELETE FROM tache WHERE id=?";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private Tache mapResultSet(ResultSet rs) throws SQLException {
        return new Tache(
                rs.getInt("id"), rs.getString("titre"), rs.getString("description"),
                rs.getInt("projet_id"), rs.getInt("employe_id"),
                rs.getString("statut"), rs.getString("priorite"),
                rs.getDate("date_debut").toLocalDate(),
                rs.getDate("date_fin").toLocalDate()
        );
    }
}
