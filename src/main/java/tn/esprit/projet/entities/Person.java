package tn.esprit.projet.entities;

import java.util.Objects;

public class Person {

    private int id;
    private String nom, prenom;
    private double salaire;

    public Person(){}

    public Person(int id, String nom, String prenom, double salaire) {
        this.id = id;
        this.nom = nom;
        this.prenom = prenom;
        this.salaire = salaire;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getPrenom() {
        return prenom;
    }

    public void setPrenom(String prenom) {
        this.prenom = prenom;
    }

    public double getSalaire() {
        return salaire;
    }

    public void setSalaire(double salaire) {
        this.salaire = salaire;
    }

    @Override
    public String toString() {
        return "Person{" +
                "id=" + id +
                ", nom='" + nom + '\'' +
                ", preom='" + prenom + '\'' +
                ", salaire=" + salaire +
                '}';
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof Person person)) return false;

        return id == person.id && Double.compare(salaire, person.salaire) == 0 && Objects.equals(nom, person.nom) && Objects.equals(prenom, person.prenom);
    }

    @Override
    public int hashCode() {
        int result = id;
        result = 31 * result + Objects.hashCode(nom);
        result = 31 * result + Objects.hashCode(prenom);
        result = 31 * result + Double.hashCode(salaire);
        return result;
    }
}
