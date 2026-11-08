import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PredictiveWorker.java
 * Multi-threaded telemetry processing unit for SCADA wind turbine records.
 * Implements Callable to return structured TelemetryResult metrics.
 * 
 * Responsibilities:
 * 1. Regex validation of Turbine Serial ID: ^TURB-[A-Z]{2,4}-\\d{4}$
 * 2. String processing (.trim() for sector, .split(":") for SCADA codes, String.format() for tickets)
 * 3. Exact Category-specific Health Score (THI) calculation
 * 4. Wind Capacity Factor computation
 * 5. Mechanical Brake Filter & Health Band Classification
 * 6. Thread-safe persistence to TicketWriter & MySQL Database via WindDAO
 */
public class PredictiveWorker implements Callable<PredictiveWorker.TelemetryResult> {

    // Validation Regex: ^TURB-[A-Z]{2,4}-\d{4}$
    private static final Pattern SERIAL_PATTERN = Pattern.compile("^TURB-[A-Z]{2,4}-\\d{4}$");
    private static final DateTimeFormatter TKT_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String rawCsvLine;
    private final int lineNumber;
    private final TicketWriter ticketWriter;
    private final WindDAO windDAO;

    public PredictiveWorker(String rawCsvLine, int lineNumber, TicketWriter ticketWriter, WindDAO windDAO) {
        this.rawCsvLine = rawCsvLine;
        this.lineNumber = lineNumber;
        this.ticketWriter = ticketWriter;
        this.windDAO = windDAO;
    }

