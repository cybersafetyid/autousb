@echo off
title AutoUSB - PC Automatic ADB Port Activator
color 0A
echo ======================================================================
echo   AutoUSB - PC Automatic ADB TCP/IP Port Activator
echo   Monitoring connected USB devices...
echo   Every time an Android phone is connected via USB, Port 5555 activates!
echo ======================================================================
echo.

:loop
for /f "tokens=1,2" %%a in ('adb devices ^| findstr /v "List"') do (
    if "%%b"=="device" (
        echo [AutoUSB] USB Device Detected: %%a
        echo [AutoUSB] Activating Wireless ADB TCP Port 5555 on %%a...
        adb -s %%a tcpip 5555
        echo [AutoUSB] Success! ADB Port 5555 activated on %%a
        echo ----------------------------------------------------------------------
    )
)
timeout /t 3 >nul
goto loop
