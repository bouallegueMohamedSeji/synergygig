package tn.esprit.SynergyGig.DAO;

import org.junit.jupiter.api.*;
import tn.esprit.SynergyGig.models.Projet;
import tn.esprit.SynergyGig.models.Tache;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Tests CRUD - TacheDao")
public class tachesdaotest {

    private static TacheDao tacheDao;
    private static ProjetDAO projetDAO;
    private static int idProjetTest;
    private static int idTacheTest;

    @BeforeAll
    static void setUp() {
        tacheDao = new TacheDao();
        projetDAO = new ProjetDAO();
        System.out.println("=== DEBUT TESTS TacheDao ===");

        // Créer un projet obligatoire pour la clé étrangère
        Projet projet = new Projet(
                "Projet Test Tache",
                "Projet pour test TacheDao",
                LocalDate.now(),
                LocalDate.now().plusDays(30),
                "EN_COURS",
                3000
        );

        projetDAO.ajouterProjet(projet);

        List<Projet> projets = projetDAO.afficherProjets();
        idProjetTest = projets.get(projets.size() - 1).getId();
    }

    @Test
    @Order(1)
    @DisplayName("CREATE - Ajouter une tâche")
    void testAjouterTache() {

        Tache t = new Tache(
                "Tache Test",
                "Description Test",
                idProjetTest,
                2, // ⚠️ Met un employe_id EXISTANT dans ta BDD
                "EN_COURS",
                "HAUTE",
                LocalDate.now(),
                LocalDate.now().plusDays(5)
        );

        int nbAvant = tacheDao.afficherTaches().size();
        tacheDao.ajouterTache(t);
        int nbApres = tacheDao.afficherTaches().size();

        assertEquals(nbAvant + 1, nbApres);

        List<Tache> taches = tacheDao.afficherTaches();
        idTacheTest = taches.get(0).getId();

        System.out.println("✅ Tache ajoutée ID=" + idTacheTest);
    }

    @Test
    @Order(2)
    @DisplayName("READ - Afficher les tâches par projet")
    void testAfficherTachesParProjet() {

        List<Tache> taches = tacheDao.afficherTachesParProjet(idProjetTest);

        assertNotNull(taches);
        assertFalse(taches.isEmpty());

        System.out.println("✅ " + taches.size() + " tâches trouvées pour projet " + idProjetTest);
    }

    @Test
    @Order(3)
    @DisplayName("UPDATE - Modifier une tâche")
    void testModifierTache() {

        List<Tache> taches = tacheDao.afficherTaches();
        assertFalse(taches.isEmpty());

        Tache t = taches.get(0);
        idTacheTest = t.getId();

        t.setTitre("Tache MODIFIEE");
        t.setStatut("TERMINE");

        tacheDao.modifierTache(t);

        List<Tache> apres = tacheDao.afficherTaches();
        Tache modifiee = apres.stream()
                .filter(x -> x.getId() == idTacheTest)
                .findFirst()
                .orElse(null);

        assertNotNull(modifiee);
        assertEquals("Tache MODIFIEE", modifiee.getTitre());
        assertEquals("TERMINE", modifiee.getStatut());

        System.out.println("✅ Tache modifiée");
    }

    @Test
    @Order(4)
    @DisplayName("DELETE - Supprimer une tâche")
    void testSupprimerTache() {

        int nbAvant = tacheDao.afficherTaches().size();

        tacheDao.supprimerTache(idTacheTest);

        int nbApres = tacheDao.afficherTaches().size();

        assertEquals(nbAvant - 1, nbApres);

        System.out.println("✅ Tache supprimée");
    }

    @AfterAll
    static void tearDown() {
        projetDAO.supprimerProjet(idProjetTest);
        System.out.println("=== FIN TESTS TacheDao ===");
    }
}
