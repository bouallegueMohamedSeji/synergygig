@echo off
echo ==========================================
echo       SynergyGig Manual Build ^& Run
echo ==========================================

if not exist lib\javafx-controls-21.0.2.jar (
    echo [ERROR] Dependencies missing. Please run setup_libs.bat first.
    pause
    exit /b
)

if exist bin rmdir /s /q bin
mkdir bin

echo [INFO] Compiling Java sources...
dir /s /B src\main\java\*.java > sources.txt
javac -d bin -cp "lib/*" @sources.txt
del sources.txt

if %errorlevel% neq 0 (
    echo [ERROR] Compilation failed.
    pause
    exit /b
)

echo [INFO] Copying resources...
xcopy /s /y /q src\main\resources\* bin\ >nul

echo [INFO] Launching Application...
java -cp "bin;lib/*" mains.MainFX

pause