    @Override
    public TelemetryResult call() {
        if (rawCsvLine == null || rawCsvLine.trim().isEmpty()) {
            return TelemetryResult.invalid(lineNumber, "Empty telemetry line");
        }

        // CSV parsing: Turbine_Serial_ID,Farm_Sector,Category,Max_Rated_Capacity_MW,
        // Operating_Hours,Actual_Energy_Produced_MWh,Gearbox_Temp_C,Blade_Vibration_mms,
        // Yaw_Misalignment_deg,Sand_Dust_Wear_pct,Ice_Accumulation_pct,Rotor_Speed_rpm,SCADA_Error_Code,Timestamp
        String[] tokens = rawCsvLine.split(",");
        if (tokens.length < 14) {
            return TelemetryResult.invalid(lineNumber, "Malformed CSV row, expected 14 columns but found " + tokens.length);
        }

        String rawSerialId = tokens[0].trim();
        String rawSector = tokens[1].trim(); // String processing: clean farm sector tag with .trim()
        String category = tokens[2].trim().toUpperCase();

        // 1. Validate Turbine Serial ID regex: ^TURB-[A-Z]{2,4}-\d{4}$
        Matcher matcher = SERIAL_PATTERN.matcher(rawSerialId);
        if (!matcher.matches()) {
            String rejectMsg = String.format("REJECTED_SERIAL_REGEX_FAIL: '%s' does not match pattern ^TURB-[A-Z]{2,4}-\\d{4}$", rawSerialId);
            ticketWriter.writeDowntimeRow(rawSerialId, rawSector, category, "CORRUPT_SERIAL_ID", "REGEX_MISMATCH", "REJECTED_INPUT");
            return TelemetryResult.invalid(lineNumber, rejectMsg);
        }

        try {
            double maxRatedCapacityMw = Double.parseDouble(tokens[3].trim());
            double operatingHours = Double.parseDouble(tokens[4].trim());
            double actualEnergyProducedMwh = Double.parseDouble(tokens[5].trim());
            double gearboxTemp = Double.parseDouble(tokens[6].trim());
            double bladeVibration = Double.parseDouble(tokens[7].trim());
            double yawMisalignment = Double.parseDouble(tokens[8].trim());
            double sandDustWear = Double.parseDouble(tokens[9].trim());
            double iceAccumulation = Double.parseDouble(tokens[10].trim());
            double rotorSpeed = Double.parseDouble(tokens[11].trim());
            String rawErrorCode = tokens[12].trim();
            String timestampStr = tokens[13].trim();

            // 2. Parse SCADA error codes with .split(":")
            String errorSeverity = "INFO";
            String errorDescription = "NOMINAL_OPERATION";
            String errorCode = "000";
            if (!rawErrorCode.isEmpty()) {
                String[] errorParts = rawErrorCode.split(":");
                if (errorParts.length >= 1) errorSeverity = errorParts[0].trim();
                if (errorParts.length >= 2) errorDescription = errorParts[1].trim();
                if (errorParts.length >= 3) errorCode = errorParts[2].trim();
            }

            // 3. Operational Category Formulas & Calculations
            double thi = 100.0;
            double maxGearboxTempCap = 85.0; // Default

            switch (category) {
                case "OFFSHORE DEEPWATER TURBINE":
                    maxGearboxTempCap = 85.0;
                    // THI = 100 - [(Gearbox_Temp / 85 * 40) + (Blade_Vibration / 4.5 * 40) + (Yaw_Misalignment / 15 * 20)]
                    double penaltyOffshore = (gearboxTemp / 85.0 * 40.0)
                                           + (bladeVibration / 4.5 * 40.0)
                                           + (yawMisalignment / 15.0 * 20.0);
                    thi = 100.0 - penaltyOffshore;
                    break;

                case "ONSHORE DESERT TURBINE":
                    maxGearboxTempCap = 90.0;
                    // THI = 100 - [(Sand_Dust_Wear / 100 * 40) + (Gearbox_Temp / 90 * 40) + (Rotor_Speed / 50 * 20)]
                    double penaltyDesert = (sandDustWear / 100.0 * 40.0)
                                         + (gearboxTemp / 90.0 * 40.0)
                                         + (rotorSpeed / 50.0 * 20.0);
                    thi = 100.0 - penaltyDesert;
                    break;

                case "HIGH-ALTITUDE MOUNTAIN TURBINE":
                    maxGearboxTempCap = 80.0;
                    // THI = 100 - [(Ice_Accumulation / 100 * 50) + (Blade_Vibration / 4.5 * 30) + (Gearbox_Temp / 80 * 20)]
                    double penaltyMountain = (iceAccumulation / 100.0 * 50.0)
                                           + (bladeVibration / 4.5 * 30.0)
                                           + (gearboxTemp / 80.0 * 20.0);
                    thi = 100.0 - penaltyMountain;
                    break;

                default:
                    return TelemetryResult.invalid(lineNumber, "Unknown turbine operational category: " + category);
            }

            // Clamp THI to a sensible lower bound for display (e.g. 0 if severely degraded)
            double calculatedThi = Math.max(0.0, Math.min(100.0, thi));

            // Wind Capacity Factor Formula:
            // CapacityFactor (%) = (ActualEnergyProduced_MWh / (MaxRatedCapacity_MW * HoursOperation)) * 100
            double capacityFactor = 0.0;
            if (maxRatedCapacityMw > 0 && operatingHours > 0) {
                capacityFactor = (actualEnergyProducedMwh / (maxRatedCapacityMw * operatingHours)) * 100.0;
            }

            // 4. Rules & Safety Filters
            // Mechanical Brake Filter: Trigger MECHANICAL_BRAKE_LOCK = true if Gearbox Temp > Max Temp Cap OR Blade Vibration > 4.5 mm/s.
            boolean mechanicalBrakeLock = (gearboxTemp > maxGearboxTempCap) || (bladeVibration > 4.5);

            // Turbine Health Bands:
            // - 90–100: Peak Efficiency Grade A (Full Operational Speed)
            // - 75–89: Standard Grade B (Normal Generation Schedule)
            // - 60–74: Maintenance Review Grade C (Scheduled Inspection Required)
            // - <60 or Brake Lock Flagged: Tripped / Offline (Emergency Repair Lockout)
            String healthBand;
            String urgency;
            String recommendedAction;
            boolean trippedOffline = false;
            String rootCauseReason = "NOMINAL";

            if (mechanicalBrakeLock || calculatedThi < 60.0) {
                trippedOffline = true;
                healthBand = "Tripped / Offline (Emergency Repair Lockout)";
                urgency = "CRITICAL / EMERGENCY";

                if (gearboxTemp > maxGearboxTempCap && bladeVibration > 4.5) {
                    rootCauseReason = String.format("DUAL_LIMIT_EXCEEDED (Temp: %.1f°C > %.1f°C, Vib: %.2f > 4.5 mm/s)",
                        gearboxTemp, maxGearboxTempCap, bladeVibration);
                } else if (gearboxTemp > maxGearboxTempCap) {
                    rootCauseReason = String.format("GEARBOX_OVERHEAT (Temp: %.1f°C > %.1f°C Cap)",
                        gearboxTemp, maxGearboxTempCap);
                } else if (bladeVibration > 4.5) {
                    rootCauseReason = String.format("EXCESSIVE_BLADE_VIBRATION (Vib: %.2f mm/s > 4.5 mm/s)",
                        bladeVibration);
                } else {
                    rootCauseReason = String.format("THI_DEGRADATION (Index: %.2f < 60.0 Threshold)",
                        calculatedThi);
                }

                recommendedAction = String.format(
                    "EMERGENCY BRAKE LOCK ACTIVATED. Asset tripped offline. SCADA Error: [%s] %s. Disallow remote restart. Dispatch field technician for on-site physical root cause inspection.",
                    errorCode, errorDescription
                );

                // Write to downtime summary report
                String metricDetail = String.format("T:%.1f/V:%.2f", gearboxTemp, bladeVibration);
                ticketWriter.writeDowntimeRow(rawSerialId, rawSector, category, rootCauseReason, metricDetail, "BRAKE_LOCKED");

                // Log to database downtime_logs
                windDAO.insertDowntimeLog(rawSerialId, rootCauseReason, "GEARBOX_TEMP/VIB", gearboxTemp, maxGearboxTempCap, "TRIPPED_OFFLINE");

            } else if (calculatedThi >= 90.0) {
                healthBand = "Peak Efficiency Grade A (Full Operational Speed)";
                urgency = "LOW / ROUTINE";
                recommendedAction = "Turbine operating at peak performance. Retain full generation schedule. Routine continuous monitoring enabled.";
            } else if (calculatedThi >= 75.0) {
                healthBand = "Standard Grade B (Normal Generation Schedule)";
                urgency = "MEDIUM / PLANNED";
                recommendedAction = "Telemetry within acceptable operational envelopes. Minor environmental wear observed. Schedule standard inspection during next low-wind window.";
            } else {
                healthBand = "Maintenance Review Grade C (Scheduled Inspection Required)";
                urgency = "HIGH / PRIORITY";
                recommendedAction = "Elevated mechanical stress detected. Derate turbine output by 15%. Issue dispatch order for mechanical inspection within 72 hours.";
            }

            // Generate Ticket ID
            String ticketId = String.format("TKT-%s-%d", rawSerialId, System.currentTimeMillis() % 100000);

            // Format Work Order Ticket using String.format()
            String formattedTicket = formatWorkOrderTicket(
                ticketId, rawSerialId, rawSector, category, calculatedThi, healthBand,
                mechanicalBrakeLock, capacityFactor, maxRatedCapacityMw, actualEnergyProducedMwh,
                operatingHours, gearboxTemp, maxGearboxTempCap, bladeVibration, yawMisalignment,
                sandDustWear, iceAccumulation, rotorSpeed, errorSeverity, errorDescription,
                errorCode, urgency, recommendedAction, timestampStr
            );

            // Thread-safe write to tickets report
            ticketWriter.writeMaintenanceTicket(formattedTicket);

            // Persist to Database (Phases 1, 2, 3)
            windDAO.upsertTurbine(rawSerialId, rawSector, category, maxRatedCapacityMw, maxGearboxTempCap);
            windDAO.insertTelemetry(rawSerialId, timestampStr, gearboxTemp, bladeVibration, yawMisalignment,
                                    sandDustWear, iceAccumulation, rotorSpeed, actualEnergyProducedMwh,
                                    operatingHours, rawErrorCode);
            windDAO.insertMaintenanceTicket(ticketId, rawSerialId, category, calculatedThi, healthBand,
                                           mechanicalBrakeLock, capacityFactor, recommendedAction, urgency);

            return new TelemetryResult(true, rawSerialId, category, calculatedThi, capacityFactor,
                                       healthBand, mechanicalBrakeLock, trippedOffline, null);

        } catch (NumberFormatException nfe) {
            return TelemetryResult.invalid(lineNumber, "Number format error in telemetry row: " + nfe.getMessage());
        } catch (Exception e) {
            return TelemetryResult.invalid(lineNumber, "Processing error: " + e.getMessage());
        }
    }

