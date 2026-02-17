package tn.esprit.SynergyGig.models;

import java.time.LocalDate;

public class Projet {

    private int id;
    private String nom;
    private String description;
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private String statut;
    private double budget;

    public Projet() {}

    public Projet(String nom, String description, LocalDate dateDebut,
                  LocalDate dateFin, String statut, double budget) {
        this.nom = nom;
        this.description = description;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
        this.statut = statut;
        this.budget = budget;
    }

    public Projet(int id, String nom, String description, LocalDate dateDebut,
                  LocalDate dateFin, String statut, double budget) {
        this.id = id;
        this.nom = nom;
        this.description = description;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
        this.statut = statut;
        this.budget = budget;
    }

    // ===== GETTERS =====
    public int getId() {
        return id;
    }

    public String getNom() {
        return nom;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getDateDebut() {
        return dateDebut;
    }

    public LocalDate getDateFin() {
        return dateFin;
    }

    public String getStatut() {
        return statut;
    }

    public double getBudget() {
        return budget;
    }

    // ===== SETTERS =====
    public void setId(int id) {
        this.id = id;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setDateDebut(LocalDate dateDebut) {
        this.dateDebut = dateDebut;
    }

    public void setDateFin(LocalDate dateFin) {
        this.dateFin = dateFin;
    }

    public void setStatut(String statut) {
        this.statut = statut;
    }

    public void setBudget(double budget) {
        this.budget = budget;
    }
}
