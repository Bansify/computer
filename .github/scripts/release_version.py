#!/usr/bin/env python3
"""Allocate version codes from v1.0.N tags without replacing old releases."""
import os
import re
import subprocess
import sys

PATTERN = re.compile(r"v1\.0\.([1-9][0-9]*)\Z")
LIMIT = 2_100_000_000


def choose_version(tags, releases, event, ref, name, code):
    versions = {}
    for tag in set(tags) | set(releases):
        match = PATTERN.fullmatch(tag)
        if not match:
            if tag.startswith("v"):
                raise ValueError(f"Unsupported version tag {tag}; review version code history before releasing")
            continue
        number = int(match.group(1))
        if number > LIMIT:
            raise ValueError(f"Version code outside Android limit: {tag}")
        versions[tag] = number

    tag_event = event == "push" and ref.startswith("refs/tags/")
    current_tag = ref.removeprefix("refs/tags/") if tag_event else None
    if current_tag:
        match = PATTERN.fullmatch(current_tag)
        if not match or current_tag not in tags:
            raise ValueError("Only existing v1.0.N tags are supported")
        if current_tag in releases:
            raise ValueError(f"Release {current_tag} already exists; refusing to overwrite")
        number = int(match.group(1))
        if name or code:
            raise ValueError("Do not supply version inputs for tag releases")
    else:
        if name and not re.fullmatch(r"1\.0\.[1-9][0-9]*", name):
            raise ValueError("Version name must have format 1.0.N")
        if code and not re.fullmatch(r"[1-9][0-9]*", code):
            raise ValueError("Version code must be a positive integer")
        if name and code and int(name.removeprefix("1.0.")) != int(code):
            raise ValueError("Version name suffix and version code must match")
        number = int(code) if code else int(name.removeprefix("1.0.")) if name else max(versions.values(), default=20) + 1
        current_tag = f"v1.0.{number}"
        if current_tag in versions:
            raise ValueError(f"Tag or release {current_tag} already exists; refusing to overwrite")

    previous = max((n for tag, n in versions.items() if tag != current_tag), default=20)
    if not 20 < number <= LIMIT or number <= previous:
        raise ValueError(f"Version code must exceed {previous} and be within the Android limit")
    return number, f"1.0.{number}"


def main():
    tags = subprocess.check_output(["git", "tag", "--list"], text=True).splitlines()
    repo = os.environ["GITHUB_REPOSITORY"]
    releases = subprocess.check_output(
        ["gh", "api", "--paginate", f"repos/{repo}/releases", "--jq", ".[].tag_name"], text=True
    ).splitlines()
    code, name = choose_version(tags, releases, os.environ["GITHUB_EVENT_NAME"], os.environ["GITHUB_REF"],
                                os.environ.get("INPUT_VERSION_NAME", ""), os.environ.get("INPUT_VERSION_CODE", ""))
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
        output.write(f"v_code={code}\nv_name={name}\n")
    print(f"Selected {name} (versionCode {code})")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, subprocess.CalledProcessError) as exc:
        print(f"::error::Version allocation failed: {exc}", file=sys.stderr)
        sys.exit(1)
