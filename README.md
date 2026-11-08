# Renewable Wind Turbine Fleet Predictive Maintenance Engine

## Project Information

- **Project Title:** Renewable Wind Turbine Fleet Predictive Maintenance Engine
- **Institution:** Kongu Engineering College
- **Department:** Information Technology
- **Course:** 24ITT32 – Programming in Java
- **Developed by:** TRISHNA A K, VIKASINI E
- **Roll Number:** 25ITR164, 25ITR172

## 1. Project Overview & System Architecture

The **Renewable Wind Turbine Fleet Predictive Maintenance Engine** is a high-throughput, concurrent telemetry processing and health analytics platform built to monitor distributed wind turbine assets across harsh environmental operating environments.

The system is deployed in a **2-Tier Cloud Architecture** (simulating VM 1 Application Host and VM 2 Database Host in Oracle Cloud Infrastructure):
- **VM 1 (Application Host):** Runs Java 17+ multithreaded processing engine with `ExecutorService` (Fixed Thread Pool), high-frequency CSV ingestion, health scoring algorithms, safety brake trip evaluation, and thread-safe file generation.
- **VM 2 (Database Host):** Hosts MySQL 8.0+ Server with the `wind_fleet_db` relational schema, storing master turbine asset registries, raw telemetry streams, predictive work orders, and safety lockout audit trails.

```
+-------------------------------------------------------------------------------------------------------+
|                                    ORACLE CLOUD INFRASTRUCTURE (OCI)                                  |
|                                                                                                       |
|  +-------------------------------------------+     +-----------------------------------------------+  |
|  | VM 1: APPLICATION WORKER HOST (10.0.1.15) |     | VM 2: DATABASE HOST (10.0.2.20)               |  |
|  | - Java 17+ Predictive Maintenance Engine  |     | - MySQL 8.0+ Enterprise Server                |  |
|  | - SCADA CSV Ingestion Stream              |====+=====> Database: wind_fleet_db                 |  |
|  | - ExecutorService (Fixed Thread Pool)     |    |   | - turbines (Master Registry)              |  |
|  | - PredictiveWorker (Formula Calculation)  |    |   | - scada_telemetry (Time-series)           |  |
|  | - TicketWriter (Synchronized File I/O)    |    |   | - maintenance_tickets (Work Orders)       |  |
|  |                                           |    |   | - downtime_logs (Safety Audit)            |  |
|  +-------------------------------------------+    |   +-----------------------------------------------+  |
|                                                   |                                                   |
|                        OCI Ingress Route: Port 3306 (TCP Only)                                        |
|                        Source Restricted to 10.0.1.0/24 (App Subnet Only)                              |
+-------------------------------------------------------------------------------------------------------+
```

---

## 2. Mathematical Models & Operational Categories

The platform calculates a real-time **Turbine Health Index (THI)** from 0.0 to 100.0 tailored to specific operational categories, their mechanical risk factors, and temperature caps:

### 2.1 Offshore Deepwater Turbine
- **Formula:**
  $$\text{THI} = 100 - \left[ \left(\frac{\text{Gearbox\_Temp}}{85} \times 40\right) + \left(\frac{\text{Blade\_Vibration}}{4.5} \times 40\right) + \left(\frac{\text{Yaw\_Misalignment}}{15} \times 20\right) \right]$$
- **Max Gearbox Temp Cap:** $85^\circ\text{C}$
- **Primary Stressors:** Severe hydrodynamic blade load, salt fog corrosion, and high cyclic yaw oscillation.

### 2.2 Onshore Desert Turbine
- **Formula:**
  $$\text{THI} = 100 - \left[ \left(\frac{\text{Sand\_Dust\_Wear}}{100} \times 40\right) + \left(\frac{\text{Gearbox\_Temp}}{90} \times 40\right) + \left(\frac{\text{Rotor\_Speed}}{50} \times 20\right) \right]$$
- **Max Gearbox Temp Cap:** $90^\circ\text{C}$
- **Primary Stressors:** High ambient heat, particulate abrasion erosion on leading blade edges, and sudden thermal expansion.

### 2.3 High-Altitude Mountain Turbine
- **Formula:**
  $$\text{THI} = 100 - \left[ \left(\frac{\text{Ice\_Accumulation}}{100} \times 50\right) + \left(\frac{\text{Blade\_Vibration}}{4.5} \times 30\right) + \left(\frac{\text{Gearbox\_Temp}}{80} \times 20\right) \right]$$
