@echo off
setlocal
echo ====================================================
echo  RenEspresso POS - Build Standalone EXE & Installer
echo ====================================================

if "%JAVA_HOME%"=="" set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"

echo [1/4] Building JAR and copying runtime dependencies...
call .\mvnw.cmd package dependency:copy-dependencies -DskipTests -DoutputDirectory=target\package-input
if %errorlevel% neq 0 (
    echo [ERROR] Maven build failed!
    exit /b %errorlevel%
)
copy /y target\RenEspresso-1.0-SNAPSHOT.jar target\package-input\ >nul

echo [2/4] Packaging standalone app with jpackage...
if exist target\dist\RenEspresso rmdir /s /q target\dist\RenEspresso
"%JAVA_HOME%\bin\jpackage.exe" ^
    --type app-image ^
    --name "RenEspresso" ^
    --input "target\package-input" ^
    --main-jar "RenEspresso-1.0-SNAPSHOT.jar" ^
    --main-class "com.coffeepos.renespresso.Launcher" ^
    --icon "package\windows\RenEspresso.ico" ^
    --dest "target\dist" ^
    --vendor "RenEspresso" ^
    --app-version "1.0.0"
if %errorlevel% neq 0 (
    echo [ERROR] jpackage failed!
    exit /b %errorlevel%
)

echo [3/4] Copying sample configuration...
copy /y src\main\resources\db.properties.sample target\dist\RenEspresso\db.properties.sample >nul

echo [4/4] Compiling Windows Setup Installer with Inno Setup...
set "ISCC=C:\Users\allan\AppData\Local\Programs\Inno Setup 6\ISCC.exe"
if exist "%ISCC%" (
    "%ISCC%" "package\windows\RenEspresso.iss"
    if %errorlevel% neq 0 (
        echo [WARNING] Inno Setup compilation encountered an issue.
    ) else (
        echo [SUCCESS] Installer created at target\dist\installer\RenEspresso_v1.0_Setup.exe
    )
) else (
    echo [NOTE] Inno Setup not found at default location. Standalone app ready in target\dist\RenEspresso\
)

echo.
echo ====================================================
echo  Build Complete!
echo  - Standalone EXE: target\dist\RenEspresso\RenEspresso.exe
echo  - Setup Installer: target\dist\installer\RenEspresso_v1.0_Setup.exe
echo ====================================================
