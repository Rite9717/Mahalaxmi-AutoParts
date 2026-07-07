@echo off
echo ========================================
echo Building Frontend and Backend
echo ========================================
echo.

echo [1/4] Building frontend...
cd /d "%~dp0front"
call npm run build
if %ERRORLEVEL% NEQ 0 (
    echo Frontend build failed!
    pause
    exit /b 1
)

echo.
echo [2/4] Copying frontend to backend...
xcopy /E /I /Y "%~dp0front\build\*" "%~dp0backend\src\main\resources\static\" >nul

echo.
echo [3/4] Building backend JAR...
cd /d "%~dp0backend"
call mvnw.cmd clean package -DskipTests
if %ERRORLEVEL% NEQ 0 (
    echo Backend build failed!
    pause
    exit /b 1
)

echo.
echo ========================================
echo Build Complete!
echo ========================================
echo Now restart the application:
echo 1. Run "Stop Mahalaxmi Auto Parts.cmd"
echo 2. Run "Start Mahalaxmi Auto Parts.cmd"
echo.
pause
