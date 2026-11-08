# ============================================================================
# PowerShell Build & Execution Script
# Renewable Wind Turbine Fleet Predictive Maintenance Engine
# ============================================================================

Write-Host "===================================================================" -ForegroundColor Cyan
Write-Host "   WIND TURBINE FLEET PREDICTIVE MAINTENANCE ENGINE : BUILD & RUN  " -ForegroundColor Cyan
Write-Host "===================================================================" -ForegroundColor Cyan

# 1. Prepare bin directory
if (-not (Test-Path "bin")) {
    New-Item -ItemType Directory -Path "bin" | Out-Null
}

# 2. Compile
Write-Host "[1/3] Compiling Java source files..." -ForegroundColor Yellow
javac -d bin -cp "lib/*" src/*.java
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Java compilation failed!" -ForegroundColor Red
    exit $LASTEXITCODE
}
Write-Host "[2/3] Compilation completed successfully." -ForegroundColor Green

# 3. Execute
Write-Host "[3/3] Launching MainApp engine..." -ForegroundColor Yellow
java -cp "bin;lib/*" MainApp

Write-Host "`n[COMPLETED] Checking output report files..." -ForegroundColor Green
if (Test-Path "predictive_maintenance_tickets.txt") {
    $tktCount = (Get-Content "predictive_maintenance_tickets.txt" | Measure-Object -Line).Lines
    Write-Host " -> predictive_maintenance_tickets.txt generated ($tktCount lines)" -ForegroundColor Gray
}
if (Test-Path "downtime_summary_report.txt") {
    $dtCount = (Get-Content "downtime_summary_report.txt" | Measure-Object -Line).Lines
    Write-Host " -> downtime_summary_report.txt generated ($dtCount lines)" -ForegroundColor Gray
}
