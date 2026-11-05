# Play submission preparation

App ID: `app.marketlens.studio` (separate from `app.marketlens`). Version 1.0.0 / code 1. Debug ID: `app.marketlens.studio.debug`.
Publisher: karthik. Support: ykn5006@gmail.com.

Implemented preparation:
- Target/compile SDK 36; minimum Android 10/API 29.
- Native Compose screens, functional calculators and private records, with source/coverage labels.
- Optional notifications and approximate WorkManager checks; no foreground-service permission or restricted personal-data permissions.
- In-app privacy policy, local deletion controls, disabled Android backup/device transfer, explicit file imports/exports.
- Separate upload signing key kept outside Git. Use Play App Signing for distribution and retain a secure backup of the upload key.
- Release APK and Android App Bundle; validation reports cover signature, package/version and ELF/ZIP alignment where generated.
- Store listing, privacy HTML/text and Data Safety/financial-feature worksheet.

Publisher actions before a public submission:
1. Verify the public read-only privacy policy URL is accessible: https://raw.githubusercontent.com/Karthik5006/marketlens-studio/main/play-store/privacy-policy.txt . Keep it in sync with the in-app policy; a publisher-domain HTML copy is also provided.
2. Verify rights/terms for public-app access, display and any redistribution of NSE/AMC/publisher data, including the bundled directory. Public endpoints and free access do not establish redistribution permission. Obtain required permissions/licences or replace a feed before distributing publicly.
3. Complete Play Console developer verification, app access, target audience/content rating, Data Safety and Financial features declarations using the final build.
4. Use the included actual-app screenshots/store graphics, inspect accessibility and the pre-launch report, and test on a physical S25 Ultra plus other supported devices. Complete any closed-testing requirements shown for your developer account.
5. Confirm upload-signing/App Signing setup, upload the AAB to an internal test track, resolve Console warnings, and then request production review.

Prepared artifacts do not mean Google has approved the app. There is no Play Console submission or publication from this workspace.

Requirements checked 5 Nov 2026:
- Target API 36: https://support.google.com/googleplay/android-developer/answer/11926878
- 16 KB support: https://developer.android.com/guide/practices/page-sizes
- User Data/privacy policy: https://support.google.com/googleplay/android-developer/answer/10144311
- Financial features: https://support.google.com/googleplay/android-developer/answer/13849271
