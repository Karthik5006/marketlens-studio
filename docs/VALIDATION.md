# Studio 1.0 validation

- Release APK and signed Android App Bundle built with JDK 17, compile/target API 36, minimum API 29. Production identity `app.marketlens.studio` is independent of original `app.marketlens`; debug uses `.studio.debug`.
- 20 JUnit tests passed: weighted overlap/exposure, residual coverage, indicators, drawdown, year history, fee-aware account-separated FIFO, oversell handling, IPO capital, annual statement ratios/context isolation, chart adjustment factors, missing screener metrics, official HDFC workbook identity/weights and explicit legacy-file conflict handling.
- 184 original captured-source parser/investor checks passed (91 + 93).
- GitHub Actions Android build/test workflow passed after updating SDK setup to request current packages rather than the retired `tools` package. It runs the JUnit suite and builds the debug APK without private upload credentials.
- The release APK/AAB signatures, package/version, API 36 target, embedded privacy text, 16 KB ELF segment alignment for bundled native libraries, and APK 16 KB ZIP alignment were checked by scripts/verify-release.py. Result metadata is in the local release bundle.

## Android UI checks

The optimized release APK was installed in a disposable Android 10/API 29 emulator. Observed checks covered launch/dashboard and navigation, imported official fund disclosures, exact-ISIN overlap rendering, stock search, quote/history tabs, candle mode, dated ownership data, manual holding creation, persistence after process restart, optional background settings and the complete in-app policy with publisher/contact details.

NSE/HDFC source responses were fetched over HTTPS by the host and explicitly placed in this emulator's private cache. This verifies rendering and offline-cache use, not a claim that direct emulator networking worked. Cache/source dates and failure labels remained visible. The app contains no fixture prices/holdings in its production assets; its bundled directory contains names and ISINs.

The UI review led to white system-bar icons for the dark theme, horizontal-only chart inspection so vertical scroll can reach the rest of the page, expanded editor/search sheets and pagination of large named-holder lists. Final release artifacts include these changes. A disposable manual holding was used only for persistence testing; it is not part of the APK, source archive or public screenshots.

Not claimed: physical S25 Ultra testing, Google Play pre-launch/review approval, exact background notification timing, full native BSE/all-AMC coverage, bank/broker/registrar verification or statutory tax computation. See FEATURES.md and the Play readiness notes for source/automation limits and publisher actions.
