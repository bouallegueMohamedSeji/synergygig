package tn.esprit;

import tn.esprit.SynergyGig.DAO.ProjetDAO;
import tn.esprit.SynergyGig.models.Projet;

import java.time.LocalDate;
import java.util.List;

public class Main {

    public static void main(String[] args) {

        ProjetDAO projetDAO = new ProjetDAO();

        System.out.println("===== TEST CRUD PROJET =====");

        // 1️⃣ CREATE
        Projet nouveauProjet = new Projet(
                "SynergyGig Platform",
                "Développement du module projets et tâches",
                LocalDate.now(),
                LocalDate.now().plusMonths(3),
                "EN_COURS",
                8000
        );

        projetDAO.ajouterProjet(nouveauProjet);

        // 2️⃣ READ
        System.out.println("\n📋 Liste des projets après ajout :");
        List<Projet> projets = projetDAO.afficherProjets();
        for (Projet p : projets) {
            System.out.println(p.getId() + " | " + p.getNom() + " | " + p.getStatut());
        }

        // 3️⃣ UPDATE (sur le dernier projet)
        if (!projets.isEmpty()) {
            Projet projetAModifier = projets.get(projets.size() - 1);
            projetAModifier.setNom("SynergyGig Platform - UPDATED");
            projetAModifier.setStatut("TERMINE");
            projetAModifier.setBudget(9500);

            projetDAO.modifierProjet(projetAModifier);
        }

        // 4️⃣ READ après UPDATE
        System.out.println("\n📋 Liste des projets après modification :");
        projetDAO.afficherProjets().forEach(p ->
                System.out.println(p.getId() + " | " + p.getNom() + " | " + p.getStatut())
        );

        // 5️⃣ DELETE (sur le dernier projet)
        projets = projetDAO.afficherProjets();
        if (!projets.isEmpty()) {
            int idASupprimer = projets.get(projets.size() - 1).getId();
            projetDAO.supprimerProjet(idASupprimer);
        }

        // 6️⃣ READ final
        System.out.println("\n📋 Liste des projets après suppression :");
        projetDAO.afficherProjets().forEach(p ->
                System.out.println(p.getId() + " | " + p.getNom())
        );

        System.out.println("\n✅ TEST CRUD TERMINÉ");
    }
}
