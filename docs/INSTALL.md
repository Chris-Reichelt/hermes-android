# Install, update and use on Android

## Before installation

Download the current APK from the [0.3.13 public-preview release](https://github.com/Chris-Reichelt/hermes-android/releases/tag/v0.3.13-public-preview). This is a **debuggable preview**, not Play Store production software. The repository itself contains documentation; APKs are release assets.

Requirements: Android 8/API 26 or later, a maintained compatible Android System WebView with `WEB_MESSAGE_LISTENER`, an enabled system browser, your own compatible Hermes server, network permission to reach it, and valid server login. Connect Tailscale on the phone and server first. A `.ts.net` suffix alone neither provisions a VPN nor authenticates a server. The app accepts `.ts.net` names or addresses in the Tailscale `100.64.0.0/10` range; ordinary LAN names, localhost and arbitrary public hosts are not accepted by this preview.

## Check the file

For the current 0.3.13 / newchatfix21 artifact:

```text
Filename: hermes-android-v0.3.13-newchatfix21.apk
SHA-256: ad497452cf92903664ec6f766633b8407fa47a88ffb1ad26ed5107ceb0dd812c
Package: com.hermes.privateapp.publicpreview
Version: 0.3.13-newchatfix21-public-preview (25)
```

On Windows PowerShell:

```powershell
Get-FileHash .\hermes-android-v0.3.13-newchatfix21.apk -Algorithm SHA256
```

On Linux:

```sh
sha256sum hermes-android-v0.3.13-newchatfix21.apk
```

Compare the result to an independently trusted publisher checksum. Matching a checksum detects a different file; it is not a security audit or proof of publisher identity.

## Install or update

The current APK uses the same public-preview signing identity as 0.3.8 and pushfix18; update in place without uninstalling. After updating, reopen and sign in. Allow notifications, send a message, then press Home before its reply. Check Android's **Hermes replies** notification channel if banners are disabled. The background listener requires a compatible `/api/push` endpoint on the same authenticated server as chat; installing the APK does not install that server endpoint. Explicit Disconnect and Android Force Stop stop background delivery.

1. Transfer the approved APK to the phone without making a public link to private server files.
2. Open it in the phone's file manager. If prompted, allow **Install unknown apps** for that specific source, not indiscriminately for every app.
3. Install. For an existing preview, choose the in-place update; do not uninstall or clear app data unnecessarily.
4. Turn off the temporary unknown-app install permission afterward if you do not need it.

Optional developer workflow, on an explicitly selected device you own:

```sh
adb devices -l
adb -s YOUR_DEVICE_SERIAL install -r hermes-android-0.3.4-debug.apk
```

`YOUR_DEVICE_SERIAL` is a placeholder. Do not use an ambiguous default device. Updates require a compatible signing identity and package/version sequence. A new developer's debug key does not update an existing signed installation. Do not export/share the original private key to solve that; use an intentional separate developer package or a planned release migration. Downgrade/install-signature errors are not instructions to delete user data.

## First connection

1. Launch **Hermes Private (Preview)** with the private network connected.
2. Enter **your own gateway base URL**, including its port if needed, and backend profile. Blank profile uses `default`. Obtain these values privately from your server administrator; this repository supplies none.
3. Leave **Sign in with browser (username/password)** selected. Tap **Connect / Sign in**.
4. Confirm the browser is at your expected gateway and enter credentials only into its login form. Do not paste passwords into the legacy token field or a chat.
5. After the browser reports sign-in received, return to the Android app. Native token exchange/identity checks finish before the renderer opens.
6. Sign-in has a three-minute timeout and a Cancel control. Retry from setup if canceled/expired. After process death or an update, sign in again: credentials are intentionally not persisted.

Use HTTPS with a valid certificate when possible. The preview permits cleartext HTTP only under its entered private-endpoint policy; that is not a recommendation to expose HTTP publicly or turn off authentication. See SECURITY.md.

## Everyday operation

- **Sessions:** open the existing server-backed history sidebar, select the intended profile/conversation. An error/Retry state is not an empty-history guarantee.
- **New chat:** create a fresh draft through the existing renderer. Type into the composer and use Send.
- **Settings:** opens the existing settings overlay with a reachable section selector and Close. Some desktop-only controls are unsupported; do not expect every visible option to work.
- **Voice input:** use the microphone control, grant microphone permission if requested, speak, then stop and review the transcription. STT service must be available on your server/provider.
- **Read Aloud:** turn **Read Responses Aloud** ON in Settings for this device. A fresh ordinary assistant reply should trigger synthesis; enabling the switch does not intentionally read all historical messages. The connection-area audio diagnostics can show gate and playback state. This does not change the server's selected voice or force other clients to speak.
- **Download an attachment:** use its **Download** action, choose a destination in Android's document picker, then open/check the saved file. Tapping a filename can open preview instead. Cancel quietly abandons the save. Keep the app alive during transfer.
- **Manual audio attachment:** v0.3.4 has a transport repair. Attached-audio playback is user-confirmed working after the update. Try Play, pause/resume and seeking with a non-sensitive small clip; those individual controls were fixture-tested, not comprehensively device-qualified. If it fails, Download and use a trusted local player. Automatic read-aloud working is not proof of attachment playback.
- **Back / disconnect:** Back closes the session surface/navigates first; the disconnect confirmation clears the native in-memory session. It does not log the system browser out of the server.

## Troubleshooting without leaking data

| Symptom | Check |
| --- | --- |
| URL rejected | Accepted private-host policy, scheme, no URL credentials/query/fragment. Use your own endpoint, not example placeholders. |
| Cannot reach sign-in | Private-network connection, ACL/firewall, server running, correct port, certificate trust, enabled browser. |
| Login completes but app stays in setup | Return to app before timeout; gateway must advertise `native_pkce`; retry after process death. Never post callback URLs or tokens. |
| No history | Correct account/profile; inspect the bounded error and Retry before assuming data loss. |
| No microphone | Android permission, system microphone privacy switch, compatible WebView; report the displayed error rather than assuming another app owns the microphone. |
| Automatic speech silent | Device-local Read Aloud ON, fresh reply, volume/output route, synthesis service and bounded audio diagnostics. |
| MP3 Play fails | Check installed version; v0.3.3 transport was incompatible. v0.3.4 playback is user-confirmed working in the deployment; Download remains a fallback for device-specific failures. |
| Download/media fails | Gateway route compatibility, authorization, network, limits; redirects are deliberately refused. |
| Work stops while backgrounded | No guaranteed foreground service, persistent socket, background audio or push delivery in this preview. |

For a public issue provide app/Android/WebView versions, steps, a sanitized error category and a synthetic sample if necessary. Never attach real chats, credentials, hostnames, recordings, raw logs or screenshots without a separate review.
