package app.marketlens

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.time.LocalDate
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

class FundPopularityTest {
    private val a = "INE002A01018"
    private val b = "INE009A01021"
    private val c = "INE040A01034"
    private val universe = setOf(a, b, c)
    private fun fund(id: String, name: String, date: String = "2026-09-30", vararg positions: Pair<String, Double>): Data.Portfolio =
        Data.Portfolio().also { p ->
            p.id = id; p.name = name; p.date = date; p.source = "https://files.hdfcfund.com/monthly.xlsx"
            p.fetched = 10
            p.holdings = positions.map { (isin, weight) -> Data.Holding().also {
                it.isin = isin; it.stock = "Company $isin"; it.weight = weight
            } }
        }
    private fun rank(funds: List<Data.Portfolio>, category: FundPopularity.Category = FundPopularity.Category.OVERALL,
                     records: List<LocalRecord> = emptyList()) = FundPopularity.rank(funds, records, "2026-09", category, universe)
    private fun classify(p: Data.Portfolio, category: FundPopularity.Category, amc: String = "HDFC") =
        LocalRecord("fundClass:${p.id}", "fundClassification", JSONObject().put("category", category.name).put("amc", amc).toString())

    @Test fun breadthRanksDistinctSchemesAndUsesCoveredDenominator() {
        val report = rank(listOf(fund("s", "HDFC Small Cap Fund", positions = arrayOf(a to 2.0, b to 9.0)),
            fund("m", "HDFC Mid Cap Fund", positions = arrayOf(a to 4.0)), fund("l", "HDFC Large Cap Fund", positions = arrayOf(c to 8.0))))
        assertEquals(3, report.schemes.size); assertEquals(a, report.stocks.first().isin)
        assertEquals(2, report.stocks.first().count); assertEquals(1, report.stocks.first().amcCount)
        assertEquals(3.0, report.stocks.first().averageWeight, 0.0)
    }
    @Test fun filtersAreSchemeCategoriesNotStockCapitalization() {
        val funds = listOf(fund("s", "HDFC Smallcap Fund", positions = arrayOf(a to 2.0)),
            fund("m", "HDFC Mid-Cap Fund", positions = arrayOf(a to 3.0)),
            fund("l", "HDFC Large Cap Fund", positions = arrayOf(a to 4.0)),
            fund("f", "HDFC Flexi Cap Fund", positions = arrayOf(a to 5.0)))
        listOf(FundPopularity.Category.SMALL, FundPopularity.Category.MID, FundPopularity.Category.LARGE, FundPopularity.Category.FLEXI).forEach {
            val report = rank(funds, it); assertEquals(1, report.schemes.size); assertEquals(1, report.stocks.single().count)
        }
        assertEquals(4, rank(funds).stocks.single().count)
    }
    @Test fun passiveAndCombinedCategoryNamesDoNotPretendToBeActiveSmallOrLargeFunds() {
        for (name in listOf("HDFC Small Cap Index Fund", "HDFC Large & Mid Cap Fund", "HDFC Large  Mid Cap Fund", "HDFC Large and Midcap Fund", "HDFC Midcap ETF"))
            assertEquals(FundPopularity.Category.OTHER, FundPopularity.classification(fund(name, name), emptyList()).category)
        assertEquals(FundPopularity.Category.UNKNOWN, FundPopularity.classification(fund("top", "HDFC Top 100 Fund"), emptyList()).category)
    }
    @Test fun planOptionsMergeWithinAnAmcAndLatestDisclosureWins() {
        val direct = fund("d", "HDFC Flexi Cap Fund - Direct Plan - Growth", "2026-09-30", a to 3.0)
        val regular = fund("r", "HDFC Flexi Cap Fund - Regular Plan - IDCW", "2026-09-29", b to 5.0)
        val report = rank(listOf(direct, regular, direct))
        assertEquals(1, report.schemes.size); assertEquals(a, report.stocks.single().isin); assertEquals(1, report.stocks.single().count)
    }
    @Test fun wordsWithinSchemeTitlesAreNotStrippedAsOptions() {
        val growth = fund("g", "HDFC Growth Opportunities Fund", positions = arrayOf(a to 1.0))
        val other = fund("o", "HDFC Opportunities Fund", positions = arrayOf(a to 2.0))
        assertEquals(2, rank(listOf(growth, other)).stocks.single().count)
    }
    @Test fun differentAmcsNeverMergeAndSourceHostsAreExact() {
        val h = fund("h", "Flexi Cap Fund", positions = arrayOf(a to 1.0))
        val p = fund("p", "Flexi Cap Fund", positions = arrayOf(a to 2.0)).also { it.source = "https://amc.ppfas.com/monthly.xlsx" }
        val report = rank(listOf(h, p)); assertEquals(2, report.stocks.single().count); assertEquals(2, report.stocks.single().amcCount)
        val spoof = fund("spoof", "HDFC Flexi Cap Fund").also { it.source = "https://hdfcfund.com.example.com/file.xlsx" }
        assertEquals("", FundPopularity.classification(spoof, emptyList()).amc)
    }
    @Test fun unverifiedAmcsStaySeparateUntilExplicitIdentityLabels() {
        val one = fund("1", "Example Small Cap Fund Direct Plan Growth", positions = arrayOf(a to 1.0)).also { it.source = "" }
        val two = fund("2", "Example Small Cap Fund Regular Plan Growth", positions = arrayOf(a to 2.0)).also { it.source = "" }
        val unknown = rank(listOf(one, two)); assertEquals(2, unknown.stocks.single().count); assertEquals(0, unknown.stocks.single().amcCount)
        val known = rank(listOf(one, two), records = listOf(classify(one, FundPopularity.Category.SMALL, "Example"), classify(two, FundPopularity.Category.SMALL, "Example")))
        assertEquals(1, known.stocks.single().count)
    }
    @Test fun onlySelectedMonthContributesToBreadth() {
        val report = rank(listOf(fund("s", "HDFC Small Cap Fund", "2026-09-30", a to 1.0), fund("m", "HDFC Mid Cap Fund", "2026-08-31", a to 2.0)))
        assertEquals(1, report.schemes.size); assertEquals(1, report.stocks.single().count); assertNull(report.stocks.single().delta)
    }
    @Test fun newCoverageDoesNotMasqueradeAsMonthlyBuying() {
        val report = rank(listOf(fund("old", "HDFC Small Cap Fund", "2026-08-31", b to 2.0),
            fund("old", "HDFC Small Cap Fund", "2026-09-30", a to 3.0),
            fund("new", "HDFC Mid Cap Fund", "2026-09-30", a to 4.0, b to 2.0)))
        assertEquals(1, report.matchedSchemes)
        val ar = report.stocks.first { it.isin == a }; assertEquals(1, ar.added); assertEquals(0, ar.absent); assertEquals(1, ar.delta)
        val br = report.stocks.first { it.isin == b }; assertEquals(0, br.added); assertEquals(1, br.absent); assertEquals(-1, br.delta)
    }
    @Test fun missingAdjacentMonthIsUnavailableEvenWithAnOlderSnapshot() {
        val report = rank(listOf(fund("s", "HDFC Small Cap Fund", "2026-07-31", a to 1.0), fund("s", "HDFC Small Cap Fund", "2026-09-30", a to 2.0)))
        assertEquals(0, report.matchedSchemes); assertNull(report.stocks.single().added)
    }
    @Test fun zeroRowsInvalidIsinsAndNonDirectorySecuritiesAreExcluded() {
        val p = fund("s", "HDFC Small Cap Fund", positions = arrayOf(a to 0.0, "INVALID" to 5.0, "INE090A01021" to 10.0))
        val report = rank(listOf(p)); assertTrue(report.stocks.isEmpty()); assertEquals(0, report.schemes.size); assertEquals(1, report.excludedSnapshots)
    }
    @Test fun missingWeightIsNotZeroButPositiveShareCountStillIdentifiesHolder() {
        val p = fund("s", "HDFC Small Cap Fund", positions = arrayOf(a to Double.NaN)).also { it.holdings.single().shares = 12.0 }
        val row = rank(listOf(p)).stocks.single(); assertEquals(1, row.count); assertTrue(row.averageWeight.isNaN()); assertEquals(0, row.knownWeights.size)
    }
    @Test fun duplicateSecurityRowsDoNotCreateExtraHoldersOrInflateAverageWeight() {
        val p = fund("s", "HDFC Small Cap Fund", positions = arrayOf(a to 3.0, a to 3.0))
        val row = rank(listOf(p)).stocks.single(); assertEquals(1, row.count); assertTrue(row.averageWeight.isNaN())
    }
    @Test fun exactIsinSeparatesSameCompanyLabels() {
        val p = fund("s", "HDFC Small Cap Fund", positions = arrayOf(a to 3.0, b to 2.0)).also { p -> p.holdings.forEach { it.stock = "Same name" } }
        assertEquals(2, rank(listOf(p)).stocks.size)
    }
    @Test fun manualCategoriesArePersistentInputsForImportedAndHistoricSchemes() {
        val p = fund("top", "HDFC Top 100 Fund", positions = arrayOf(a to 2.0))
        assertTrue(rank(listOf(p), FundPopularity.Category.LARGE).stocks.isEmpty())
        val label = classify(p, FundPopularity.Category.LARGE)
        assertEquals(1, rank(listOf(p), FundPopularity.Category.LARGE, listOf(label)).stocks.single().count)
        assertTrue(FundPopularity.classification(p, listOf(label)).manual)
    }
    @Test fun archivesRetainPeriodsButRemovedSchemesDoNotReturnAndFutureDatesAreExcluded() {
        val current = fund("s", "HDFC Small Cap Fund", "2026-09-30", a to 2.0)
        val old = fund("s", "HDFC Small Cap Fund", "2026-08-31", b to 2.0)
        val removed = fund("gone", "HDFC Mid Cap Fund", "2026-09-30", c to 2.0)
        fun snapshot(p: Data.Portfolio) = SourceSnapshot("fund:${p.id}:${p.date}", p.json().toString(), p.source, p.date, p.fetched)
        val catalog = FundPopularity.catalog(listOf(current), listOf(snapshot(current), snapshot(old), snapshot(removed)), LocalDate.parse("2026-11-05"))
        assertEquals(2, catalog.size); assertEquals(listOf("2026-09", "2026-08"), FundPopularity.months(catalog))
        assertTrue(FundPopularity.catalog(listOf(current), emptyList(), LocalDate.parse("2026-08-01")).isEmpty())
    }
    @Test fun exportPreservesDenominatorsMissingComparisonsAndSourceNames() {
        val p = fund("s", "HDFC Flexi Cap Fund", positions = arrayOf(a to 3.0)).also { it.holdings.single().stock = "Name, \"Limited\"" }
        val r = rank(listOf(p)); val csv = FundPopularity.csv(r, r.stocks)
        val rows = Parse.csv(csv); assertEquals(2, rows.size); assertEquals("2026-09", rows[1][0]); assertEquals("Overall", rows[1][1])
        assertEquals("Name, \"Limited\"", rows[1][3]); assertEquals("1", rows[1][5]); assertEquals("", rows[1][13])
    }
    @Test fun officialFlexiWorkbookWithLargeFormattedSheetCanFeedTheRanking() {
        val path = "research/v2/hdfc-flexi-sample.xlsx"
        val file = File("../$path").takeIf { it.exists() } ?: File(path)
        val funds = Xlsx.portfolios(file.readBytes(), "HDFC Flexi Cap Fund", "2026-09-30", "https://files.hdfcfund.com/monthly.xlsx")
        assertEquals(1, funds.size); assertTrue(funds.single().holdings.size > 30)
        val report = rank(funds, FundPopularity.Category.FLEXI)
        assertEquals(1, report.schemes.size); assertTrue(report.stocks.isNotEmpty())
        assertTrue(report.stocks.all { it.count == 1 })
    }
    @Test fun streamedLargeWorksheetPreservesEveryOriginalHoldingAndWeight() {
        val path = "research/v2/hdfc-flexi-sample.xlsx"
        val file = File("../$path").takeIf { it.exists() } ?: File(path)
        val normal = Xlsx.portfolios(file.readBytes(), "HDFC Flexi Cap Fund", "2026-09-30", "https://files.hdfcfund.com/monthly.xlsx").single()
        val buffer = ByteArrayOutputStream()
        ZipFile(file).use { source -> ZipOutputStream(buffer).use { target ->
            source.entries().asSequence().forEach { entry ->
                target.putNextEntry(ZipEntry(entry.name))
                val bytes = source.getInputStream(entry).readBytes()
                if (entry.name == "xl/worksheets/sheet1.xml") {
                    // A large formatting comment forces streaming on the actual holdings sheet.
                    target.write(bytes.toString(Charsets.UTF_8).replace("</worksheet>", "<!--" + "x".repeat(4 * 1024 * 1024) + "--></worksheet>").toByteArray())
                } else target.write(bytes)
                target.closeEntry()
            }
        } }
        val streamed = Xlsx.portfolios(buffer.toByteArray(), normal.name, normal.date, normal.source).single()
        assertEquals(normal.holdings.size, streamed.holdings.size)
        normal.holdings.zip(streamed.holdings).forEach { (a, b) ->
            assertEquals(a.isin, b.isin); assertEquals(a.stock, b.stock)
            assertEquals(a.shares, b.shares, 0.0); assertEquals(a.weight, b.weight, 0.0)
        }
    }
}
