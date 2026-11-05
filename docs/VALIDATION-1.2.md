# Studio 1.2 validation

- 63 JUnit tests passed: the previous 39 and 24 IPO/range regressions. These cover actual BCSSL high/low entries and listing metadata, established-range rules, share-demand percentages, unavailable denominators, publisher scope versus derived counts, application-based lottery capacity, SME two-lot units, whole-count/overflow guards, BSE identity/date extensions, persistence, named-column parsing, unit safety, source-origin restrictions and blocked/unsupported pages.
- 184 original captured-source parser/investor/IPO/GMP checks passed (91 + 93).
- JDK 17/Gradle release APK and signed Android App Bundle built successfully, including release vital lint. App version is 1.2.0/code 3; app ID and Room schema are unchanged.
- Original NSE fixtures were downloaded on 5 November 2026. Both high/low feeds at 15:24:06 IST contain BCSSL with no prior reference; the directory and past-IPO response give listing date 5 November and series BE. Request provenance is in app/src/test/fixtures/v12/README.md.
- BSE publicissue and tested API requests returned HTTP 403 in this environment. A live Antala subscription, category allocation and valid-application count were not verified. No third-party subscription, guessed offer terms or fabricated odds were bundled as official data. BSE parsing tests use explicitly constructed table shapes; they are not captured exchange responses.
- The BSE viewer restricts capture to original HTTPS BSE public-issue paths. It has no native JavaScript bridge, file/content access or geolocation; third-party cookies are disabled. The in-app policy is byte-identical to the public plain-text policy. App data erasure uses Android clearApplicationUserData, including browser data.

See IPO-AND-RANGE-FIXES.md for the exact formulas, source/import workflow and limits. Passing calculations/build checks does not verify BSE accessibility or every table layout on the user's network, nor final registrar allotment or physical S25 Ultra behavior.

The final release also passed scripts/verify-release.py: APK/AAB signatures, app/version identity, API 36 target, publisher/privacy text, 16 KB native ELF alignment and APK ZIP alignment. A new guard rejects rows whose cell counts differ from the recognized header, preventing nested/shifted financial columns from being guessed.

## Installed Android release checks

The final signed release was installed over Studio 1.1 in the disposable Android 10/API 29 emulator. Observed checks passed for version 1.2/code 3, the established-range filter, BCSSL's actual listing-day/range-since-listing label and dual NSE event explanation, the IPO BSE recovery/manual-entry actions, opening the original BSE viewer, the explicit public-table read control, the labelled original-source form and retention of the pre-existing TCS position. These were observed across the main and resumed UI flows; initial screen-sleep/gesture timing attempts were inconclusive.

Five original NSE responses were explicitly placed in this emulator's private cache, preserving their host-download timestamps. This verifies offline rendering/source-date handling, not direct emulator network access. The viewer could not read live BSE data in this environment. No live Antala capture, final subscription/application inputs, full end-to-end allotment-scenario form save or physical S25 test is claimed. Formula/source-parser behavior is covered separately by the unit suite. No fixture market data are bundled in production assets.
