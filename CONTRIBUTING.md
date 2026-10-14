# Contributing to AutoUSB

Thank you for your interest in contributing to **AutoUSB**! We welcome bug reports, feature requests, documentation improvements, and code contributions from the community.

---

## 🛠️ How to Contribute

### 1. Reporting Bugs & Issues
If you encounter a bug or unexpected behavior:
1. Search existing [GitHub Issues](https://github.com/cybersafetyid/autousb/issues) to ensure it hasn't been reported.
2. If not reported, create a new issue providing:
   - Device model, Android version, and Root status (`su` or non-root).
   - Steps to reproduce the bug.
   - Relevant logcat or AutoUSB Log tab output.

### 2. Suggesting Features
Enhancements and feature requests are welcome! Please open an issue titled `[Feature Request] <Feature Name>` and describe the problem it solves and how it should work.

### 3. Submitting Pull Requests (PRs)
1. **Fork** the repository: `https://github.com/cybersafetyid/autousb`
2. **Clone** your fork locally:
   ```bash
   git clone https://github.com/YOUR_USERNAME/autousb.git
   cd autousb
   ```
3. **Create a feature branch**:
   ```bash
   git checkout -b feature/amazing-feature
   ```
4. **Make your changes** adhering to Kotlin coding standards and Jetpack Compose best practices.
5. **Run local tests**:
   ```bash
   ./gradlew testDebugUnitTest
   ```
6. **Commit your changes** following conventional commits format (`feat: ...`, `fix: ...`, `docs: ...`):
   ```bash
   git commit -m "feat: add support for custom ADB pairing port"
   ```
7. **Push to your branch** and open a **Pull Request** against `master` or `main`.

---

## 🎨 Code Style Guidelines

- **Language**: Kotlin 1.9+
- **UI Framework**: Jetpack Compose with Material 3
- **Architecture**: Modern Android architecture (Data Flow, StateFlow, Foreground Service)
- **Formatting**: Use official Kotlin style guide rules.
- **Package Namespace**: `com.cybersafetyid.mobile.autousb`

---

## 🚀 Creating Releases & Tags

Releases are automated via GitHub Actions:
- Push a tag matching `v*` (e.g., `git tag -a v1.2.0 -m "Release v1.2.0" && git push origin v1.2.0`).
- The GitHub Actions workflow will automatically build `AutoUSB-v1.2.0.apk`, create the GitHub release, and generate release notes.

---

## 📄 License
By contributing to AutoUSB, you agree that your contributions will be licensed under the project's [MIT License](LICENSE).
