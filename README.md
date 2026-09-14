# Latch

**Share. Save. Done.** An Android app by Mohammed Zaid.

Share a supported video link to Latch, choose video or audio and an available format, and save the result on your phone. The share panel is a translucent Android activity, so the source application can remain visible behind it.

## Install the test APK

Open this repository's **Actions** tab, open the latest successful **Build and test Latch** run, and download the **Latch-Android-APK** artifact. Unzip it and install **Latch-0.1.1-arm64.apk**.

The test build supports Android 10 or later on ARM64 phones, including the hardware architecture used by the Poco M7 5G. Actual device compatibility must still be checked. This is a development-signed APK, not a Play Store release.

## Instagram fix in 0.1.1

This update replaces the November 2025 extractor bundled in 0.1.0 with the checksum-pinned yt-dlp 2026.08.19 release, including upstream's 2026 Instagram rewrite and Android-compatible webpage fallback. Installing the update also replaces any old extractor retained in app data; clearing app data is unnecessary.

Instagram links are normalized to the canonical HTTPS URL while preserving the complete post ID. Errors now distinguish an ambiguous Instagram response from a definite login requirement or rate limit. Use **Copy error details** on the share panel to report a remaining failure; copied details omit URLs and session values.

Install the APK over the previous version. Downloads and settings are retained when Android accepts the existing development signature.

## Use it

1. Open a video in the source app.
2. Choose Share, then More or the Android sharing options, then **Latch**.
3. Select Video or Audio and a format the source provides.
4. Tap Download. You can return to the original app.
5. Find the file in **Movies/Latch** or **Music/Latch**, or open it from Latch's Downloads screen.

You can also paste a link from the Home screen.

## Included

- Native Kotlin / Jetpack Compose interface, with light, dark and system themes.
- Share-intent receiver with a compact bottom sheet.
- Source metadata and actual source formats; no invented 4K options.
- On-device yt-dlp extraction and FFmpeg merging/audio conversion.
- Video downloads, original audio extraction and explicit MP3 encoding presets.
- Persistent Room download records, a serial queue, retry and cancellation.
- Foreground progress notifications and completion notifications.
- Validation of media tracks and saved file size before marking a download complete.
- MediaStore shared storage without broad storage permissions.
- Wi-Fi-only preference and a default audio preference.
- Open, share, delete, and clear-history actions.

Higher MP3 bitrates do not restore detail absent from the source. When separate video and audio need merging, this version uses MKV to retain the selected streams without unnecessary re-encoding.

## Source support and limits

| Source | Implementation | Verification requirement |
| --- | --- | --- |
| YouTube videos and Shorts | Public-link adapter backed by the bundled extractor | Platform restrictions apply; each live URL may fail |
| Instagram Reels and video posts | Public-link adapter backed by the bundled extractor | Login-required links and carousels are unsupported |
| Facebook videos and Reels | Public-link adapter backed by the bundled extractor | Private/login-required links are unsupported |
| Direct HTTPS media files | The same native extraction and download pipeline | Synthetic media is exercised in Android instrumentation tests |

Receiving a shared link does not guarantee permission or ability to download it. Only use Latch where the source platform and relevant rights holders permit saving. Public availability alone is not permission. YouTube's official API does not grant general-purpose download or audio-extraction rights; this project does not use that API or claim platform endorsement.

Private, DRM-protected, age-restricted and live media are unsupported. There is no account login, cookie import, paywall bypass or hidden download service. A platform can change or block access at any time. Download support therefore needs continuing maintenance.

The app contains real adapters, not hardcoded success responses. Building successfully does not prove that all current Instagram, Facebook and YouTube links work. See the Actions results and [testing notes](docs/TESTING.md) for the actual verification scope. The APK ZIP also includes an explicitly labeled live Instagram metadata probe result from the CI network; it is not a phone download test.

## Build on Windows

Install Android Studio and its Android SDK, use JDK 17, and open this repository as a project. The Gradle wrapper uses Gradle 8.11.1. The downloadable source archive includes the verified wrapper JAR.

After cloning the repository, run **python tools/bootstrap_wrapper.py** once if the wrapper JAR is missing. This fetches the official wrapper for a pinned Gradle version and verifies its Git object checksum. The build workflow has read-only repository access; it never pushes commits. All workflow actions are pinned to exact commits.

From the project directory:

    gradlew.bat testDebugUnitTest lintDebug assembleDebug -PlatchAbis=arm64-v8a

The result is **app/build/outputs/apk/debug/app-debug.apk**.

The Android build plugin, Kotlin, Compose, Room and media dependencies are pinned in the Gradle files. The pre-build task downloads the pinned extractor from its official GitHub release and verifies SHA-256 before packaging. The app installs that verified APK asset locally and does not fetch executable updates at runtime. No API key or backend is required.

For emulator media tests, generate the synthetic fixture using the FFmpeg command in the workflow, then run:

    gradlew.bat connectedDebugAndroidTest -PlatchAbis=x86_64

## Background behavior

Downloads start from a user action and run in a declared data-transfer/media-processing foreground service. This avoids relying on long-running WorkManager quotas for this combined workflow. It is still subject to Android's foreground-service limits; the timeout callback stops work cleanly.

The serial queue is saved in Room. Rotation does not own download execution. If the process is stopped, unfinished work becomes Interrupted on the next app start and can be retried. Latch does not secretly restart after a force stop. Partial transfer reuse depends on the source and the downloader; there is no misleading Pause button.

## Privacy

URLs and files are processed on the device. Latch has no account system, advertising SDK, telemetry or server. Source platforms and their CDNs receive the normal requests needed to resolve and transfer a file. Download history stays in the local database and can be cleared. No clipboard access occurs until the user presses Paste.

## Signing and distribution

CI caches a development signing key for test-build updates; caches can expire. Production release signing must use a privately held keystore or CI secrets. Never commit a signing key or password. A release build has signing configuration intentionally left to the owner.

Play Store publication is a separate product and policy decision. This project does not claim Play Store eligibility. Native-library 16 KB compatibility and real-device behavior must be verified for any release target.

## License

Latch is distributed under **GNU GPL version 3**, matching the linked youtubedl-android dependency. Copyright 2026 Mohammed Zaid. No warranty.

See [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for source, notices, and dependency build references.
