# Security and privacy boundaries

## What is protected by the implementation

- Password entry is in the system browser on the gateway's own login page. Native S256 PKCE, random state/callback path and loopback callback validation protect the app's authorization exchange.
- Access/refresh tokens remain in native process memory; they are not intentionally saved in preferences or exposed as renderer connection tokens. Native code mints fresh socket tickets and performs authenticated transport.
- The main-frame/origin-scoped message bridge and same-endpoint request checks reject cross-connection retargeting. Redirects are refused rather than forwarding credentials.
- APK assets have a local secure WebView origin. File/content URL access, mixed content, remote navigation and off-origin WebView HTTP access are restricted. WebView debugging is disabled.
- Android backup is disabled, and `FLAG_SECURE` requests screenshot/recents protection for the app. This does not protect the external browser or guarantee protection on a compromised device.
- Downloads use the system document picker instead of broad filesystem permission. External HTTPS image fetching uses a separate client without gateway credentials/cookies. Audio bytes cross the bridge; bearer tokens do not.

These controls are implementation observations, not independent penetration-test certification.

## What is not guaranteed

**This APK is debug-signed and Android-debuggable.** Disabling WebView debugging is not the same as a non-debuggable release. Do not treat this as hardened public-distribution software. The signing keystore must remain private; published certificate fingerprints are not private keys.

The endpoint policy accepts `.ts.net` hosts or addresses in a defined private-network range; it does not verify network enrollment, ACL correctness or server ownership. Cleartext traffic is permitted by the manifest for the preview's HTTP private-endpoint mode. Prefer valid HTTPS and least-privilege networking; do not rely on a hostname suffix as authentication.

The trusted renderer can request powerful authenticated APIs. Bundled UI integrity, WebView security, server authorization and backend tool permissions are part of the trust boundary. A private network does not sandbox server tools or make arbitrary agent actions safe.

Memory-only native credentials do **not** mean the device is data-free. Renderer DOM/local storage can retain preferences or other UI state; browser cookies and browser password-manager state belong to the system browser; saved downloads persist where chosen. This task did not produce a full storage-retention inventory or prove the absence of every cached transcript. Do not promise zero persistence.

Disconnect clears local native credentials, not necessarily server-side sessions/tokens or browser cookies. Logout/revocation behavior is provider-specific. The preview has no implemented encrypted native credential persistence.

Microphone audio and generated speech may pass to the backend and its configured external providers. Model calls and tools can transfer data beyond the private network. Review the operator's provider choices, retention, tool permissions and costs. There is no claim that all traffic is local or that the app is a no-telemetry-certified product.

## Distribution hardening checklist

- Complete review of source, third-party assets/licenses and release provenance; produce a dependency inventory/SBOM.
- Create intentional release signing with securely managed private keys, non-debuggable builds and an update/migration policy. Do not publish keystores or key passwords.
- Review manifest/exported components, cleartext/network policy, bridge surface, lifecycle races and renderer content handling.
- Test auth expiry, disconnect, process death, callback cancel, network loss, stale-scope responses and download/media limits on actual Android devices.
- Complete storage/privacy review, accessibility and background-behavior validation; document a private vulnerability reporting channel before inviting sensitive reports.
- Never publish server configs, profile state, credential stores, logs, session databases, user recordings or actual operational endpoint values.

Until a reviewed private reporting channel exists, report only a minimal non-sensitive issue publicly and ask the maintainer how to transfer details securely. Do not include live tokens to demonstrate an auth bug.
