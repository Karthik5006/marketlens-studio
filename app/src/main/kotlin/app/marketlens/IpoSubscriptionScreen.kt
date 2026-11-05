package app.marketlens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import org.json.JSONObject
import java.time.LocalDate

internal object IpoPools {
    fun id(issue: Research.Ipo, category: String) = "ipoPool:${issue.id()}:${BseIssues.key(category, "allocation")}"
}

@Composable internal fun IpoSubscriptionInsights(i: Research.Ipo, info: Research.IssueInfo?, s: LensState, vm: LensViewModel, a: UiActions) {
    SectionTitle("Subscription & allotment", "Separate share demand from application odds")
    val multiple = IpoSubscription.reported(i)
    LensCard(accent = Violet) {
        MetricPair("Total subscription", if (multiple.isFinite()) number(multiple) + "×" else "Unavailable",
            "Subscription percentage", percent(IpoSubscription.percentage(multiple)))
        if (i.bids.isFinite() && i.offered.isFinite()) {
            MetricPair("Source shares bid", number(i.bids, 0), "Source shares offered", number(i.offered, 0))
            val calculated=IpoSubscription.multiple(i.bids,i.offered)
            Caption("Bid shares ÷ stated offered shares gives ${number(calculated)}×. Percentage = multiple × 100; 4.82× means 482% subscribed.")
            if(i.subscription.isFinite()&&kotlin.math.abs(calculated-i.subscription)>.02)Caption("The stated-count ratio differs from the source's reported multiple. The reported value is retained; review the source pool, exclusions and update times.",Pink)
        } else Caption("Percentage converts the publisher's reported multiple when available. A missing count or multiple is not zero.")
        Caption(if(i.subscription.isFinite())"Published multiple retained; its percentage is a conversion, not a separately reported exchange figure."else "No published multiple available; a ratio is calculated only when both source counts exist.")
        Caption("This is demand for shares, not the chance that one application receives an allotment. BSE/NSE quantities are not added together; their reported scope can already include consolidated bids.")
    }
    val categories = info?.bids.orEmpty()
    categories.forEach { row ->
        val category = row[0]
        val record = s.json(IpoPools.id(i, category))
        val allocated = Analytics.num(record, "offered")
        val ratio = IpoSubscription.multiple(Parse.number(row[1]), allocated)
        LensCard {
            Text(category, color = Ink, fontWeight = FontWeight.SemiBold)
            MetricPair("Reported shares bid", number(Parse.number(row[1]), 0), "Applications received", number(Parse.number(row[2]), 0))
            MetricPair("Category subscription", if (ratio.isFinite()) number(ratio) + "×" else "Allocation needed", "% of category shares", percent(IpoSubscription.percentage(ratio)))
            if (allocated.isFinite()) Caption("Denominator: your recorded category allocation ${number(allocated, 0)} shares. Verify it against any revised offer terms.")
            Action("Record allocation / estimate odds", { a.form(ipoPoolForm(i, info, category, row, s, vm)) }, icon = Icons.Rounded.Calculate)
        }
    }
    Caption("Only parent categories and the source total are shown; child categories are not added again. Applications received may include bids later rejected. Category ratios need that category's share allocation; total issue size is not used as a substitute.")
    Action("Add a category / allotment scenario", { a.form(ipoPoolForm(i, info, "Individual / retail", null, s, vm)) }, primary = true, icon = Icons.Rounded.Calculate)
    val pools = s.rows("ipoPool").filter { JSONObject(it.json).optString("issueId") == i.id() }
    pools.forEach { record ->
        val j = JSONObject(record.json)
        val ratio = IpoSubscription.multiple(Analytics.num(j, "bids"), Analytics.num(j, "offered"))
        val lottery = if (j.optString("model") == "Equal minimum-unit lottery")
            IpoSubscription.lottery(Analytics.num(j, "offered"), Analytics.num(j, "lot"), Analytics.num(j, "minimumLots"), Analytics.num(j, "applications")) else null
        LensCard(accent = Violet) {
            Label("Your source-backed scenario", Violet)
            Text(j.optString("category"), color = Ink, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            MetricPair("Snapshot subscription", if (ratio.isFinite()) number(ratio) + "×" else "—", "% of category allocation", percent(IpoSubscription.percentage(ratio)))
            if (lottery != null) {
                MetricPair("Conditional allotment estimate", percent(lottery.probability), "Minimum allotments possible", number(lottery.capacity.toDouble(), 0), Violet)
                Caption("${number(lottery.applications.toDouble(), 0)} ${j.optString("applicationsBasis").lowercase()} · ${number(Analytics.num(j, "minimumLots"), 0)} lots × ${number(Analytics.num(j, "lot"), 0)} shares per minimum allotment.")
                Caption("Model: min(1, floor(category shares ÷ minimum allotment shares) ÷ applications). Assumes equal lottery chances, correct eligibility and the stated allocation. It does not multiply your chance by the number of lots you apply for.")
            } else Caption(if (j.optString("model") == "Other / proportional") "A lottery probability does not apply to the recorded proportional/other model." else "Odds unavailable until positive whole-share allocation, minimum units and application counts are supplied.")
            if (j.optString("applicationsBasis") == "Received — provisional") Caption("Provisional scenario: received applications are not the final valid-application count. Rejections, withdrawals, final demand and reallocation can change the outcome.", Pink)
            Caption("This is a calculation from your recorded inputs, not an exchange or registrar probability. Do not use 1 ÷ total share subscription as the chance.")
            SourceNote(j.optString("source"), j.optString("asOf"), open = a.url)
            Action("Edit scenario", { a.form(ipoPoolForm(i, info, j.optString("category"), null, s, vm)) })
            TextButton({ vm.delete(record.id) }) { Text("Remove scenario", color = Muted) }
        }
    }
    Action("Official NSE SME application rules", { a.url("https://www.nseindia.com/static/trade/e-ipo-faqs") })
}

private fun ipoPoolForm(i: Research.Ipo, info: Research.IssueInfo?, category: String, row: Array<String>?, s: LensState, vm: LensViewModel): FormSpec {
    val old = s.json(IpoPools.id(i, category))
    fun value(key: String, fallback: String = "") = old.optString(key).ifBlank { fallback }
    val defaultLot = info?.lot?.takeIf { it.isFinite() } ?: i.lot
    val individual = (category.contains("Retail", true) || category.contains("Individual", true)) && !category.contains("Non", true)
    val proportional = !individual || category.contains("Qualified", true) || category.contains("QIB", true) || category.equals("Total", true)
    return FormSpec("Category subscription & odds", "${i.name}\nUse one category and one reporting snapshot. Copy its allocation from the original offer terms. This is an assumption-based estimate, not a registrar result. SME individual applications can require two lots; verify the issue's rules.", listOf(
        FormField("category", "Category", category),
        FormField("offered", "Shares available to this category", value("offered"), number = true),
        FormField("bids", "Shares bid in this category (optional)", value("bids", row?.getOrNull(1).orEmpty()), number = true),
        FormField("applications", "Applications in this category (optional)", value("applications", row?.getOrNull(2).orEmpty()), number = true),
        FormField("applicationsBasis", "Application-count basis", value("applicationsBasis", "Received — provisional"), listOf("Received — provisional", "Valid — as recorded in source")),
        FormField("lot", "Shares per lot", value("lot", if (defaultLot.isFinite()) defaultLot.toLong().toString() else ""), number = true),
        FormField("minimumLots", "Lots per minimum allotment", value("minimumLots", when(ipoBoard(i)){"SME"->"2";"Mainboard"->"1";else->""}), number = true),
        FormField("model", "Allocation model from original terms", value("model", if (proportional) "Other / proportional" else "Equal minimum-unit lottery"), listOf("Equal minimum-unit lottery", "Other / proportional")),
        FormField("asOf", "Source as-of date/time", value("asOf", info?.stamp?.takeIf{it.isNotBlank()} ?: readTime()), helper = "Use the source update time. A 'read' time records your observation, not an exchange update."),
        FormField("source", "Original offer / subscription HTTPS source", value("source", i.url()))
    ), "Save calculation") { v ->
        require(v.getValue("category").isNotBlank()) { "Category is required" }
        require(safeUrl(v.getValue("source"))) { "An original HTTPS source is required" }
        require(v.getValue("asOf").isNotBlank()) { "Record the source as-of time or your read time" }
        val j = JSONObject().put("issueId", i.id())
        v.forEach { (k, x) ->
            if (k in listOf("offered", "bids", "applications", "lot", "minimumLots")) {
                if (x.isNotBlank()) { val n = Parse.number(x); require(n.isFinite() && n >= 0 && n <= 9e15 && n == kotlin.math.floor(n)) { "Use non-negative whole counts" }; j.put(k, n) }
            } else j.put(k, x)
        }
        require(Analytics.num(j, "offered") > 0) { "A positive category share allocation is required" }
        if (v.getValue("model") == "Equal minimum-unit lottery") {
            require(!v.getValue("category").contains("QIB", true) && !v.getValue("category").contains("Qualified", true) && !v.getValue("category").equals("Total", true)) { "Use a category with a confirmed lottery basis, not QIB or the total issue." }
            require(IpoSubscription.lottery(Analytics.num(j, "offered"), Analytics.num(j, "lot"), Analytics.num(j, "minimumLots"), Analytics.num(j, "applications")) != null) { "For odds, enter positive whole lot/application counts with capacity for at least one minimum allotment." }
        }
        vm.save("ipoPool", IpoPools.id(i, v.getValue("category")), j)
    }
}

internal fun bseIssueForm(initialName: String, vm: LensViewModel, old: Research.Ipo? = null): FormSpec = FormSpec(
    "Add original-source IPO facts", "Read the exchange page or issuer document and enter its published facts. These will be labelled as your entries. No issue or subscription is silently inferred from an unrelated issuer.", listOf(
        FormField("name", "Issuer name", old?.name ?: initialName), FormField("symbol", "Official BSE symbol / code (optional)", old?.symbol.orEmpty()),
        FormField("board", "Board", old?.board?.ifBlank { "Unclassified" } ?: "Unclassified", listOf("SME", "Mainboard", "Unclassified")),
        FormField("start", "Issue opens · YYYY-MM-DD", old?.start.orEmpty()), FormField("end", "Issue closes · YYYY-MM-DD", old?.end.orEmpty()),
        FormField("listing", "Listing date · YYYY-MM-DD (optional)", old?.listing.orEmpty()), FormField("range", "Published price band / fixed price", old?.range.orEmpty()),
        FormField("lot", "Shares per lot (optional)", old?.lot?.takeIf { it.isFinite() }?.toLong()?.toString().orEmpty(), number = true),
        FormField("bids", "Source total shares bid (optional)", old?.bids?.takeIf { it.isFinite() }?.toLong()?.toString().orEmpty(), number = true),
        FormField("offered", "Source offered shares for that total (optional)", old?.offered?.takeIf { it.isFinite() }?.toLong()?.toString().orEmpty(), number = true),
        FormField("subscription", "Reported subscription multiple × (optional)", old?.subscription?.takeIf { it.isFinite() }?.toString().orEmpty(), number = true),
        FormField("stamp", "Source update time / your read time", old?.sourceStamp?.takeIf{it.isNotBlank()} ?: readTime()), FormField("url", "Original BSE / issuer HTTPS source", old?.url() ?: BseIssues.PAGE)
    ), "Save original-source facts") { v ->
        val start = LocalDate.parse(v.getValue("start")); val end = LocalDate.parse(v.getValue("end"))
        require(!end.isBefore(start)) { "Closing date cannot precede opening" }
        val listing = v.getValue("listing"); if (listing.isNotBlank()) require(!LocalDate.parse(listing).isBefore(end)) { "Check the listing date" }
        require(v.getValue("name").isNotBlank() && v.getValue("name").length <= 300)
        require(safeUrl(v.getValue("url"))) { "Original HTTPS source is required" }
        require(v.getValue("stamp").isNotBlank()) { "Record the source update time or when you read the page" }
        fun count(k: String): Double = if (v.getValue(k).isBlank()) Double.NaN else Parse.number(v.getValue(k)).also { require(it.isFinite() && it >= 0) { "Invalid $k" }; if (k != "subscription") require(it == kotlin.math.floor(it) && it <= 9e15) }
        val issue = Research.Ipo().also {
            it.exchange = "BSE"; it.name = v.getValue("name"); it.symbol = v.getValue("symbol").uppercase(); it.start = start.toString(); it.end = end.toString()
            it.sourceId = old?.sourceId?.takeIf { it.isNotBlank() } ?: BseIssues.key(it.name, it.start); it.board = v.getValue("board"); it.series = if (it.board == "SME") "SME" else ""
            it.listing = listing; it.range = v.getValue("range"); val b = Research.band(it.range); it.low = b[0]; it.high = b[1]
            it.lot = count("lot"); it.bids = count("bids"); it.offered = count("offered"); it.subscription = count("subscription")
            it.sourceUrl = v.getValue("url"); it.sourceStamp = v.getValue("stamp"); it.origin = "Your original-source entry"; it.fetched = System.currentTimeMillis(); it.saved = true
        }
        vm.saveExchangeIssue(issue)
    }

private fun readTime() = "read " + java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata"))
    .format(java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm 'IST'"))
