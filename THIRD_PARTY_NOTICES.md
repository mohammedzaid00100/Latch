# Third-party notices and source

Latch's application source is original and licensed under GNU GPL version 3. It does not copy Seal or YTDLnis branding or application source.

## youtubedl-android 0.18.1
- Maven modules: io.github.junkfood02.youtubedl-android:library and :ffmpeg.
- Android wrapper copyright belongs to its upstream authors; GNU GPL v3.
- Exact upstream source: https://github.com/yausername/youtubedl-android/tree/0.18.1
- Source archive: https://github.com/yausername/youtubedl-android/archive/refs/tags/0.18.1.zip
- Native Python build references: BUILD_PYTHON.md in that source.
- Native FFmpeg build references: BUILD_FFMPEG.md in that source.
- The project includes bundled runtimes and transitive native dependencies; preserve their notices when distributing a derived build.
- No executable updates or arbitrary plugins are fetched at app runtime.

## yt-dlp
- Upstream source and license: https://github.com/yt-dlp/yt-dlp
- Active extractor: official yt-dlp 2026.08.19 zipimport release, replacing the wrapper's legacy extractor at initialization.
- Exact source: https://github.com/yt-dlp/yt-dlp/tree/2026.08.19
- Release: https://github.com/yt-dlp/yt-dlp/releases/tag/2026.08.19
- Artifact SHA-256: 1fa6733c37ea6fb51c99ad8fe785e7b7e5f3246c9b980230329d4fb72ed8d4d6.
- The checksum-verified executable is included in the downloadable application source archive. The Gradle build pins and verifies the official artifact.
- Core source uses the Unlicense; bundled components retain their individual licenses.

## FFmpeg
- Source and licensing: https://ffmpeg.org/download.html and https://ffmpeg.org/legal.html
- GNU LGPL/GPL requirements depend on the enabled components in the upstream Android build.
- The Android build configuration and package references are in the pinned wrapper source above.

## Android, Kotlin and supporting libraries
- AndroidX and Compose: https://android.googlesource.com/platform/frameworks/support/ (Apache 2.0).
- Kotlin and kotlinx libraries: https://github.com/JetBrains/kotlin and https://github.com/Kotlin (Apache 2.0).
- OkHttp and Okio: https://github.com/square/okhttp and https://github.com/square/okio (Apache 2.0).
- Coil: https://github.com/coil-kt/coil (Apache 2.0).
- Gradle wrapper 8.11.1: https://github.com/gradle/gradle/tree/v8.11.1 (Apache 2.0); original script headers retained.
- Transitive dependency licenses remain applicable.

The repository and its source archive are the corresponding application source location for Latch. Exact third-party source and native build instructions are provided above. Before wider redistribution, retain the full corresponding source for the exact bundled native revisions and verify all applicable license obligations.
