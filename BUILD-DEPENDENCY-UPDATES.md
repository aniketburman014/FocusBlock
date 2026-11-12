# Build dependency remediation

The CI scan successfully inventoried 287 resolved artifacts. All findings in the supplied output were tagged `build-tools`. No runtime-, unit-test-, or device-test-scoped advisory appeared in that output. This means the findings concern the compiler/build pipeline; it does not establish complete APK safety or prove that any vulnerability was exploited.

## Prepared versions

| Component | Previous version | Prepared version |
| --- | --- | --- |
| Android Gradle Plugin | 8.6.1 | 8.13.2 |
| Gradle distribution | 8.7 | 8.13, published SHA-256 pinned |
| Kotlin Gradle plugin | 1.9.23 | 2.4.21 |
| D8/R8 | AGP bundled | 9.1.29, to support Kotlin 2.4 metadata |
| Guava | 32.0.1-jre | 33.7.2-jre |
| Protobuf Java family (3.x requests only) | 3.22.3 | 3.25.8 |
| Commons IO | 2.13.0 | 2.22.0 |
| Netty 4.1 family | 4.1.93.Final | 4.1.140.Final |
| Commons Compress | 1.21 | 1.28.0 |
| jose4j | 0.9.5 | 0.9.6 |
| Bouncy Castle jdk18on family | 1.77 | 1.86 |
| JDOM | 2.0.6 | 2.0.6.1 |

The transitive version rules are limited to the root buildscript's `classpath`. They do not force these build libraries into the app's runtime dependencies. Netty's tcnative modules retain their own version series rather than being assigned a 4.1 version. Protobuf 4.x requests, if introduced by a plugin, are not downgraded to 3.x. All resolved modules are still scanned, so any additional or remaining advisories block delivery for review.

Upgrading Kotlin also changes the compiler-selected Kotlin standard library version. AndroidX/Material runtime dependency declarations and application features were not changed. Instagram and YouTube support remain enabled. The new APK must be associated with its own source commit, dependency report, signature, and SHA-256; the earlier APK's hash does not apply.

D8/R8 is loaded early from `pluginManagement.buildscript` in settings, following the publisher's override instructions, and declared on the root buildscript classpath so the dependency inventory also scans it. CI checks the actually loaded `Version.getVersionString()` before inventorying dependencies; an unexpected compiler implementation fails the run.

## Validation status

The prepared versions were checked against publisher releases, advisories, and compatibility guidance. The Kotlin advisory lists versions below 2.4.20-Beta1 as affected; 2.4.21 is a stable release above that fixed version. The Gradle/AGP/Kotlin combination uses supported version ranges, with the documented D8/R8 override for Kotlin 2.4. JDK 17, Android platform 35, and Build Tools 35.0.0 are used.

Local workflow syntax and diff checks can run, but the new Gradle configuration and actual dependency resolution cannot be executed here because Java/Android tooling is unavailable. Transitive build-library substitutions can expose binary compatibility issues. **The upgrade is a remediation candidate until CI resolves the new graph, reports no applicable advisories, compiles the app, and passes tests/lint.** No vulnerability has been suppressed or accepted through an allowlist.

The APK permission/signature checks and OSV API failure handling remain enabled. The workflow uses a fresh hosted runner without shared Gradle build caches; an advisory finding is not evidence that that runner or the prior APK was compromised.

## References

- Kotlin compatibility: https://kotlinlang.org/docs/gradle-configure-project.html
- Kotlin build-cache advisory: https://github.com/advisories/GHSA-r937-wjx7-w2jp
- Kotlin stable releases: https://kotlinlang.org/docs/releases.html
- Android Gradle Plugin: https://developer.android.com/build/releases/agp-8-13-0-release-notes
- Kotlin/D8/R8 compatibility: https://developer.android.com/build/kotlin-support
- R8 build override: https://r8.googlesource.com/r8/+/refs/heads/main/README.md
- Gradle distribution checksum: https://gradle.org/release-checksums/
- Guava advisory: https://github.com/advisories/GHSA-xxph-c9ww-hj94
- Protobuf advisory: https://github.com/advisories/GHSA-735f-pc8j-v9w8
- Protobuf release: https://github.com/protocolbuffers/protobuf/releases/tag/v3.25.8
- Commons IO: https://commons.apache.org/proper/commons-io/changes.html
- Commons Compress: https://commons.apache.org/proper/commons-compress/changes.html
- Netty releases: https://netty.io/news/
- jose4j advisory: https://github.com/advisories/GHSA-3677-xxcr-wjqv
- Bouncy Castle releases: https://www.bouncycastle.org/download/bouncy-castle-java/
- JDOM advisory: https://github.com/advisories/GHSA-2363-cqg2-863c
