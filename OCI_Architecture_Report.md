# Oracle Cloud Infrastructure (OCI) 2-Tier Architecture Report
## Renewable Wind Turbine Fleet Predictive Maintenance Engine
**Project Assignment Set 24 | Enterprise Cloud Solutions Architecture**

---

## 1. Executive Architecture Overview

The **Renewable Wind Turbine Fleet Predictive Maintenance Engine** is engineered as a decoupled, resilient, **2-Tier Enterprise Cloud Architecture** hosted on **Oracle Cloud Infrastructure (OCI)**. 

The architecture physically isolates high-throughput multi-threaded computing workloads from persistent, ACID-compliant relational storage, guaranteeing enterprise security, zero unauthorized external database exposure, and predictable I/O throughput.

```
+-------------------------------------------------------------------------------------------------------+
|                                    ORACLE CLOUD INFRASTRUCTURE (OCI)                                  |
|  REGION: us-ashburn-1 (or ap-hyderabad-1)                                                             |
|  VIRTUAL CLOUD NETWORK (VCN): vcn-wind-fleet (10.0.0.0/16)                                            |
|                                                                                                       |
|  +-------------------------------------------+     +-----------------------------------------------+  |
|  | PUBLIC / APP SUBNET (10.0.1.0/24)         |     | PRIVATE / DB SUBNET (10.0.2.0/24)             |  |
|  | Security List: sl-wind-app-subnet         |     | Security List: sl-wind-db-subnet (Isolated)   |  |
|  |                                           |     |                                               |  |
|  |  +-------------------------------------+  |     |  +-----------------------------------------+  |  |
|  |  | VM 1: App Worker Host               |  |     |  | VM 2: Database Host (MySQL 8.0+)        |  |  |
|  |  | Shape: VM.Standard.E4.Flex          |  |     |  | Shape: VM.Standard.E4.Flex              |  |  |
|  |  | OCPU: 2, RAM: 16 GB, OS: OL 9 / Win |  |     |  | OCPU: 4, RAM: 32 GB, Block: 200 GB UHP  |  |  |
|  |  | Private IP: 10.0.1.15               |  |     |  | Private IP: 10.0.2.20                   |  |  |
|  |  |                                     |  |     |  |                                         |  |  |
|  |  | - Java 17+ Predictive Engine        |  |     |  | - MySQL 8.0+ Enterprise Server          |  |  |
|  |  | - SCADA Telemetry Stream Reader     |====+=====>| - Database: wind_fleet_db               |  |  |
|  |  | - Multithreaded ExecutorService     |    |   | - Tables: turbines, scada_telemetry,   |  |  |
|  |  | - PredictiveWorker (Formulas/Brake) |    |   |           maintenance_tickets,         |  |  |
|  |  | - TicketWriter (Synchronized I/O)   |    |   |           downtime_logs                |  |  |
|  |  +-------------------------------------+  |    |   +-----------------------------------------+  |  |
|  +-------------------------------------------+    |   +-----------------------------------------------+  |
|                                                   |                                                   |
|                        OCI Ingress Route: Port 3306 (TCP Only)                                        |
|                        Source Restricted to 10.0.1.0/24 (App Subnet Only)                              |
+-------------------------------------------------------------------------------------------------------+
```

---

## 2. OCI Virtual Cloud Network (VCN) & Security Ingress Rules

### 2.1 Subnet Segmentation
| Subnet Name | Type | CIDR Block | Route Table | Security Purpose |
| :--- | :--- | :--- | :--- | :--- |
| **`app-subnet`** | Public / DMZ | `10.0.1.0/24` | Internet Gateway | Ingests SCADA edge streams; hosts VM 1 App engine. |
| **`db-subnet`** | Private | `10.0.2.0/24` | NAT Gateway (No IGW) | Zero public IP address; hosts VM 2 MySQL Database. |

### 2.2 Security List / Network Security Group (NSG) Ingress Configuration

To enable VM 1 (`10.0.1.15`) to communicate with VM 2 (`10.0.2.20`) exclusively over the MySQL protocol:

1. Navigate to **OCI Console** -> **Networking** -> **Virtual Cloud Networks** -> `vcn-wind-fleet`.
2. Select **Security Lists** -> `sl-wind-db-subnet` (or create a dedicated NSG `nsg-mysql-db`).
3. Add **Ingress Rule**:
   - **Stateless:** `No` (Stateful tracking enabled)
   - **Source Type:** `CIDR`
   - **Source CIDR:** `10.0.1.0/24` (Restricted strictly to the App Subnet)
   - **IP Protocol:** `TCP` (Protocol 6)
   - **Source Port Range:** `All`
   - **Destination Port Range:** `3306`
   - **Description:** `Permit App Host SCADA worker telemetry ingestion to MySQL DB`

4. Add **Egress Rule** on `sl-wind-app-subnet`:
   - **Destination CIDR:** `10.0.2.0/24`
   - **IP Protocol:** `TCP`
   - **Destination Port Range:** `3306`
   - **Description:** `Allow outbound database traffic to MySQL private subnet`

---

## 3. Host Operating System Firewall Setup

Even with OCI VCN security lists active, the OS-level firewall on VM 2 must permit TCP port 3306 from the specific IP or CIDR of VM 1.

### 3.1 Option A: Oracle Linux 8/9 / RHEL (Firewalld)

Execute on **VM 2 (Database Host)**:

