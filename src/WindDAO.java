import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WindDAO.java
 * Data Access Object providing robust SQL CRUD operations for Wind Fleet entities.
 * Supports Phased ingestion:
 * - Phase 1: Master Asset Registry (turbines)
 * - Phase 2: High-frequency SCADA Telemetry Stream (scada_telemetry)
 * - Phase 3: Predictive Work Orders & Safety Downtime Logs (maintenance_tickets, downtime_logs)
 */
public class WindDAO {

    private final AtomicInteger simulatedTurbines = new AtomicInteger(0);
    private final AtomicInteger simulatedTelemetry = new AtomicInteger(0);
    private final AtomicInteger simulatedTickets = new AtomicInteger(0);
    private final AtomicInteger simulatedDowntime = new AtomicInteger(0);

    public WindDAO() {
        if (DBConnection.isDatabaseAvailable()) {
            ensureTablesCreated();
        }
    }

    /**
     * Initializes required tables if connected to MySQL.
     */
    public synchronized void ensureTablesCreated() {
        if (!DBConnection.isDatabaseAvailable()) return;

        String createTurbines = """
            CREATE TABLE IF NOT EXISTS turbines (
                turbine_id VARCHAR(30) PRIMARY KEY,
                farm_sector VARCHAR(50) NOT NULL,
                category VARCHAR(50) NOT NULL,
                max_rated_capacity_mw DECIMAL(6, 2) NOT NULL,
                max_gearbox_temp_cap DECIMAL(5, 2) NOT NULL,
                commission_date DATE NOT NULL,
                status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            ) ENGINE=InnoDB;
        """;

        String createTelemetry = """
            CREATE TABLE IF NOT EXISTS scada_telemetry (
                telemetry_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                turbine_id VARCHAR(30) NOT NULL,
                recorded_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                gearbox_temp DECIMAL(6, 2) NOT NULL,
                blade_vibration DECIMAL(6, 2) NOT NULL,
                yaw_misalignment DECIMAL(6, 2) DEFAULT 0.00,
                sand_dust_wear DECIMAL(6, 2) DEFAULT 0.00,
                ice_accumulation DECIMAL(6, 2) DEFAULT 0.00,
                rotor_speed DECIMAL(6, 2) NOT NULL,
                actual_energy_mwh DECIMAL(8, 2) NOT NULL,
                operating_hours DECIMAL(6, 2) NOT NULL,
                scada_error_code VARCHAR(100) NULL,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (turbine_id) REFERENCES turbines (turbine_id) ON DELETE CASCADE
            ) ENGINE=InnoDB;
        """;

        String createTickets = """
            CREATE TABLE IF NOT EXISTS maintenance_tickets (
                ticket_id VARCHAR(40) PRIMARY KEY,
                turbine_id VARCHAR(30) NOT NULL,
                category VARCHAR(50) NOT NULL,
                health_score DECIMAL(5, 2) NOT NULL,
                health_band VARCHAR(100) NOT NULL,
                brake_lock_triggered BOOLEAN NOT NULL DEFAULT FALSE,
                capacity_factor DECIMAL(5, 2) NOT NULL,
                recommended_action TEXT NOT NULL,
                urgency_level VARCHAR(20) NOT NULL,
                generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (turbine_id) REFERENCES turbines (turbine_id) ON DELETE CASCADE
            ) ENGINE=InnoDB;
        """;

        String createDowntime = """
            CREATE TABLE IF NOT EXISTS downtime_logs (
                downtime_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                turbine_id VARCHAR(30) NOT NULL,
                incident_reason VARCHAR(255) NOT NULL,
                root_cause_metric VARCHAR(100) NOT NULL,
                metric_value DECIMAL(8, 2) NOT NULL,
                threshold_exceeded DECIMAL(8, 2) NOT NULL,
                lockout_status VARCHAR(50) NOT NULL,
                logged_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (turbine_id) REFERENCES turbines (turbine_id) ON DELETE CASCADE
            ) ENGINE=InnoDB;
        """;

        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTurbines);
            stmt.execute(createTelemetry);
            stmt.execute(createTickets);
            stmt.execute(createDowntime);
        } catch (SQLException e) {
            System.err.println("[WindDAO] Warning creating tables: " + e.getMessage());
        }
    }

    /**
     * Phase 1: Ingests or verifies turbine asset in registry.
     */
    public boolean upsertTurbine(String turbineId, String farmSector, String category,
                                 double maxCapacityMw, double maxTempCap) {
        if (!DBConnection.isDatabaseAvailable()) {
            simulatedTurbines.incrementAndGet();
            return true;
        }

        String sql = """
            INSERT INTO turbines (turbine_id, farm_sector, category, max_rated_capacity_mw, max_gearbox_temp_cap, commission_date, status)
            VALUES (?, ?, ?, ?, ?, CURRENT_DATE, 'ACTIVE')
            ON DUPLICATE KEY UPDATE
                farm_sector = VALUES(farm_sector),
                category = VALUES(category),
                max_rated_capacity_mw = VALUES(max_rated_capacity_mw),
                max_gearbox_temp_cap = VALUES(max_gearbox_temp_cap);
        """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, turbineId);
            ps.setString(2, farmSector);
            ps.setString(3, category);
            ps.setDouble(4, maxCapacityMw);
            ps.setDouble(5, maxTempCap);
            int rows = ps.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            System.err.println("[WindDAO] SQL Error in upsertTurbine: " + e.getMessage());
            return false;
        }
    }

    /**
     * Phase 2: Ingests SCADA telemetry stream.
     */
    public boolean insertTelemetry(String turbineId, String recordedTimestamp, double gearboxTemp,
                                   double bladeVibration, double yawMisalignment, double sandDustWear,
                                   double iceAccumulation, double rotorSpeed, double actualEnergyMwh,
                                   double operatingHours, String scadaErrorCode) {
        if (!DBConnection.isDatabaseAvailable()) {
            simulatedTelemetry.incrementAndGet();
            return true;
        }

        String sql = """
            INSERT INTO scada_telemetry (
                turbine_id, recorded_timestamp, gearbox_temp, blade_vibration,
                yaw_misalignment, sand_dust_wear, ice_accumulation, rotor_speed,
                actual_energy_mwh, operating_hours, scada_error_code
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
        """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, turbineId);
            Timestamp ts;
            try {
                ts = Timestamp.valueOf(recordedTimestamp);
            } catch (Exception ex) {
                ts = new Timestamp(System.currentTimeMillis());
            }
            ps.setTimestamp(2, ts);
            ps.setDouble(3, gearboxTemp);
            ps.setDouble(4, bladeVibration);
            ps.setDouble(5, yawMisalignment);
            ps.setDouble(6, sandDustWear);
            ps.setDouble(7, iceAccumulation);
            ps.setDouble(8, rotorSpeed);
            ps.setDouble(9, actualEnergyMwh);
            ps.setDouble(10, operatingHours);
            ps.setString(11, scadaErrorCode);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[WindDAO] SQL Error in insertTelemetry: " + e.getMessage());
            return false;
        }
    }

    /**
     * Phase 3: Records generated predictive maintenance ticket.
     */
    public boolean insertMaintenanceTicket(String ticketId, String turbineId, String category,
                                          double healthScore, String healthBand, boolean brakeLock,
                                          double capacityFactor, String recommendedAction,
                                          String urgencyLevel) {
        if (!DBConnection.isDatabaseAvailable()) {
            simulatedTickets.incrementAndGet();
            return true;
        }

        String sql = """
            INSERT INTO maintenance_tickets (
                ticket_id, turbine_id, category, health_score, health_band,
                brake_lock_triggered, capacity_factor, recommended_action, urgency_level
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);
        """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ticketId);
            ps.setString(2, turbineId);
            ps.setString(3, category);
            ps.setDouble(4, healthScore);
            ps.setString(5, healthBand);
            ps.setBoolean(6, brakeLock);
            ps.setDouble(7, capacityFactor);
            ps.setString(8, recommendedAction);
            ps.setString(9, urgencyLevel);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[WindDAO] SQL Error in insertMaintenanceTicket: " + e.getMessage());
            return false;
        }
    }

    /**
     * Phase 3: Records safety lockout event in downtime logs.
     */
    public boolean insertDowntimeLog(String turbineId, String incidentReason, String rootCauseMetric,
                                    double metricValue, double thresholdExceeded, String lockoutStatus) {
        if (!DBConnection.isDatabaseAvailable()) {
            simulatedDowntime.incrementAndGet();
            return true;
        }

        String sql = """
            INSERT INTO downtime_logs (
                turbine_id, incident_reason, root_cause_metric, metric_value,
                threshold_exceeded, lockout_status
            ) VALUES (?, ?, ?, ?, ?, ?);
        """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, turbineId);
            ps.setString(2, incidentReason);
            ps.setString(3, rootCauseMetric);
            ps.setDouble(4, metricValue);
            ps.setDouble(5, thresholdExceeded);
            ps.setString(6, lockoutStatus);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[WindDAO] SQL Error in insertDowntimeLog: " + e.getMessage());
            return false;
        }
    }

    public int getTicketCount() {
        if (!DBConnection.isDatabaseAvailable()) {
            return simulatedTickets.get();
        }
        String sql = "SELECT COUNT(*) FROM maintenance_tickets";
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            // fallback
        }
        return simulatedTickets.get();
    }

    public int getDowntimeCount() {
        if (!DBConnection.isDatabaseAvailable()) {
            return simulatedDowntime.get();
        }
        String sql = "SELECT COUNT(*) FROM downtime_logs";
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            // fallback
        }
        return simulatedDowntime.get();
    }
}
