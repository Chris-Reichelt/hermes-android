# Publication scope, audit and review gates

## Minimal publication proposal

Publish **only this documentation bundle**, after owner review, to the intended public `hermes-android` repository. There is no Git history, remote, push, release upload or account identity in this staging tree. GitHub authentication and final publication are outside this task. A repository name is not a license decision or binary-publication approval.

`publication-manifest.json` is the exact file allowlist with SHA-256 values for every payload file except the manifest itself. `scripts/verify_public_bundle.py` checks allowlist equality, symlinks, hashes, local Markdown links and high-signal leak patterns. Run it from this directory:

```sh
python3 scripts/verify_public_bundle.py
```

The `.gitignore` is an allowlist-style guard, not a security scanner. Start a **new repository from reviewed staged files**, not by publishing an existing home directory, development tree or Git history. The maintainer should explicitly review the manifest, diff and staged file list; no `git add .` against an unreviewed development tree.

## Included

- README plus architecture, server, install/use, build, security, limitations, provenance and publication documents.
- Sanitized v0.3.4 release identity and acceptance/evidence metadata.
- Sanitized audit counts/results and the explicit include/exclude manifest.
- Upstream MIT notice with scope explained; no unapproved downstream license assignment.
- A small local bundle-verification script and restrictive `.gitignore`.

## Explicitly excluded

| Category | Reason |
| --- | --- |
| Entire Android source checkout, copied renderer/shared source, tests and build scripts | Not yet a reviewed portable release; private paths, identities/network examples and fixture dependencies require classification/sanitization |
| APKs, generated renderer/assets and native build output | Preliminary scan is not complete binary/artwork/license or production-hardening clearance |
| Original internal README/handoffs/parity reports, raw validation JSON/logs | Contain private paths, deployment context and test/session metadata |
| Real/private MP3 recordings or other input media | User/test content; no redistribution assumed |
| Screenshots, UI dumps, videos, crash dumps, emulator state | Potential chats, endpoints, personal information and device identifiers |
| Signing keystores/cert private keys, passwords, tokens, auth databases | Secret material; never publish to solve build reproducibility |
| Home/profile/config directories, environment files, session/state stores, memories | Operational/private data, not application source |
| `node_modules`, SDK/toolchain/AVD/Gradle caches, local.properties, machine paths | Nonportable, large, may contain keys/state or upstream license obligations |
| Existing repository history, remotes, GitHub login metadata | Publication/account identity must be reviewed separately |

## Audit performed

The reviewer inspected native setup/auth/endpoint/download/media code, Android manifest/Gradle files, bridge/media/phone behavior, renderer/package/build configuration, provenance and versioned handoffs. The current source is v0.3.4; later audio acceptance was incorporated separately as user confirmation.

A bounded recursive candidate-text scan covered **3229 files**, excluding dependency caches, generated output, artifact and asset directories. It searched for home paths, private addresses/tailnet hostnames, credential URLs, common token/private-key formats and known private identity terms. Findings are candidate matches, not an assertion all are real secrets: upstream tests contain deliberately fake addresses/credentials and common names overlap ordinary words. The unreviewed source is excluded instead of being declared clean after a simple find/replace.

The delivered v0.3.4 APK was hashed, metadata/signature-verified and all **1098 uncompressed ZIP members** scanned for the same patterns. All **1051 packaged renderer assets** matched the completed renderer output. The scan found an example tailnet hostname and the documented address-range constant; token/credential-URL hits inspected were syntax-highlighting/localization false positives. No confirmed live gateway endpoint, personal home path or credential was identified by those checks. No `.mp3`, `.wav`, `.keystore`, `.jks`, `.env`, `.log` or `.map` member was found by extension. Names such as session code modules are not conversation logs.

**This is not complete APK clearance.** Regex/ASCII checks do not prove absence of every encoded secret, PII string, hidden payload, image detail, telemetry behavior or third-party license obligation. Binary art was not visually cleared. The private test recording resides outside the APK but is hard-coded in current tests. Keep source and binary excluded until their separate gates are complete.

The new public bundle is prose/metadata only, created from scratch rather than copying internal documents. Final automated allowlist/hash/link/leak validation is recorded in the review result. `audit-report.json` contains sanitized counts and scope, never raw private matches.

## Source-publication blockers

1. The checkout is a modified upstream working-tree snapshot without an independent frozen release commit or complete downstream diff.
2. Root dependencies rely on an external `node_modules` symlink; no portable root workspace/lockfile is present. Build/auth integration assumes local toolchain/backend paths.
3. Both browser and native media tests reference a private test clip. Replace with an intentionally redistributable sample and remove operational path/profile identifiers.
4. Candidate source includes private-path/network/name matches mixed with upstream fixtures; each proposed source file must be reviewed. Do not export Electron-only files just because they happen to be in the copied desktop directory.
5. Restore upstream notices and resolve downstream/asset/transitive licensing and branding. The docs' license also needs publisher confirmation.
6. Release-signing/hardening and clean-machine build/device validation are separate from successful preview use.

No source subset is staged as “buildable” because the dependency/source closure has not been established. A few isolated Java files without their tested renderer/build/test context would not satisfy reproducibility.

## Parent/maintainer publication checklist

- [ ] Read every included document; confirm accepted scope and selected documentation license.
- [ ] Run the bundle validator and compare the manifest to the actual staged repository files.
- [ ] Confirm account/repository and public visibility through the intended secure GitHub flow.
- [ ] Upload only reviewed allowlisted docs; verify the remote tree after writing.
- [ ] Keep source/APK release attachments disabled until their blockers are resolved.
- [ ] If later including a binary, scan the exact final hash again, publish required notices and signing/update policy, and keep all private signing material and private media out.

Do not include this development machine's original directories as attachments. No real backend endpoint or account identifier is needed in a public issue, README, commit message or screenshot.
