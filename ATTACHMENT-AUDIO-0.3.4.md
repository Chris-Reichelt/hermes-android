# Android 0.3.4 — attachment playback only

## Evidence and scope

The user confirmed the same MP3 plays on the computer, manual Play still failed on Android, downloads work, and automatic read-aloud works after enabling its device-local toggle. Automatic speech, autoplay policy, microphone code, backend configuration, and GatewayDownload are unchanged in this release.

The v0.3.3 packaged renderer resolved filesystem MP3 attachments to Electron's `hermes-media://remote/...` protocol. Android does not register that Electron protocol: its WebView allows packaged assets and blocks other network URLs. The isolated original-renderer test returned HTMLMediaElement error 4 / readyState 0 for that source. This is an attachment transport incompatibility, not evidence of corrupted synthesis or an automatic-speech regression. The exact user-approved clip decodes with ffmpeg without errors: MP3, 127872 bytes, 21.312 seconds.

## Repair

- `media.ts` uses an Android-only optional `readGatewayMedia` capability instead of the Electron protocol.
- `bridge.ts` snapshots owner scope and turns native-returned bytes into a Blob URL. Existing attachment cleanup revokes Blob URLs on unmount or late canceled completion.
- `MainActivity` admits `media.read`; `GatewayMedia.java` fetches bytes through GatewayAuth and the existing `/api/fs/download` contract, preserving profile, connection and optional session scope.
- Tokens remain native. Redirects and cross-connection owners are refused. Reads are bounded to 8 MiB, two simultaneous native reads, the existing 60-second authenticated request timeout, and session-generation cancellation. Larger clips can still use the unchanged Download action.
- Version 0.3.4 / code 7, same package and signing certificate as 0.3.3. No WebView security or autoplay relaxation.

## Verification

`bash build.sh` passed: 48 adapter/JS tests, 26 speech regression tests, phone UI fixtures, working download-button fixture, existing PCM/REST read-aloud fixtures, renderer and adapter builds, Android build/lint, native tests and isolated real-auth integration. Native reports contain 25 passing tests including 4 new media tests and 8 existing download tests. The media native test transfers the approved MP3 bytes byte-for-byte through a disposable authenticated HTTP server and verifies owner/query scope, redirects, 404, size limits, empty response and stale-generation rejection.

`tests/media-playback-browser.mjs` uses the real built renderer and real HTML audio controls with a synthetic native port. Original build: unsupported Electron source, error 4. Fixed build: Play control advances, pause/seek/resume work, playback reaches 21.312 seconds and fires ended without error. It does not pretend to be Android runtime verification.

`python3 tests/validate-media-artifact.py` verified all 1051 packaged renderer files against dist, package/version, same certificate, retained microphone permissions/security restrictions and the final APK hash.

APK: `/home/will/hermes-android/artifacts/hermes-android-0.3.4-debug.apk`
SHA256: `2c0e33523c97e4324d368d0ee076d597a2d3f9d7b95b259d88f811d20f7bec76`

No connected Android device was available; this user cannot access /dev/kvm and prior software-emulator/WebView instability remains a limitation. No emulator was started or phone installation performed. **Manual attachment playback on the user's phone still requires verification after installing this APK.** Production services and user conversations were not accessed or changed.

Source changes: MainActivity.java; new GatewayMedia.java; Android bridge.ts; global.d.ts; lib/media.ts; version metadata; build.sh. Tests: new GatewayMediaTest.java, media-playback-browser.mjs, validate-media-artifact.py; bridge version assertion. Evidence: artifacts/media-playback-{baseline,fixed,validation}.json. Original edited files backed up under artifacts/audio-baseline-0.3.3. The workspace is not a Git repository.
