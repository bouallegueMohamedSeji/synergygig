package tn.esprit.SynergyGig.DAO;

import org.junit.jupiter.api.*;
import tn.esprit.SynergyGig.models.Projet;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires JUnit Jupiter - ProjetDAO
 *
 * @TestMethodOrder : exécute les tests dans l'ordre des @Order
 * @DisplayName : affiche un nom lisible dans les résultats
 * @BeforeAll : s'exécute UNE FOIS avant tous les tests
 * @Test : marque une méthode comme test unitaire
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Tests CRUD - ProjetDAO")
public class Projetdaotest {

    private static ProjetDAO projetDAO;
    private static int idProjetTest;

    @BeforeAll
    @DisplayName("Initialisation : Connexion BDD")
    static void setUp() {
        projetDAO = new ProjetDAO();
        System.out.println("=== DEBUT DES TESTS ProjetDAO ===");
    }

    @Test
    @Order(1)
    @DisplayName("Test 1 : CREATE - Ajouter un projet")
    void testAjouterProjet() {
        System.out.println("--- Test : ajouterProjet ---");

        // ARRANGE : préparer les données de test
        Projet projet = new Projet(
                "Projet Test JUnit",
                "Description test automatique",
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 6, 30),
                "EN_COURS",
                5000.0
        );

        // ACT : exécuter l'action à tester
        int nbAvant = projetDAO.afficherProjets().size();
        projetDAO.ajouterProjet(projet);
        int nbApres = projetDAO.afficherProjets().size();

        // ASSERT : vérifier le résultat
        assertEquals(nbAvant + 1, nbApres, "Le nombre de projets doit augmenter de 1");
        assertTrue(nbApres > 0, "La liste ne doit pas etre vide");
        System.out.println("✅ Projet ajoute ! Total projets : " + nbApres);
    }

    @Test
    @Order(2)
    @DisplayName("Test 2 : READ - Afficher tous les projets")
    void testAfficherProjets() {
        System.out.println("--- Test : afficherProjets ---");

        // ACT
        List<Projet> projets = projetDAO.afficherProjets();

        // ASSERT
        assertNotNull(projets, "La liste ne doit pas etre null");
        assertFalse(projets.isEmpty(), "La liste ne doit pas etre vide");

        // Garde l'ID du dernier projet pour les tests suivants
        idProjetTest = projets.get(projets.size() - 1).getId();

        System.out.println("✅ " + projets.size() + " projets trouves");
        System.out.println("   ID du projet test : " + idProjetTest);
    }

    @Test
    @Order(3)
    @DisplayName("Test 3 : UPDATE - Modifier un projet")
    void testModifierProjet() {
        System.out.println("--- Test : modifierProjet ---");

        // ARRANGE : récupère le projet et le modifie
        List<Projet> projets = projetDAO.afficherProjets();
        assertFalse(projets.isEmpty(), "Il doit y avoir au moins un projet");

        Projet projet = projets.get(projets.size() - 1);
        idProjetTest = projet.getId();

        String nouveauNom = "Projet MODIFIE par JUnit";
        projet.setNom(nouveauNom);
        projet.setStatut("TERMINE");
        projet.setBudget(7500.0);

        // ACT
        projetDAO.modifierProjet(projet);

        // ASSERT : recharge et vérifie la modification
        List<Projet> projetsApres = projetDAO.afficherProjets();
        Projet projetModifie = projetsApres.stream()
                .filter(p -> p.getId() == idProjetTest)
                .findFirst()
                .orElse(null);

        assertNotNull(projetModifie, "Le projet doit exister apres modification");
        assertEquals(nouveauNom, projetModifie.getNom(), "Le nom doit etre modifie");
        assertEquals("TERMINE", projetModifie.getStatut(), "Le statut doit etre TERMINE");
        assertEquals(7500.0, projetModifie.getBudget(), "Le budget doit etre 7500");

        System.out.println("✅ Projet #" + idProjetTest + " modifie avec succes");
    }

    @Test
    @Order(4)
    @DisplayName("Test 4 : DELETE - Supprimer un projet")
    void testSupprimerProjet() {
        System.out.println("--- Test : supprimerProjet ---");

        // ARRANGE
        int nbAvant = projetDAO.afficherProjets().size();
        assertTrue(idProjetTest > 0, "L'ID du projet test doit etre valide");

        // ACT
        projetDAO.supprimerProjet(idProjetTest);
        int nbApres = projetDAO.afficherProjets().size();

        // ASSERT
        assertEquals(nbAvant - 1, nbApres, "Le nombre de projets doit diminuer de 1");

        // Vérifie que le projet n'existe plus
        List<Projet> projetsApres = projetDAO.afficherProjets();
        boolean existe = projetsApres.stream()
                .anyMatch(p -> p.getId() == idProjetTest);

        assertFalse(existe, "Le projet supprime ne doit plus exister");
        System.out.println("✅ Projet #" + idProjetTest + " supprime avec succes");
    }

    @AfterAll
    @DisplayName("Nettoyage final")
    static void tearDown() {
        System.out.println("=== FIN DES TESTS ProjetDAO ===\n");
    }
}