- **Max Gearbox Temp Cap:** $80^\circ\text{C}$
- **Primary Stressors:** Severe rime/glaze ice mass imbalance, low air density aerodynamic stall, and low operating temperature limits.

### 2.4 Wind Capacity Factor Formula
$$\text{CapacityFactor (\%)} = \left( \frac{\text{ActualEnergyProduced\_MWh}}{\text{MaxRatedCapacity\_MW} \times \text{HoursOperation}} \right) \times 100$$

---

## 3. Safety Filters & Health Bands

### 3.1 Mechanical Brake Safety Filter
The engine immediately triggers an emergency shutdown lockout (`MECHANICAL_BRAKE_LOCK = true`) whenever:
$$\text{Gearbox\_Temp} > \text{Max\_Gearbox\_Temp\_Cap} \quad \text{OR} \quad \text{Blade\_Vibration} > 4.5\text{ mm/s}$$

### 3.2 Health Band Classification Hierarchy
| Health Band Score | Classification Grade | Operational Status & Action |
| :--- | :--- | :--- |
| **90 – 100 THI** | **Peak Efficiency Grade A** | Full Operational Speed. Routine continuous SCADA monitoring. |
| **75 – 89 THI** | **Standard Grade B** | Normal Generation Schedule. Planned inspection in next low-wind window. |
| **60 – 74 THI** | **Maintenance Review Grade C** | Scheduled Inspection Required within 72 hours. Derate output by 15%. |
| **< 60 THI or Brake Lock** | **Tripped / Offline** | **Emergency Repair Lockout**. Mechanical brake engaged. Remote start blocked. |

---

## 4. Technical Architecture & Phased Requirements

### 4.1 String Processing Implementation
- **Regex Serial Validation:** Enforces format `^TURB-[A-Z]{2,4}-\d{4}$` (e.g. `TURB-OFF-1001`, `TURB-DES-2002`, `TURB-MNT-3003`). Rejects corrupt input rows.
- **SCADA Error Code Parsing:** Splits structured diagnostic codes using `.split(":")` into `[severity, description, error_code]`.
- **Farm Sector Sanitization:** Cleans leading/trailing sector whitespace using `.trim()`.
- **Work Order Formatting:** Formats tickets into structured engineering tables using `String.format()`.

### 4.2 High-Reliability File I/O
- **Input:** Ingests sensor streams from `scada_wind.csv`.
- **Work Order Output:** Exports work order tickets to `predictive_maintenance_tickets.txt`.
- **Incident Audit Output:** Exports tripped asset records to `downtime_summary_report.txt`.

### 4.3 Multithreading & Thread Safety
- **Worker Execution:** `PredictiveWorker.java` implements `Callable<TelemetryResult>` submitted to a fixed `ExecutorService` thread pool.
- **Thread-Safe Writing:** `TicketWriter.java` uses `synchronized` blocks to ensure concurrent threads write cleanly to text files without race conditions or character corruption.

---

## 5. Repository File Structure

```
WindTurbineFleetEngine/
├── src/
│   ├── DBConnection.java          # MySQL 8.0+ JDBC connection manager with fail-safe resilience
│   ├── WindDAO.java               # SQL CRUD logic for turbines, telemetry, tickets, and downtime
│   ├── PredictiveWorker.java      # Multi-threaded worker for formulas, regex, and safety locks
│   ├── TicketWriter.java          # Thread-safe synchronized file writer for output reports
│   ├── MainApp.java               # Execution entry point & thread pool dispatcher
│   └── FleetEngineTest.java       # Comprehensive 29-assertion automated unit test suite
├── lib/
│   └── mysql-connector-j-8.3.0.jar# Official MySQL Connector/J JDBC driver
├── scada_wind.csv                 # SCADA input telemetry covering all operational edge cases
├── wind_fleet_schema.sql          # Production MySQL DDL schema with foreign keys and constraints
├── OCI_Architecture_Report.md     # 2-Tier Cloud Solutions Architecture & Firewall Report
├── pom.xml                        # Maven build and dependency configuration
├── build_and_run.bat              # Windows batch compilation and execution script
├── build_and_run.ps1              # PowerShell compilation and execution script
└── README.md                      # Complete system documentation and engineering guide
```

