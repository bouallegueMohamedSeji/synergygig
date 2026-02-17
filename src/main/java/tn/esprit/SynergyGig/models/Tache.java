package tn.esprit.SynergyGig.models;

import java.time.LocalDate;

/**
 * Modèle Tache - adapté à la table MySQL existante
 *
 * Structure de la table tache :
 * id | titre | description | projet_id | employe_id | statut | priorite | date_debut | date_fin
 */
public class Tache {

    private int id;
    private String titre;
    private String description;
    private int projetId;
    private int employeId;
    private String statut;      // A_FAIRE / EN_COURS / TERMINEE
    private String priorite;    // HAUTE / MOYENNE / BASSE
    private LocalDate dateDebut;
    private LocalDate dateFin;

    // ===== CONSTRUCTEUR VIDE =====
    public Tache() {}

    // ===== CONSTRUCTEUR SANS ID (pour l'ajout) =====
    public Tache(String titre, String description, int projetId, int employeId,
                 String statut, String priorite, LocalDate dateDebut, LocalDate dateFin) {
        this.titre = titre;
        this.description = description;
        this.projetId = projetId;
        this.employeId = employeId;
        this.statut = statut;
        this.priorite = priorite;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
    }

    // ===== CONSTRUCTEUR AVEC ID (lecture depuis BDD) =====
    public Tache(int id, String titre, String description, int projetId, int employeId,
                 String statut, String priorite, LocalDate dateDebut, LocalDate dateFin) {
        this.id = id;
        this.titre = titre;
        this.description = description;
        this.projetId = projetId;
        this.employeId = employeId;
        this.statut = statut;
        this.priorite = priorite;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
    }

    // ===== GETTERS =====
    public int getId()              { return id; }
    public String getTitre()        { return titre; }
    public String getDescription()  { return description; }
    public int getProjetId()        { return projetId; }
    public int getEmployeId()       { return employeId; }
    public String getStatut()       { return statut; }
    public String getPriorite()     { return priorite; }
    public LocalDate getDateDebut() { return dateDebut; }
    public LocalDate getDateFin()   { return dateFin; }

    // ===== SETTERS =====
    public void setId(int id)                { this.id = id; }
    public void setTitre(String titre)       { this.titre = titre; }
    public void setDescription(String d)     { this.description = d; }
    public void setProjetId(int projetId)    { this.projetId = projetId; }
    public void setEmployeId(int employeId)  { this.employeId = employeId; }
    public void setStatut(String statut)     { this.statut = statut; }
    public void setPriorite(String priorite) { this.priorite = priorite; }
    public void setDateDebut(LocalDate d)    { this.dateDebut = d; }
    public void setDateFin(LocalDate d)      { this.dateFin = d; }

    @Override
    public String toString() {
        return "Tache{id=" + id + ", titre='" + titre + "', statut='" + statut +
                "', priorite='" + priorite + "', projetId=" + projetId + "}";
    }
}
