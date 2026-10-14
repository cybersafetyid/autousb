<div align="center">

# 🔌 AutoUSB

**Automated Wireless ADB & USB OTG Companion Service for Android & Android TV**

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Android Min SDK](https://img.shields.io/badge/Min%20SDK-API%2021%20%28Android%205.0%29-blue.svg)](https://developer.android.com)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-API%2037%20%28Android%2015%2B%29-brightgreen.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg)](https://kotlinlang.org)
[![Build Status](https://github.com/cybersafetyid/autousb/actions/workflows/release.yml/badge.svg)](https://github.com/cybersafetyid/autousb/actions/workflows/release.yml)

---

**AutoUSB** is a powerful lightweight Android application designed for developers, system integrators, and digital signage/kiosk deployments. It automates **Wireless ADB over TCP/IP (Port 5555)** and **USB OTG APK Auto-Installation** seamlessly on system boot and USB drive insertion.

[Key Features](#-key-features) • [Installation](#-installation) • [How It Works](#-how-it-works) • [Release & CI/CD](#-automated-releases--tags) • [Contributing](#-contributing)

</div>

---

## ✨ Key Features

- ⚡ **Automated Wireless ADB**: Automatically enables `service.adb.tcp.port 5555` on device boot or application start via Root (`su`) or local shell engine.
- 💾 **USB OTG Auto-APK Installer**: Detects connected USB flash drives on insertion, scans for target `.apk` files, and automatically updates or installs them (via Root `pm install` or System Package Installer).
- 🌐 **Real-time Device IP Tracker & Toast**: Monitors active network interfaces (`wlan0`, `eth0`, `usb0`, `rndis0`) and displays a quick-copy `adb connect <IP>:5555` toast overlay.
- 💻 **Termux-style Embedded Console**: Built-in interactive shell terminal with quick-preset action chips (`Enable ADB 5555`, `Get ADB Port`, `Check IP`, `Check Root`).
- 🛡️ **Direct Boot & Persistent Foreground Service**: Keeps ADB and USB monitoring active reliably in the background with customizable foreground service notifications.
- 🎨 **Modern Jetpack Compose M3 UI**: Beautiful dark terminal green aesthetic designed with Material 3 components.

---

## 📸 Screen Overview

| Tab | Description |
| :--- | :--- |
| **Info / Control Center** | Overview of service status, ADB port, active IP addresses, root status, and quick script execution. |
| **Config / Automation** | Toggle auto-start on boot, script triggers, USB APK target matching, execution method, and toast settings. |
| **Terminal Console** | Interactive command console with line-by-line output logging and preset command chips. |
| **Logs History** | Detailed execution log repository tracking timestamped command outputs and exit codes. |

---

## 🛠️ How It Works

```
                        +----------------------------+
                        |     Device Boot / USB      |
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
                        |    (Foreground Service)    |
                        +--------------+-------------+
                                       |
                   +-------------------+-------------------+
                   |                                       |
                   v                                       v
     +---------------------------+           +---------------------------+
     |   Wireless ADB Trigger    |           |   USB OTG APK Installer   |
     | (setprop service.adb.tcp) |           |  (Scan & pm install -r)   |
     +-------------+-------------+           +-------------+-------------+
                   |                                       |
                   +-------------------+-------------------+
                                       |
                                       v
                        +----------------------------+
                        |  Network IP & Toast Notify |
                        | "adb connect <IP>:5555"    |
                        +----------------------------+
```

---

## 🚀 Installation & Requirements

### Download APK
Download the latest pre-built APK from the [**GitHub Releases**](https://github.com/cybersafetyid/autousb/releases) page.

### System Requirements
- **Android OS**: Android 5.0 (Lollipop / API 21) or higher.
- **Root Permission (`su`)**: *Recommended* for fully automated ADB TCP port binding without PC intervention. (Magisk / KernelSU / APatch supported).
- **Non-Root Support**: Functions as a status display, USB OTG installer, and manual shell runner.

---

## 🤖 GitHub Repository Details

If you are setting up or managing this repository on GitHub, configure the following metadata:

### 📝 Repository Description
> Automated Wireless ADB (TCP/IP 5555) & USB OTG Auto-APK Installer Service for Android & Android TV built with Jetpack Compose.

### 🏷️ Recommended GitHub Topics
```text
android, adb, wireless-adb, usb-otg, apk-installer, jetpack-compose, kotlin, android-tv, root, shell-executor, automation, android-service, cybersafetyid
```

---

## 🔄 Automated Releases & Tags

Releases are fully automated with GitHub Actions (`.github/workflows/release.yml`).

### How to Create a New Release
1. Tag your commit with Semantic Versioning:
   ```bash
   git tag -a v1.2.0 -m "Release version 1.2.0"
   git push origin v1.2.0
   ```
2. GitHub Actions will automatically:
   - Build the release APK (`AutoUSB-v1.2.0-vc3-release.apk`).
   - Create a GitHub Release entry for tag `v1.2.0`.
   - Generate auto-changelog notes from git commits.
   - Attach the compiled APK to the release.

---

## 🤝 Contributing

Contributions are warmly welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for details on code style, testing, and PR submission processes.

---

## 📜 License

This project is open-source software licensed under the **[MIT License](LICENSE)**.

---

<div align="center">

Crafted with ❤️ by **[@cybersafetyid](https://github.com/cybersafetyid)**

</div>
