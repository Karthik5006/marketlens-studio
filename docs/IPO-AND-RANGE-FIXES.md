# Studio 1.2 — IPO sources, subscription and range events

## Why Antala was absent

Studio 1.1 downloaded the NSE current/upcoming/past IPO endpoints. A BSE-only SME issue could not appear in those feeds. The user identified the missing issue as Antala Industries. The BSE public-issues page returned HTTP 403 to this environment; no live Antala subscription or allocation was verified from BSE here, and none is bundled or hardcoded.

Studio 1.2 attempts named-column BSE HTML tables as an additional source. A blocked response, JavaScript-only page or unsupported layout produces a visible coverage failure, never a claim that BSE has no IPOs.

On your phone, open **IPOs → Read official BSE page**. The original https://www.bseindia.com/publicissue loads in an optional WebView. Select the current IPO table, wait for the data and tap **Read displayed IPOs**. Only named columns and valid opening/closing dates are imported. If the exchange requires an access check, complete it yourself; the app does not automate it. Return to IPOs to inspect the dated source facts. Supported table/ARIA-grid layouts are read; unsupported layouts remain unimported.

**Add missing IPO** is a fallback for facts copied from the original BSE/issuer document. Name, dates and source are required; unprovided prices, share counts and subscriptions remain unavailable. Entries retain their manual/captured provenance and read time. No secondary publisher is presented as an exchange. Older imported facts are not automatically refreshed just because the phone is online.

BSE records have exchange-specific identities; an issue-period extension updates the same saved BSE issue and an existing tracked copy. NSE IDs retain their prior format, preserving existing watch/application records. NSE/BSE records are not fuzzy-merged or their bid totals added together, because the scopes can overlap. A double-listed issuer can have explicitly labelled source entries.

## Subscription calculations

- Share subscription multiple = source shares bid / source shares offered.
- Subscription percentage = multiple × 100. For example, **4.82× = 482%**, not 4.82%.
- A published multiple is retained when available. The stated-count calculation is shown separately; a material mismatch asks the user to inspect the source pool, exclusions and update time. Gross issue size, market-maker/anchor exclusions and net offer pools must not be silently equated.
- Rupee issue size is never parsed as offered shares. Zero bids remain zero; an unknown/zero denominator remains unavailable.
- Category subscription uses that category's share allocation. NSE bid details may provide bids/applications without category allocation, so Studio requires the allocation from original terms instead of assuming a regulatory percentage. Parent categories and totals are displayed without adding children again.

The IPO details screen provides **Record allocation / estimate odds** for a reported category and a general **Add a category / allotment scenario** action. Recorded allocations and calculated snapshots show their original source and as-of/read time. Existing entries can be edited or removed.

## Conditional allotment model

Under an explicitly selected equal minimum-unit lottery model:

`capacity = floor(category available shares / (shares per lot × lots per minimum allotment))`

`conditional chance = min(1, capacity / category applications) × 100`

The estimate is for at least a minimum allotment under that model, conditional on correct inputs and eligibility. It is not `1 / total share subscription`, and applying more lots is not treated as multiplying the chance. Two lots per minimum unit are supported for SME individual scenarios. QIB/total-issue lottery scenarios are rejected; other/proportional models do not display lottery odds. NII/unknown categories default to the other/proportional model unless the user explicitly records a different basis.

Received application counts are labelled **provisional**. A user may record valid counts from the source, but the app does not validate PANs, payment mandates, bid eligibility, technical rejections or final registrar allocations. Final demand, reallocation and rejected/withdrawn bids can change the result. No exact live Antala odds are claimed without category inputs.

The current [NSE e-IPO FAQ](https://www.nseindia.com/static/trade/e-ipo-faqs) explains SME individual two-lot applications and classification of larger applications. Always check the specific offer terms. The list's **Open by dates** phase is a calendar classification; it is not confirmation that the exchange's intraday bidding window remains open.

## Why BCSSL appears in both lists

Original NSE captures on 5 November 2026, source time **15:24:06 IST**, include BCSSL in both feeds:

- High event: ₹95.55; previous reference 0; previous reference date `-`.
- Low event: ₹91.00; previous reference 0; previous reference date `-`.

The official NSE equity directory and IPO past-issues response both give its listing date as **5 November 2026**. It trades in series **BE**, which the IPO board filter now recognizes as mainboard.

On listing day, the available trading range starts that day. Both its intraday high and low can be new exchange range events. That does not establish a full year of trading history, or that its last traded price is still at either extreme. The app retains the exchange rows rather than deleting one side.

Markets now shows listing dates/range-since-listing labels, missing prior references and same-session dual-event explanations. **Established range** excludes known listings under one year and rows without a prior reference. Headline counts still describe the complete exchange feed; a separate shown/total count describes the filter. This is a general rule for every stock, not a hardcoded BCSSL exception.

Primary request captures used for verification are in app/src/test/fixtures/v12. BSE field-shape test inputs are explicitly constructed fixtures, not captured Antala data.

## Privacy and compatibility

The optional BSE WebView loads the original website under BSE policies. Third-party cookies, geolocation, file/content access and a native JavaScript bridge are disabled. Reading displayed tables is an explicit action. Erase Studio data clears all app data, including WebView storage. The in-app/public policy and Play Data Safety worksheet were updated.

Version 1.2.0/code 3 keeps `app.marketlens.studio`, the existing upload signature and Room schema. It upgrades Studio; the separate original MarketLens application is unaffected.
