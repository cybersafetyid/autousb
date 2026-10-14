#!/usr/bin/env python3
import re
import sys
import datetime
import subprocess
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
BUILD_GRADLE_PATH = PROJECT_ROOT / "app" / "build.gradle.kts"
CHANGELOG_PATH = PROJECT_ROOT / "CHANGELOG.md"

def parse_semver(version_str):
    parts = version_str.strip().split(".")
    major = int(parts[0]) if len(parts) > 0 and parts[0].isdigit() else 1
    minor = int(parts[1]) if len(parts) > 1 and parts[1].isdigit() else 0
    patch = int(parts[2]) if len(parts) > 2 and parts[2].isdigit() else 0
    return major, minor, patch, len(parts)

def format_semver(major, minor, patch, original_parts_count):
    if original_parts_count == 2 and patch == 0:
        return f"{major}.{minor}"
    return f"{major}.{minor}.{patch}"

def get_commits_since_last_tag():
    try:
        last_tag = subprocess.check_output(
            ["git", "describe", "--tags", "--abbrev=0"],
            text=True, stderr=subprocess.DEVNULL, cwd=PROJECT_ROOT
        ).strip()
        log_range = f"{last_tag}..HEAD"
    except Exception:
        log_range = "HEAD"

    try:
        log_output = subprocess.check_output(
            ["git", "log", log_range, "--pretty=format:%s"],
            text=True, cwd=PROJECT_ROOT
        ).strip()
        if not log_output:
            return []
        return [line.strip() for line in log_output.split("\n") if line.strip()]
    except Exception:
        return []

def generate_changelog_section(new_version, commits):
    today = datetime.date.today().strftime("%Y-%m-%d")

    added = []
    changed = []
    fixed = []
    other = []

    for msg in commits:
        if msg.startswith("chore(release):") or msg.startswith("Merge branch"):
            continue

        lower_msg = msg.lower()
        if lower_msg.startswith("feat") or "add" in lower_msg or "new" in lower_msg:
            added.append(msg)
        elif lower_msg.startswith("fix") or "fix" in lower_msg or "bug" in lower_msg:
            fixed.append(msg)
        elif lower_msg.startswith("refactor") or lower_msg.startswith("style") or "update" in lower_msg or "change" in lower_msg:
            changed.append(msg)
        else:
            other.append(msg)

    lines = [f"## [{new_version}] - {today}\n"]

    if added:
        lines.append("### Added")
        for item in added:
            lines.append(f"- {item}")
        lines.append("")

    if changed:
        lines.append("### Changed")
        for item in changed:
            lines.append(f"- {item}")
        lines.append("")

    if fixed:
        lines.append("### Fixed")
        for item in fixed:
            lines.append(f"- {item}")
        lines.append("")

    if other:
        lines.append("### Maintenance")
        for item in other:
            lines.append(f"- {item}")
        lines.append("")

    if not (added or changed or fixed or other):
        lines.append("### Maintenance")
        lines.append(f"- Release version v{new_version}")
        lines.append("")

    return "\n".join(lines).strip()

def update_changelog_file(new_version, commits):
    if not CHANGELOG_PATH.exists():
        initial_content = "# Changelog\n\nAll notable changes to the AutoUSB project will be documented in this file.\n\n---\n\n"
        CHANGELOG_PATH.write_text(initial_content, encoding="utf-8")

    current_changelog = CHANGELOG_PATH.read_text(encoding="utf-8")
    new_section = generate_changelog_section(new_version, commits)

    # Prevent duplicating if version header already exists
    if f"## [{new_version}]" in current_changelog:
        print(f"Section for [{new_version}] already exists in CHANGELOG.md. Skipping duplication.")
        return

    # Insert section after introductory header
    marker = "---\n\n"
    if marker in current_changelog:
        parts = current_changelog.split(marker, 1)
        updated_changelog = f"{parts[0]}{marker}{new_section}\n\n---\n\n{parts[1]}"
    else:
        updated_changelog = f"# Changelog\n\n{new_section}\n\n---\n\n{current_changelog}"

    CHANGELOG_PATH.write_text(updated_changelog, encoding="utf-8")
    print(f"Successfully updated CHANGELOG.md with entry for [{new_version}]")

def bump_version(bump_type):
    if not BUILD_GRADLE_PATH.exists():
        print(f"Error: {BUILD_GRADLE_PATH} not found!")
        sys.exit(1)

    content = BUILD_GRADLE_PATH.read_text(encoding="utf-8")

    # Match versionCode
    vc_match = re.search(r'versionCode\s*=\s*(\d+)', content)
    if not vc_match:
        print("Error: Could not find versionCode in build.gradle.kts")
        sys.exit(1)

    current_vc = int(vc_match.group(1))
    new_vc = current_vc + 1

    # Match versionName
    vn_match = re.search(r'versionName\s*=\s*"([^"]+)"', content)
    if not vn_match:
        print("Error: Could not find versionName in build.gradle.kts")
        sys.exit(1)

    current_vn = vn_match.group(1)
    major, minor, patch, parts_count = parse_semver(current_vn)

    if bump_type == "major":
        major += 1
        minor = 0
        patch = 0
    elif bump_type == "minor":
        minor += 1
        patch = 0
    elif bump_type == "patch":
        if parts_count == 2:
            patch = 1
        else:
            patch += 1
    else:
        print(f"Unknown bump type '{bump_type}'. Use: patch, minor, or major")
        sys.exit(1)

    new_vn = format_semver(major, minor, patch, parts_count)

    print("======================================================")
    print(f" AutoUSB Version Bump ({bump_type.upper()})")
    print(f" versionCode: {current_vc} -> {new_vc}")
    print(f" versionName: {current_vn} -> {new_vn}")
    print("======================================================")

    # 1. Collect commits & Update CHANGELOG.md
    commits = get_commits_since_last_tag()
    update_changelog_file(new_vn, commits)

    # 2. Update app/build.gradle.kts
    new_content = re.sub(r'versionCode\s*=\s*\d+', f'versionCode = {new_vc}', content)
    new_content = re.sub(r'versionName\s*=\s*"[^"]+"', f'versionName = "{new_vn}"', new_content)

    BUILD_GRADLE_PATH.write_text(new_content, encoding="utf-8")
    print(f"Updated {BUILD_GRADLE_PATH}")

    # 3. Git operations
    tag_name = f"v{new_vn}"
    commit_msg = f"chore(release): bump version to {tag_name} (vc{new_vc})"

    try:
        subprocess.run(["git", "add", str(BUILD_GRADLE_PATH), str(CHANGELOG_PATH)], check=True, cwd=PROJECT_ROOT)
        subprocess.run(["git", "commit", "-m", commit_msg], check=True, cwd=PROJECT_ROOT)
        print(f"Git commit created: '{commit_msg}'")

        subprocess.run(["git", "tag", "-a", tag_name, "-m", f"Release {tag_name}"], check=True, cwd=PROJECT_ROOT)
        print(f"Git tag created: '{tag_name}'")

        # Push to remote
        print(f"Pushing commit and tag {tag_name} to origin...")
        subprocess.run(["git", "push", "origin", "master", "--tags"], check=True, cwd=PROJECT_ROOT)
        print(f"SUCCESS! Version {tag_name} bumped, CHANGELOG.md updated, committed, tagged, and pushed!")
    except subprocess.CalledProcessError as e:
        print(f"Git command failed: {e}")
        sys.exit(1)

if __name__ == "__main__":
    b_type = sys.argv[1].lower() if len(sys.argv) > 1 else "patch"
    bump_version(b_type)
