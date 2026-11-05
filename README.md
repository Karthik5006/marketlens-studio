# MarketLens Studio

A separate Android investment-research app for Indian stocks, fund disclosures, IPOs and private portfolio records. Native Kotlin/Jetpack Compose UI, ViewModel/StateFlow, source repositories, Room and optional WorkManager alerts.

- Production app: `app.marketlens.studio`, version 1.0.0; Android 10+; target API 36.
- Debug app: `app.marketlens.studio.debug`.
- Original `app.marketlens` is a separate app and is not replaced. Transfer older investor records only through a file you choose.
- Publisher: karthik; support: ykn5006@gmail.com.

See [features and coverage](docs/FEATURES.md), [architecture](ARCHITECTURE.md), [privacy](play-store/privacy-policy.html) and [Play submission preparation](play-store/RELEASE-READINESS.md). Financial records are local and manual; source dates and coverage remain visible. The app does not place orders or provide personalized recommendations.

## Build and test

Use JDK 17+, Android SDK platform 36 and Build Tools 35.0.0. Set `sdk.dir` in a local `local.properties` or use the standard Android SDK environment.

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
./gradlew :app:assembleRelease :app:bundleRelease
```

The workspace helper `bash scripts/gradle.sh ...` preserves its managed proxy/CA settings. `bash scripts/test.sh` also runs 184 original parser checks using the local ECJ/Android toolchain when present. Captured official source fixtures are under `app/src/test/fixtures` and `research/v2`; new calculation/import checks use JUnit.

For a signed release, create `.signing/marketlens-studio-upload.jks` with alias `marketlens-studio-upload` and `.signing/signing.properties` containing `storePassword` and `keyPassword`. These files and APK/AAB/build outputs are ignored by Git. Use your own secure passwords, keep an offline backup and enroll in Play App Signing. A source checkout without this key can build the independent debug app; it cannot recreate the workspace release signature.

No credentials, keystores or personal portfolio exports belong in commits. GitHub Actions builds/tests the debug app without an upload key. Use a properly licensed data setup and complete the publisher actions in the Play preparation document before public distribution.
