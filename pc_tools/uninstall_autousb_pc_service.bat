@echo off
title AutoUSB - Uninstall PC Background Service
color 0C
echo ======================================================================
echo   AutoUSB - Windows Background Service Uninstaller
echo ======================================================================
echo.

set "STARTUP_FOLDER=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup"
set "VBS_SCRIPT=%STARTUP_FOLDER%\autousb_background_runner.vbs"

if exist "%VBS_SCRIPT%" (
    del "%VBS_SCRIPT%"
    echo [OK] Removed AutoUSB script from Windows Startup folder.
) else (
    echo [INFO] AutoUSB background service was not found in Startup folder.
)

taskkill /f /im cmd.exe /fi "WINDOWTITLE eq AutoUSB - PC Automatic ADB Port Activator*" >nul 2>&1

echo.
echo AutoUSB PC Background Service has been uninstalled.
pause
