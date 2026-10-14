#!/bin/bash
echo "======================================================================"
echo "  AutoUSB - PC Automatic ADB TCP/IP Port Activator"
echo "  Monitoring connected USB devices..."
echo "  Every time an Android phone is connected via USB, Port 5555 activates!"
echo "======================================================================"
echo ""

while true; do
    devices=$(adb devices | grep -w "device" | awk '{print $1}')
    for device in $devices; do
        if [ -n "$device" ]; then
            echo "[AutoUSB] USB Device Detected: $device"
            echo "[AutoUSB] Activating Wireless ADB TCP Port 5555 on $device..."
            adb -s "$device" tcpip 5555
            echo "[AutoUSB] Success! ADB Port 5555 activated on $device"
            echo "----------------------------------------------------------------------"
        fi
    done
    sleep 3
done
