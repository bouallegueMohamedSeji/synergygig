package tn.esprit.synergygig.test;

import tn.esprit.synergygig.utils.MyDBConnexion;
import java.sql.Connection;

public class TestDB {
    public static void main(String[] args) {

        Connection cnx = MyDBConnexion.getInstance().getCnx();

        if (cnx != null) {
            System.out.println("🎉 Connexion réussie !");
        } else {
            System.out.println("❌ Échec de la connexion");
        }
    }
}
