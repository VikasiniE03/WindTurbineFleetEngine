@echo off
REM ============================================================================
REM Build and Run Script for Wind Turbine Fleet Predictive Maintenance Engine
REM Compatible with Windows Command Prompt / PowerShell
REM ============================================================================

echo [STEP 1] Cleaning and creating build output directory...
if not exist "bin" mkdir "bin"

echo [STEP 2] Compiling Java source files with javac...
javac -d bin -cp "lib/*" src\*.java

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Compilation failed with error code %ERRORLEVEL%.
    exit /b %ERRORLEVEL%
)

echo [STEP 3] Compilation successful! Executing MainApp...
echo.
java -cp "bin;lib/*" MainApp

echo.
echo [DONE] Execution complete. Verify output files:
echo   - predictive_maintenance_tickets.txt
echo   - downtime_summary_report.txt
