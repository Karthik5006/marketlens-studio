# MarketLens 1.0 validation

## Build

- Native Java app compiled against Android SDK 35 with Build Tools 35.0.0 and ECJ, converted to DEX with D8, aligned and signed.
- APK signature verification passed. Android 10 minimum, target 35, versionCode 1, versionName 1.0.0. No native libraries; architecture independent, including ARM64.
- Production APK has only Internet permission. No debuggable flag, instrumentation, accessibility service, broker/trading integration, tracking SDK, or test helper is included.
- The only bundled app data is the official NSE symbol/name/series/ISIN directory. No prices, scheme portfolios, or sample holdings are bundled into the installer.

## Data checks

91 automated checks passed against captured official responses, including:

- ISIN checksums, safe symbol handling, both NSE high/low price buckets and duplicate removal.
- New quote schema, annual range, raw-INR market cap, exchange P/E, exact stock/series selection and original source timestamps.
- Latest financial period selection independent of response order, consolidated preference within a period, one consistent reporting context, INR units, and unsupported currency rejection.
- Financial taxonomies for Reliance, TCS, HDFC Bank and SBI; correct bank total-income labels and basic EPS.
- Company MF/UTI percentage units, aggregate holder count, joining typed XBRL contexts for named fund holders, and quarterly source dates.
- Official portfolio XLSX parsing, scheme names, reporting dates, Excel percentage scaling, quantities, CSV import, saved portfolio round trips, malformed inputs, and XML/ZIP protections.

11 fresh source/schema checks passed using the app's Java HTTPS client through the managed environment's normal proxy with TLS verification:

1. NSE new 52-week highs: 59 rows, source 4 Nov 2026 16:00:27 IST.
2. NSE new 52-week lows: 51 rows, same source timestamp.
3. Reliance quote: source 4 Nov 2026 16:00 IST.
4. Reliance integrated financials: consolidated, quarter ended 30 Sep 2026.
5. Reliance company ownership: 30 Sep 2026, two named fund holders extracted.
6–11. All six supported PPFAS XLSX scheme disclosures discovered from the live official page, portfolio date 30 Sep 2026.

After refining asset-section exclusions, all six just-downloaded workbooks were checked again from the captured responses: 298 recognized physical-equity rows in total. Debt, money-market, REIT/InvIT and derivative sections are excluded. These checks verify the supported files and formats, not every AMC workbook layout.

Additional fresh integrated filings for HDFC Bank, SBI and TCS were downloaded and used in the 91 parser checks. Reduced test snapshots of the relevant reporting/table facts are under app/src/test/fixtures and are excluded from the APK; unrelated narrative and factsheet prose are omitted. The full original workbooks were used for the separate live checks.

## Android UI and offline checks

Eight checks passed in an Android 10 emulator using locally seeded copies of the verified official source responses, rather than invented demo prices:

1. Both exchange lists render with their source date.
2. Switching to lows changes the stock rows and preserves the saved-download label.
3. Stock search opens the selected detail screen.
4. A saved quote and 52-week range render.
5. Saving a stock persists its watchlist selection.
6. The holdings jump shows exact-ISIN scheme matches.
7. Watchlist quotes preserve the exchange source timestamp.
8. The coverage screen shows the six saved scheme disclosures.

The test harness initially expected the AutoCompleteTextView's implementation class in the accessibility tree; Android reports it as EditText. The selector was corrected to the displayed search prompt, after which the eight checks passed. This was a test-only correction.

The release APK was installed after the UI checks. The production audit confirms no DEBUGGABLE flag and no test classes. Device screenshots are validation previews of dated saved source responses, not claims of a live phone connection.

## Limits

No physical Galaxy S25 Ultra or Android 15/16 device test was completed. Live source access was verified from the managed Java environment; the Android UI/offline test used seeded verified responses. End-to-end provider connectivity from a physical Android device and arbitrary AMC file-picker layouts remain unverified.

Public website endpoints may be blocked, delayed or changed by their providers. Refresh errors retain dated saved data or show unavailable. There is no licensed streaming feed or complete all-AMC holdings database. Native stock feeds are NSE; BSE is an external official-source link only. Scheme portfolios are disclosure snapshots and are not live ownership or net hedged exposure.
