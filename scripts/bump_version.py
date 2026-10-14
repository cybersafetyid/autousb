#!/usr/bin/env python3
import re
import sys
import subprocess
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
BUILD_GRADLE_PATH = PROJECT_ROOT / "app" / "build.gradle.kts"

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

    # Replace in content
    new_content = re.sub(r'versionCode\s*=\s*\d+', f'versionCode = {new_vc}', content)
    new_content = re.sub(r'versionName\s*=\s*"[^"]+"', f'versionName = "{new_vn}"', new_content)

    BUILD_GRADLE_PATH.write_text(new_content, encoding="utf-8")
    print(f"Updated {BUILD_GRADLE_PATH}")

    # Git operations
    tag_name = f"v{new_vn}"
    commit_msg = f"chore(release): bump version to {tag_name} (vc{new_vc})"

    try:
        subprocess.run(["git", "add", str(BUILD_GRADLE_PATH)], check=True, cwd=PROJECT_ROOT)
        subprocess.run(["git", "commit", "-m", commit_msg], check=True, cwd=PROJECT_ROOT)
        print(f"Git commit created: '{commit_msg}'")

        subprocess.run(["git", "tag", "-a", tag_name, "-m", f"Release {tag_name}"], check=True, cwd=PROJECT_ROOT)
        print(f"Git tag created: '{tag_name}'")

        # Push to remote
        print(f"Pushing commit and tag {tag_name} to origin...")
        subprocess.run(["git", "push", "origin", "master", "--tags"], check=True, cwd=PROJECT_ROOT)
        print(f"SUCCESS! Version {tag_name} bumped, committed, tagged, and pushed!")
    except subprocess.CalledProcessError as e:
        print(f"Git command failed: {e}")
        sys.exit(1)

if __name__ == "__main__":
    b_type = sys.argv[1].lower() if len(sys.argv) > 1 else "patch"
    bump_version(b_type)
