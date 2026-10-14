# Changelog

All notable changes to the **AutoUSB** project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.3.2] - 2026-10-14

### Added
- feat(ci): automate CHANGELOG.md generation and GitHub release notes extraction
- feat(ci): add version bumping scripts for patch, minor, and major release automation

---

## [1.2.0] - 2026-10-14

### Added
- **Package Renaming**: Migrated package namespace to `com.cybersafetyid.mobile.autousb`.
- **USB OTG Auto-APK Installer**: Automatic background/manual scanner for connected USB drives to auto-install or update target APKs.
- **Embedded Terminal Engine**: Termux-style terminal emulator screen with quick action chips for executing shell commands and scripts.
- **Automated GitHub Release CI/CD**: Added GitHub Actions workflow (`release.yml`) for automated APK build and release note generation.
- **Open Source Assets**: Added professional `README.md`, `CONTRIBUTING.md`, `LICENSE` (MIT), `CODE_OF_CONDUCT.md`, and `CHANGELOG.md`.

### Changed
- Refactored foreground service notification handling and IP address detection across multiple network interfaces (`wlan0`, `eth0`, `usb0`, `rndis0`).
- Optimized `AppPreferences` storage with device-protected storage support for direct boot compatibility.

### Fixed
- Fixed notification intent flags compatibility for Android 12+ (API 31+).
- Improved root detection logic (`su`) with fallback execution modes.

---

## [1.1.0] - 2026-08-01

### Added
- Wireless ADB auto-configuration service on boot and app start.
- Device IP Toast notification system for instant `adb connect` command copy/display.
- Jetpack Compose Material 3 UI with Dark Terminal Green aesthetic theme.

---

## [1.0.0] - 2026-05-15

### Added
- Initial release of AutoUSB background ADB service.
- Basic Root/Non-Root shell executor.
