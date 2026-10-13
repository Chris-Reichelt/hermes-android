# Android automatic speech investigation — 0.3.2 / code 5

## Outcome and evidence boundary

Two real defects reproduced and repaired in the isolated Android workspace. This is a tested APK, not verified recovery on the user's phone. `adb devices -l` returned no connected device. The prior emulator WebView SIGTRAP remains an Android-runtime blocker; desktop Chromium is explicitly a control, not Android playback proof. No production backend request, config change, real chat submission, or credential lookup was performed. The previously denied synthesis/auth probe was not retried.

## Reproduced defects

1. **Settings gate mismatch.** The schema-driven Read Responses Aloud switch wrote `voice.auto_tts` to backend config, while `useAutoSpeakReplies` reads the already-seeded device-local `hermes.desktop.autoSpeakReplies` atom. Prepatch production-renderer interaction: switch visibly checked, synthetic backend true, local storage false, fresh assistant completion, zero speech sockets. Evidence: `artifacts/auto-speech-baseline.json`. On Android the same Settings control now reads/writes the existing local preference, with no backend config PUT. It does not force-enable existing users or change agent voices.
2. **Native socket rejection silently missed by speech.** The adapter's native RPC rejection dispatched EventTarget error/close events but did not invoke `onerror`/`onclose` property handlers used by speech playback. Red test: addEventListener saw one error while both property handlers saw zero; see `artifacts/auto-speech-native-rejection-baseline.log`. A common delivery helper now invokes both styles, including socket-send rejection. The production-renderer fixture proves native socket-open rejection advances to owner-scoped REST and a real HTMLAudioElement finishes playing synthetic WAV data.
3. **Failure cleanup leaked a rejected promise.** `void done.finally(...)` created an unhandled rejection even though the caller displayed the synthesis error. Reproduced with fixture synthesis failure; cleanup now catches its own derived rejection while preserving the original caller's error. Browser page-error arrays now remain empty.

## Diagnostics delivered

A collapsed Audio diagnostics row below the connection status shows the actual local gate and preparing/speaking/idle state. Expanding it shows the latest eight metadata events, newest first: fresh reply accepted/skipped, profile owner, synthesis rung, REST response, stream AudioContext state, autoplay rejection or synthesis error. No reply text, URLs, credentials or persisted diagnostic logs. Existing error notification remains intact.

## Transport audit and remaining hypotheses

- Completed-reply path: composer owner -> useAutoSpeakReplies -> ambient claim -> playSpeechText -> direct config/synthesis -> native speech-stream WebSocket -> owner-scoped `/api/audio/speak` JSON/data URL.
- Native GatewayAuth explicitly allows `/api/audio/speak-stream`, keeps the profile query, strips caller auth query fields and mints a fresh single-use ticket for each socket. MainActivity relays PCM as base64 with binary=true; the adapter reconstructs ArrayBuffer. REST API JSON responses carry data URLs without needing remote audio URL interception.
- MainActivity still blocks off-origin WebView HTTP requests and keeps `mediaPlaybackRequiresUserGesture(true)`. No PKCE, auth, endpoint/Tailscale policy, microphone Java handler, or manifest permission was relaxed. Provider-direct HTTP can be blocked; the fixture confirms blocked direct fetch falls back to stream and then REST. That is not evidence of the user's actual failure.
- User-clicked attachment playback does not exercise automatic PCM AudioContext playback or the local auto-speak gate. Suspended WebView audio remains a possible separate cause; diagnostics now expose it. A stream can currently finish while context is suspended; the diagnostic explicitly says playback is not verified. We did not label browser autoplay behavior as Android proof or change policy speculatively.
- Native bridge requests still have a 65-second cap, native HTTP read timeout 60 seconds; long synthesis could time out despite longer desktop timeoutMs. No timeout change was made without reproduction.

## Verification

- `bash build.sh`: passed renderer build, adapter-only TypeScript check, 47 Android/shared/microphone JS tests, 26 speech tests, production-renderer browser interactions, Android assembly/lint and isolated real-auth tests.
- Speech browser test, twice (relay config and actual blocked provider-direct fetch): visible switch on/off changes local gate; enabling skips existing reply; fresh reply reaches owner codex on android-remote; PCM 24kHz/0.2-second synthetic buffer ends on a running real AudioContext; WS fallback surfaces synthesis error; native socket-open rejection falls back to REST; 0.2-second synthetic WAV finishes in a real Audio element; toggling off prevents later synthesis; no config PUT; no unhandled page errors.
- Phone UI fixtures pass 360/393/412px widths, history/resume/send/settings and keyboard-size viewport controls. Fixed a test race by awaiting the actual visualViewport CSS height after resize, not by changing the microphone/layout behavior.
- Original ad hoc speech run reproduced **23 passed / 2 failed plus an owner-test import failure**. Fixed isolated test aliases; Node 26 webstorage shadowing handled with worker NODE_OPTIONS; Storage.prototype spy correctly simulates quota/security exceptions. Final **26/26 pass**. Historical failure logs retained. jsdom still prints its expected unimplemented media play warning; real browser playback checks provide media evidence.
- **12/12 Java tests**, including disposable production-auth component fixture; auth throttle/ticket expiry checks pass. No live-account login.
- APK application ID retained, versionName 0.3.2, versionCode 5, same signing certificate as 0.3.1. Both RECORD_AUDIO and MODIFY_AUDIO_SETTINGS retained. All **1051 packaged renderer assets byte-match** tested dist.
- No full upstream desktop TypeScript suite or actual Android audio test claimed. Existing test-file ws typing warnings are not a production adapter typecheck failure.

## Artifact

`/home/will/hermes-android/artifacts/hermes-android-0.3.2-debug.apk`

SHA256: `6f718d50fa58523e730d4c52224ca37ff6f1814033fac649f1e9c89ba9ada97d`

Validation: `artifacts/auto-speech-artifact-validation.json`; checksum sidecar: `artifacts/hermes-android-0.3.2-debug.apk.sha256`.

## Files changed

Production source: Android bridge.ts, phone-shell.tsx, mobile.css, new speech-diagnostics.ts; Settings config-settings.tsx; use-auto-speak-replies.ts; voice-playback.ts; android/app/build.gradle version only.

Tests/build: build.sh, tests/bridge.test.ts, tests/phone-ui.mjs, store/voice-prefs.test.ts (test spy only), new tests/auto-speech-browser.mjs, vitest.speech.config.ts, tests/validate-speech-artifact.py. Evidence logs/JSON/screenshot under artifacts. No upstream live source changed.

## Handoff

Install the APK as an in-place update (same signature; do not clear app storage). It repairs the control's wiring, rather than merely repeating toggle advice. The settings switch and connection-area Read aloud status now agree. Phone recovery is only established after the user confirms a fresh ordinary reply plays. If it remains silent, the visible Audio diagnostics row identifies whether the attempt reached synthesis and whether WebView audio was suspended, without asking for credentials or another blind backend settings change.
