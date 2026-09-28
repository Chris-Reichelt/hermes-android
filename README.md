# Hermes Android — server-backed client preview

This project documents an Android client that packages the Hermes Desktop React interface inside a native Android WebView shell. The phone is a client: agents, models, tools, profiles, history and server files live on an independently operated Hermes backend. It is not an on-phone model runtime, the web dashboard in a browser tab, or a promise of complete desktop parity.

**Publication scope: documentation only.** This review bundle contains no application source, APK, credentials, recording, screenshot or operational configuration. Source and binary publication remain gated by the work in [Publication](docs/PUBLICATION.md). No GitHub upload is implied by this bundle. This is a downstream preview, not a claim of an official Nous Research Android release.

## Release described

**v0.3.4 / versionCode 7**, package `com.hermes.privateapp`, minimum Android 8 (API 26), target/compile API 35. The inspected APK is **debug-signed and debuggable**, not a production-hardened store release. Its verified identity is in [release metadata](release-metadata.json); the APK itself is intentionally not attached.

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
| 0.1.x | Initial native shell and reused desktop renderer. |
| 0.2.0 | System-browser native sign-in, bearer refresh and fresh WebSocket tickets. |
| 0.3.0 | Phone navigation, responsive settings access, keyboard-height/layout adaptations. |
| 0.3.1 | Added Chromium-required audio-device permission alongside microphone recording permission; clearer capture failure messages. |
| 0.3.2 | Wired Android Read Responses Aloud to the existing local preference; repaired native socket error delivery and speech failure cleanup; added bounded metadata diagnostics. |
| 0.3.3 | Authenticated file/image download bridge and Android destination picker. |
| 0.3.4 | Authenticated attachment-media read, bounded byte transfer and Blob-backed playback; no change to automatic-speech policy. |

Historical fixture success is not device validation. Reports about earlier versions are not treated as proof that every feature works on every Android/WebView combination.
