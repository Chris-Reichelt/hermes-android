# Immediate Android download failure: findings and diagnostic patch

## What is established

- The native file path uses `download.file` -> `/api/fs/download` for both APK and TXT. There is no APK-specific native transport branch. The picker uses `application/octet-stream` for both; file response MIME is not required to be an image.
- `MainActivity` extends `Activity`, not `Fragment`. A detached fragment is not a direct failure path here. Picker launch and finishing/destroyed activity errors are now separate from input errors.
- A normal gateway 403/404 is raised as `GatewayAuth.AuthException`, not the old generic storage/connection error. This remains an HTTP-status diagnostic without a response body or private path.
- If the *full old message* “Could not save download. Check connection, available storage ...” was displayed (not merely the renderer's “Download failed” title), it came from the worker **after a successful document-picker result**. Immediate failures can occur at temporary-file creation, request execution, reading/staging response bytes, or opening/writing the document provider. Timing alone cannot distinguish them.
- APK MIME/size did not reproduce the reported phone failure: both exact supplied APKs pass production `GatewayDownload` and `GatewaySave` through an isolated loopback server, with SHA-256 equality after destination copy. No timeout change was made; the previous minute-long timeout experiment is not evidence for an immediate failure.

## Patch

`DownloadFailure` builds bounded enum-based errors; it never includes exception messages, URLs, paths, payloads or headers. Known HTTP statuses are retained. `GatewaySave` extracts the existing stage-then-copy pipeline so its real failure boundaries can be regression tested. `MainActivity` invokes this pipeline and classifies picker/worker boundaries. Strict JSON string checks refuse malformed fields rather than stringifying objects into file paths or scope parameters; valid path bytes and owner/session values stay unchanged.

| Code | Boundary |
|---|---|
| DL_INPUT | Payload validation and owner policy, before picker |
| DL_BUSY | Existing save still pending |
| DL_PICKER_LAUNCH | Native Save dialog launch / unusable Activity |
| DL_PICKER_RESULT | Non-cancel result lacks a usable content document |
| DL_WORKER | Task submission/unclassified worker setup |
| DL_STAGING | Temporary-file create/open/finalize |
| DL_REQUEST | Gateway request before response-body receipt |
| DL_RECEIVE | Reading the response into staging, including staging writes |
| DL_SESSION | Session changed before opening destination |
| DL_STAGED_READ | Reopen completed temporary file |
| DL_DESTINATION_OPEN | Document provider open / null output stream / permission denial |
| DL_DESTINATION_COPY | Destination writes and close/finalization |

Security invariants unchanged: redirects refused, same endpoint/auth scope retained, owner/session not retargeted, limits unchanged, content URI required, staging precedes destination open, no broad storage/install permission, no raw exception diagnostics. Cancellation remains quiet.

## Verified without shared builds

`tests/run-download-native-isolated.py` compiles into TMPDIR and runs:

- 21 passing JVM tests: existing download route/auth/redirect/bounds tests plus malformed JSON, absent null session, immediate truncation, staging failure, request failure, destination permission/null/write/close failures, vanished staging file, session changes, exact binary copy, cleanup, and non-sensitive error text.
- Exact original APK: **19,900,837 bytes**, `application/vnd.android.package-archive`, SHA-256 equal after real native pipeline.
- Exact public preview APK: **13,008,331 bytes**, same MIME, SHA-256 equal.
- TXT comparison: **31 bytes**, `text/plain`, SHA-256 equal.
- All native Java sources including Activity wiring compile against Android SDK 35 (only existing deprecated-API note).

`tests/download-browser.mjs` passes using existing renderer assets and a synthetic bridge: artifact button calls `download.file`, owner/path preserved, cancel quiet, success accepted, and the **DL_DESTINATION_OPEN diagnostic is visibly rendered in the error toast**. This test does not claim actual native picker execution.

Evidence is in codex scratch `android-download-investigation/stage-native-tests.txt` and `stage-renderer-receipt.json`.

## Remaining

No phone-specific root cause is proven. No phone SAF run, Gradle/APK build, version/signing edit, production access/modification, or publication was performed. After other work is consolidated, the parent should build/test/package the chosen version and run the existing Android picker instrumentation against that final build. Do not characterize this as a confirmed fix for the phone; it replaces an unhelpful catch-all with actionable stage diagnostics and adds tested input hardening.
