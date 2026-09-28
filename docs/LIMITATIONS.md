# Feature status and evidence

Status applies to the documented **v0.3.4** artifact. “Implemented” means present in inspected code; “fixture verified” means an isolated test, not the same thing as Android hardware or a live account. User reports below are narrow observations, not a multi-device qualification matrix.

| Feature | Status / evidence | Remaining boundary |
| --- | --- | --- |
| Browser PKCE login, native bearer refresh, socket tickets | Implemented; isolated production-auth component tests recorded | Full provider/device matrix not validated; basic provider is the tested provider |
| Phone Sessions/New chat/Settings, composer | Production renderer fixtures exercised at narrow phone widths | Native keyboard, rotation, accessibility and every screen need device QA |
| Server-backed history and streaming chat | Existing desktop/shared protocol reused; scoped fixture tests | No universal cross-device/history/room parity claim |
| Microphone/transcription | User-reported working in deployment; permission fix shipped in 0.3.1 | Earlier software-emulator acquisition crashed; all start/cancel/audio-route races not certified |
| Automatic read-aloud | User-reported working after device-local toggle ON; speech fixtures pass | Historical replies are not replayed automatically; WebView gesture/audio route and long-synthesis constraints remain |
| File downloads | User-reported working; native transport and renderer button fixtures | Historical emulator picker attempt did not prove saved bytes; cancellation/image-provider matrix remains incomplete |
| Image saves | Implemented, bounded native/data/blob/HTTPS paths; component tests | Not the same as user-confirmed every image source/provider |
| Attached MP3 Play | v0.3.3 error 4 reproduced for Electron-only source; v0.3.4 Blob transport repaired and browser play/pause/seek/resume/end passes | **Android attachment playback user-confirmed working after v0.3.4**; no instrumented Android device/emulator playback test or broad codec/device certification |
| Inline media limits | 8 MiB and two concurrent native reads | Fully buffered, no streaming/range/background guarantee; larger files use Download |
| Profiles | Multiple backend profile scopes on one endpoint | Native multi-gateway registry and separate-server aliases unavailable |
| Team rooms, tools, approvals, tasks, remote editing | Retained renderer/protocol code where backend supports it | Not acceptance-tested end to end across all Android flows |
| Uploads/local file chooser | Unsupported desktop adapters | Download support does not imply uploads |
| Native notifications / push | Not implemented | In-app notices do not imply background delivery |
| Clipboard text / keep-awake | Native adapters implemented | Clipboard images and comprehensive device behavior not verified |
| Electron previews/popouts/plugins/SSH/bootstrap/updater | Unavailable or explicitly unsupported | UI reuse is not Electron runtime reuse |
| Binary WebSocket sending | Unsupported | Receiving binary PCM is a different, implemented path |
| Background/lifecycle | Best-effort client behavior | No guaranteed persistent sockets, background audio, resumable downloads or OS-process survival |

## Release evidence

The attachment-release handoff records a successful build with 48 Android/shared JavaScript tests, 26 speech regression tests, phone/download/speech/media browser fixtures, adapter typecheck, renderer build, Gradle build/lint and isolated real-auth integration. Native reports total **25 passing tests**, including media and download tests. These are recorded results from that build, not a fresh rerun of the suite by the documentation task. No full upstream desktop typecheck or zero-warning lint claim is made.

The documentation audit independently recomputed the v0.3.4 APK SHA-256, ran `aapt` metadata inspection and `apksigner` verification, and compared **all 1051 packaged renderer assets** with the completed renderer output. Signature matches the preceding preview. No Android emulator/device was launched for the documentation work.

The media browser fixture uses the production renderer, a synthetic native bridge and valid MP3 bytes. It is meaningful evidence for renderer source resolution and controls, but it does not exercise Android's complete WebView/native audio device chain. Separately, the updated APK's attachment playback was confirmed working by its Android user; that is not an instrumented test receipt or proof that every control/codec/device is qualified. The original test clip is private and is **not part of this public bundle**. A redistributable synthetic fixture must replace it before source/test publication.

## Practical bounds

- HTTP authenticated requests have existing 60-second bounds; most bridge calls have a 65-second bound. Download bridge requests have a longer bound for the picker/transfer workflow. Long model speech can still time out.
- Files are capped at 256 MiB, images at 16 MiB, inline media at 8 MiB. One save runs at a time. Redirects and arbitrary HTTP external-image URLs are deliberately refused.
- On normal errors, native save code cleans staging and attempts destination cleanup. Abrupt process death can leave partial files.
- Microphone live-conversation, wake behavior, interruptions, Bluetooth/audio focus and every device codec combination are not established by basic transcription or one MP3 fixture.
- No release signing, store compliance or independent mobile-security certification has been completed by this documentation task.
