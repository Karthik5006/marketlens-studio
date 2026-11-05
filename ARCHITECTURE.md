# MarketLens Studio architecture

MarketLens is an offline-capable, single-activity Android app with a Compose interface and a unidirectional data flow. It has no server, broker authentication or trading access.

```
Compose screens → LensViewModel → MarketRepository
                                    ├─ existing Java NSE/AMC parsers + HTTPS cache
                                    ├─ FinancialParser and Analytics (deterministic)
                                    └─ Room: investor records, dated snapshots, alert inbox
Android WorkManager → MarketRepository → notification channel / Room inbox
```

- **Presentation:** Material 3, Compose Canvas charts, saveable tab/filter state, lifecycle-aware StateFlow collection. A shared design system provides cards, inputs, source metadata, animated transitions and allocation bars. Investor forms validate before closing.
- **State:** ViewModel owns requests, loading/error state and source results. Requests execute outside the UI thread and a semaphore limits foreground work. A single repository owns persistent writes.
- **Domain:** `Analytics` implements exact-ISIN weighted overlap, look-through exposure with explicit residuals, Wilder RSI, moving averages, drawdown, volatility, aligned-return correlation, FIFO fee allocation and IPO capital arithmetic. No buy/sell scores are manufactured.
- **Mutual Fund Radar:** `FundPopularity` derives monthly scheme breadth from current portfolios and retained Room snapshots. It validates reporting dates, filters to the NSE equity directory, merges known-AMC plan aliases, chooses one snapshot per scheme/month and compares only a matched adjacent-month cohort. `FundPopularityScreen` computes the catalog and ranking on `Dispatchers.Default`; search/sort/pagination operate on the report. Optional category/AMC overrides are ordinary Room records, so no schema migration is necessary.
- **Storage:** Room stores added investor records, observed source snapshots and the alert inbox. A new independent app sandbox starts empty; optional file transfer imports selected original MarketLens records with explicit conflict handling. Portfolio workbooks continue to use the existing private JSON store. Dated snapshots are archived in Room before replacements.
- **Source adapters:** Existing allowlisted HTTPS clients and strict NSE identity/series matching are reused. PPFAS and supported HDFC XLSX disclosures are recognized; unsupported formats and empty responses produce visible errors. Originals remain linked.
- **Scheduling:** Optional WorkManager jobs (minimum 15 minutes) use connected/unmetered constraints, rotate up to 10 symbols, establish a first-run filings baseline and deduplicate notifications. Quiet hours defer delivery. Android notification permission is requested only through an explicit action. WorkManager survives process death and reschedules after reboot; exact timing is not guaranteed.
- **Boundaries:** Brokerage/account records, fund factsheets, IPO lifecycle entries, dividend receipts, supplemental statements and chart adjustment factors are manual records. Native BSE feeds, all-AMC automatic sync, registrar scraping, legal tax determinations and encrypted restore/sync are not claimed.

## Financial integrity

Exchange source dates, reporting dates and retrieval times are distinct. Cache fallback never advances a source date. Schemes match by verified ISIN, and combined quarterly shareholder groups are never split into invented scheme positions. Unknown or non-equity fund weights remain unmapped. Unpriced direct positions are excluded from value-based exposure, with coverage displayed.

Financial extraction accepts whole-company, matching-period INR contexts, not segment facts. Ratios require the relevant numerators and denominators; annual ROE/ROCE require opening and closing balance-sheet values. Manual figures must cite the original statement and remain labelled. Chart split/bonus adjustments use explicit user-entered factors and do not alter tax lots or average-cost positions.

FIFO records are separate from average-cost portfolio summaries. Sales cannot use another account's lots or exceed recorded acquisitions. Fees are allocated per share. Financial-year exports contain lot-level dates and cost. Tax previews use user-entered assumptions, without asserting statutory rates/exemptions.

## Build

JDK 17+, Android SDK 36 and Gradle 8.11.1. Kotlin 2.1.20, Compose BOM 2025.04.01, Room 2.7.1, WorkManager 2.10.0.

```
./gradlew :app:testDebugUnitTest :app:assembleRelease
bash scripts/test.sh
```

`local.properties`, build caches and `.signing` are local and must not be distributed. Studio has a separate private upload key. It coexists with MarketLens 1.1. A fresh checkout builds the separate debug application without production signing credentials.
