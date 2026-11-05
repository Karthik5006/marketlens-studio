# Studio 1.2 original-source fixtures

Downloaded 5 November 2026 over HTTPS. NSE high/low timestamps: 05-Nov-2026 15:24:06 IST. These are test fixtures, never bundled sample market data.

- highs.json: https://www.nseindia.com/api/live-analysis-52Week?index=high
- lows.json: https://www.nseindia.com/api/live-analysis-52Week?index=low
- bcssl-ipo.json: unchanged BCSSL row extracted from https://www.nseindia.com/api/public-past-issues. The primary response states listingDate 05-NOV-2026 and securityType BE.

BSE publicissue and API attempts returned HTTP 403 here. No synthetic BSE response is labelled as a captured official response. BSE table test inputs are deliberately constructed field-shape examples in Kotlin, not claims about Antala's live terms or subscriptions.
