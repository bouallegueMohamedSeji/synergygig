package tn.esprit.SynergyGig.DAO;

import tn.esprit.SynergyGig.models.Projet;
import tn.esprit.synergygig.utils.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProjetDAO {

    private final Connection cnx;

    public ProjetDAO() {
        cnx = DatabaseConnection.getInstance().getConnection();
    }

    // CREATE
    public void ajouterProjet(Projet p) {
        String sql = "INSERT INTO projet (nom, description, date_debut, date_fin, statut, budget) VALUES (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setString(1, p.getNom());
            ps.setString(2, p.getDescription());
            ps.setDate(3, Date.valueOf(p.getDateDebut()));
            ps.setDate(4, Date.valueOf(p.getDateFin()));
            ps.setString(5, p.getStatut());
            ps.setDouble(6, p.getBudget());
            ps.executeUpdate();

            System.out.println("✔ Projet ajouté avec succès");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // READ
    public List<Projet> afficherProjets() {
        List<Projet> projets = new ArrayList<>();
        String sql = "SELECT * FROM projet";

        try (Statement st = cnx.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                projets.add(new Projet(
                        rs.getInt("id"),
                        rs.getString("nom"),
                        rs.getString("description"),
                        rs.getDate("date_debut").toLocalDate(),
                        rs.getDate("date_fin").toLocalDate(),
                        rs.getString("statut"),
                        rs.getDouble("budget")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return projets;
    }

    // UPDATE
    public void modifierProjet(Projet p) {
        String sql = "UPDATE projet SET nom=?, description=?, statut=?, budget=? WHERE id=?";

        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setString(1, p.getNom());
            ps.setString(2, p.getDescription());
            ps.setString(3, p.getStatut());
            ps.setDouble(4, p.getBudget());
            ps.setInt(5, p.getId());
            ps.executeUpdate();

            System.out.println("✔ Projet modifié");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // DELETE
    public void supprimerProjet(int id) {
        String sql = "DELETE FROM projet WHERE id=?";

        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();

            System.out.println("✔ Projet supprimé");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
