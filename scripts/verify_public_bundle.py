#!/usr/bin/env python3
"""Verify the reviewed docs-only bundle, not an app/APK or legal clearance."""
import hashlib
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "publication-manifest.json"
manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
errors = []
allowed = {row["path"]: row for row in manifest["include"]}
actual = set()
for item in ROOT.rglob("*"):
    rel = item.relative_to(ROOT).as_posix()
    if ".git" in item.relative_to(ROOT).parts:
        continue  # Repository metadata is not a publication payload.
    if item.is_symlink():
        errors.append(f"Symlink forbidden: {rel}")
    elif item.is_file():
        actual.add(rel)
expected = set(allowed) | {"publication-manifest.json"}
if actual != expected:
    errors.append(f"Unexpected files: {sorted(actual - expected)}")
    errors.append(f"Missing files: {sorted(expected - actual)}")
patterns = {
    "personal absolute home path": re.compile(r"(?:/home/|/Users/)[A-Za-z0-9_.-]+"),
    "private-key block": re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    "GitHub credential": re.compile(r"(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})"),
    "private tailnet hostname": re.compile(r"\b(?:[A-Za-z0-9-]+\.)+[A-Za-z0-9-]+\.ts\.net\b"),
}
for rel in sorted(actual):
    file = ROOT / rel
    data = file.read_bytes()
    if rel in allowed:
        row = allowed[rel]
        if hashlib.sha256(data).hexdigest() != row["sha256"]:
            errors.append(f"SHA-256 mismatch: {rel}")
        if len(data) != row["bytes"]:
            errors.append(f"Size mismatch: {rel}")
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError:
        errors.append(f"Non-text payload forbidden: {rel}")
        continue
    for label, pattern in patterns.items():
        if pattern.search(text):
            errors.append(f"Review-required pattern ({label}): {rel}")
    if file.suffix == ".md":
        for target in re.findall(r"\[[^\]]*\]\(([^)]+)\)", text):
            if "://" in target or target.startswith("#"):
                continue
            target = target.split("#", 1)[0]
            if not (file.parent / target).is_file():
                errors.append(f"Broken local link: {rel} -> {target}")
if errors:
    print(json.dumps({"ok": False, "errors": errors}, indent=2))
    sys.exit(1)
print(json.dumps({"ok": True, "payload_files": len(allowed),
                  "total_files_including_manifest": len(actual),
                  "scope": "docs-only; hashes, allowlist, text patterns, local links"}, indent=2))
