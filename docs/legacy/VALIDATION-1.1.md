# MarketLens 1.1.0 validation

## Build and update

- Compiled against Android SDK 35 / Build Tools 35.0.0 with ECJ and D8, aligned and signed.
- VersionCode 2, versionName 1.1.0, Android 10 minimum, target 35. No native libraries, so the APK is architecture independent.
- APK signature verification passed; the certificate matches MarketLens 1.0.0. The update can retain app storage.
- Release manifest remains Internet-only. No debug/test component, notification permission, calendar permission, storage permission, broker integration, or background notification service was added.
- Only the official company directory is bundled as app data. No prices, GMP quotes, fund holdings or example personal positions are included in the APK.

## Source and calculation checks

184 checks passed: 91 existing market/portfolio-parser checks and 93 new investor checks. These cover:

- SEDEMAC's original September ownership percentages, named group count, combined SBI stake/share count and original names.
- Aggregate FPI percentage, +6.03 percentage-point change from June, current-period pledge flags and prevention of a historical no-pledge flag becoming a current zero.
- Matching financial reporting duration, income definition and consolidation basis, including non-positive growth bases.
- NSE IPO identity merging across feeds, correct dates/phases, reported subscriptions, upper price bands, lot calculations and parent-category bids without double-counting child categories.
- Independent GMP publishers, explicit missing-versus-zero quotes, preservation of publisher timestamps/date text and disagreements, safe source links and rejection of challenge/changed-format pages.
- Manual positions, costs/fees, P&L, unavailable valuations, research-state serialization, threshold validation/deduplication, rejection of saved/undated quotes as new alert triggers, tracking and bounded observed GMP history.

Fixtures contain reduced public source facts/table data, excluding article prose, advertising and unrelated filing narrative. They are test resources, not app data.

## Fresh source access

11 fresh source checks passed through the app's Java HTTPS client with normal proxy configuration and TLS verification:

1. NSE current IPO feed: 1 reported record.
2. NSE upcoming IPO feed: a valid empty list.
3. NSE past IPO feed: 1,493 records.
4. VPFL original issue/lot details and bid timestamp: 1,200 shares per lot, upper band ₹94; bid data dated 4 November 2026 19:00:02.
5. InvestorGain original report: 25 parsed GMP rows, publisher page modification time retained.
6. IPOWatch original report: 7 parsed GMP rows, publisher page modification time retained.
7. SEDEMAC original current/previous shareholding XML: September MF/UTI 20.57%, FPI 10.30%, FPI change +6.03 pp from June.
8. SEDEMAC quote: exchange timestamp 5 November 2026 09:41:16.
9. SEDEMAC company announcements: 30 returned records.
10. SEDEMAC corporate actions: a valid empty source window.
11. SEDEMAC latest integrated financials: standalone, period ended 30 September 2026. The June comparison document also parsed successfully.

GMP values and page dates can change between these checks and installation. These counts and dates record the actual test, not a guarantee of current market values.

## Android checks

Twelve investor UI/offline checks passed in an Android 10 emulator, in three batches:

1. Expanded navigation renders after an in-place update.
2. The original watchlist survives the update.
3. SEDEMAC source ownership and filing holder count render.
4. The SBI filing-row stake is labelled combined.
5. The original full name group is viewable without a fabricated per-scheme split.
6. A native manual-position form saves 10 shares at ₹3,000 plus ₹50 fees, and the private JSON/cost of ₹30,050 is verified.
7. Official IPO source records render.
8. Original IPO identity can be tracked locally.
9. Official issue lot information is displayed.
10. GMP has an explicit unofficial identity and publisher rows.
11. Observed GMP history is source-labelled.
12. The second original GMP publisher remains separate.

The production release was installed after these checks and its installed version/flags were inspected. Test data exists only in the emulator's private storage, not in the installer.

The emulator used verified source downloads placed in private cache and was deliberately offline for UI/fallback checks. Saved data stayed labelled with its original source dates. These were not demonstrations of live provider connectivity from a physical phone. The fresh network checks above ran separately through the same Java request/parser code.

The UI harness needed corrections for Android's uppercase dialog button labels and software-keyboard layout movement. The manual position form was additionally exercised with keyboard focus traversal and its saved private JSON was checked. These were harness corrections; position validation/calculation is also covered in the automated checks.

No orders, IPO applications, calendar events or broker actions were executed in testing. Calendar and export use user-controlled Android intent/document flows; calendar saving and arbitrary document providers were not tested end-to-end.

## Coverage and limits

- Native stock/IPO data is NSE. BSE-only issue coverage requires the linked original BSE page.
- GMP is an unofficial publisher report, not an NSE/BSE price, a verified dealer trade or a guaranteed listing outcome. Publishers remain separate; there is no fabricated consensus or fuzzy issuer match.
- Named company-filing fund positions are a disclosed subset. A row naming several schemes has one combined total; no per-scheme split is invented.
- Automatic monthly scheme coverage remains six PPFAS schemes plus imported AMC portfolios, not every fund in India.
- Positions, application status and notes are manual local records. P&L is unrealized and based on provided cost/fees and source-dated prices; it is not a broker/tax ledger.
- Threshold checks run in app on fresh successful quote refreshes. There are no background push notifications. Missing prices are not zero-valued holdings.
- No physical S25 Ultra or Android 15/16 test of this update was completed. The user-provided screenshots show the earlier build on their phone; they do not validate the new features.
