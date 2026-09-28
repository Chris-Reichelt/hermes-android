# Architecture and request flows

## System layout

```text
Android phone
  Native setup screen -> system browser -> gateway sign-in
  Java Activity + GatewayAuth + GatewayDownload + GatewayMedia
       ^ scoped message bridge / native REST and WebSockets
  Android System WebView
       bundled React desktop renderer + shared JSON-RPC client
       phone navigation / local preferences / media Blob URLs
                         |
             private-network connection (normally HTTPS)
                         |
Your Hermes gateway / backend
  authentication + profile/session routing + agent execution
  tools / model providers / history / filesystem / STT / TTS
                         |
            operator-selected local or cloud providers
```

Windows/macOS/Linux desktop clients can use the same backend. Shared server history is not stored or synchronized by a new Android-specific history engine. Access and ownership depend on server account/profile configuration; simultaneous cross-device editing and all reconnection cases have not been certified.

## Native shell and renderer reuse

`MainActivity.java` hosts the renderer using AndroidX `WebViewAssetLoader` at the synthetic local origin `https://appassets.androidplatform.net`. That origin serves APK assets; it is **not the remote server address**. JavaScript and DOM storage are enabled; file/content URL access, mixed-content loading, off-origin WebView networking and remote navigation are restricted.

`apps/desktop/src/android/bridge.ts` adapts the desktop bridge API to native messages. The existing desktop composer, transcript, sidebar, settings and shared JSON-RPC channel remain in use. The shell adds phone navigation and viewport/inset handling rather than a second agent or conversation implementation. Native messages require the trusted origin and main frame. Unsupported desktop functions explicitly reject or use declared no-op shims; retained UI code does not establish working mobile capability.

The connection registry exposes a single native endpoint. Profile scope travels with REST and socket calls; unknown connection IDs are rejected instead of silently targeting another backend. The server is still the authorization authority: client profile selection is not an access-control boundary.

## Authentication

1. Read public `/api/status` and require the advertised native sign-in capability for browser mode.
2. Bind an ephemeral listener to literal loopback `127.0.0.1`; generate random state, callback path and S256 PKCE verifier/challenge.
3. Open `/auth/native/authorize` in the system browser. The gateway controls its login page and identity provider; the password does not pass through renderer JavaScript.
4. Accept only the expected callback path/state, exchange the code at `/auth/native/token`, then verify identity with `/api/auth/me` before opening the renderer.
5. `GatewayAuth` adds bearer auth to native REST calls, serializes rotating refresh at `/auth/native/refresh`, and performs one authenticated-401 retry. Uncertain network failures during refresh are not blindly replayed.
6. For each real WebSocket open/reconnect, mint a fresh ticket through `/api/auth/ws-ticket`; native code adds it to the socket URL. Caller-supplied auth query fields are removed. Credential-free descriptors reach the renderer.

Tokens are process-memory-only. Disconnect/process death clears local auth; it does not promise server-side revocation or browser logout. The explicit legacy-token mode is separate and refuses auth-required servers. Only the basic username/password provider has isolated integration-test evidence; do not infer complete third-party SSO support or Desktop's embedded-login fallback.

## Chat and other server features

Renderer actions use the existing shared JSON-RPC channel over native text WebSockets, including streamed chat/tool events. Session listing and settings use native-backed API calls. Received binary socket frames are relayed as base64 and reconstructed as ArrayBuffers, supporting incoming speech PCM. **Binary socket sends are unsupported.** Backend tools execute with the backend's privileges, not as Android shell processes.

## Voice paths: three different features

- **Microphone / transcription:** Android runtime `RECORD_AUDIO`, manifest `MODIFY_AUDIO_SETTINGS`, and the trusted WebView audio-capture grant permit renderer `getUserMedia`/MediaRecorder. Transcription depends on the backend/provider. Android permission success alone does not prove working acquisition.
- **Automatic reply speech:** a fresh completed assistant reply passes the device-local gate, then the retained synthesis/fallback path. Native `/api/audio/speak-stream` can supply PCM; owner-scoped `/api/audio/speak` REST can return playable data. Provider-direct WebView fetch may be blocked by policy. Do not weaken policy to bypass it. The local toggle is not a global backend TTS-setting change.
- **Manual attached audio:** v0.3.3 used Electron's `hermes-media://` protocol, unavailable in Android; the fixture reproduced media error 4. v0.3.4 uses `readGatewayMedia` -> native `media.read` -> `GatewayMedia` -> authenticated `/api/fs/download` -> base64 bytes -> Blob URL. Existing renderer cleanup revokes URLs after use/cancel. This is inline playback, not a user-visible file save.

Inline media is limited to **8 MiB**, **two simultaneous native reads**, existing 60-second authenticated request bounds and session-generation checks. This is fully buffered with base64 overhead, not streaming or background download. Larger attachments should use Download. Desktop fixture playback is verified; Android attachment playback is also user-confirmed working after the update. No instrumented Android playback run is claimed.

## Downloads

`download.file` and `download.image` preserve connection/auth/profile/session ownership. File transfer uses `/api/fs/download` with encoded `path`, `profile`, and optional `session_id`; the backend resolves and authorizes that path. Native code rejects redirects and does not send bearer credentials to an external image host.

Android `ACTION_CREATE_DOCUMENT` lets the person select the destination. Bytes stage in private temporary storage and then copy to the chosen document URI. Limits: **256 MiB files**, **16 MiB images**, one save at a time, five-minute picker bound. Normal failure cleans temporary state and attempts removal of an incomplete document. Process death can still leave partial output. There is no broad storage permission, resumable background service or upload implementation.

## Code map for a future reviewed source release

| Component | Responsibility |
| --- | --- |
| `android/app/src/main/java/com/hermes/privateapp/MainActivity.java` | Setup, WebView, permissions, native bridge, sockets, document picker/lifecycle |
| `GatewayAuth.java` / `EndpointPolicy.java` in that directory | Native auth, refresh/tickets, endpoint policy |
| `GatewayDownload.java` / `GatewayMedia.java` | Bounded authenticated saving and inline media |
| `apps/desktop/src/android/` | Android adapter, phone shell, styling, speech diagnostics |
| `apps/desktop/src/lib/media.ts` | Platform-specific media resolution |
| `apps/desktop/src/` / `apps/shared/src/` | Reused renderer and shared protocol |
| `tests/`, native test sources, Vite/Vitest/Gradle configuration | Fixtures, protocol tests, build and artifact validation |

These are source-layout references, not files included in this documentation bundle. Public framework references and evidence boundaries are in PROVENANCE.md.
