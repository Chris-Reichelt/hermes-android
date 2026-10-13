# Phone renderer validation workflow

- Reuse `ContribWiring` and its `WiredPane` surfaces; change only shell chrome. Do not duplicate chat state, session ownership, RPC dispatch or backend history.
- Do not infer missing server sessions from a phone screenshot. First inspect collapsed panes, then exercise the actual sidebar REST response shape (`recents`, `cron`, `messaging`) and its existing error/Retry state. Label fixture history explicitly and never present it as live-account evidence.
- Desktop `SessionInfo` fixtures require `started_at` and `last_active`, not guessed `created_at`/`updated_at`. `session.create` needs the durable `stored_session_id` and runtime `info` or optimistic UI can hit invalid-time errors unrelated to the shell.
- Assert usable navigation, resume transcript, fresh draft, correct `prompt.submit` runtime ID/profile, settings navigation and close, and composer/send bounds after viewport reduction. Pixel-only or hero-only checks cannot establish chat usability.
- Scope repeated text locators to the visible sidebar/overlay: the real renderer keeps hidden surfaces mounted and accessibility labels can have hidden duplicates.
- Build first, then run Playwright against `apps/desktop/dist` and compare all APK assets byte-for-byte with that build. `build.sh` includes the phone regression suite.
- Use explicit emulator serials, software acceleration when KVM is unavailable, and never change host groups. System UI/Launcher ANRs may recover with Wait.
- Reinstall the final artifact, pull the installed `base.apk`, and compare SHA256. Distinguish native setup checks from authenticated WebView checks and browser resize tests from a real Android IME.
- Keep browser PKCE, native bearer refresh and per-socket WS tickets untouched while modifying layout. Run the existing isolated real auth-component integration after every native-shell change.
