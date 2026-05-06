package utils;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * HikariCP-backed connection pool.
 * <p>
 * {@link #getConnection()} returns a <b>fresh pooled connection</b> every time.
 * Callers <b>must</b> close it when done (returns it to the pool).
 * Use try-with-resources:
 * <pre>
 *   try (Connection conn = MyDatabase.getInstance().getConnection();
 *        PreparedStatement ps = conn.prepareStatement(sql)) {
 *       ...
 *   }
 * </pre>
 */
public class MyDatabase {

    private static volatile MyDatabase instance;
    private HikariDataSource dataSource;
    private boolean warnedNoPool = false;

    private MyDatabase() {
        // Skip pool init in API mode — JDBC is not used
        if (!AppConfig.isApiMode()) {
            // Open SSH tunnel if needed (remote MySQL behind firewall)
            int tunnelPort = SshTunnel.open();
            initPool(tunnelPort);
        } else {
            System.out.println("ℹ API mode — skipping HikariCP pool (using REST API)");
        }
    }

    private void initPool(int tunnelPort) {
        String url = AppConfig.getDbUrl();
        String user = AppConfig.getDbUser();
        String password = AppConfig.getDbPassword();

        // If SSH tunnel is open, rewrite JDBC URL to go through localhost tunnel
        if (tunnelPort > 0) {
            url = url.replaceFirst("//[^/]+:\\d+/", "//localhost:" + tunnelPort + "/");
            System.out.println("🔗 JDBC URL rewritten for SSH tunnel: " + url);
        }

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(url);
        cfg.setUsername(user);
        cfg.setPassword(password);
        cfg.setMaximumPoolSize(20);
        cfg.setMinimumIdle(3);
        cfg.setIdleTimeout(300_000);          // 5 min
        cfg.setMaxLifetime(1_800_000);        // 30 min
        cfg.setConnectionTimeout(5_000);      // 5 s
        cfg.setValidationTimeout(3_000);      // 3 s
        cfg.setLeakDetectionThreshold(60_000);// warn after 60 s
        cfg.setPoolName("SynergyGig-Pool");

        // MySQL performance properties
        cfg.addDataSourceProperty("cachePrepStmts", "true");
        cfg.addDataSourceProperty("prepStmtCacheSize", "250");
        cfg.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        cfg.addDataSourceProperty("useServerPrepStmts", "true");

        try {
            dataSource = new HikariDataSource(cfg);
            System.out.println("\u2705 HikariCP pool started (" + AppConfig.getMode() + " mode, pool=" + cfg.getMaximumPoolSize() + ")");
        } catch (Exception e) {
            System.err.println("\u274c Connection pool failed: " + e.getMessage());
            System.err.println("   URL: " + url + "  User: " + user);
            System.err.println("   If remote mode, make sure the SSH tunnel is running:");
            System.err.println("   ssh -L 3306:localhost:3306 -N " + AppConfig.getServerSshUser() + "@" + AppConfig.getServerHost());
        }
    }

    public static MyDatabase getInstance() {
        if (instance == null) {
            synchronized (MyDatabase.class) {
                if (instance == null) {
                    instance = new MyDatabase();
                }
            }
        }
        return instance;
    }

    /**
     * Returns a connection from the pool.
     * <b>Caller must close it</b> (e.g. via try-with-resources) to return it to the pool.
     */
    public Connection getConnection() {
        if (dataSource == null) {
            if (!warnedNoPool) {
                warnedNoPool = true;
                System.err.println("\u274c No connection pool (API mode?) — JDBC not available");
            }
            return null;
        }
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            System.err.println("\u274c Pool getConnection failed: " + e.getMessage());
            return null;
        }
    }

    /** Shuts down the pool and SSH tunnel (call on app exit). */
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            System.out.println("HikariCP pool closed.");
        }
        SshTunnel.close();
    }
}
