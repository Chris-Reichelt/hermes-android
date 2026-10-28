# Android pasted-link editing fix — 0.3.38-apk50

## Diagnosis and changes

The Android native paste branch left insertion to WebView, but `handleEditorInput` immediately serialized and repainted that live text as a non-editable URL reference chip, moving the caret. This conflicts with native IME ownership of the same editable text/selection. Previous chip-deletion workarounds did not establish phone recovery.

Android now leaves pasted links as native editable text. Removed input-time URL chipping, timed URL-tail folding and debug alerts; disabled typed-space URL chipping on Android. Desktop rich-paste behavior and explicit directive chips are retained. Restored older `@url:` directive drafts are not migrated to plain URLs by this change.

Assembly initially succeeded but lint failed on two existing literal `getSystemService("audio")` calls. Both now use the equal-valued `Context.AUDIO_SERVICE`; no lint suppression was added. Assembly, unit tests and lint then passed. Initial failed output is retained in `artifacts/link-editing-apk50-build.log`; rerun in `artifacts/link-editing-apk50-build-rerun.log`.

## Deliverable

- APK: `artifacts/hermes-android-0.3.38-apk50-public-preview.apk`
- Package: `com.hermes.privateapp.publicpreview`
- Version code: 50
- Same signing certificate as the previous apk49 installer; verification receipt contains fingerprint and APK SHA-256.
- Intended as an update to that public-preview track only, not the separately signed private package. No public upload or device install was performed.

## Executed checks

- 105 composer tests across seven files passed, including native text-node/selection preservation, split composition input, non-cancelable delete handling and Android typed-space behavior; desktop paste regressions retained.
- 50 Android adapter/shared tests passed across eight files.
- Production renderer built.
- Gradle assembleDebug, testDebugUnitTest and lintDebug passed; lint warnings retained.
- Isolated real auth fixture passed. Final JUnit XML: 38 tests, zero failures/errors/skips.
- APK signature verified; package/version and signing identity compared with apk49.
- All 1061 packaged renderer assets match the tested dist byte-for-byte.
- Trusted Chromium clipboard paste tests against renderer extracted from the final APK passed: character backspace, further typing, select-all delete, selection replacement preserving surrounding text, navigation rerender retention and multiple multiline links. No prompt submissions, renderer clipboard-read RPCs or page errors.
- Same browser harness against extracted apk49 fails the plain-editable-link criterion because paste becomes a chip. This is a renderer mechanism regression control, not reproduction of the phone's keyboard failure.

## Limits and phone acceptance

No Android device/emulator was connected at build-time verification (`adb devices` empty). Chromium keyboard tests and jsdom composition fixtures are not actual Android IME tests. After installing the delivered update, Chris confirmed on October 28, 2026 (UTC conversation follow-up): “sweet i can paste and delete links now.” Paste/delete recovery is therefore user-confirmed on his phone, not instrumented Android coverage. This does not establish every keyboard, old directive draft, or unrelated capability works. The original machine receipt retains `physicalDeviceVerified: false` to preserve its build-time automation scope.

Install over the existing matching public-preview app; Android should offer Update. Stop if it offers a separate app installation. In a fresh/empty composer, paste a link, delete characters using the phone keyboard, then select all and delete. The link should remain visible as ordinary text rather than a compact chip. Do not send a test prompt.

## Re-run

```
NODE_OPTIONS=--no-experimental-webstorage node node_modules/vitest/vitest.mjs run --config vitest.link-editing.config.ts
python3 tests/validate-link-editing-artifact.py
RENDERER_ROOT="$TMPDIR/link-editing-apk50-renderer" node tests/link-editing-browser.mjs
```

Machine receipts: `artifacts/link-editing-apk50-verification.json`, `artifacts/link-editing-apk50-packaged-browser.log`, `artifacts/link-editing-unit.log`, `artifacts/link-editing-android-unit.log`, `artifacts/link-editing-auth.log`.
