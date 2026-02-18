package tn.esprit.synergygig.utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {

    private static DatabaseConnection instance;   //instance unique
    private Connection connection;

    private final String URL = "jdbc:mysql://localhost:3306/gestion_rh";
    private final String USER = "root";
    private final String PASSWORD = "";

    private DatabaseConnection() { //constructeur prive
        try {
            //etablir la connexion
            connection = DriverManager.getConnection(URL, USER, PASSWORD);
            System.out.println("✅ Connexion à la base de données réussie");
        } catch (SQLException e) {
            System.err.println("❌ Erreur de connexion BD");
            e.printStackTrace();
        }
    }

    public static DatabaseConnection getInstance() { // methode statique  permet d acceder a l instance unique
        if (instance == null) {
            instance = new DatabaseConnection();
        }
        return instance;
    }

    public Connection getConnection() {
        return connection;
    }
}
