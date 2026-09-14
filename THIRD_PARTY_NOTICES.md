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
- The bundled revision is supplied by the pinned Android library; consult its raw resource and build records.
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
