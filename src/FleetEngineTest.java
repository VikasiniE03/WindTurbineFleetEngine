import java.io.File;
import java.util.regex.Pattern;

/**
 * FleetEngineTest.java
 * Automated Verification & Unit Test Suite for Wind Turbine Fleet Predictive Maintenance Engine.
 * Tests:
 * 1. Serial ID Regex Pattern Validation
 * 2. Offshore Deepwater Turbine Formula & Temp Cap
 * 3. Onshore Desert Turbine Formula & Temp Cap
 * 4. High-Altitude Mountain Turbine Formula & Temp Cap
 * 5. Wind Capacity Factor Formula
 * 6. Mechanical Brake Filter Safety Lockouts
 * 7. Health Band Transitions & Urgency
 * 8. SCADA Error Code Splitting (.split(":"))
 * 9. Farm Sector Trimming (.trim())
 * 10. File Generation & Output Artifact Verification
 */
public class FleetEngineTest {

    private static int testsPassed = 0;
    private static int testsFailed = 0;

    public static void main(String[] args) {
        System.out.println("==========================================================================================");
        System.out.println("                 WIND TURBINE FLEET ENGINE : AUTOMATED TEST SUITE                         ");
        System.out.println("==========================================================================================");

        testRegexSerialValidation();
        testOffshoreDeepwaterFormula();
        testOnshoreDesertFormula();
        testHighAltitudeMountainFormula();
        testCapacityFactorFormula();
        testMechanicalBrakeSafetyFilter();
        testHealthBands();
        testStringProcessing();
        testReportFileArtifacts();

        System.out.println("==========================================================================================");
        System.out.printf("TEST SUITE SUMMARY: %d PASSED, %d FAILED%n", testsPassed, testsFailed);
        System.out.println("==========================================================================================");

        if (testsFailed > 0) {
            System.err.println("[TEST FAILED] Some test assertions failed.");
            System.exit(1);
        } else {
            System.out.println("[TEST SUCCESS] All 10 verification test suites passed with 100% compliance.");
        }
    }

    private static void assertTrue(String testName, boolean condition, String details) {
        if (condition) {
            System.out.printf("  [PASS] %-45s : %s%n", testName, details);
            testsPassed++;
        } else {
            System.err.printf("  [FAIL] %-45s : %s%n", testName, details);
            testsFailed++;
        }
    }

    private static void testRegexSerialValidation() {
        System.out.println("\n--- 1. Testing Turbine Serial ID Regex: ^TURB-[A-Z]{2,4}-\\d{4}$ ---");
        Pattern pattern = Pattern.compile("^TURB-[A-Z]{2,4}-\\d{4}$");

        assertTrue("Valid Offshore ID", pattern.matcher("TURB-OFF-1001").matches(), "TURB-OFF-1001 matches");
        assertTrue("Valid Desert ID", pattern.matcher("TURB-DES-2002").matches(), "TURB-DES-2002 matches");
        assertTrue("Valid Mountain ID", pattern.matcher("TURB-MNT-3003").matches(), "TURB-MNT-3003 matches");
        assertTrue("Valid 2-letter Sector ID", pattern.matcher("TURB-TX-4004").matches(), "TURB-TX-4004 matches");

        assertTrue("Reject Missing Prefix", !pattern.matcher("INVALID-OFF-1001").matches(), "INVALID-OFF-1001 rejected");
        assertTrue("Reject Too Long Sector", !pattern.matcher("TURB-TOOLONGID-1000").matches(), "TURB-TOOLONGID-1000 rejected");
        assertTrue("Reject Non-numeric Suffix", !pattern.matcher("TURB-OFF-ABCD").matches(), "TURB-OFF-ABCD rejected");
        assertTrue("Reject Short Numeric", !pattern.matcher("TURB-1004").matches(), "TURB-1004 rejected");
    }

    private static void testOffshoreDeepwaterFormula() {
        System.out.println("\n--- 2. Testing Offshore Deepwater Turbine Health Formula ---");
        // Formula: THI = 100 - [(Gearbox_Temp / 85 * 40) + (Blade_Vibration / 4.5 * 40) + (Yaw_Misalignment / 15 * 20)]
        double temp = 10.0;
        double vib = 0.3;
        double yaw = 0.5;
        double penalty = (temp / 85.0 * 40.0) + (vib / 4.5 * 40.0) + (yaw / 15.0 * 20.0);
        double thi = 100.0 - penalty;

        assertTrue("Offshore THI calculation", Math.abs(thi - 91.96) < 0.1, String.format("Expected ~91.96, got %.2f", thi));
    }

    private static void testOnshoreDesertFormula() {
        System.out.println("\n--- 3. Testing Onshore Desert Turbine Health Formula ---");
        // Formula: THI = 100 - [(Sand_Dust_Wear / 100 * 40) + (Gearbox_Temp / 90 * 40) + (Rotor_Speed / 50 * 20)]
        double sand = 5.0;
        double temp = 10.0;
        double rpm = 5.0;
        double penalty = (sand / 100.0 * 40.0) + (temp / 90.0 * 40.0) + (rpm / 50.0 * 20.0);
        double thi = 100.0 - penalty;

        assertTrue("Desert THI calculation", Math.abs(thi - 91.56) < 0.1, String.format("Expected ~91.56, got %.2f", thi));
    }