    /**
     * Formats work order ticket in clean tabular layout using String.format()
     */
    private String formatWorkOrderTicket(
            String ticketId, String serialId, String sector, String category, double thi,
            String healthBand, boolean brakeLock, double capacityFactor, double maxMw, double actualMwh,
            double hours, double temp, double tempCap, double vib, double yaw, double sand,
            double ice, double rpm, String errorSev, String errorDesc, String errCode,
            String urgency, String action, String timestampStr) {

        StringBuilder sb = new StringBuilder();
        sb.append("+--------------------------------------------------------------------------------------------------+\n");
        sb.append(String.format("| WORK ORDER DISPATCH TICKET : %-67s |\n", ticketId));
        sb.append("+--------------------------------------------------------------------------------------------------+\n");
        sb.append(String.format("| Asset Serial ID  : %-32s | Farm Sector       : %-28s |\n", serialId, sector));
        sb.append(String.format("| Category         : %-32s | Timestamp         : %-28s |\n", category, timestampStr));
        sb.append(String.format("| Health Score THI : %6.2f / 100.00                   | Health Band       : %-28s |\n", thi, healthBand));
        sb.append(String.format("| Capacity Factor  : %6.2f %% (%.1f MW / %.1f MWh)      | Brake Lock Active : %-28s |\n",
            capacityFactor, maxMw, actualMwh, brakeLock ? "TRUE [MECHANICAL_LOCKOUT]" : "FALSE [NORMAL]"));
        sb.append(String.format("| Dispatch Urgency : %-32s | SCADA Error Code  : [%s] %-18s |\n", urgency, errCode, errorSev + ":" + errorDesc));
        sb.append("+--------------------------------------------------------------------------------------------------+\n");
        sb.append(String.format("| SENSOR TELEMETRY AUDIT:                                                                          |\n"));
        sb.append(String.format("|   * Gearbox Temp     : %6.2f °C (Max Cap: %.1f °C)                                               |\n", temp, tempCap));
        sb.append(String.format("|   * Blade Vibration  : %6.2f mm/s (Safety Threshold: 4.50 mm/s)                                  |\n", vib));
        sb.append(String.format("|   * Rotor Speed      : %6.2f RPM                                                                 |\n", rpm));
        if (category.contains("OFFSHORE")) {
            sb.append(String.format("|   * Yaw Misalignment : %6.2f deg                                                                 |\n", yaw));
        } else if (category.contains("DESERT")) {
            sb.append(String.format("|   * Sand / Dust Wear : %6.2f %%                                                                   |\n", sand));
        } else if (category.contains("MOUNTAIN")) {
            sb.append(String.format("|   * Ice Accumulation : %6.2f %%                                                                   |\n", ice));
        }
        sb.append("+--------------------------------------------------------------------------------------------------+\n");
        sb.append(String.format("| RECOMMENDED ENGINEERING ACTION:                                                                  |\n"));
        sb.append(String.format("|   %-94s |\n", action.length() > 94 ? action.substring(0, 94) : action));
        if (action.length() > 94) {
            String remainder = action.substring(94);
            sb.append(String.format("|   %-94s |\n", remainder.length() > 94 ? remainder.substring(0, 94) : remainder));
        }
        sb.append("+--------------------------------------------------------------------------------------------------+");
        return sb.toString();
    }

    /**
     * DTO containing result metrics for batch aggregation.
     */
    public static class TelemetryResult {
        public final boolean valid;
        public final String turbineId;
        public final String category;
        public final double healthScore;
        public final double capacityFactor;
        public final String healthBand;
        public final boolean brakeLockTriggered;
        public final boolean trippedOffline;
        public final String errorMessage;

        public TelemetryResult(boolean valid, String turbineId, String category, double healthScore,
                               double capacityFactor, String healthBand, boolean brakeLockTriggered,
                               boolean trippedOffline, String errorMessage) {
            this.valid = valid;
            this.turbineId = turbineId;
            this.category = category;
            this.healthScore = healthScore;
            this.capacityFactor = capacityFactor;
            this.healthBand = healthBand;
            this.brakeLockTriggered = brakeLockTriggered;
            this.trippedOffline = trippedOffline;
            this.errorMessage = errorMessage;
        }

        public static TelemetryResult invalid(int line, String error) {
            return new TelemetryResult(false, "N/A", "N/A", 0.0, 0.0, "INVALID", false, false, "Line " + line + ": " + error);
        }
    }
}
