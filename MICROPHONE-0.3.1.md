# Android microphone investigation — v0.3.1

## Outcome / confidence

A concrete native packaging defect was found and corrected: **v0.3.0 declared RECORD_AUDIO but omitted MODIFY_AUDIO_SETTINGS**. Chromium's Android capture-device selection explicitly requires both. This is a source-backed cause of capture failure, not evidence another application owns the microphone. It is a strong candidate for the reported failure; **the affected phone was not accessed and recovery is unverified**.

Only production changes: one manifest permission; version 0.3.1/versionCode 4; the shared renderer mic-error mapper now reports the generic start failure plus bounded `NotReadableError` / `TrackStartError`, not “another app”. Auth, mobile layout, playback, media constraints and native bridge implementation are unchanged. No unprocessed-audio fallback, sleeps, permission bypass, backend changes or live chat submissions were added.

## Exact path

- Regular continuous conversation: `use-voice-conversation.ts:startListening` pauses the wake listener via `beforeMicOpen`, then `use-mic-recorder.ts:start` calls `hermesDesktop.requestMicrophoneAccess()` → `android/bridge.ts` native `microphone` RPC → `MainActivity` runtime RECORD_AUDIO check/request (code 7). The recorder waits for old meter AudioContexts to close, then calls getUserMedia with `{audio:{echoCancellation:true,noiseSuppression:true}}`.
- WebView independently invokes the actual `MainActivity.onPermissionRequest`. Only the trusted `https://appassets.androidplatform.net` origin and `RESOURCE_AUDIO_CAPTURE` are granted. This is separate from Android runtime permission. Local appassets is the secure renderer origin; camera/resources are not broadly granted.
- GPT-Live: `use-voice-live-conversation` → `VoiceLiveSession.start` → getUserMedia with AGC/AEC/noise suppression true. It relies on the WebView permission callback, not the desktop microphone RPC. Both modes route DOMExceptions through the same `micError` function. The reported toast alone cannot distinguish modes.
- Chromium 124 `AudioManagerAndroid.setDevice` checks MODIFY_AUDIO_SETTINGS **and** RECORD_AUDIO and returns false if either is absent. `AudioManagerAndroid::MakeLowLatencyInputStream` calls `SetAudioDevice` and returns NULL on failure. Mainline Chromium still performs the two-permission check (method now called `setCommunicationDevice`). A granted WebView callback and granted RECORD_AUDIO therefore do not establish that native acquisition can succeed.
- Playback does not establish that this capture-specific input-device selection succeeded.

Primary sources inspected (copies under artifacts/):
- https://github.com/chromium/chromium/blob/124.0.6367.219/media/base/android/java/src/org/chromium/media/AudioManagerAndroid.java (setDevice, lines 241–251)
- https://github.com/chromium/chromium/blob/124.0.6367.219/media/audio/android/audio_manager_android.cc (MakeLowLatencyInputStream, lines 305–320)
- https://github.com/chromium/chromium/blob/main/media/base/android/java/src/org/chromium/media/AudioManagerAndroid.java
- Android `PermissionRequest` API and runtime permission docs; MDN getUserMedia NotReadableError definition.

## Ownership / bridge review

Wake capture, normal recorder, live session and barge-in monitor are separate acquisition paths. Conversation and dictation call the wake-pause seam; barge monitor has a disposed guard after getUserMedia and blocks conversation restart while it owns a capture. No evidence from the phone identifies simultaneous ownership. Acquisition constraints were intentionally retained, not experimentally disabled as a purported fix.

There are separate potential lifecycle-hardening opportunities: the recorder only checks recorderRef before its async permission/acquisition waits; the native permission bridge keeps one pending WebView request and one RPC, shares code 7, and does not handle onPermissionRequestCanceled. This patch does not claim those are the user's cause or claim pending-start cancellation races are repaired. The repeated normal start/stop and post-acquisition cancel control passed; it is not a test of every in-flight cancellation race.

## Verification

- `bash build.sh`: production Vite renderer, adapter typecheck, Java unit tests, isolated real auth-component integration, assembly/signing/lint passed. Lint retains warnings.
- Expanded Vitest run: **46 passed / 8 files** (original bridge/shared RPC tests, new permission/error-copy contracts, actual recorder-meter, barge-in and live-routing tests). Java: **12 tests, zero failures/errors/skips**.
- Original phone renderer fixture suite passes at 360/393/412px, with existing history/navigation/composer/settings and draft-resize checks. Fixture prompt submission only; no production requests.
- `node tests/microphone-browser.mjs`: **Linux Chromium synthetic input**, actual recorder hook, five native MediaRecorder → decode → playback → reacquire cycles, nonzero decoded signal, all tracks ended, plus cancel/restart. This is expressly NOT an Android capture result.
- Final APK's **1051 renderer files exactly equal the built renderer**; bundled error mapper inspected. Both declared native audio permissions are present. Package/version and signing verified; certificate equals v0.3.0.
- Final v0.3.1 APK upgraded on Android 15 emulator; installed base.apk pulled and SHA256 equals delivered artifact. MODIFY_AUDIO_SETTINGS confirmed granted. Native sign-in setup inspected; System UI ANR dismissed with Wait.
- **Android acquisition blocked:** isolated instrumentation attached to the old and updated APK's real MainActivity WebView and trusted origin, with RECORD_AUDIO pregranted, no gateway/auth/network. WebView **124.0.6367.219** repeatedly crashed its renderer with SIGTRAP before usable capture results (first attempt timed out). The instrumentation APK is separate, not shipped. This is not a reproduction of the phone's NotReadableError, nor proof of Android capture recovery. The unaccelerated emulator also had System UI/launcher stalls; emulator was shut down.

## Deliverable

- `/home/will/hermes-android/artifacts/hermes-android-0.3.1-debug.apk`
- Identical current alias `/home/will/hermes-android/artifacts/hermes-android-debug.apk`
- SHA256 `6c045f66bb3d171836ca953b19a7fdbb75b5e61cab9e4a083af2f033cfccf80b`
- Package `com.hermes.privateapp`, versionName `0.3.1`, versionCode `4`.
- Original v0.3.0 preserved as `artifacts/hermes-android-0.3.0-debug.apk`.

Install as an update, not uninstall. Memory-only auth can require browser sign-in again after process restart. Phone checks still needed: first capture, repeated stop/start, restart after playback, cancel/restart and actual spoken audio/transcription. If startup still fails, retain the displayed error code and Android/WebView versions; do not assume another app is responsible. No physical phone or production backend changes were made.

Evidence: `artifacts/microphone-validation.json`, `artifacts/microphone-browser-control.json`, `artifacts/mic-baseline.txt`, `artifacts/mic-baseline-crash.txt`, `artifacts/mic-native-crash.txt`, `artifacts/mic-updated.txt`, `artifacts/mic-updated-logcat.txt`, `artifacts/emulator-v4-installed.apk`, `artifacts/emulator-v4-setup.xml`. Historical `artifacts/validation.json` remains v0.3.0 evidence; use microphone-validation.json for this release.
