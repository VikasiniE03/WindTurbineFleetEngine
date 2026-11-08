import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * DBConnection.java
 * High-performance JDBC Connection Provider for Renewable Wind Turbine Fleet Engine.
 * Supports 2-Tier Architecture (VM 1 App Host -> VM 2 DB Host simulation).
 * Configurable via Environment Variables or System Properties with graceful fallback.
 */
public class DBConnection {

    private static final String DEFAULT_HOST = "localhost";
    private static final String DEFAULT_PORT = "3306";
    private static final String DEFAULT_DB   = "wind_fleet_db";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASS = "0000";

    private static boolean driverLoaded = false;
    private static Boolean connectionAvailable = null;

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            driverLoaded = true;
        } catch (ClassNotFoundException e) {
            System.err.println("[WARN] MySQL Connector/J Driver not found on classpath: " + e.getMessage());
            driverLoaded = false;
        }
    }

    public static String getHost() {
        String env = System.getenv("DB_HOST");
        if (env != null && !env.isBlank()) return env.trim();
        return System.getProperty("db.host", DEFAULT_HOST);
    }

    public static String getPort() {
        String env = System.getenv("DB_PORT");
        if (env != null && !env.isBlank()) return env.trim();
        return System.getProperty("db.port", DEFAULT_PORT);
    }

    public static String getDbName() {
        String env = System.getenv("DB_NAME");
        if (env != null && !env.isBlank()) return env.trim();
        return System.getProperty("db.name", DEFAULT_DB);
    }

    public static String getUser() {
        String env = System.getenv("DB_USER");
        if (env != null && !env.isBlank()) return env.trim();
        return System.getProperty("db.user", DEFAULT_USER);
    }

    public static String getPassword() {
        String env = System.getenv("DB_PASSWORD");
        if (env != null) return env;
        return System.getProperty("db.password", DEFAULT_PASS);
    }

    public static String getJdbcUrl() {
        return String.format(
            "jdbc:mysql://%s:%s/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=3000&socketTimeout=5000",
            getHost(), getPort(), getDbName()
        );
    }

    public static String getServerOnlyJdbcUrl() {
        return String.format(
            "jdbc:mysql://%s:%s/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=3000&socketTimeout=5000",
            getHost(), getPort()
        );
    }

    public static Connection getConnection() throws SQLException {
        if (!driverLoaded) {
            throw new SQLException("MySQL JDBC Driver (com.mysql.cj.jdbc.Driver) is not loaded.");
        }
        return DriverManager.getConnection(getJdbcUrl(), getUser(), getPassword());
    }

    /**
     * Tests connectivity to the database host.
     * Returns true if database is reachable and credentials are valid.
     */
    public static boolean testConnection() {
        if (!driverLoaded) {
            connectionAvailable = false;
            return false;
        }
        try (Connection conn = getConnection()) {
            boolean valid = (conn != null && !conn.isClosed());
            connectionAvailable = valid;
            return valid;
        } catch (SQLException e) {
            // Check if server is up but database does not exist yet
            try (Connection serverConn = DriverManager.getConnection(getServerOnlyJdbcUrl(), getUser(), getPassword())) {
                initializeDatabaseSchema(serverConn);
                connectionAvailable = true;
                return true;
            } catch (SQLException ex) {
                connectionAvailable = false;
                return false;
            }
        }
    }

    public static boolean isDatabaseAvailable() {
        if (connectionAvailable == null) {
            return testConnection();
        }
        return connectionAvailable;
    }

    private static void initializeDatabaseSchema(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS " + getDbName() + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            stmt.executeUpdate("USE " + getDbName());
            System.out.println("[INFO] Successfully initialized database schema: " + getDbName());
        } catch (SQLException e) {
            System.err.println("[WARN] Schema initialization skipped: " + e.getMessage());
        }
    }
}
