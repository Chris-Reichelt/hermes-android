# Provenance, licensing and dependencies

## What this bundle is

Newly written, sanitized documentation for a downstream Android preview. It summarizes inspected implementation and versioned validation records; it does **not** include the internal records, raw logs, source snapshot, test recording or APK. No account names, operational endpoints, private host paths or original session IDs are needed to understand this design.

The inspected `SOURCE.json` identifies upstream Hermes Agent commit:

```text
96aa40a49f8c8725eb20c5a710c3937a54182bbb
```

The snapshot explicitly includes working-tree customizations. That hash is an upstream provenance anchor, **not a complete commit identifying every Android change**. The isolated app directory has no Git history of its own. A future source release must inventory downstream changes and freeze a reproducible source revision; it must not invent an upstream-only pedigree for modified files.

## Evidence hierarchy

1. APK metadata/signature/hash and packaged-renderer comparison independently rerun for the documentation review.
2. Inspected native/renderer/build source and the completed v0.3.4 release handoff.
3. Recorded isolated tests/build receipts, clearly labeled as prior execution rather than rerun here.
4. User-reported Android success for downloads, microphone/transcription, device-local automatic Read Aloud, and attachment audio after v0.3.4. Those are acceptance observations, not instrumented multi-device tests.
5. Official public documentation for framework/server behavior. Upstream capabilities do not automatically become supported Android features.

Earlier overview/parity notes contained stale version numbers and limitations. The documentation reconciles them with later versioned handoffs and user confirmation rather than copying them verbatim. The actual v0.3.4 APK, not a mutable unversioned alias or root package.json's historical version field, defines this release.

## Licensing status

Upstream Hermes Agent's license at the identified commit is MIT, copyright Nous Research. Its exact notice is preserved in `LICENSES/Hermes-Agent-MIT.txt` for attribution/reference. That file applies to the upstream material and is **not a blanket license grant for every downstream contribution, this new documentation, artwork, or dependency**.

The isolated snapshot did not carry top-level/app/shared LICENSE/NOTICE files in the inspected locations. A source export must restore required notices and preserve relevant file headers. The publisher still needs to select/confirm a license for downstream work and these docs, establish contributor rights, and review branding. Do not label a future repository “fully MIT” based solely on one upstream license. Publishing docs without a chosen reuse license also does not grant an unrestricted reuse license.

This documentation does not suggest official endorsement by Nous Research. The app/package name and upstream project name describe implementation provenance; logos, sprites, fonts, emoji datasets and other art require their own attribution/redistribution review before binary/source release.

## Dependency considerations

Observed native dependencies include AndroidX WebKit and OkHttp; tests use JUnit and org.json. The renderer uses React, local shared Hermes modules and a broader dependency graph including editor, Markdown/math, syntax highlighting, UI components, fonts and emoji data. Declared selected versions are in BUILD.md. These declarations are not a complete SBOM or proof of shipped runtime composition.

Before publishing source or APK:

- Resolve and lock the complete portable JavaScript workspace, not a symlink to a private installed tree.
- Produce a native/JS dependency and asset inventory from the frozen build. Record versions, source locations, applicable licenses and notices.
- Separate Electron-only/build-time/test packages from shipped Android runtime content; do not redistribute `node_modules`, SDK, emulator images, Gradle caches or arbitrary package-manager caches.
- Review transitive licenses and required notices, bundled fonts/art/emoji rights, and app branding.
- Replace private audio fixtures with generated/authored redistributable samples. Do not publish a private clip merely because the APK playback test used it.
- Preserve original copyright/notice text; ask the publisher to resolve downstream licensing rather than assigning it silently.

## Public primary references consulted

- Official docs index: https://hermes-agent.nousresearch.com/docs/llms.txt
- Hermes source: https://github.com/NousResearch/hermes-agent
- Upstream license at the recorded commit: https://raw.githubusercontent.com/NousResearch/hermes-agent/96aa40a49f8c8725eb20c5a710c3937a54182bbb/LICENSE
- Installation: https://hermes-agent.nousresearch.com/docs/getting-started/installation
- Dashboard/server options and authentication: https://hermes-agent.nousresearch.com/docs/user-guide/features/web-dashboard
- Native broker protocol: https://hermes-agent.nousresearch.com/docs/guides/desktop-native-signin
- Android packaged WebView content: https://developer.android.com/develop/ui/views/layout/webapps/load-local-content
- Android signing/update identity: https://developer.android.com/studio/publish/app-signing

The live docs index, dashboard/native sign-in pages, commit-pinned license and Android local-content/signing pages were retrieved during this review. Installation is linked as the upstream entry point, not asserted as a tested fresh deployment here. Android-specific behavior is established from the inspected port, not by copying Desktop documentation's token persistence or fallback behavior.
