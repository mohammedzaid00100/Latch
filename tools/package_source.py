#!/usr/bin/env python3
"""Package source plus the verified wrapper, never CI credentials or build caches."""
import pathlib
import subprocess
import zipfile

root = pathlib.Path(__file__).resolve().parent.parent
tracked = subprocess.check_output(["git", "ls-files", "-z"], cwd=root).decode().split("\0")
additional = ["gradle/wrapper/gradle-wrapper.jar", "app/src/androidTest/assets/latch-test.mp4", "app/build/generated/extractorAssets/engine/yt-dlp"]
additional += [str(p.relative_to(root)) for p in (root / "app/schemas").rglob("*.json")] if (root / "app/schemas").exists() else []
destination = root / "distribution/Latch-Source.zip"
destination.parent.mkdir(exist_ok=True)
with zipfile.ZipFile(destination, "w", zipfile.ZIP_DEFLATED) as archive:
    for name in sorted(set(tracked + additional)):
        if name and (root / name).is_file():
            archive.write(root / name, "Latch/" + name)
