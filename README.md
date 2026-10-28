# Hermes Android — private preview 0.3.1

## 0.3.1 microphone packaging fix

Added the missing native `MODIFY_AUDIO_SETTINGS` permission required by Chromium's Android capture-device selection alongside `RECORD_AUDIO`. Replaced the misleading “another app” diagnosis for `NotReadableError` / `TrackStartError` with generic startup failure and the bounded browser error code. Auth, layout, playback and capture constraints are unchanged.

**Phone recovery is not verified.** Android emulator upgraded successfully and its installed APK hash matches, but both old and updated WebView capture probes were blocked by renderer crashes. A separate Linux Chromium synthetic-input control passed five record/decode/playback/reacquire cycles and cancel/restart; this is not Android capture evidence. Regression results: **46 Vitest, 12 JUnit**, phone-layout fixtures, isolated real auth integration, renderer build/typecheck, Gradle/signature/lint passed (lint warnings retained).

**Current download: [v0.3.38-apk50 public preview](https://github.com/Chris-Reichelt/hermes-android/releases/download/v0.3.38-apk50/hermes-android-0.3.38-apk50-public-preview.apk)** — Android pasted-link editing fix (pasted URLs stay editable; backspace/deletion work normally; paste/delete confirmed by the reporting user). Package `com.hermes.privateapp.publicpreview`, versionCode **50**, versionName **0.3.38-apk50-public-preview**, same signing certificate as apk49 (upgrade-in-place). SHA256: `929b020504ec1e987136a6a94be4bbaae516e92fb776ff7b35c79da5df9f7922`.

Historical: `artifacts/hermes-android-0.3.1-debug.apk` (private package `com.hermes.privateapp`, versionCode **4**) and the `artifacts/hermes-android-debug.apk` alias predate the public-preview line and are not the current download.

Full acquisition-path diagnosis, evidence and remaining phone checks: **MICROPHONE-0.3.1.md**. Machine-readable evidence: `artifacts/microphone-validation.json`. Everything below describes retained functionality or historical v0.3.0 validation, not a claim of successful v0.3.1 phone capture.


**Signed APK with phone navigation and existing Desktop chat/history/settings. Not a full mobile-parity release.**

## 0.3.0 phone-usability fix

- Persistent 44px **Sessions / New chat / Settings** buttons replace inaccessible desktop pane chrome. Sessions opens the actual Desktop sidebar, including shared backend history, profile controls, loading/failure states and Retry. Selecting a row returns to the existing transcript, including selecting the same conversation again.
- New chat calls Desktop's existing fresh-draft action. The actual rich composer and Send button submit through the unchanged shared JSON-RPC transport; no second chat engine or alternate history store.
- Settings opens the existing responsive Desktop settings overlay. Its section dropdown and close button remain reachable.
- Height follows `visualViewport`; native system-bar/cutout/IME insets are consumed at the shell and Android `adjustResize` is retained. Composer controls have phone-sized targets; the empty hero is smaller and disappears at keyboard-sized heights. Android Back first closes Sessions or navigates back, then offers Disconnect.
- **Auth unchanged:** `GatewayAuth.java` was not modified. Browser PKCE, native bearer refresh and fresh native WS tickets remain intact. Installing an update can kill the process, so memory-only auth can still require browser sign-in again.

### Diagnosis and evidence

The confirmed regression is presentation/navigation: the desktop layout drops collapsible side panes at narrow widths, leaving desktop tab/hero/composer chrome without accessible phone controls. The screenshot alone cannot prove the account's history was missing. This task did **not** authenticate to production or inspect its session data. A selective redacted log scan yielded no sidebar HTTP-status evidence.

`node tests/phone-ui.mjs` exercises the **actual production renderer bundled in the APK**, with explicitly labeled test fixtures through the native bridge adapter. At widths **360, 393, 412**, assertions pass for navigation targets, the original scoped sidebar endpoint, history resume/transcript, new draft, editable composer, exactly one `prompt.submit` with the correct runtime session, settings section navigation/close, Back, and resize from 852px to 440px without losing the draft or covering Send. A separate failure/retry scenario proves history errors are not silently presented as empty history. No live model prompt or live session mutation occurred.

`bash build.sh` passed production build, adapter typecheck, **32 Vitest tests**, **12 JUnit tests with no failures/skips**, isolated real auth-component integration, Gradle assembly and lint (warnings retained). APK assets exactly match all **1051** renderer files. Original signing certificate is preserved.

The **final APK** was installed/upgraded and launched on the software Android 15 emulator. Its native browser-login setup was inspected. The installed `base.apk` was pulled and hashes exactly match. Launcher/System UI ANRs recovered with Wait. The emulator was stopped. **Not verified:** authenticated Android WebView runtime, native WebView IME behavior, physical phone, live shared account history or a real streamed model reply. Browser viewport resize is not represented as a native keyboard test; the emulator setup keyboard attempt did not show an IME.

SHA256: `b8858724968bf81a1928ddca0db874796ff113a1155fbb1df00ceaee94697e40`

Evidence: `artifacts/validation.json`, `artifacts/phone-ui-results.json`, `phone-ui.log`, `artifacts/emulator-v3-final-setup.xml`, `artifacts/emulator-v3-installed.apk`. Screenshots are **fixture data**, not a live account: `artifacts/phone-sessions-fixture.png`, `phone-history-fixture.png`, `phone-composer-fixture.png`, `phone-keyboard-resize-fixture.png`, `phone-settings-fixture.png`, `phone-history-error-fixture.png`.

Only the isolated port was edited. Copied Desktop source changes are restricted to `app/contrib/controller.tsx` and `wiring.tsx`; new phone chrome lives under `src/android`. Production Desktop/shared sources were untouched.

## Install and sign in

Artifact: `/home/will/hermes-android/artifacts/hermes-android-debug.apk`

Package `com.hermes.privateapp`, versionCode **3**, versionName **0.3.0**, minimum Android 8 / API 26, target API 35. Same debug signing certificate as 0.1.0; upgrade preserves the package. Original APK retained as `artifacts/hermes-android-0.1.0-debug.apk`.

1. Connect the phone to its existing Tailscale network.
2. Install the updated APK over the old preview. No physical phone was accessed by this task.
3. Enter the existing gateway URL and backend profile (e.g. `codex`). Leave the default **Sign in with browser (username/password)** selected. **No gateway token is required for this mode.**
4. Tap **Connect / Sign in**. The phone's browser opens the gateway's own login page. Enter your existing username and password there; the server renders a masked password field. Never enter a password in the legacy token field or send credentials in chat.
5. After successful sign-in, the browser displays “Sign-in received. Return to Hermes Private.” Switch back to the app. The actual desktop renderer opens after native token exchange and authenticated identity verification.
6. Android Back → Disconnect clears the app's memory-only session and returns to setup. Sign in again after process death. Cancel is available during login; browser callback timeout is three minutes.

Only `.ts.net` and `100.64.0.0/10` gateway endpoints are accepted. HTTPS is preferred; HTTP is allowed only for the explicitly entered trusted Tailscale endpoint. This app does not provision VPN access or change the gateway.

**Legacy gateway token** is a separate explicit option for ungated legacy servers only. The app checks `/api/status` and refuses this mode on an auth-required server. A model API key or dashboard password is not a gateway session token.

## Actual authentication implemented

- Gateway-advertised `native_pkce`, not a guessed OAuth/password-grant endpoint.
- System-browser `GET /auth/native/authorize`, S256 PKCE, cryptographically random state and callback path, native listener bound only to `127.0.0.1` on an ephemeral port.
- The server accepts only literal-loopback HTTP redirects, not Android custom URI schemes. Its basic provider uses the existing `/login` and `/auth/password-login`; passwords remain in the browser, not the native bridge or renderer.
- The exact callback path and state are verified; code exchange uses `POST /auth/native/token`. Native bearer authentication is used for REST; `/api/auth/me` must succeed before the renderer opens.
- Serialized `/auth/native/refresh`, proactive expiry refresh and one retry after an authenticated 401. No automatic replay on network failure of token/refresh exchanges.
- A fresh, single-use `/api/auth/ws-ticket` is minted immediately inside native `socket.open`, including reconnects. Tickets, access tokens and refresh tokens never appear in renderer connection descriptors. Native WS retains profile query scope and replaces any caller-supplied auth query values.
- The native-broker mechanism can route to other interactive providers through the server's chooser, but **only the basic username/password provider has been integration-tested**. No claim of Portal/SSO validation.
- Native tokens are process-memory-only, never written to app preferences, logs or assets. Browser-managed login state/password-manager choices belong to the browser. No passwords were read from the host or embedded in the APK.
- Disconnect clears local credentials; it is not a claim of server-side revocation. The basic provider's tokens are stateless.

## Retained implementation and boundaries

The APK packages the actual desktop React renderer, shared JSON-RPC client, profile/session routing, Bot Chat registry, team rooms, composer, tools, approvals and settings. It is not the web dashboard or a replacement chat UI. `SOURCE.json` records the snapshot provenance.

`apps/desktop/src/android/bridge.ts` adapts the desktop APIs. `MainActivity.java` hosts local assets using AndroidX WebViewAssetLoader; main-frame/origin-scoped messaging supplies native REST, WebSockets, clipboard, microphone permission and screen-awake. `GatewayAuth.java` owns native authentication. No CORS, Origin, server auth or live routing policies were weakened.

The shell supports one gateway endpoint with backend profile scopes. Unknown connection IDs fail closed. Separate-gateway aliases and native multi-endpoint registry migration remain unimplemented. Desktop-only operations reject explicitly; uploads, native notifications, binary WS sends and complete phone UX/audio parity remain unavailable/unverified. See `PARITY.md`.

Android backup, WebView debugging and remote navigation are disabled. `FLAG_SECURE` blocks screenshots/recents of the app, not the external browser. Explicit renderer external links require confirmation. Debug signing is for private testing, not production distribution.

## Historical 0.2.0 validation (superseded by 0.3.0 above)

Reproduce: `bash /home/will/hermes-android/build.sh`.

- Vite production renderer build and standalone Android adapter TypeScript check passed. This is not a full upstream desktop typecheck.
- **32 Vitest tests in 4 files passed**, including adapter descriptors without credentials, profile isolation, native bridge correlation, binary-frame delivery and unchanged shared JSON-RPC behavior.
- **12 JUnit tests passed, zero failures/skips in the isolated integration run.** Five test methods execute the APK's actual Java auth class against a real loopback HTTP server using production Hermes basic provider, auth routes, middleware and ticket verifier with freshly generated disposable credentials. No production config/server/plugins are started.
- Integration covers native authorize → masked server form → password verification → actual loopback callback → PKCE code exchange → authenticated identity; fresh WS ticket upgrades and rejected reuse; proactive and 401-triggered refresh; rejected bad password/refresh; state mismatch; rejected custom-scheme callbacks; bad PKCE and code replay; gated legacy-token rejection; cross-endpoint rejection.
- Additional real-component checks: unauthenticated ticket mint denied, invalid refresh denied, password rate limiting, 30-second ticket expiry. Error-redaction unit checks and a disposable-password scan of test reports passed.
- Gradle `assembleDebug`, `testDebugUnitTest`, `lintDebug` passed. Lint retains warnings (including missing launcher icon, enabled JavaScript and localization/dependency advice); not zero-warning certification.
- APK v2 signature verified; original/updated certificate output matches. Package/minSdk/target/version confirmed with `aapt`.
- **Android 15 software emulator booted. Original 0.1.0 → 0.2.0 upgrade installed successfully; the updated native setup screen launched and was inspected through UI hierarchy.** Default browser sign-in and no token field were visible. This emulator check used the 0.2.0 development APK before the final lifecycle-race guards; the final rebuilt hash was not reinstalled. Hardware acceleration remains unavailable to this user; software emulation was slow and the emulator's System UI briefly reported not responding.
- **Not verified:** complete browser sign-in and renderer startup on Android, physical-phone runtime, live-account authentication, real chat, history, voice, bots or rooms. Earlier phone-sized offline Chromium renderer smoke remains only an offline renderer check, not authenticated native validation.

Logs/evidence: `auth-integration.log`, `tests-js.log`, `adapter-typecheck.log`, `renderer-build.log`, `android-build.log`, `emulator-auth.log`, `artifacts/emulator-setup.xml`, `artifacts/emulator-upgrade.txt`, APK signing/badging/hash reports, and `artifacts/validation.json`.

All edits/builds are isolated to this workspace. Production source `/home/will/.hermes/hermes-agent` was read-only; real auth components were imported for isolated tests without writing bytecode. No live service restart, credential access, backend mutation, bot contact or host-group change was performed. Dependencies/toolchain are reused as described in the initial snapshot; this is not a portable standalone npm distribution.
