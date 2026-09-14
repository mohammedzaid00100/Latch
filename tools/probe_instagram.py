#!/usr/bin/env python3
"""One bounded metadata-only probe of the official Instagram upstream test reel.

This is diagnostic, since platforms may block CI IPs. A failed live probe never
gets reported as a passing download test. Its outcome is shipped with the APK.
"""
import datetime
import json
import pathlib
import re
import subprocess
import sys

root = pathlib.Path(__file__).resolve().parent.parent
binary = root / "app/build/generated/extractorAssets/engine/yt-dlp"
url = "https://www.instagram.com/reel/Chunk8-jurw/"
command = [sys.executable, str(binary), "--ignore-config", "--no-playlist",
           "--no-cache-dir", "--age-limit", "0", "--socket-timeout", "12",
           "--retries", "0", "--extractor-retries", "0", "--skip-download",
           "--dump-single-json", url]
report = {
    "checked_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
    "url": url, "scope": "Metadata only, official upstream test reel, Linux CI network; not the user's phone or reel",
    "download_tested": False,
}
try:
    result = subprocess.run(command, capture_output=True, text=True, timeout=55)
    if result.returncode == 0:
        data = json.loads(result.stdout)
        formats = data.get("formats") or []
        report.update(status="metadata_available", format_count=len(formats))
        if not formats:
            report["status"] = "no_formats"
    else:
        lines = result.stderr.splitlines()
        reason = next((line for line in reversed(lines) if line.startswith("ERROR:")), "No metadata returned")
        report.update(status="not_verified", reason=re.sub(r"https?://\S+", "[link]", reason)[:1000])
except subprocess.TimeoutExpired:
    report.update(status="not_verified", reason="The metadata probe timed out after 55 seconds")
except Exception as error:
    report.update(status="not_verified", reason=str(error)[:500])

destination = root / "distribution/Instagram-metadata-check.json"
destination.parent.mkdir(exist_ok=True)
destination.write_text(json.dumps(report, indent=2) + "\n")
print(json.dumps(report, indent=2))
