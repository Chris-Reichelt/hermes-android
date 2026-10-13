# Android chat downloads — 0.3.3 / versionCode 6

## Deliverable

`/home/will/hermes-android/artifacts/hermes-android-0.3.3-debug.apk`

SHA256: `e41e7e9414efd015de2df2a20fb4d5aaeefc1a5edce327ac053f6fbb87065e81`

Application ID `com.hermes.privateapp`, same signing certificate as 0.3.2 (certificate SHA256 `15e9b474b521c81c642fe7bd4a7c0a6df2f99f8ac4c1a3a8ad5085f3735a4411`). Install as an update, not by uninstalling. The original versioned 0.3.2 APK retains its recorded SHA256. The workspace is a source snapshot, not a Git repository; baseline copies of the main changed files are under `artifacts/download-baseline-0.3.2/`.

## Implemented

- Removed the unsupported assignments for `saveGatewayFile` and `saveImageFromUrl`; added native `download.file` and `download.image` messages. Other unrelated unsupported desktop features remain unchanged.
- Inspected the actual existing desktop and backend download contracts. Native file saves use `/api/fs/download?path=…&profile=…&session_id=…`; path is transported literally through query encoding, including file URIs. The server remains responsible for gateway filesystem resolution and authorization. No guessed endpoint or production backend modification.
- Freeze connection/auth/profile/session when starting the save. Reject unknown connection owners instead of retargeting. Use existing native `GatewayAuth.request`, including bearer refresh and authenticated 401 retry. The auth implementation itself is unchanged.
- Android Storage Access Framework `ACTION_CREATE_DOCUMENT` lets the user choose the destination. No broad storage permission or credential-bearing browser URL. User cancellation resolves quietly (`{saved:false,canceled:true}`; images return `false`). Only one save at a time; picker expires after five minutes.
- Download streams to private temporary storage, then copies to the chosen document. File bytes capped at 256 MiB, images at 16 MiB, 32 KiB copy buffers; native HTTP call retains its 60-second bound. Declared and streamed sizes are checked. Normal failures delete staging and attempt to delete the incomplete destination; successful result occurs only after the destination stream closes.
- Image saves handle base64 image data URLs, renderer-local blob conversion, approved same-gateway image routes through native auth, and external HTTPS images via a separate credential/cookie-free client. All redirects are refused; no bearer follows a redirect. A same-gateway image URL's explicit profile is retained.
- Existing microphone permissions/handlers, endpoint policy, WebView network restrictions, phone shell, and 0.3.2 audio fixes are preserved.

## Verified receipts

- Full existing `bash build.sh` passed renderer build, adapter-only TypeScript, **48 JS Android/shared/microphone tests**, **26 speech unit tests**, phone UI browser controls at 360/393/412 px, both speech browser fixtures, Android assembly/lint, and isolated real-auth component tests.
- Extended real-auth run: `artifacts/download-auth-integration.log`; **21 Java tests, zero failures/errors/skips**, including **8 new download tests** and an added authenticated download/401-refresh test using production auth routes/middleware/provider plus synthetic artifact bytes. Checks native bearer headers, binary preservation, query encoding, owner/session, wrong-owner refusal, redirects, 404, oversize, stream cancellation, data-image bytes and filename safety. No real account or file access.
- Production-renderer chat Download button exercised in Chromium, not substituted with direct bridge invocation: `artifacts/download-renderer-receipt.json`. Synthetic native responder confirms owner/path request, quiet cancellation, accepted success, surfaced failure, no page errors. Browser fixture is not Android storage proof.
- `artifacts/download-artifact-validation.json`: **1051 packaged renderer assets byte-match** tested dist; signature verified; package/version/permissions checked; native download class and CREATE_DOCUMENT action present; instrumentation not packaged. Previous APK preserved.
- Exact final APK installed on explicit software emulator `emulator-5580`; installed `base.apk` pulled and SHA256 matched final artifact: `artifacts/download-installed-validation.json`.

## Emulator boundary — NOT a completed save proof

The emulator has no usable KVM. Boot encountered `DeadSystemException`/`DeadObjectException` and repeated System UI ANRs. APK and separate instrumentation installed successfully. Instrumentation reached **“Native file picker starting” and “Save tapped”**, recorded in `artifacts/download-emulator-receipt.txt`, but did **not** reach completed destination-byte verification before a bounded foreground timeout. An earlier background instrumentation attempt was explicitly terminated (exit 143); that is not a pass. Subsequent foreground attempts also timed out. Therefore **native end-to-end download/cancel/image storage is not claimed verified on emulator or phone**. The separate test runner is retained for a usable Android runtime. Emulator and instrumentation were stopped afterward.

## Remaining limits

- Real phone confirmation is still needed: tap a chat attachment's Download, choose a destination, then open/check the saved file. No user prompt or private artifact was used for testing.
- Requires the existing streaming `/api/fs/download` backend route. Old backends without it return an error; no JSON/base64 fallback for large files.
- No background/resumable transfer; keep app alive. Abrupt OS process death can leave a partial/empty provider document or private staging file; normal error/cancellation cleanup is implemented.
- External image redirects and HTTP image URLs are deliberately refused. Base64 image data URLs supported; arbitrary non-base64 SVG data URLs are not. Public extensionless image URLs may need a filename extension chosen in the picker.
- The isolated bridge test file retains pre-existing `ws` declaration/type warnings in ad-hoc per-file tsc; production adapter typecheck and configured test suites pass.

## Source/test changes

- `apps/desktop/src/android/bridge.ts`
- `android/app/src/main/java/com/hermes/privateapp/MainActivity.java`
- New `android/app/src/main/java/com/hermes/privateapp/GatewayDownload.java`
- `android/app/build.gradle` (version only)
- `tests/bridge.test.ts`, `tests/run-real-auth.py`, `build.sh`
- New `GatewayDownloadTest.java`; augmented `GatewayAuthTest.java`
- New `tests/download-browser.mjs`, `tests/validate-download-artifact.py`
- Existing androidTest manifest retains MicrophoneProbe and adds separately packaged `DownloadInstrumentation.java`.

No production backend, other profile data, live authentication credentials, or real chats were modified or accessed.
