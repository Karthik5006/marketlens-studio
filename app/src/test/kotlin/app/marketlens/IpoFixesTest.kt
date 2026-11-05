package app.marketlens

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

class IpoFixesTest {
    private fun fixture(name: String): String {
        val p = "app/src/test/fixtures/v12/$name"
        return (File("../$p").takeIf { it.exists() } ?: File(p)).readText()
    }
    private fun capture(headers: List<String>, vararg rows: List<String>, url: String = BseIssues.PAGE): String {
        val all = JSONArray().put(JSONObject().put("cells", JSONArray(headers)))
        rows.forEach { all.put(JSONObject().put("cells", JSONArray(it)).put("links", JSONArray().put(BseIssues.PAGE))) }
        return JSONObject().put("url", url).put("tables", JSONArray().put(JSONObject().put("context", "SME").put("rows", all))).toString()
    }
    private val headers = listOf("Company Name", "Issue Open Date", "Issue Close Date", "Price Band", "Lot Size", "Subscription")
    private val row = listOf("Fixture Industries Limited", "29-Oct-2026", "05-Nov-2026", "62 to 66", "2000", "4.82x")

    @Test fun originalNseDataReallyContainsBcsslInBothFeeds() {
        val high = Parse.screen(fixture("highs.json")); val low = Parse.screen(fixture("lows.json"))
        val h = high.hits.single { it.stock.symbol == "BCSSL" }; val l = low.hits.single { it.stock.symbol == "BCSSL" }
        assertEquals(high.stamp, low.stamp); assertEquals(95.55, h.level, 0.0); assertEquals(91.0, l.level, 0.0)
        assertEquals(0.0, h.previousLevel, 0.0); assertEquals("-", h.previousDate)
        assertTrue(h.priorRangeMissing()); assertTrue(l.priorRangeMissing())
    }
    @Test fun authoritativeListingDateAndTradeToTradeSeriesArePreserved() {
        val source = File("../app/src/main/assets/equities.csv").takeIf { it.exists() } ?: File("app/src/main/assets/equities.csv")
        val stock = Parse.catalog(source.readText()).single { it.symbol == "BCSSL" }
        assertEquals("BE", stock.series); assertEquals("2026-11-05", stock.listed); assertEquals("INE0XFY01011", stock.isin)
        val h = Parse.screen(fixture("highs.json")).hits.single { it.stock.symbol == "BCSSL" }.also { it.stock = stock }
        assertFalse(MarketExtremes.hasYearHistory(h, LocalDate.parse("2026-11-05")))
        val note = MarketExtremes.note(h, true, true, LocalDate.parse("2026-11-05"))
        assertTrue(note.contains("range since listing")); assertTrue(note.contains("both intraday")); assertTrue(note.contains("2026-11-05"))
    }
    @Test fun establishedRangeRequiresPriorReferenceAndExcludesKnownYoungListings() {
        val h = Data.Hit().also { it.stock = Data.Stock("EXAMPLE", "Example", ""); it.stock.listed = "2020-01-01"; it.previousLevel = 50.0; it.previousDate = "2025-11-06" }
        val session = LocalDate.parse("2026-11-05")
        assertTrue(MarketExtremes.hasYearHistory(h, session))
        h.stock.listed = "2026-01-01"; assertFalse(MarketExtremes.hasYearHistory(h, session))
        h.stock.listed = "2020-01-01"; h.previousDate = "-"; assertFalse(MarketExtremes.hasYearHistory(h, session))
    }
    @Test fun missingReferenceDoesNotInventANewListingDate() {
        val h = Data.Hit().also { it.stock = Data.Stock("EXAMPLE", "Example", ""); it.level = 10.0 }
        val note = MarketExtremes.note(h, false, false, LocalDate.parse("2026-11-05"))
        assertTrue(note.contains("Previous range unavailable")); assertFalse(note.contains("Listed"))
    }
    @Test fun originalPastIpoRowConfirmsBcsslListingDateAndMainboardClassification() {
        val issue = Research.issues(fixture("bcssl-ipo.json"), "past").rows.single()
        assertEquals("2026-11-05", issue.listing); assertEquals("Listed", issue.phase(LocalDate.parse("2026-11-05")))
        assertEquals("Mainboard", ipoBoard(issue))
    }
    @Test fun subscriptionPercentageMayExceedOneHundred() {
        val x = IpoSubscription.multiple(482_000.0, 100_000.0)
        assertEquals(4.82, x, 1e-10); assertEquals(482.0, IpoSubscription.percentage(x), 1e-9)
        assertEquals(0.0, IpoSubscription.multiple(0.0, 10.0), 0.0)
    }
    @Test fun unknownOrInvalidDenominatorsNeverBecomeZeroSubscription() {
        assertTrue(IpoSubscription.multiple(10.0, 0.0).isNaN())
        assertTrue(IpoSubscription.multiple(Double.NaN, 10.0).isNaN())
        assertTrue(IpoSubscription.multiple(-1.0, 10.0).isNaN())
        assertTrue(IpoSubscription.percentage(Double.MAX_VALUE).isNaN())
    }
    @Test fun publishedScopeIsPreservedAndCountsAreUsedOnlyWhenItIsMissing() {
        val i = Research.Ipo().also { it.bids = 2005.0; it.offered = 1000.0; it.subscription = 2.0 }
        assertEquals(2.0, IpoSubscription.reported(i), 0.0)
        i.subscription = Double.NaN; assertEquals(2.005, IpoSubscription.reported(i), 1e-12)
    }
    @Test fun lotteryUsesApplicationBreadthNotReciprocalShareDemand() {
        val odds = IpoSubscription.lottery(1000.0, 100.0, 1.0, 200.0)!!
        assertEquals(10L, odds.capacity); assertEquals(5.0, odds.probability, 0.0)
        // Applicants request two lots on average: reciprocal 40x share demand would wrongly give 2.5%.
        assertNotEquals(100.0 / IpoSubscription.multiple(40_000.0, 1000.0), odds.probability, 1e-9)
    }
    @Test fun smeTwoLotMinimumAndRoundingAreRespected() {
        val odds = IpoSubscription.lottery(5500.0, 1000.0, 2.0, 10.0)!!
        assertEquals(2L, odds.capacity); assertEquals(20.0, odds.probability, 0.0)
        assertEquals(100.0, IpoSubscription.lottery(20_000.0, 100.0, 1.0, 10.0)!!.probability, 0.0)
    }
    @Test fun missingZeroFractionalAndImpossibleLotteryInputsRemainUnavailable() {
        assertNull(IpoSubscription.lottery(1000.0, 100.0, 1.0, 0.0))
        assertNull(IpoSubscription.lottery(1000.0, 100.0, 1.0, 1.5))
        assertNull(IpoSubscription.lottery(1000.0, 100.0, Double.NaN, 20.0))
        assertNull(IpoSubscription.lottery(10.0, 100.0, 1.0, 20.0))
        assertNull(IpoSubscription.lottery(1000.0, 9e15, 9e15, 20.0))
    }
    @Test fun bseNamedColumnsProduceIndependentSourceLinkedIssue() {
        val i = BseIssues.parse(capture(headers, row), 1234L).single()
        assertEquals("BSE", i.exchange); assertEquals("SME", ipoBoard(i)); assertEquals("2026-11-05", i.end)
        assertEquals(66.0, i.high, 0.0); assertEquals(2000.0, i.lot, 0.0); assertEquals(4.82, i.subscription, 0.0)
        assertEquals(1234L, i.fetched); assertTrue(i.captured); assertEquals(BseIssues.PAGE, i.url())
        assertEquals("", i.symbol); assertTrue(i.offered.isNaN())
    }
    @Test fun genericPercentageColumnIsNotMistakenForAMultiple() {
        val r = row.toMutableList().also { it[5] = "482%" }
        assertEquals(4.82, BseIssues.parse(capture(headers, r)).single().subscription, 1e-12)
    }
    @Test fun issueSizeInRupeesIsNeverAssumedToBeOfferedShares() {
        val h = headers + listOf("Issue Size (Rs Cr)", "Shares Bid")
        val i = BseIssues.parse(capture(h, row + listOf("22.98", "1,00,000"))).single()
        assertTrue(i.offered.isNaN()); assertEquals(100_000.0, i.bids, 0.0)
    }
    @Test fun explicitlyNamedSharesAndPercentageHaveCorrectUnits() {
        val h = headers.dropLast(1) + listOf("Shares Offered", "Shares Bid", "Subscription Percentage")
        val i = BseIssues.parse(capture(h, row.dropLast(1) + listOf("1,00,000", "4,82,000", "482"))).single()
        assertEquals(4.82, IpoSubscription.reported(i), 1e-12)
    }
    @Test fun bseExtensionsUpdateTheSameIdentityAndRoundTripWithoutLosingProvenance() {
        val before = row.toMutableList().also { it[2] = "02-Nov-2026" }
        val a = BseIssues.parse(capture(headers, before)).single(); val b = BseIssues.parse(capture(headers, row)).single()
        assertEquals(a.id(), b.id()); assertNotEquals(a.end, b.end)
        val saved = Research.Ipo.from(b.json()); assertEquals(b.id(), saved.id()); assertEquals(b.url(), saved.url())
        assertTrue(saved.captured); assertEquals("SME", saved.board); assertEquals(b.lot, saved.lot, 0.0)
        val state = InvestorState("{}")
        state.track(a, true); state.track(b, true)
        assertEquals(1, state.tracked().size); assertEquals("2026-11-05", state.tracked().single().end)
    }
    @Test fun bseAndNseDoNotSilentlyMergeOrSumSubscriptions() {
        val b = BseIssues.parse(capture(headers, row)).single().also { it.symbol = "FIXTURE" }
        val n = Research.Ipo().also { it.symbol = "FIXTURE"; it.name = b.name; it.start = b.start; it.end = b.end; it.subscription = 1.0 }
        val nf = Research.Feed<Research.Ipo>().also { it.rows = listOf(n) }; val bf = Research.Feed<Research.Ipo>().also { it.rows = listOf(b) }
        val merged = Research.merge(listOf(nf, bf)); assertEquals(2, merged.size); assertEquals(1.0, n.subscription, 0.0)
        assertEquals("FIXTURE|2026-10-29|2026-11-05", n.id())
    }
    @Test fun duplicateTableRowsDoNotCreateDuplicateIssues() {
        assertEquals(1, BseIssues.parse(capture(headers, row, row)).size)
    }
    @Test(expected = IllegalArgumentException::class) fun unrelatedOrSpoofedOriginIsRejected() {
        BseIssues.parse(capture(headers, row, url = "https://www.bseindia.com.example.com/publicissue"))
    }
    @Test(expected = IllegalArgumentException::class) fun bseAccountPagesAreNotTableCaptureSources() {
        BseIssues.parse(capture(headers, row, url = "https://www.bseindia.com/account"))
    }
    @Test(expected = IllegalArgumentException::class) fun javascriptShellOrAccessDeniedPageIsNotAnEmptyMarket() {
        BseIssues.fromHtml("<html><body>Access denied</body></html>", BseIssues.PAGE, 0)
    }
    @Test(expected = IllegalArgumentException::class) fun invalidSourceDatesDoNotCreateLiveIssues() {
        val bad = row.toMutableList().also { it[1] = "30-Feb-2026" }
        BseIssues.parse(capture(headers, bad))
    }
    @Test(expected = IllegalArgumentException::class) fun shiftedNestedRowsDoNotGuessFinancialColumns() {
        BseIssues.parse(capture(headers, row + listOf("Nested cell")))
    }
    @Test fun originalTableHtmlAndDomCaptureHaveEquivalentFacts() {
        val html = "<table><caption>SME</caption><tr>" + headers.joinToString("") { "<th>$it</th>" } + "</tr><tr>" + row.joinToString("") { "<td>$it</td>" } + "</tr></table>"
        val captured = BseIssues.parse(capture(headers, row)).single()
        val downloaded = BseIssues.fromHtml(html, BseIssues.PAGE, 1000).single()
        assertEquals(captured.id(), downloaded.id()); assertEquals(captured.range, downloaded.range)
        assertEquals(captured.board, downloaded.board)
        assertFalse(downloaded.captured); assertEquals("BSE table download", downloaded.origin)
    }
}