```bash
# 1. Check current firewalld status
sudo firewall-cmd --state

# 2. Add rich rule permitting VM 1 App Host to access port 3306
sudo firewall-cmd --permanent --zone=public --add-rich-rule='rule family="ipv4" source address="10.0.1.0/24" port protocol="tcp" port="3306" accept'

# 3. Alternatively, add port to trusted zone with source binding
sudo firewall-cmd --permanent --zone=trusted --add-source=10.0.1.0/24

# 4. Reload firewall configuration to apply changes
sudo firewall-cmd --reload

# 5. Verify the active firewall rules
sudo firewall-cmd --zone=public --list-rich-rules
sudo firewall-cmd --list-all
```

### 3.2 Option B: Ubuntu / Debian (UFW)

Execute on **VM 2 (Database Host)**:

```bash
# 1. Allow incoming TCP on port 3306 exclusively from VM 1 / App Subnet
sudo ufw allow from 10.0.1.0/24 to any port 3306 proto tcp comment 'Allow Wind Fleet App Engine'

# 2. Enable or reload UFW
sudo ufw reload

# 3. Verify active firewall status
sudo ufw status verbose
```

### 3.3 Option C: Windows Server Host (PowerShell)

If VM 2 is running on Windows Server:

```powershell
New-NetFirewallRule -DisplayName "MySQL Server Port 3306 - Wind Fleet Engine" `
    -Direction Inbound `
    -Protocol TCP `
    -LocalPort 3306 `
    -RemoteAddress 10.0.1.0/24 `
    -Action Allow
```

---

## 4. MySQL 8.0+ Remote Binding & User Security Provisioning

By default, MySQL restricts socket listening to `127.0.0.1` (localhost only). To allow remote connections from VM 1, configure remote binding and provision dedicated service credentials.

### 4.1 Update `mysqld.cnf` Binding
Edit the MySQL configuration file:
- On Linux: `/etc/mysql/mysql.conf.d/mysqld.cnf` or `/etc/my.cnf.d/mysql-server.cnf`
- On Windows: `C:\ProgramData\MySQL\MySQL Server 8.0\my.ini`

Set `bind-address` to bind all network interfaces or specifically VM 2's private IP:

```ini
[mysqld]
# Bind to all private interfaces on VM 2
bind-address            = 0.0.0.0
mysqlx-bind-address     = 0.0.0.0

# Performance and connection settings for multi-threaded ingestion
max_connections         = 200
connect_timeout         = 10
wait_timeout            = 28800
interactive_timeout     = 28800
innodb_buffer_pool_size = 8G
innodb_log_file_size    = 1G
```

Restart MySQL service:
```bash
# Linux
sudo systemctl restart mysql
# or
sudo systemctl restart mysqld

# Windows
net stop MySQL80 && net start MySQL80
```

### 4.2 Provision Dedicated Least-Privilege Database User

Connect to MySQL locally on VM 2 as root:
```bash
sudo mysql -u root -p
```

Execute SQL to create the database and remote service user:

```sql
-- 1. Create database
CREATE DATABASE IF NOT EXISTS wind_fleet_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- 2. Create service user restricted to VM 1 Subnet (10.0.1.%)
CREATE USER 'wind_admin'@'10.0.1.%' IDENTIFIED BY 'WindFleetSecure_2026!';

-- 3. Grant strict DDL & DML permissions on wind_fleet_db
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, INDEX, ALTER, REFERENCES 
    ON wind_fleet_db.* TO 'wind_admin'@'10.0.1.%';

-- 4. Enforce TLS 1.3 encryption
ALTER USER 'wind_admin'@'10.0.1.%' REQUIRE SSL;

-- 5. Apply privileges
FLUSH PRIVILEGES;
```

---

## 5. End-to-End Connectivity Verification

### 5.1 Network Layer Connectivity Test (from VM 1)
```bash
# Test TCP Port Reachability
nc -zv 10.0.2.20 3306
# Or using telnet
telnet 10.0.2.20 3306
```

### 5.2 Application Layer JDBC Verification
Run the Java Engine with VM 2 environment flags:

```bash
# On VM 1 (Linux):
export DB_HOST="10.0.2.20"
export DB_PORT="3306"
export DB_NAME="wind_fleet_db"
export DB_USER="wind_admin"
export DB_PASSWORD="WindFleetSecure_2026!"

java -cp "bin:lib/*" MainApp
```

```powershell
# On VM 1 (Windows PowerShell):
$env:DB_HOST="10.0.2.20"
$env:DB_PORT="3306"
$env:DB_NAME="wind_fleet_db"
$env:DB_USER="wind_admin"
$env:DB_PASSWORD="WindFleetSecure_2026!"

java -cp "bin;lib/*" MainApp
```

---

## 6. Architecture Resiliency & Fallback Matrix

| Component | Normal 2-Tier State | Failure Mode | Engine Resilience Behavior |
| :--- | :--- | :--- | :--- |
| **MySQL Database (VM 2)** | Connected via Port 3306 | Network disconnect / DB down | **Resilient Simulation Mode**: In-memory telemetry caching, thread-safe local file logging, zero pipeline crash. |
| **VCN Security List** | Allows 10.0.1.0/24:3306 | Misconfigured ingress rule | Driver reports connection timeout (3s limit); gracefully falls back to local disk persistence. |
| **Predictive Worker Pool** | 4 Worker threads | Heavy telemetry load | Worker pool dynamically bounds concurrent tasks via `ExecutorService` without socket exhaustion. |
| **Safety Filter** | Continuous evaluation | Critical overheat / vibration | Auto-triggers `MECHANICAL_BRAKE_LOCK = true` and writes to `downtime_summary_report.txt` in real time. |
