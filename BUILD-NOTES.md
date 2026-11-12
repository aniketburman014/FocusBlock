# Personal build for Galaxy S25

Source base: `5509a35c707894fe16ba1445d515a5e4ce5d35c3`.

This checkout is separate from `/Users/aniket.burman/FocusBlock`. Accessibility behavior has been hardened as described below. It targets Instagram Reels, while retaining Feed and Explore. It requires Android 8.0 or later; the S25 meets that minimum, but detection still requires device testing.

## Prepared changes

- Fixed the Unix wrapper's missing APP_HOME and made the script executable.
- Updated Android Gradle Plugin to 8.6.1, which supports compile SDK 35 with Gradle 8.7 and JDK 17. Removed the old unsupported-SDK warning suppression.
- Removed debug signing from release builds. Optional personal signing uses environment variables; otherwise release output is unsigned.
- Ignored common private signing-file names.
- Removed AndroidX's exported ProfileInstallReceiver during manifest merging. This external profiling/benchmark entry point is not needed for FocusBlock; AndroidX's in-process initializer remains. The APK checker still permits only the launcher activity and protected Accessibility service to be exported.
- Suppressed MissingClass only on that receiver's removal marker, which lint otherwise treats as a live class declaration. No global lint baseline or lint disabling was added. Failed CI builds now print the lint report and include its text in the reports artifact.
- Scoped Accessibility events to the six supported packages, removed click-event subscriptions, and required matching visible, active, focused application windows before detection or actions.
- Removed cached activity names and event-label shortcuts from detection. Hidden and foreign nodes cannot identify blocked content. Navigation labels are inspected only for YouTube's shared navigation resource ID; Instagram detection uses IDs and selected state.
- Replaced broad text/ancestor clicks with exact Instagram navigation IDs and visibility/enabled/window checks. Other platforms, or missing Instagram IDs, use guarded Back; Back may leave the app if its navigation stack has no previous page.
- Removed automatic post-action retries, limited actions per observed blocked episode, and cancelled queued work on pause, interruption, unbind, or teardown. No Accessibility event or node is retained for delayed work.
- Added six JVM guard tests and six Android detector tests. They have not run because Java/Android tools are unavailable.

## Build prerequisites

Install JDK 17 and Android SDK platform 35, platform-tools, and compatible build tools (AGP 8.6 defaults to Build Tools 34.0.0). Configure JAVA_HOME and ANDROID_HOME. The first build also needs access to Google's Maven repository, Maven Central, and the Gradle distribution server.

The local build attempt is blocked: no usable Java runtime or SDK was found, and DNS requests for required download hosts failed. No APK or signing key has been produced. The wrapper JAR now matches Gradle's published 8.7 SHA-256 (`cb0da6751c2b753a16ac168bb354870ebb1e162e9083f116729cec9c781156b8`), and the distribution checksum is pinned in wrapper properties.

## Build with GitHub Actions

The workflow `.github/workflows/android-apk.yml` runs on pushes to `main` or `codex/focusblock-accessibility`, pull requests, and manual dispatch. It uses the hosted Ubuntu 24.04 JDK 17 and installs Android SDK 35 and Build Tools 34.0.0. No repository signing secrets are needed for this development build.

It verifies the wrapper, runs six Python tests for the final-manifest checker, runs JVM tests and Android lint, and compiles both the application and Android device tests. The six Android detector tests are compiled only; they still require execution on a device. It then checks the APK's merged manifest for unexpected permissions/exported components and verifies its signature.

After a successful run, open your fork's **Actions → Build reviewed FocusBlock APK → successful run → Artifacts → FocusBlock-test-apk**. Unzip it to get the APK, APK SHA-256, source commit, manifest, and signing certificate details. Artifacts are retained for seven days. If Actions is disabled on your fork, enable it and push again or run the workflow from its branch once available.

This is a **debug-signed development APK**, not a personally signed release. Fresh runners may produce different signing keys, requiring uninstalling an earlier build before installing a subsequent one. No signing keys are uploaded with the artifact. Use personal release signing for a lasting installation.

## Development test build

```sh
./gradlew --no-daemon lintDebug assembleDebug
```

Expected output: `app/build/outputs/apk/debug/FocusBlock-debug.apk`. This is a development-key-signed test build, not a personally signed release.

## Personal release

Create and retain a private signing key outside the repository using Android Studio's signing workflow or JDK keytool. Keep its passwords outside shell history and source control. Supply:

- FOCUSBLOCK_KEYSTORE_PATH (absolute path)
- FOCUSBLOCK_STORE_PASSWORD
- FOCUSBLOCK_KEY_ALIAS
- FOCUSBLOCK_KEY_PASSWORD

Then run:

```sh
./gradlew --no-daemon lintRelease assembleRelease
```

The release filename is customized to `FocusBlock.apk`. Without signing variables it is unsigned and must not be presented as installable. Verify its actual signature before delivery; the filename is not evidence of signing.

## Checks before delivery

1. Authenticate the Gradle wrapper and pin the distribution checksum; resolve and review build dependencies.
2. Review any resolved transitive dependency advisories and the source diff.
3. Run Android lint and compile the chosen variant.
4. Inspect the final APK permissions with Android SDK tools. Confirm INTERNET, unexpected exported components, and unexpected permissions have not been introduced through manifest merging.
5. Verify the APK with `apksigner verify --verbose --print-certs` and record its SHA-256 using `shasum -a 256`.
6. Install on the S25 and test Reels, DMs, stories, normal feed, switching apps, and the protection toggle. Keep Play Protect enabled.
7. Run `./gradlew testDebugUnitTest` and, with a connected test device, `./gradlew connectedDebugAndroidTest`. Exercise app switching, notifications, system dialogs, split-screen, and pausing with work queued. Source fixes address the original review's event scope, stale state, and text-click issues; runtime validation remains outstanding. Accessibility actions cannot be atomic with another app's UI changes, and event package filtering is not a complete sandbox for Accessibility capabilities.

A source build and static permission check improve confidence. They do not establish that software is completely safe or replace runtime testing.
