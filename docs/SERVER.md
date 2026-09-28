# Operate your own compatible backend

The APK is a client, not a server installer or account invitation. The recipient must operate a compatible backend or have explicit authorization from its operator. Installing it does not grant access to the original deployment, its profiles, files, model subscriptions or private network.

## Deployment checklist

1. Install Hermes Agent on a machine you administer using the official installation documentation referenced in PROVENANCE.md. Linux is a suitable backend host; a Windows desktop client is a separate client, not required to power Android.
2. Configure your model/provider and permitted tool execution on that host. Use the normal `hermes setup` and diagnostics workflow for your installed version. Keep model keys and identity-provider secrets on the server, outside version control.
3. Configure the HTTP backend/dashboard gateway that serves the desktop API, **not merely a messaging bot or an OpenAI-compatible `/v1` proxy**. Confirm native auth and route compatibility below. Upstream evolves; a working CLI chat alone does not establish this API contract.
4. Configure an authentication provider. The tested native flow uses the basic username/password provider. Current official dashboard documentation describes an interactive setup prompt when binding non-loopback without an existing provider; unattended deployment requires preconfiguration and fails closed. Follow the documentation for your installed server version; do not copy someone else's config or invent credentials.
5. Enroll server and phone into a private network you control, allow only the needed users/devices/port, and configure DNS/certificate handling. Prefer HTTPS, with a private-network-facing endpoint or HTTPS proxy that preserves authentication and WebSocket upgrades.
6. Create/authorize the desired backend profiles and enforce permissions on the server. The app's profile parameter is routing context, not a substitute for server authorization.
7. Enable compatible STT/TTS services if voice is required. A backend provider may send audio/text to cloud services: private transport from the phone does not imply local inference or local transcription.
8. From an authorized client, verify browser login, a small streamed reply, the expected profile/history, a synthetic download, transcription and a fresh Read Aloud reply before relying on it.

## Launch-command reference, not a one-command deployment

Official dashboard docs list `--host`, `--port`, and `--no-open`. A loopback-only local start is:

```sh
hermes dashboard --host 127.0.0.1 --port 9119 --no-open
```

A phone cannot reach the server's loopback directly, and the Android endpoint validator rejects loopback URLs. For deployment, either put an authenticated HTTPS private-network proxy in front of that listener or bind to **your own** Tailscale interface after reviewing authentication/firewall settings:

```sh
hermes dashboard --host "$YOUR_TAILSCALE_IP" --port "$YOUR_GATEWAY_PORT" --no-open
```

These environment variables are placeholders that the operator must set; no actual address is supplied. Do not use `0.0.0.0` or disable auth just to make connectivity work. These commands are verified against current upstream docs, not a tested full deployment recipe for every server version; confirm `hermes dashboard --help` locally. The proxy must not redirect authenticated API calls to a different endpoint: native transport refuses redirects.

## Required compatibility contract

| API/behavior | Used for |
| --- | --- |
| `/api/status`, `auth_flows` including `native_pkce` | Native-login capability discovery |
| `/auth/native/authorize`, `/auth/native/token`, `/auth/native/refresh` | Browser/PKCE broker and rotated native credentials |
| `/api/auth/me`, `/api/auth/ws-ticket` | Identity check and single-use socket tickets |
| `/api/ws` and the renderer's profile/session APIs | Shared JSON-RPC chat and history |
| `/api/fs/download?path=...&profile=...` with optional `session_id` | Binary authenticated download and inline media |
| `/api/audio/speak-stream`, `/api/audio/speak`, compatible transcription API | Speech, depending on selected mode/provider |

The server must accept the broker's literal HTTP loopback callback for the phone browser. This callback points to the **phone's native listener**, not to the remote server. Do not configure arbitrary custom URI redirects to compensate for an incompatible server.

Other retained settings, tools, bot-room or filesystem UI may need additional server APIs and privileges. Compatibility with those surfaces is not certified by the table. Native desktop features, arbitrary remote preview pages and a multi-endpoint connection registry do not become available just by enabling a server setting.
