import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * TicketWriter.java
 * High-reliability Thread-Safe File Writer using synchronization.
 * Exports:
 * 1. predictive_maintenance_tickets.txt: Formatted engineering work orders.
 * 2. downtime_summary_report.txt: Incident audit log for tripped / lockout turbines.
 */
public class TicketWriter {

    private final File ticketFile;
    private final File downtimeFile;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public TicketWriter(String baseDirectory) {
        this.ticketFile = new File(baseDirectory, "predictive_maintenance_tickets.txt");
        this.downtimeFile = new File(baseDirectory, "downtime_summary_report.txt");
    }

    public synchronized void initializeReports() {
        try {
            // Initialize maintenance tickets report
            try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(ticketFile, false)))) {
                pw.println("====================================================================================================");
                pw.println("                    RENEWABLE WIND TURBINE FLEET PREDICTIVE MAINTENANCE ENGINE                      ");
                pw.println("                               WORK ORDER & HEALTH DISPATCH REPORT                                 ");
                pw.println("====================================================================================================");
                pw.printf("Report Generated: %s | System Architecture: 2-Tier Fleet Cloud Engine%n", LocalDateTime.now().format(TIME_FMT));
                pw.println("Standard Protocols: ISO 10816-21 (Wind Vibration) | IEC 61400-25 (SCADA Telemetry Integration)");
                pw.println("====================================================================================================\n");
            }

            // Initialize downtime summary report
            try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(downtimeFile, false)))) {
                pw.println("====================================================================================================");
                pw.println("                    RENEWABLE WIND TURBINE FLEET SAFETY LOCKOUT & DOWNTIME AUDIT                    ");
                pw.println("                              OPERATIONAL TRIPPED TURBINE SUMMARY REPORT                            ");
                pw.println("====================================================================================================");
                pw.printf("Report Generated: %s | Safety Filter: Mechanical Brake Lock Auto-Trigger%n", LocalDateTime.now().format(TIME_FMT));
                pw.println("Lockout Conditions: Gearbox Temp > Cap OR Blade Vibration > 4.5 mm/s OR Health Index < 60");
                pw.println("====================================================================================================");
                pw.printf("%-18s | %-16s | %-24s | %-18s | %-12s | %-14s%n",
                    "TURBINE SERIAL ID", "FARM SECTOR", "OPERATIONAL CATEGORY", "ROOT CAUSE INCIDENT", "METRIC / CAP", "LOCKOUT STATUS");
                pw.println("----------------------------------------------------------------------------------------------------");
            }
        } catch (IOException e) {
            System.err.println("[TicketWriter] Error initializing output reports: " + e.getMessage());
        }
    }

    /**
     * Thread-safe synchronized append of a generated predictive maintenance ticket.
     */
    public synchronized void writeMaintenanceTicket(String ticketBlock) {
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(ticketFile, true)))) {
            pw.println(ticketBlock);
            pw.println(); // Blank line separator
        } catch (IOException e) {
            System.err.println("[TicketWriter] Error writing maintenance ticket: " + e.getMessage());
        }
    }

    /**
     * Thread-safe synchronized append of a downtime incident row.
     */
    public synchronized void writeDowntimeRow(String turbineId, String sector, String category,
                                             String rootCause, String metricInfo, String lockoutStatus) {
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(downtimeFile, true)))) {
            pw.printf("%-18s | %-16s | %-24s | %-18s | %-12s | %-14s%n",
                turbineId, sector, category, rootCause, metricInfo, lockoutStatus);
        } catch (IOException e) {
            System.err.println("[TicketWriter] Error writing downtime report row: " + e.getMessage());
        }
    }

    /**
     * Appends an executive summary trailer to the report files.
     */
    public synchronized void finalizeReports(int totalProcessed, int validRecords, int invalidRecords,
                                            int ticketsGenerated, int emergencyTrips, double avgHealthScore) {
        try {
            try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(ticketFile, true)))) {
                pw.println("====================================================================================================");
                pw.println("                                     EXECUTIVE DISPATCH SUMMARY                                     ");
                pw.println("====================================================================================================");
                pw.printf("Total SCADA Records Read : %d%n", totalProcessed);
                pw.printf("Valid Telemetry Analyzed : %d%n", validRecords);
                pw.printf("Corrupted / Invalid IDs  : %d%n", invalidRecords);
                pw.printf("Tickets Dispatched       : %d%n", ticketsGenerated);
                pw.printf("Emergency Lockouts       : %d%n", emergencyTrips);
                pw.printf("Fleet Avg Health Score   : %.2f / 100.00%n", avgHealthScore);
                pw.println("====================================================================================================");
                pw.println("                                      END OF WORK ORDER REPORT                                      ");
                pw.println("====================================================================================================");
            }

            try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(downtimeFile, true)))) {
                pw.println("----------------------------------------------------------------------------------------------------");
                pw.printf("TOTAL TRIPPED TURBINES FLAGGED FOR LOCKOUT: %d%n", emergencyTrips);
                pw.println("ACTION REQUIRED: Field technician dispatch required before mechanical brake release.");
                pw.println("====================================================================================================");
                pw.println("                                      END OF DOWNTIME AUDIT                                         ");
                pw.println("====================================================================================================");
            }
        } catch (IOException e) {
            System.err.println("[TicketWriter] Error finalizing reports: " + e.getMessage());
        }
    }

    public File getTicketFile() {
        return ticketFile;
    }

    public File getDowntimeFile() {
        return downtimeFile;
    }
}
