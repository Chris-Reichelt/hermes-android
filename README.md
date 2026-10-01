# Hermes Android — server-backed client preview

This project documents an Android client that packages the Hermes Desktop React interface inside a native Android WebView shell. The phone is a client: agents, models, tools, profiles, history and server files live on an independently operated Hermes backend. It is not an on-phone model runtime, the web dashboard in a browser tab, or a promise of complete desktop parity.

**Latest APK:** [Download 0.3.12 / pushfix19](https://github.com/Chris-Reichelt/hermes-android/releases/tag/v0.3.12-public-preview). This debug preview (package `com.hermes.privateapp.publicpreview`, versionCode 23) updates the existing public-preview app in place with the same signing identity. It requires your own compatible Hermes backend over Tailscale. This is not an official Nous Research Android release.

## What's new: 0.3.12 / pushfix19

- Fixed background listener reconnects, including reconnecting after the app screen closes: the running service retains its in-memory authentication until disconnect or final cleanup.
- Reply notifications now use a separate high-importance channel; the persistent listener notification remains quiet. Foreground replies remain suppressed.
- Removed the green Push Log debug button and credential/reply-body logging.
- Kept the existing renderer assets unchanged except for removal of the debug-button script.

The original pushfix18 failed native reconnect checks and used a default-importance reply channel. This build passes all six native Android emulator scenarios: foreground suppression, background reply, streamed-text fallback, server-close reconnect, return-to-foreground suppression, and reconnect/notification after Activity destruction. Physical-device notification settings and manufacturer restrictions still apply.

Install the APK over the current public preview (no uninstall), reopen and sign in, allow notifications, then send a message and press Home before its reply. Verify downloads with the release's `SHA256SUMS` and `verification.json`. Background delivery requires a compatible authenticated `/api/push` endpoint on the same server as chat. This is a foreground-service WebSocket listener, not FCM: force-stop, process death, network loss and Android background-service time limits can interrupt delivery; there is no offline replay guarantee.

## Historical architecture baseline (0.3.4)

The following historical baseline describes **v0.3.4 / versionCode 7**, package `com.hermes.privateapp`. Current release identity is in [release metadata](release-metadata.json). Both require minimum Android 8 (API 26), target/compile API 35, and are **debuggable previews**, not production-hardened store releases.

- Phone navigation exposes Sessions, New chat and Settings around the reused desktop renderer.
- Browser sign-in uses the gateway's native PKCE broker. Native code owns credentials and authenticated REST/WebSocket transport.
- Voice transcription, file downloads, attached-audio playback and automatic read-aloud have user-reported success in the existing deployment. Automatic speech requires the **device-local Read Aloud switch to be ON**.
- v0.3.4 repairs attached-audio transport: a native authenticated read becomes a renderer-local Blob URL instead of an unsupported Electron protocol. Play, pause, seek, resume and end were verified in a desktop browser fixture with a valid MP3. **Android attachment playback is now user-confirmed working after the v0.3.4 update.** This user confirmation is separate from the fixture evidence; no instrumented Android playback test was run.
- One private gateway endpoint is supported with backend profile scopes. Native multi-server routing, uploads, native push notifications and many Electron-only features are not implemented.

**Installing or sharing an APK does not grant access to anyone else's backend.** You need your own compatible server or permission from its administrator, private-network access, and your own sign-in. There are no default server credentials or author-hosted public service in this project.

## Start here

1. Read [feature status and limitations](docs/LIMITATIONS.md).
2. Arrange your own backend using [server requirements](docs/SERVER.md).
3. If you receive an explicitly approved APK, follow [Android installation and use](docs/INSTALL.md).
4. Developers: read [architecture](docs/ARCHITECTURE.md) and [build notes](docs/BUILD.md). This docs-only bundle is **not independently buildable**; the build notes distinguish actual project commands from missing portable-release inputs.
5. Review [security and privacy](docs/SECURITY.md), [provenance and dependencies](docs/PROVENANCE.md), and [publication/audit gates](docs/PUBLICATION.md).

## Technical history

| Version | Change relevant to users/developers |
| --- | --- |
| 0.3.12 / pushfix19 | Background notification channel, socket reconnect and shared-auth lifecycle repairs; green debug button removed. Public-preview versionCode 23. |
| 0.1.x | Initial native shell and reused desktop renderer. |
| 0.2.0 | System-browser native sign-in, bearer refresh and fresh WebSocket tickets. |
| 0.3.0 | Phone navigation, responsive settings access, keyboard-height/layout adaptations. |
| 0.3.1 | Added Chromium-required audio-device permission alongside microphone recording permission; clearer capture failure messages. |
| 0.3.2 | Wired Android Read Responses Aloud to the existing local preference; repaired native socket error delivery and speech failure cleanup; added bounded metadata diagnostics. |
| 0.3.3 | Authenticated file/image download bridge and Android destination picker. |
| 0.3.4 | Authenticated attachment-media read, bounded byte transfer and Blob-backed playback; no change to automatic-speech policy. |
| 0.3.8 | **In-app sign-in** (type credentials directly in the app, no external browser). Fixes the `Gateway HTTP 400` that the old in-app path could hit: it now (a) reads `auth_providers` as a flat name list, (b) follows the `/auth/native/authorize` redirect chain to capture the `hermes_session_pkce` broker cookie (OkHttp does not auto-follow cross-origin 302s), and (c) extracts the auth `code` from the `next` URL in the `password-login` JSON body. Shipped as the `com.hermes.privateapp.publicpreview` public preview, versionCode 19. |

Historical fixture success is not device validation. Reports about earlier versions are not treated as proof that every feature works on every Android/WebView combination.
