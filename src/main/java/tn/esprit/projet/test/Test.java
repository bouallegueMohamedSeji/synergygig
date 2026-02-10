package tn.esprit.projet.test;

import tn.esprit.projet.entities.Person;
import tn.esprit.projet.services.PersonService;
import tn.esprit.projet.utils.MyDBConnexion;

import java.sql.SQLException;

public class Test {

    public static void main(String[] args) {
        MyDBConnexion c1 = MyDBConnexion.getInstance();

        Person p = new  Person(0, "Wassim", "Bech ye5ou - 5", 0.1);

        PersonService ps = new PersonService() ;

        try {
            ps.insertOneUpdated(p);

            System.out.println(ps.selectALL());
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }

    }
}
