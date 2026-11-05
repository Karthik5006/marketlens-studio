# Mutual Fund Radar — Studio 1.1

Open **Funds → Most held**. Sync official disclosures or import an AMC's original workbook/holdings CSV. Choose a reporting month and a scheme category: Overall, Small-cap, Mid-cap, Large-cap, Flexi-cap, Other or Unclassified.

Each stock shows the number of holding schemes out of covered schemes, the number of known fund houses, its mean disclosed fund-asset weight, category breadth and available matched-month changes. Expand **See holding schemes** for names, category, weight, report/retrieval dates and original source links. **Follow** uses the existing stock watchlist. Search by company, NSE symbol or ISIN; filter to the watchlist or a minimum holder count; sort by scheme count, net monthly additions or average weight. CSV export includes all matching rows, including those beyond the visible pagination.

## Counting and identity

- Ranking unit: one scheme with positive disclosed weight or share quantity in a verified NSE equity ISIN. Zero positions, invalid ISINs, fund units, derivatives and securities outside the bundled NSE equity directory are excluded. This directory can lag recent listings; BSE-only securities are outside this view.
- Stock names are display labels; matching uses the complete ISIN. Duplicate rows never add extra scheme votes; their weight is treated as unavailable.
- Select one month: do not combine a stale August holding with September holdings. The latest report date, then retrieval date, chooses a scheme snapshot within that month. Reporting days remain visible in holder details.
- Direct/regular plan and growth/IDCW option suffixes are normalized within a known AMC. The same normalized scheme contributes once. Names in the body of scheme titles remain intact. Different fund houses never merge.
- Automatic AMC identity currently comes from the exact official HDFC/PPFAS source domains. For other imports, set a consistent AMC label in **Explore → Edit scheme classification**. Unverified AMC imports stay separate by saved ID; separately named copies or scheme renamings need identity review. Use original scheme names and remove duplicate imports rather than treating arbitrary labels as unique funds.
- Filters describe **the fund's category**, not the capitalization of the stocks it owns. Explicit small/mid/large/flexi-cap names are inferred. Index/ETF and large-and-mid-cap names go to Other; ambiguous names remain Unclassified. Review against original scheme documents and save overrides in Explore or the coverage inspector. Current labels also apply to retained history; historical recategorization is not reconstructed.
- Overall includes every covered equity-bearing scheme, including hybrid/passive/tax-saving/other and unclassified categories. Schemes with no recognized NSE equity positions are excluded from the denominator; their count is disclosed.

## Reading the insights

**Scheme breadth:** holding schemes / eligible covered schemes for the selected month and category. The fund-house count includes only identified AMCs; identified-holder coverage is shown. Ten schemes at one AMC are ten scheme votes and one fund house.

**Average weight:** equal-weighted mean of available holding-scheme weights (% of each fund's assets). Missing weights stay unavailable and their coverage is shown. This is not a company's ownership percentage, summed rupee/AUM exposure or an AUM-weighted result.

**Monthly breadth:** additions/absences are measured only on schemes with recognized equities in both the selected month and the immediately preceding calendar month. New or dropped source coverage is excluded. A missing preceding period produces unavailable changes, not zero. An absent recognized row is not proof of a sale: incomplete files, identity changes or source coverage can affect comparisons. Share-price changes can change asset weights without any trade.

The leading default sort is holding-scheme count; ties are deterministic. Net-addition and average-weight sorts keep missing metrics last. Only stocks held in the selected month appear in this ranking; fully exited stocks remain inspectable in the existing per-scheme Changes view. Followed stocks retain the existing price/filing alert behavior; this feature does not add fund-breadth push notifications.

## Coverage and persistence

Automatic sync remains PPFAS and supported HDFC files. Import more original AMC disclosures to broaden coverage. This is **not an all-AMC industry consensus feed**. Every ranking displays its month, scheme count and known AMC count. Schemes imported by the user retain their import/source labels in Explore.

Room preserves actually saved reporting periods; there is no fabricated history. Radar historical periods include retained disclosures only for schemes still saved in Explore. Removing a saved scheme removes it from these rankings, while its source archive remains retained under the existing storage behavior. Category/AMC edits and watchlist choices survive restart. Rankings are derived locally; no new account, permission or external analytics is introduced.

Studio 1.1.0 uses the same application ID and signing identity as Studio 1.0.0, with version code 2, so the new APK upgrades Studio while preserving its records. Original MarketLens remains separate.
