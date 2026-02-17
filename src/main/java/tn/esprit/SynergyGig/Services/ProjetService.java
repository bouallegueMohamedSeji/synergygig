package tn.esprit.SynergyGig.Services;

import tn.esprit.SynergyGig.DAO.ProjetDAO;
import tn.esprit.SynergyGig.models.Projet;

import java.util.List;

/**
 * ====================================================
 * COUCHE SERVICE - ProjetService
 * ====================================================
 * Position dans l'architecture :
 * FXML → Controller → [ SERVICE ] → DAO → Base de données
 *
 * Rôle du Service :
 * - Recevoir les appels du Controller
 * - Appliquer les règles métier
 * - Transmettre les données au DAO
 *
 * Le Controller ne parle PLUS directement au DAO.
 * Il passe TOUJOURS par le Service.
 * ====================================================
 */
public class ProjetService {

    // Le Service possède le DAO
    // C'est lui qui l'instancie, plus le Controller
    private final ProjetDAO projetDAO = new ProjetDAO();

    // ===== CREATE =====
    /**
     * Reçoit un projet du Controller et le transmet au DAO pour l'insérer en BDD
     */
    public void ajouterProjet(Projet p) {
        projetDAO.ajouterProjet(p);
    }

    // ===== READ =====
    /**
     * Demande au DAO la liste de tous les projets et la retourne au Controller
     */
    public List<Projet> afficherProjets() {
        return projetDAO.afficherProjets();
    }

    // ===== UPDATE =====
    /**
     * Reçoit un projet modifié du Controller et le transmet au DAO pour la mise à jour en BDD
     */
    public void modifierProjet(Projet p) {
        projetDAO.modifierProjet(p);
    }

    // ===== DELETE =====
    /**
     * Reçoit un id du Controller et le transmet au DAO pour supprimer en BDD
     */
    public void supprimerProjet(int id) {
        projetDAO.supprimerProjet(id);
    }
}
