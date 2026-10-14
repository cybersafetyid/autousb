<div align="center">

# 🔌 AutoUSB

**Automated Wireless ADB & USB OTG Companion Service for Android, POS Kiosk & Android TV**

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Android Min SDK](https://img.shields.io/badge/Min%20SDK-API%2021%20%28Android%205.0%29-blue.svg)](https://developer.android.com)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-API%2037%20%28Android%2015%2B%29-brightgreen.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg)](https://kotlinlang.org)
[![Build Status](https://github.com/cybersafetyid/autousb/actions/workflows/release.yml/badge.svg)](https://github.com/cybersafetyid/autousb/actions/workflows/release.yml)

---

**AutoUSB** is an advanced lightweight Android application designed for developers, system integrators, POS terminals, and digital signage/kiosk deployments. It automates **Wireless ADB over TCP/IP (Port 5555)** and **USB OTG APK Auto-Installation** seamlessly on system boot, USB cable plug-in, and USB drive insertion.

[Key Features](#-key-features) • [Execution Architecture](#-multi-tier-execution-pipeline) • [PC Automation Tools](#-pc-auto-activator-tools-pc_tools) • [Logging & Tracing](#-real-time-logcat-tracing) • [Installation](#-installation--requirements)

</div>

---

## ✨ Key Features

- ⚡ **Automated Wireless ADB Activation**: Automatically enables `service.adb.tcp.port 5555` on device boot or USB plug-in via Root (`su 0`), **Shizuku (ADB Shell, No Root Required!)**, Embedded Wireless ADB Protocol, or USB OTG Host engine.
- 🛠️ **Multi-tier Execution Fallback Pipeline**:
  `Root (su 0)` ➔ `Shizuku (uid=2000)` ➔ `Local ADB Engine (127.0.0.1)` ➔ `USB OTG ADB Host Engine` ➔ `Lollipop Property Injector`.
- 🔌 **USB ADB Host Engine (`UsbAdbHostEngine`)**: Allows one Android device running AutoUSB to act as USB Host and activate Wireless ADB Port 5555 on another connected Android device over USB OTG cable.
- 📦 **Bundled Shizuku Installer**: Bundled official Shizuku release APK (`shizuku.apk`) in app assets with 1-tap extraction and installation.
- 💻 **PC Auto-Activator Suite (`pc_tools/`)**: Includes Windows 1-click Background Service installer (`install_autousb_pc_service.bat`) that silently activates Port 5555 on non-rooted phones whenever plugged into USB.
- 💾 **USB OTG Auto-APK Installer**: Detects connected USB flash drives on insertion, scans for target `.apk` files, and automatically updates or installs them.
- 🌐 **Real-time Device IP Tracker & Toast**: Monitors active network interfaces (`wlan0`, `eth0`, `usb0`, `rndis0`) and displays a quick-copy `adb connect <IP>:5555` toast overlay.
- 🔍 **Real-time Logcat Tracing**: All events, broadcasts, shell commands, and USB status are printed under unified logcat tag **`AutoUSB`** (`adb logcat -s AutoUSB`).
- 🎨 **Modern Jetpack Compose M3 UI**: Dark terminal green aesthetic designed with Material 3 components, fully compatible with Android 5.0 Lollipop (API 21) up to Android 15 (API 35+).

---

## 🏗️ Multi-tier Execution Pipeline

```
                     +----------------------------+
                     |   Device Boot / USB Plug   |
                     +--------------+-------------+
                                    |
                                    v
                     +----------------------------+
                     |        BootReceiver        |
                     +--------------+-------------+
                                    |
                                    v
                     +----------------------------+
                     |      AutoUsbService        |
                     +--------------+-------------+
                                    |
                                    v
                 +------------------------------------+
                 |    ShellExecutor Multi-Tier Engine |
                 +------------------+-----------------+
                                    |
         +--------------------------+--------------------------+
         |                          |                          |
         v                          v                          v
+------------------+       +------------------+       +------------------+
|    Root (su 0)   |       | Shizuku (uid=2000|       | Local ADB Socket |
|  Vendor POS/Kiosk|       | Wireless Debugging|      | (127.0.0.1:5555) |
+--------+---------+       +--------+---------+       +--------+---------+
         |                          |                          |
         +--------------------------+--------------------------+
                                    |
                                    v
                  +-----------------------------------+
                  |  USB ADB Host / Lollipop Injector |
                  |   (tcpip 5555 over USB OTG)       |
                  +-----------------+-----------------+
                                    |
                                    v
                     +----------------------------+
                     |  ADB Port 5555 Listening!  |
                     | "adb connect <IP>:5555"    |
                     +----------------------------+
```

---

## 💻 PC Auto-Activator Tools (`pc_tools/`)

For Non-Rooted Android devices (e.g. Z90, Android 5.1.1 Non-Root), the project includes automated PC background scripts:

| File | Description |
| :--- | :--- |
| **[`install_autousb_pc_service.bat`](file:///E:/StudioProjects/autousb/pc_tools/install_autousb_pc_service.bat)** | **1-Click Windows Installer**: Installs silent background runner in Windows Startup. Every time any phone is plugged into USB, PC automatically runs `adb tcpip 5555`! |
| **[`uninstall_autousb_pc_service.bat`](file:///E:/StudioProjects/autousb/pc_tools/uninstall_autousb_pc_service.bat)** | **1-Click Uninstaller**: Removes background service from Windows Startup. |
| **[`autousb_pc_activator.bat`](file:///E:/StudioProjects/autousb/pc_tools/autousb_pc_activator.bat)** | Windows batch loop script detecting USB insertion and executing `adb tcpip 5555`. |
| **[`autousb_pc_activator.sh`](file:///E:/StudioProjects/autousb/pc_tools/autousb_pc_activator.sh)** | Linux / macOS bash loop script for automatic USB ADB activation. |
| **[`autousb_usb_event_listener.ps1`](file:///E:/StudioProjects/autousb/pc_tools/autousb_usb_event_listener.ps1)** | PowerShell WMI event listener for instant hardware USB insertion events. |

---

## 🔍 Real-time Logcat Tracing

All internal operations, intent broadcasts, shell command outputs (`[STDOUT]` / `[STDERR]`), and IP resolutions are logged under tag **`AutoUSB`**.

### Read Logcat in Terminal
```bash
adb logcat -s AutoUSB
```
or
```bash
adb logcat -v time | grep AutoUSB
```

---

## 🚀 Installation & System Requirements

### Download APK
Download the latest pre-built APK from [**GitHub Releases**](https://github.com/cybersafetyid/autousb/releases).

### System Requirements
- **Android OS**: Min SDK API 21 (Android 5.0 Lollipop) to API 37 (Android 15+).
- **Tested Hardware Devices**:
  - **SZFP FP8800** (Android 7.1.2) - Factory Root (`su 0`)
  - **Z90** (Android 5.1.1) - Non-Rooted OS with USB OTG & PC Activator support

---

## 📜 License

This project is open-source software licensed under the **[MIT License](LICENSE)**.

<div align="center">

Crafted with ❤️ by **[@cybersafetyid](https://github.com/cybersafetyid)**

</div>
