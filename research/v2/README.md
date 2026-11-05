# Source captures used by JUnit

Downloaded 5 Nov 2026 from original sources; these are verification snapshots, not live sample prices in the app.

- `history.txt`: NSE TCS historical OHLC/volume response for 1 Oct–5 Nov 2026. Source request uses the historicalOR/generateSecurityWiseHistoricalData endpoint.
- `history-year-complete.json`: five bounded NSE historical requests merged for TCS, 6 Nov 2025–5 Nov 2026. Exact request URLs are embedded in `sources`; the response contains 248 sessions.
- `hdfc-sample.xlsx`: HDFC Retirement Fund – Equity Plan, reporting date 30 Sep 2026, linked from https://www.hdfcfund.com/statutory-disclosure/portfolio/monthly-portfolio.

Provider terms and rights must be reviewed for any production redistribution. The app retains independent source/date metadata and never presents this test data as a fresh download.

## Additional Studio 1.1 fixture

`hdfc-flexi-sample.xlsx` is the unmodified HDFC Flexi Cap Fund portfolio dated 30 September 2026, fetched over HTTPS on 5 November 2026 from the original HDFC monthly disclosure directory. It includes a 6.7 MB worksheet with 65,501 mostly formatting-only rows; the regression verifies bounded streaming without truncating value-bearing rows. It is a test fixture, never a production asset.

Original URL: https://files.hdfcfund.com/s3fs-public/2026-10/Monthly%20HDFC%20Flexi%20Cap%20Fund%20-%2030%20September%202026_0.xlsx?VersionId=hZBquZwtxT0gkdhzSD680e07SLaMm4ny
