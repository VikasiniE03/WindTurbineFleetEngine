import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * MainApp.java
 * Execution Entry Point for Renewable Wind Turbine Fleet Predictive Maintenance Engine.
 * 
 * Pipeline:
 * 1. Verifies 2-Tier Architecture connectivity (VM 1 App Host -> VM 2 DB Host simulation).
 * 2. Initializes thread-safe TicketWriter for work orders and downtime summary reports.
 * 3. Uses an ExecutorService (Fixed Thread Pool) to process SCADA telemetry in parallel.
 * 4. Submits PredictiveWorker tasks handling category formulas, brake locks, regex validation,
 *    SCADA error parsing, and output formatting.
 * 5. Awaits thread pool completion and outputs executive KPI dashboard to stdout.
 */
public class MainApp {

    private static final int THREAD_POOL_SIZE = 4;
    private static final String DEFAULT_CSV_FILE = "scada_wind.csv";

    public static void main(String[] args) {
        System.out.println("==========================================================================================");
        System.out.println("     RENEWABLE WIND TURBINE FLEET PREDICTIVE MAINTENANCE ENGINE (PROJECT SET 24)          ");
        System.out.println("==========================================================================================");
        System.out.println("[INIT] Starting Fleet Engine on Java Runtime: " + System.getProperty("java.version"));
        System.out.println("[INIT] Architecture: 2-Tier Setup (App Worker Host <--> MySQL 8.0+ Database Host)");

        // 1. Check Database Connectivity
        boolean dbConnected = DBConnection.testConnection();
        if (dbConnected) {
            System.out.println("[DATABASE] Connected successfully to MySQL Host at: " + DBConnection.getJdbcUrl());
        } else {
            System.out.println("[DATABASE] MySQL Host unavailable or offline. Engine continuing in Resilient Simulation Mode.");
            System.out.println("           (Telemetry processing, thread pool analysis, and .txt report generation active).");
        }

        // Determine base working directory
        String baseDir = System.getProperty("user.dir");
        File csvFile = new File(baseDir, DEFAULT_CSV_FILE);
        if (!csvFile.exists()) {
            // Check parent or child directory fallback
            File parentCsv = new File(baseDir, "WindTurbineFleetEngine/" + DEFAULT_CSV_FILE);
            if (parentCsv.exists()) {
                csvFile = parentCsv;
                baseDir = csvFile.getParent();
            } else {
                File subCsv = new File(baseDir, "../" + DEFAULT_CSV_FILE);
                if (subCsv.exists()) {
                    csvFile = subCsv;
                }
            }
        }

        System.out.println("[I/O] Reading SCADA Sensor Telemetry from: " + csvFile.getAbsolutePath());

        // 2. Initialize TicketWriter
        TicketWriter ticketWriter = new TicketWriter(baseDir);
        ticketWriter.initializeReports();

        // 3. Initialize WindDAO
        WindDAO windDAO = new WindDAO();

        // 4. Multithreading: ExecutorService with Fixed Thread Pool
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        List<Future<PredictiveWorker.TelemetryResult>> futures = new ArrayList<>();

        int totalLinesRead = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(csvFile))) {
            String line = reader.readLine(); // Header line
            int lineNum = 1;

            while ((line = reader.readLine()) != null) {
                lineNum++;
                totalLinesRead++;
                PredictiveWorker worker = new PredictiveWorker(line, lineNum, ticketWriter, windDAO);
                futures.add(executor.submit(worker));
            }
        } catch (IOException e) {
            System.err.println("[FATAL] Could not read input CSV: " + e.getMessage());
            executor.shutdown();
            return;
        }

        System.out.printf("[THREADS] Dispatched %d telemetry records across %d parallel worker threads.%n",
            futures.size(), THREAD_POOL_SIZE);

        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                System.err.println("[WARN] Thread pool did not terminate within 60s timeout, forcing shutdown.");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            System.err.println("[WARN] Processing interrupted: " + e.getMessage());
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // 5. Aggregate Results
        int validCount = 0;
        int invalidCount = 0;
        int gradeACount = 0;
        int gradeBCount = 0;
        int gradeCCount = 0;
        int trippedCount = 0;
        int brakeLockCount = 0;
        double sumHealthScore = 0.0;
        double sumCapacityFactor = 0.0;

        int offshoreCount = 0;
        int desertCount = 0;
        int mountainCount = 0;

        for (Future<PredictiveWorker.TelemetryResult> future : futures) {
            try {
                PredictiveWorker.TelemetryResult result = future.get();
                if (result.valid) {
                    validCount++;
                    sumHealthScore += result.healthScore;
                    sumCapacityFactor += result.capacityFactor;

                    if (result.category.contains("OFFSHORE")) offshoreCount++;
                    else if (result.category.contains("DESERT")) desertCount++;
                    else if (result.category.contains("MOUNTAIN")) mountainCount++;

                    if (result.brakeLockTriggered) brakeLockCount++;

                    if (result.trippedOffline) {
                        trippedCount++;
                    } else if (result.healthScore >= 90.0) {
                        gradeACount++;
                    } else if (result.healthScore >= 75.0) {
                        gradeBCount++;
                    } else {
                        gradeCCount++;
                    }
                } else {
                    invalidCount++;
                    System.out.println("[AUDIT REJECT] " + result.errorMessage);
                }
            } catch (InterruptedException | ExecutionException e) {
                invalidCount++;
                System.err.println("[ERROR] Worker future execution exception: " + e.getMessage());
            }
        }

        double avgHealthScore = validCount > 0 ? (sumHealthScore / validCount) : 0.0;
        double avgCapacityFactor = validCount > 0 ? (sumCapacityFactor / validCount) : 0.0;

        // Finalize Report Files
        ticketWriter.finalizeReports(totalLinesRead, validCount, invalidCount, validCount, trippedCount, avgHealthScore);

        // 6. Print Executive Summary Dashboard
        System.out.println("\n==========================================================================================");
        System.out.println("                 PREDICTIVE MAINTENANCE ENGINE : EXECUTIVE FLEET DASHBOARD                ");
        System.out.println("==========================================================================================");
        System.out.printf("| Total SCADA Records Processed   : %4d records                                           |%n", totalLinesRead);
        System.out.printf("| Valid Telemetry Records         : %4d records (%.1f%%)                                  |%n",
            validCount, totalLinesRead > 0 ? (validCount * 100.0 / totalLinesRead) : 0);
        System.out.printf("| Invalid / Corrupt Serial IDs    : %4d records (Regex Filtered)                          |%n", invalidCount);
        System.out.println("+----------------------------------------------------------------------------------------+");
        System.out.println("| OPERATIONAL CATEGORY BREAKDOWN:                                                        |");
        System.out.printf("|   - Offshore Deepwater Turbines : %4d records                                           |%n", offshoreCount);
        System.out.printf("|   - Onshore Desert Turbines     : %4d records                                           |%n", desertCount);
        System.out.printf("|   - High-Altitude Turbines      : %4d records                                           |%n", mountainCount);
        System.out.println("+----------------------------------------------------------------------------------------+");
        System.out.println("| TURBINE HEALTH BAND CLASSIFICATION:                                                    |");
        System.out.printf("|   - Peak Efficiency Grade A     : %4d (90 - 100 THI) - Full Operational Speed           |%n", gradeACount);
        System.out.printf("|   - Standard Grade B            : %4d (75 -  89 THI) - Normal Generation Schedule       |%n", gradeBCount);
        System.out.printf("|   - Maintenance Review Grade C  : %4d (60 -  74 THI) - Scheduled Inspection Required    |%n", gradeCCount);
        System.out.printf("|   - Tripped / Offline Lockout   : %4d (<60 THI or Mechanical Brake Triggered)           |%n", trippedCount);
        System.out.println("+----------------------------------------------------------------------------------------+");
        System.out.println("| SAFETY & GENERATION METRICS:                                                           |");
        System.out.printf("|   - Mechanical Brake Locks      : %4d SAFETY TRIPS                                      |%n", brakeLockCount);
        System.out.printf("|   - Fleet Average Health Score  : %6.2f / 100.00                                        |%n", avgHealthScore);
        System.out.printf("|   - Fleet Avg Capacity Factor   : %6.2f %%                                              |%n", avgCapacityFactor);
        System.out.println("+----------------------------------------------------------------------------------------+");
        System.out.println("| GENERATED REPORT ARTIFACTS:                                                            |");
        System.out.printf("|   * Tickets File  : %-64s |%n", ticketWriter.getTicketFile().getName());
        System.out.printf("|   * Downtime File : %-64s |%n", ticketWriter.getDowntimeFile().getName());
        System.out.println("==========================================================================================");
        System.out.println("[SUCCESS] Renewable Wind Turbine Fleet Predictive Maintenance Engine execution complete.\n");
    }
}
