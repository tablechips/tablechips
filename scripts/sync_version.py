#!/usr/bin/env python3
"""Keeps the files F-Droid reads in step with version.txt.

version.txt is where the version lives, and release-please bumps it. Gradle
derives the version code from it, but F-Droid's update checker cannot: it
reads the version code out of a file as a literal number. And F-Droid shows
each version's changelog from fastlane/.../changelogs/<versionCode>.txt.

This writes both from version.txt and CHANGELOG.md. The release workflow runs
it on the release PR every time release-please updates that PR, and the
Gradle build refuses to run when version-code.txt has fallen behind.

    scripts/sync_version.py            write version-code.txt and the changelog
    scripts/sync_version.py --check    only say whether version-code.txt is right
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CHANGELOGS = ROOT / "fastlane/metadata/android/en-US/changelogs"
# F-Droid shows the first 500 characters; past that it is cut mid-sentence.
CHANGELOG_LIMIT = 500


def version_code(version: str) -> int:
    """The same formula as app/build.gradle.kts: MAJOR*10000 + MINOR*100 + PATCH."""
    major, minor, patch = (int(part) for part in version.split("."))
    if minor >= 100 or patch >= 100:
        sys.exit(f"version.txt: {version} does not fit the version code scheme")
    return max(1, major * 10000 + minor * 100 + patch)


def changelog_for(version: str) -> str:
    """This version's CHANGELOG.md section, as plain text for a store listing."""
    changelog = ROOT / "CHANGELOG.md"
    if not changelog.exists():
        return ""
    lines = changelog.read_text().splitlines()
    heading = re.compile(rf"^## \[?{re.escape(version)}\]?[ (]")
    start = next((i for i, line in enumerate(lines) if heading.match(line)), None)
    if start is None:
        return ""
    section = []
    for line in lines[start + 1:]:
        if line.startswith("## "):
            break
        # "### Bug Fixes" becomes "Bug fixes:", a bullet loses its commit link.
        line = re.sub(r"^### (.+)$", lambda m: m.group(1).capitalize() + ":", line)
        line = re.sub(r"\s*\(\[[0-9a-f]{7,}\]\([^)]*\)\)", "", line)
        line = re.sub(r"\*\*([^*]+):\*\* ", r"\1: ", line)
        line = re.sub(r"^\* ", "- ", line)
        section.append(line.rstrip())
    text = re.sub(r"\n{3,}", "\n\n", "\n".join(section)).strip()
    if len(text) > CHANGELOG_LIMIT:
        text = text[:CHANGELOG_LIMIT].rsplit("\n", 1)[0].rstrip() + "\n…"
    return text


def main() -> None:
    version = (ROOT / "version.txt").read_text().strip()
    code = version_code(version)
    code_file = ROOT / "version-code.txt"

    if "--check" in sys.argv:
        written = code_file.read_text().strip() if code_file.exists() else None
        if written != str(code):
            sys.exit(f"version-code.txt says {written}, version.txt {version} means {code}:"
                     " run scripts/sync_version.py")
        return

    code_file.write_text(f"{code}\n")
    changelog = changelog_for(version)
    if changelog:
        CHANGELOGS.mkdir(parents=True, exist_ok=True)
        (CHANGELOGS / f"{code}.txt").write_text(changelog + "\n")
    print(f"{version} -> {code}" + ("" if changelog else " (no changelog section found)"))


if __name__ == "__main__":
    main()
