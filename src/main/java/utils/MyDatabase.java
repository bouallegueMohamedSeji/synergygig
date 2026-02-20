package utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class MyDatabase {

    private Connection connection;
    private static MyDatabase instance;

    private MyDatabase() {
        String url = AppConfig.getDbUrl();
        String user = AppConfig.getDbUser();
        String password = AppConfig.getDbPassword();

        try {
            connection = DriverManager.getConnection(url, user, password);
            System.out.println("✅ Database connection established (" + AppConfig.getMode() + " mode, user=" + user + ")");
        } catch (SQLException e) {
            System.err.println("❌ Database connection failed: " + e.getMessage());
            System.err.println("   URL: " + url + "  User: " + user);
            System.err.println("   If remote mode, make sure the SSH tunnel is running:");
            System.err.println("   ssh -L 3306:localhost:3306 -N " + AppConfig.getServerSshUser() + "@" + AppConfig.getServerHost());
        }
    }

    public static synchronized MyDatabase getInstance() {
        if (instance == null) {
            instance = new MyDatabase();
        }
        return instance;
    }

    public Connection getConnection() {
        return connection;
    }
}
