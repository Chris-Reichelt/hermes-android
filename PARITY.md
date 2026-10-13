# Feature parity and acceptance checklist

This checklist distinguishes **retained code**, **implemented adapter**, and **verified behavior**. A compiled feature is not necessarily usable on a phone.

| Requirement | Initial implementation | Verification / remaining acceptance |
|---|---|---|
| Actual Hermes Desktop UI | Working-tree React renderer and shared modules copied; original entry imported after Android bridge | 0.3.0 production-bundle functional tests passed at 360/393/412px with phone navigation; authenticated native WebView runtime remains unverified |
| Private connection | Native browser PKCE/basic login, bearer REST and fresh native WS tickets; explicit legacy-token mode; one Tailscale URL; memory-only tokens | Real isolated auth-component integration passed, APK upgrade/native setup verified on Android 15 emulator; no live-account login performed |
| Q / Ari / Vee identity & direct routing | Existing profile/bot/session owner logic retained; profile scopes preserved; unknown connection IDs rejected | Adapter scope tests passed; actual aliases and separate remote endpoints are **not migrated/configured**. Must add multi-endpoint native registry before claiming full fleet routing |
| Shared sessions/history | Original backend-authoritative session list/resume, durable/runtime identity mappings and shared RPC retained | 0.3.0 actual sidebar REST path/profile, fixture shared-history list/resume/transcript and failure/Retry tested; live account history, compression lineage and cross-device changes untested |
| Streaming chat | Original composer/transcript and JSON-RPC channel over native text WS | 0.3.0 original composer/new draft/Send exercised: exactly one correctly scoped prompt.submit reached fixture transport; no actual model turn sent |
| Voice `af_heart` | Original backend STT/TTS/voice selection code retained; microphone permission adapter; received binary PCM relay implemented | Binary byte-delivery test passed; no microphone, audio-focus, latency, barge-in, background playback or `af_heart` synthesis validation. No backend voice setting changed |
| Team rooms | Existing team-room UI and protocol retained | Backend room membership, simultaneous streams, identity attribution and phone layout untested |
| Files / remote editing | Existing remote `/api/fs/*` JSON adapter paths retained | No real remote read/write performed. Android file picker, uploads, download/save/share and clipboard-image adapters unavailable |
| Previews | React/data-URL/text components retained | Electron `<webview>`, desktop filesystem URLs, browser automation, pop-outs and native image downloads are unavailable. No remote preview parity claim |
| Tools / approvals | Existing stream events and approval dialogs retained | Channel tests passed; real approval/resume/cancel, native desktop-tool failure handling and background recovery untested |
| Tasks / cron | Existing backend task UI retained | Real tasks and notifications untested; no task created or modified |
| Settings | Existing backend JSON settings UI retained | 0.3.0 reachable Settings button, actual section dropdown, About navigation and Close tested; real backend settings writes untested; desktop connection editor, local installs/updater, SSH and cloud OAuth are unavailable. Use initial native connection screen |
| Notifications | In-app original notifications retained | Native notifications, permission workflow, push/background delivery and notification actions **not implemented** |
| Clipboard | Native plain-text read/write implemented | Compiled/linted; Android runtime untested; images unsupported |
| Security / isolation | Origin + main-frame-scoped bridge, same-endpoint checks, no redirects, no saved auth, screenshots/backup disabled, external link confirmation | Java policy tests and APK build verified; independent mobile security review still required; debug APK is not a production-hardened release |
| Phone UX / lifecycle | Viewport/safe-area additions, Android Back navigation/disconnect, screen-awake adapter | 0.3.0 phone nav/composer bounds and 44px targets tested; 852→440px browser resize retains draft and Send. Native inset/Back handling added. Final-hash emulator upgrade/setup verified; native authenticated WebView/IME, rotation, screen readers and lifecycle still need device QA |

## Required next acceptance gates

1. Android 15 software-emulator final 0.3.0 upgrade, native setup, and installed APK SHA256 equality are verified. Complete authenticated native WebView renderer startup on an authorized device; hardware acceleration remains unavailable to this build user.
2. Native PKCE/basic login and fresh WS tickets are implemented in 0.2.0 and verified against isolated real auth components. Complete browser login → renderer on an authorized Android device and existing gateway. Native credentials intentionally stay in memory; persistent secure storage is not implemented.
3. Configure explicit credential-free route identities for Q, Ari and Vee; enter credentials on-device. Test A→B→A against their real configured endpoints, owner-scoped REST, canonical Bot Chats, rooms, no cross-profile session leakage.
4. Under user authorization, perform one real streamed turn, existing-history resume, approval/cancel, reconnect, shared desktop/mobile history and team-room tests. Do not use generated test conversations as parity evidence.
5. Test phone microphone STT, backend-selected `af_heart` voice, PCM playback and interruption. Add native audio focus, device routing and lifecycle handling.
6. Implement Android document picker/upload/download and scoped preview safety. Replace or explicitly disable desktop-only features in their actual UI controls (currently unavailable actions surface errors).
7. Implement notifications and background delivery, then test Android process/battery behavior. Never assume a WebSocket survives process suspension.
8. Conduct phone UX/accessibility and security review; add launcher icon/localized native setup; move to intentional release signing and updates only after explicit authorization.
