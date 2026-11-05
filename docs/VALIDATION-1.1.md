# Studio 1.1 validation

## Calculation and source checks

- 39 JUnit tests passed: the prior 20 plus 19 radar/import regressions.
- Radar checks cover covered-scheme denominators, scheme-category filters, passive/large-and-mid separation, latest monthly snapshots, direct/regular/option deduplication, AMC isolation, exact source host matching, explicit identity overrides, exact ISIN joins, zero/invalid/out-of-directory securities, missing weights, duplicate rows, matched-cohort additions/absences, missing adjacent periods, active-scheme archival history, future-date rejection and CSV labels/escaping.
- Two actual-workbook regressions exercise the original September 2026 HDFC Flexi Cap Fund file, including its 6.7 MB mostly formatted worksheet. A second test forces the real holdings sheet through the bounded streaming reader and compares every ISIN, name, share count and weight against the ordinary reader.
- 184 original NSE/AMC/investor/IPO/GMP checks passed (91 + 93).
- Four original HDFC workbooks were fetched over HTTPS and parsed separately for verification: Small Cap (85 recognized rows), Mid Cap (79), Large Cap (46) and Flexi Cap (81), each dated 30 September 2026. Their overlap is not a claim of all-AMC coverage. These verification records are not bundled with the production app.

Studio uses version 1.1.0/code 2, the same app ID and upload key as Studio 1.0. No database schema change, new permission, tracker, account or remote portfolio upload is introduced. See FUND-RADAR.md for data/identity limits and the existing validation record for the original application flows.

## Release and CI

- JDK 17/Gradle build completed for the release APK and signed Android App Bundle, including release vital lint and the 39-test suite.
- scripts/verify-release.py passed signatures, app/version identity, target API 36, embedded publisher/privacy text, 16 KB native ELF segment alignment and APK ZIP alignment.
- The 1.0 and 1.1 APK signing certificate SHA-256 digests match.
- GitHub Actions passed the complete unit suite and debug APK build for the feature commit.

These checks do not constitute Google Play approval, all-AMC data coverage or physical S25 Ultra testing.
