# Validation scope

The GitHub Actions workflow is the authoritative record of which checks passed for a commit.

## Automated checks
- Unit tests for shared text, supported hosts, unsafe input, source formats, split-stream selection, unavailable audio, conversion labels, protected/age-restricted media, and filenames.
- Android lint and an ARM64 debug APK build.
- Android API 35 / x86_64 emulator tests using the actual native Python, yt-dlp and FFmpeg libraries.
- An original two-second color-and-tone fixture served by a local instrumentation test server.
- Native resolution and transfer of the fixture, MP3 conversion, track validation, MediaStore publication, saved size checks, and removal of test output.
- Native navigation, the empty Downloads screen, invalid-share handling, dismiss behavior, and screenshots.

The engine's injected test network validator is used only by instrumentation code for its loopback server. Production uses the default validator; user-facing link parsing accepts HTTPS and rejects local addresses.

## Device checks still required
- Share an owner-permitted link from the actual Instagram, Facebook and YouTube Android apps.
- Confirm visible return to the same screen in the source app; the source app may pause or change its own UI.
- Confirm real URL format availability and playback on a physical ARM64 phone.
- Check notification denial, network changes, Wi-Fi-only behavior, cancellation during conversion, low storage, source rate limiting, and long transfers.
- Verify Android versions beyond the emulator API level and 16 KB native-library compatibility before production release.
- Check OEM battery management and large-font layouts on the target phone.

A synthetic fixture passing does not establish permission or compatibility for any particular third-party video. No live social-platform download is claimed as tested unless a later validation record names that test and result.

## 0.1.1 Instagram regression checks

- Real yt-dlp 2026.08.19 Instagram extraction against synthetic public webpage responses with browser impersonation unavailable, matching the Android capability. Also checks that login redirects, gated media, and explicit access restrictions do not turn into media formats.
- JVM checks for canonical Instagram share variants, unchanged direct-media signatures, ambiguous extractor errors, rate limits, explicit restrictions, and redacted diagnostic text.
- Android instrumentation writes a stale extractor into the real installation location, installs the new bundled asset, verifies idempotency, and executes the actual Android Python runtime to assert version 2026.08.19.
- Existing native video/MP3/MediaStore and share-panel tests remain mandatory.
- A separate metadata-only probe uses Instagram's official test reel from upstream's test list. Its status is recorded in distribution/Instagram-metadata-check.json. Platform blocking of CI is a diagnostic outcome, never represented as successful live downloading.

The user's exact reported reel was not supplied as a URL. Reproduction of that particular link on the user's network remains a device check.
