@echo off
echo ==========================================
echo       SynergyGig Database Setup
echo ==========================================

set MYSQL_PATH=C:\xampp\mysql\bin\mysql.exe
set SQL_FILE=c:\Users\Turtl\Desktop\pi\final_synergygig.sql

if not exist "%MYSQL_PATH%" (
    echo [ERROR] MySQL not found at %MYSQL_PATH%.
    echo Please ensure XAMPP is installed or update the script with your MySQL path.
    pause
    exit /b
)

if not exist "%SQL_FILE%" (
    echo [ERROR] SQL file not found at %SQL_FILE%.
    pause
    exit /b
)

echo [INFO] Importing database from %SQL_FILE%...
"%MYSQL_PATH%" -u root < "%SQL_FILE%"

if %errorlevel% equ 0 (
    echo [SUCCESS] Database imported successfully!
    echo [INFO] Seeding default admin user...
    "%MYSQL_PATH%" -u root -e "USE finale_synergygig; INSERT IGNORE INTO users (email, password, first_name, last_name, role) VALUES ('admin@synergygig.com', 'admin123', 'Super', 'Admin', 'ADMIN');"
) else (
    echo [ERROR] Failed to import database.
)

pause
