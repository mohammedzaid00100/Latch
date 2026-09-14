#!/usr/bin/env python3
"""Fetch a pinned, verified Gradle wrapper. No project code or secrets are sent."""
import hashlib
import pathlib
import re
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
DEST = ROOT / "gradle/wrapper/gradle-wrapper.jar"
BLOB = "a4b76b9530d66f5e68d973ea569d8e19de379189"
URL = "https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradle/wrapper/gradle-wrapper.jar"
if not DEST.exists():
    with urllib.request.urlopen(URL, timeout=60) as response:
        data = response.read()
    actual = hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()
    if actual != BLOB:
        raise SystemExit("Gradle wrapper verification failed.")
    DEST.parent.mkdir(parents=True, exist_ok=True)
    DEST.write_bytes(data)
    print("Verified Gradle 8.11.1 wrapper.")
properties = DEST.with_suffix(".properties")
text = properties.read_text()
if "distributionSha256Sum=" not in text:
    with urllib.request.urlopen("https://services.gradle.org/distributions/gradle-8.11.1-bin.zip.sha256", timeout=60) as response:
        checksum = response.read().decode().strip()
    if not re.fullmatch(r"[a-f0-9]{64}", checksum):
        raise SystemExit("Invalid Gradle distribution checksum.")
    properties.write_text(text + "distributionSha256Sum=" + checksum + "\n")
    print("Pinned Gradle distribution SHA-256: " + checksum)
