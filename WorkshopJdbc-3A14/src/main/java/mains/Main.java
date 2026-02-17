package mains;

import entities.Personne;
import services.ServicePersonne;

import java.sql.SQLException;

public class Main {
    public static void main(String[] args) {
        ServicePersonne servicePersonne = new ServicePersonne();
        Personne personne = new Personne("foulen","benfoulen",20);
        Personne personne1 = new Personne(1,"ahmed","ben ahmed",33);
        try {
            System.out.println(servicePersonne.recuperer());
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }


    }
}
