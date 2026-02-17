@echo off
echo ==========================================
echo       SynergyGig Dependency Setup
echo ==========================================

if not exist lib mkdir lib

echo [INFO] Downloading MySQL Connector...
curl.exe -L -o lib/mysql-connector-j-8.0.33.jar https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.0.33/mysql-connector-j-8.0.33.jar

echo [INFO] Downloading JavaFX...
set JFX_VER=21.0.2
set URL_BASE=https://repo1.maven.org/maven2/org/openjfx

:: Controls
curl.exe -L -o lib/javafx-controls-%JFX_VER%.jar %URL_BASE%/javafx-controls/%JFX_VER%/javafx-controls-%JFX_VER%.jar
curl.exe -L -o lib/javafx-controls-%JFX_VER%-win.jar %URL_BASE%/javafx-controls/%JFX_VER%/javafx-controls-%JFX_VER%-win.jar

:: Graphics
curl.exe -L -o lib/javafx-graphics-%JFX_VER%.jar %URL_BASE%/javafx-graphics/%JFX_VER%/javafx-graphics-%JFX_VER%.jar
curl.exe -L -o lib/javafx-graphics-%JFX_VER%-win.jar %URL_BASE%/javafx-graphics/%JFX_VER%/javafx-graphics-%JFX_VER%-win.jar

:: Base
curl.exe -L -o lib/javafx-base-%JFX_VER%.jar %URL_BASE%/javafx-base/%JFX_VER%/javafx-base-%JFX_VER%.jar
curl.exe -L -o lib/javafx-base-%JFX_VER%-win.jar %URL_BASE%/javafx-base/%JFX_VER%/javafx-base-%JFX_VER%-win.jar

:: FXML
curl.exe -L -o lib/javafx-fxml-%JFX_VER%.jar %URL_BASE%/javafx-fxml/%JFX_VER%/javafx-fxml-%JFX_VER%.jar
curl.exe -L -o lib/javafx-fxml-%JFX_VER%-win.jar %URL_BASE%/javafx-fxml/%JFX_VER%/javafx-fxml-%JFX_VER%-win.jar

:: Media
curl.exe -L -o lib/javafx-media-%JFX_VER%.jar %URL_BASE%/javafx-media/%JFX_VER%/javafx-media-%JFX_VER%.jar
curl.exe -L -o lib/javafx-media-%JFX_VER%-win.jar %URL_BASE%/javafx-media/%JFX_VER%/javafx-media-%JFX_VER%-win.jar

echo [SUCCESS] Dependencies downloaded to /lib.
pause