---

## 6. Build, Test, and Execution Instructions

### 6.1 Prerequisites
- **JDK 17 or later** (`java -version`, `javac -version`).
- **MySQL 8.0+** (Optional: application automatically activates Resilient Simulation Mode if offline).

### 6.2 Option A: One-Click Command Prompt Execution
```cmd
cd WindTurbineFleetEngine
build_and_run.bat
```

### 6.3 Option B: Manual Command-Line Compilation & Execution
```bash
# 1. Create bin directory
mkdir bin

# 2. Compile Java sources
javac -d bin -cp "lib/*" src/*.java

# 3. Run the Predictive Maintenance Engine
java -cp "bin;lib/*" MainApp

# 4. Run the Automated Test Suite (All 29 Tests)
java -cp "bin;lib/*" FleetEngineTest
```


## 7. Sample Engine Output

```text
==========================================================================================
     RENEWABLE WIND TURBINE FLEET PREDICTIVE MAINTENANCE ENGINE (PROJECT SET 24)          
==========================================================================================
[INIT] Starting Fleet Engine on Java Runtime: 25.0.4.1
[INIT] Architecture: 2-Tier Setup (App Worker Host <--> MySQL 8.0+ Database Host)
[DATABASE] MySQL Host unavailable or offline. Engine continuing in Resilient Simulation Mode.
           (Telemetry processing, thread pool analysis, and .txt report generation active).
[I/O] Reading SCADA Sensor Telemetry from: D:\JAVA_TEAM_PROJECT\WindTurbineFleetEngine\scada_wind.csv
[THREADS] Dispatched 18 telemetry records across 4 parallel worker threads.
[AUDIT REJECT] Line 17: REJECTED_SERIAL_REGEX_FAIL: 'INVALID-TURBINE-999' does not match pattern ^TURB-[A-Z]{2,4}-\d{4}$
[AUDIT REJECT] Line 18: REJECTED_SERIAL_REGEX_FAIL: 'TURB-TOOLONGID-1000' does not match pattern ^TURB-[A-Z]{2,4}-\d{4}$
[AUDIT REJECT] Line 19: REJECTED_SERIAL_REGEX_FAIL: 'TURB-1004' does not match pattern ^TURB-[A-Z]{2,4}-\d{4}$

==========================================================================================
                 PREDICTIVE MAINTENANCE ENGINE : EXECUTIVE FLEET DASHBOARD                
==========================================================================================
| Total SCADA Records Processed   :   18 records                                           |
| Valid Telemetry Records         :   15 records (83.3%)                                  |
| Invalid / Corrupt Serial IDs    :    3 records (Regex Filtered)                          |
+----------------------------------------------------------------------------------------+
| OPERATIONAL CATEGORY BREAKDOWN:                                                        |
|   - Offshore Deepwater Turbines :    5 records                                           |
|   - Onshore Desert Turbines     :    5 records                                           |
|   - High-Altitude Turbines      :    5 records                                           |
+----------------------------------------------------------------------------------------+
| TURBINE HEALTH BAND CLASSIFICATION:                                                    |
|   - Peak Efficiency Grade A     :    3 (90 - 100 THI) - Full Operational Speed           |
|   - Standard Grade B            :    3 (75 -  89 THI) - Normal Generation Schedule       |
|   - Maintenance Review Grade C  :    3 (60 -  74 THI) - Scheduled Inspection Required    |
|   - Tripped / Offline Lockout   :    6 (<60 THI or Mechanical Brake Triggered)           |
+----------------------------------------------------------------------------------------+
| SAFETY & GENERATION METRICS:                                                           |
|   - Mechanical Brake Locks      :    5 SAFETY TRIPS                                      |
|   - Fleet Average Health Score  :  63.66 / 100.00                                        |
|   - Fleet Avg Capacity Factor   :  68.17 %                                              |
+----------------------------------------------------------------------------------------+
| GENERATED REPORT ARTIFACTS:                                                            |
|   * Tickets File  : predictive_maintenance_tickets.txt                               |
|   * Downtime File : downtime_summary_report.txt                                      |
==========================================================================================
[SUCCESS] Renewable Wind Turbine Fleet Predictive Maintenance Engine execution complete.
```

---

## BY

TRISHNA A K- 25ITR164
VIKASINI E - 25ITR172
