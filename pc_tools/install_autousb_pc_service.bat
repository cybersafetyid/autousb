@echo off
title AutoUSB - Install PC Auto-Activator Background Service
color 0B
echo ======================================================================
echo   AutoUSB - Windows Background Service Installer
echo ======================================================================
echo.
echo Installing AutoUSB PC Auto-Activator into Windows Startup...

set "STARTUP_FOLDER=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup"
set "TARGET_BAT=%~dp0autousb_pc_activator.bat"
set "VBS_SCRIPT=%STARTUP_FOLDER%\autousb_background_runner.vbs"

echo Creating silent background runner script in Windows Startup...
(
  echo Set WshShell = CreateObject("WScript.Shell"^)`
  echo WshShell.Run """" ^& "%TARGET_BAT%" ^& """", 0, False
) > "%VBS_SCRIPT%"

echo.
echo ======================================================================
echo SUCCESS! AutoUSB PC Background Service installed into Windows Startup.
echo.
echo How it works:
echo  1. Every time Windows boots, AutoUSB runs silently in the background.
echo  2. Whenever Z90 or any Android phone is plugged into USB,
echo     PC automatically executes 'adb tcpip 5555' over USB!
echo ======================================================================
echo.
echo Starting AutoUSB PC Background Service now...
wscript "%VBS_SCRIPT%"
echo Service is now running silently in background!
pause
