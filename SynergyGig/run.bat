@echo off
echo ==========================================
echo       SynergyGig Launcher
echo ==========================================

:: 1. Check for Java
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Java is not installed or not in PATH.
    echo Please install Java 17+ and try again.
    pause
    exit /b
)
echo [OK] Java found.

:: 2. Check for Maven
call mvn -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Maven (mvn) is not in your PATH.
    echo.
    echo To run this application, you have two options:
    echo 1. Open this folder in IntelliJ IDEA and run 'mains.MainFX'.
    echo 2. Install Apache Maven and add it to your PATH.
    echo.
    echo Since you have the source code, Option 1 is recommended.
    pause
    exit /b
)

:: 3. Run Application
echo [INFO] Building and running SynergyGig...
call mvn clean javafx:run

if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Application failed to start. Check the logs above.
    pause
)
