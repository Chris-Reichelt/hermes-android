# Build and validation notes

## Reproducibility status — read first

**This is a documentation-only staging bundle, not a buildable source release.** The inspected Android project is a working-tree snapshot, not its own Git repository. It reuses an external upstream `node_modules` installation through a symlink, lacks a self-contained root dependency lockfile/workspace manifest, and its auth tests import a separately installed Hermes backend. Build/test files contain machine-specific paths. The current media tests also read a private test recording. Copying this checkout or running a fresh `npm install` is not a supported public reproduction recipe.

The recorded v0.3.4 build succeeded in its prepared environment. The documentation task did not rebuild or change the active app checkout. Commands below are actual project build stages, sanitized to relative paths/placeholders. **They require the complete reviewed source and resolved prerequisites, neither supplied here.** Do not imply bit-for-bit reproduction of the signed APK: keys, dependency resolution and build environment matter.

## Inspected toolchain

| Input | Version / requirement in project |
| --- | --- |
| Build host | Linux/Bash workflow with Python 3; Windows native build not validated |
| Java | Build script selects JDK 21; Java source/target compatibility 17 |
| Gradle | 8.11.1 supplied separately under `toolchain/`; no portable wrapper shown |
| Android Gradle Plugin | 8.9.1 |
| Android SDK | Compile/target 35; build-tools 35.0.0; minimum API 26 |
| Native dependencies | AndroidX WebKit 1.12.1, OkHttp 4.12.0; JUnit 4.13.2 and org.json 20240303 for tests |
| Node engine declared by desktop package | `^22.22.0 || ^24.11.0 || >=26.0.0` |
| Selected declared JS tools | TypeScript 6.0.3, Vite 8.2.0, Vitest 4.1.10, Playwright test 1.62.1 |
| Renderer | React/React DOM 19.2.7 and local `apps/shared`, plus the desktop dependency graph |
| Integration-test backend | Compatible Hermes Python environment with the production auth routes/provider and fixture dependencies |

These are inspected declarations, not a freshly solved public dependency lock. JS dependencies include many desktop-only packages; separate build-time needs from what actually enters the Android bundle. Current media fixture dependencies must be parameterized and replaced before public use.

## Actual stages (from the prepared project)

Run only in a **disposable reviewed source copy**, never by overlaying files onto a live backend installation. Set your own `JAVA_HOME`, `ANDROID_HOME`, `ANDROID_USER_HOME`, and `GRADLE_USER_HOME`. SDK installation/licenses and Gradle provisioning must be completed first. Resolve dependencies from a pinned reviewed workspace rather than copying another person's `node_modules`.

```sh
mkdir -p artifacts
node node_modules/vitest/vitest.mjs run --config vitest.android.config.ts
node node_modules/typescript/bin/tsc --ignoreConfig --noEmit --skipLibCheck   --target es2022 --module esnext --moduleResolution bundler   --lib dom,es2022 --types node apps/desktop/src/android/bridge.ts
(cd apps/desktop && node ../../node_modules/vite/bin/vite.js build --configLoader runner)
node tests/phone-ui.mjs
node tests/download-browser.mjs
node tests/media-playback-browser.mjs
NODE_OPTIONS=--no-experimental-webstorage   node node_modules/vitest/vitest.mjs run --config vitest.speech.config.ts
node tests/auto-speech-browser.mjs
DIRECT=1 node tests/auto-speech-browser.mjs
```

The media command currently fails outside the private prepared environment until its private-clip dependency is replaced. `NODE_OPTIONS` addresses the environment-specific webstorage issue in the recorded speech run; choose a supported Node version and verify the flag there. The standalone adapter typecheck is **not a full desktop application typecheck**.

Next, the actual build script replaces `android/app/src/main/assets/` with the contents of `apps/desktop/dist/` using Python `shutil`. Do not merge stale renderer files. In the prepared project the following Gradle stage then runs:

```sh
(cd android && ../toolchain/gradle-8.11.1/bin/gradle --no-daemon   :app:assembleDebug :app:testDebugUnitTest :app:lintDebug)
```

Output: `android/app/build/outputs/apk/debug/app-debug.apk`. The project's top-level `bash build.sh` also runs the auth fixture and writes artifact reports, but **the current script is not portable**: it overrides local toolchain paths, creates/uses a private preview keystore, and hard-codes a backend Python path. Do not publish that script unchanged or include its keystore.

After replacing hard-coded backend imports/interpreter references with an explicitly supplied, compatible isolated checkout/environment, the integration stage has this shape:

```sh
PYTHONDONTWRITEBYTECODE=1 "$HERMES_PYTHON" tests/run-real-auth.py
```

`HERMES_PYTHON` is a future sanitized parameter, not a supported option in the unmodified script. The test mounts real auth components with disposable credentials on loopback; it must not load a production profile or contact a live account. This stage does not establish Android runtime playback.

## Inspect a candidate artifact

These commands are directly usable with your own SDK and candidate file:

```sh
"$ANDROID_HOME/build-tools/35.0.0/aapt" dump badging app-debug.apk
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose --print-certs app-debug.apk
sha256sum app-debug.apk
```

Inspect package ID, version, minimum/target SDK, permissions and debuggable state. Compare the signing certificate with the intended update chain. Compare every packaged `assets/` entry byte-for-byte with the tested renderer output; version text alone is insufficient. Do not attach raw reports until paths and sensitive contents are sanitized.

## Requirements before a reproducible public source release

1. Freeze the intended release source and record its relationship to upstream plus downstream changes; do not call an upstream commit alone the full Android source revision.
2. Assemble only needed reviewed native sources, renderer/shared sources, build configuration and tests. Parameterize machine paths and include a complete workspace manifest and lockfile.
3. Replace the private MP3 with an authored/generated redistributable fixture and update both browser/native tests. No real recordings, account state or operational endpoints.
4. Include upstream license/notices and inventory fonts, art, JS/native dependencies and fixture rights. Confirm license for downstream contributions and new docs.
5. Provide a documented SDK/JDK/Gradle/Node bootstrap and intentional test signing strategy, without distributing any private key.
6. Rebuild on a clean machine, run the suites, scan source and unpacked APK, compare packaged assets and verify artifact metadata/signature.
7. Install the final-hash APK on an authorized Android device and test native browser login, chat/history, microphone, Read Aloud, save/cancel and attachment Play/pause/seek/end. Separate fixture results from device results.

Until those gates are complete, publishing only these docs is the accurate minimal scope.
