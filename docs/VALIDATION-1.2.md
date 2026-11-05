# Studio 1.2 validation

- 63 JUnit tests passed: the previous 39 and 24 IPO/range regressions. These cover actual BCSSL high/low entries and listing metadata, established-range rules, share-demand percentages, unavailable denominators, publisher scope versus derived counts, application-based lottery capacity, SME two-lot units, whole-count/overflow guards, BSE identity/date extensions, persistence, named-column parsing, unit safety, source-origin restrictions and blocked/unsupported pages.
- 184 original captured-source parser/investor/IPO/GMP checks passed (91 + 93).
- JDK 17/Gradle release APK and signed Android App Bundle built successfully, including release vital lint. App version is 1.2.0/code 3; app ID and Room schema are unchanged.
- Original NSE fixtures were downloaded on 5 November 2026. Both high/low feeds at 15:24:06 IST contain BCSSL with no prior reference; the directory and past-IPO response give listing date 5 November and series BE. Request provenance is in app/src/test/fixtures/v12/README.md.
- BSE publicissue and tested API requests returned HTTP 403 in this environment. A live Antala subscription, category allocation and valid-application count were not verified. No third-party subscription, guessed offer terms or fabricated odds were bundled as official data. BSE parsing tests use explicitly constructed table shapes; they are not captured exchange responses.
- The BSE viewer restricts capture to original HTTPS BSE public-issue paths. It has no native JavaScript bridge, file/content access or geolocation; third-party cookies are disabled. The in-app policy is byte-identical to the public plain-text policy. App data erasure uses Android clearApplicationUserData, including browser data.

See IPO-AND-RANGE-FIXES.md for the exact formulas, source/import workflow and limits. Passing calculations/build checks does not verify BSE accessibility or every table layout on the user's network, nor final registrar allotment or physical S25 Ultra behavior.

The final release also passed scripts/verify-release.py: APK/AAB signatures, app/version identity, API 36 target, publisher/privacy text, 16 KB native ELF alignment and APK ZIP alignment. A new guard rejects rows whose cell counts differ from the recognized header, preventing nested/shifted financial columns from being guessed.