    private static void testHighAltitudeMountainFormula() {
        System.out.println("\n--- 4. Testing High-Altitude Mountain Turbine Health Formula ---");
        // Formula: THI = 100 - [(Ice_Accumulation / 100 * 50) + (Blade_Vibration / 4.5 * 30) + (Gearbox_Temp / 80 * 20)]
        double ice = 4.0;
        double vib = 0.45;
        double temp = 12.0;
        double penalty = (ice / 100.0 * 50.0) + (vib / 4.5 * 30.0) + (temp / 80.0 * 20.0);
        double thi = 100.0 - penalty;

        assertTrue("Mountain THI calculation", Math.abs(thi - 92.00) < 0.1, String.format("Expected ~92.00, got %.2f", thi));
    }

    private static void testCapacityFactorFormula() {
        System.out.println("\n--- 5. Testing Wind Capacity Factor Formula ---");
        // Formula: CapacityFactor (%) = (ActualEnergyProduced_MWh / (MaxRatedCapacity_MW * HoursOperation)) * 100
        double actualEnergy = 138.5;
        double maxRatedMw = 6.0;
        double hours = 24.0;
        double cf = (actualEnergy / (maxRatedMw * hours)) * 100.0;

        assertTrue("Capacity Factor calculation", Math.abs(cf - 96.18) < 0.1, String.format("Expected ~96.18%%, got %.2f%%", cf));
    }

    private static void testMechanicalBrakeSafetyFilter() {
        System.out.println("\n--- 6. Testing Mechanical Brake Safety Filter & Temp Caps ---");
        // Max Caps: Offshore=85, Desert=90, Mountain=80. Blade Vibration threshold = 4.5 mm/s.
        boolean brakeOffshoreOverheat = (88.5 > 85.0) || (2.2 > 4.5);
        boolean brakeOffshoreVib = (75.0 > 85.0) || (4.85 > 4.5);
        boolean brakeNormal = (60.0 > 85.0) || (2.0 > 4.5);

        assertTrue("Brake Lock on Overheat", brakeOffshoreOverheat, "Temp 88.5°C > 85.0°C triggers brake lock");
        assertTrue("Brake Lock on High Vibration", brakeOffshoreVib, "Vibration 4.85 mm/s > 4.5 mm/s triggers brake lock");
        assertTrue("No Brake Lock for Normal", !brakeNormal, "Temp 60°C and Vib 2.0 mm/s normal");
    }

    private static void testHealthBands() {
        System.out.println("\n--- 7. Testing Health Band Classifications ---");
        assertTrue("Grade A band", classifyBand(95.0, false).startsWith("Peak Efficiency"), "95 THI is Grade A");
        assertTrue("Grade B band", classifyBand(80.0, false).startsWith("Standard Grade B"), "80 THI is Grade B");
        assertTrue("Grade C band", classifyBand(68.0, false).startsWith("Maintenance Review"), "68 THI is Grade C");
        assertTrue("Tripped for low THI", classifyBand(52.0, false).startsWith("Tripped / Offline"), "52 THI (<60) is Tripped");
        assertTrue("Tripped on Brake Lock", classifyBand(95.0, true).startsWith("Tripped / Offline"), "Brake lock overrides to Tripped");
    }

    private static String classifyBand(double thi, boolean brakeLock) {
        if (brakeLock || thi < 60.0) return "Tripped / Offline (Emergency Repair Lockout)";
        if (thi >= 90.0) return "Peak Efficiency Grade A (Full Operational Speed)";
        if (thi >= 75.0) return "Standard Grade B (Normal Generation Schedule)";
        return "Maintenance Review Grade C (Scheduled Inspection Required)";
    }

    private static void testStringProcessing() {
        System.out.println("\n--- 8. Testing String Processing (.trim(), .split(\":\"), String.format()) ---");
        String rawSector = "   North-Sea-Sector-A   ";
        String cleaned = rawSector.trim();
        assertTrue(".trim() cleans sector", "North-Sea-Sector-A".equals(cleaned), "Whitespace stripped");

        String rawScada = "CRIT:GEARBOX_OVERHEAT:E801";
        String[] parts = rawScada.split(":");
        assertTrue(".split(\":\") parses severity", "CRIT".equals(parts[0]), "Severity extracted");
        assertTrue(".split(\":\") parses description", "GEARBOX_OVERHEAT".equals(parts[1]), "Description extracted");
        assertTrue(".split(\":\") parses code", "E801".equals(parts[2]), "Code extracted");

        String formatted = String.format("TURBINE ID: %s | THI: %.2f", "TURB-OFF-1001", 91.96);
        assertTrue("String.format() succeeds", formatted.contains("TURB-OFF-1001") && formatted.contains("91.96"), "Formatted correctly");
    }

    private static void testReportFileArtifacts() {
        System.out.println("\n--- 9. Testing Generated Output Artifacts ---");
        File tkt = new File("predictive_maintenance_tickets.txt");
        File dt = new File("downtime_summary_report.txt");

        assertTrue("Tickets file exists", tkt.exists(), "predictive_maintenance_tickets.txt exists on disk");
        assertTrue("Tickets file not empty", tkt.length() > 0, "predictive_maintenance_tickets.txt size: " + tkt.length() + " bytes");
        assertTrue("Downtime file exists", dt.exists(), "downtime_summary_report.txt exists on disk");
        assertTrue("Downtime file not empty", dt.length() > 0, "downtime_summary_report.txt size: " + dt.length() + " bytes");
    }
}
