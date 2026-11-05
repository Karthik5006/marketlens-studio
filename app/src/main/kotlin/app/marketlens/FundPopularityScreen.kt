package app.marketlens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable internal fun FundPopularityScreen(s: LensState, vm: LensViewModel, a: UiActions) {
    val catalog by produceState<List<Data.Portfolio>?>(null, s.funds, s.snapshots) {
        value = withContext(Dispatchers.Default) { FundPopularity.catalog(s.funds, s.snapshots) }
    }
    val portfolios = catalog
    if (portfolios == null) { Caption("Preparing disclosure coverage…"); return }
    val months = remember(portfolios) { FundPopularity.months(portfolios) }
    if (months.isEmpty()) { EmptyState("A ranking needs dated disclosures", "Sync or import a monthly equity portfolio to begin."); return }
    var selectedMonth by rememberSaveable { mutableStateOf("") }
    val month = selectedMonth.takeIf { it in months } ?: months.first()
    var categoryName by rememberSaveable { mutableStateOf(FundPopularity.Category.OVERALL.name) }
    val category = FundPopularity.Category.valueOf(categoryName)
    val classifications = remember(s.records) { s.rows("fundClassification") }
    val equityIsins = remember(vm) { vm.repo.legacy.stocks.values.map { it.isin }.toSet() }
    val report by produceState<FundPopularity.Report?>(null, portfolios, classifications, month, category) {
        value = null
        value = withContext(Dispatchers.Default) { FundPopularity.rank(portfolios, classifications, month, category, equityIsins) }
    }
    LensCard(accent = Lime) {
        Label("MUTUAL FUND RADAR", Lime)
        Text("Where funds\ncome together.", color = Ink, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
        Caption("Discover stocks held across the most covered schemes. Follow the breadth, then inspect the holdings.")
        Action("Reporting month · ${monthLabel(month)}", {
            a.form(FormSpec("Reporting month", "Only disclosures from the chosen month are combined.",
                listOf(FormField("month", "Saved reporting periods", month, months)), "Apply") {
                selectedMonth = it.getValue("month")
            })
        }, icon = Icons.Rounded.CalendarMonth)
    }
    ChoiceRow(FundPopularity.Category.entries.map { it.label }, category.label) { label ->
        categoryName = FundPopularity.Category.entries.first { it.label == label }.name
    }
    Caption("Filters describe the scheme's category, not the market cap of each stock. Overall includes other and unclassified equity-bearing schemes.")
    val r = report
    if (r == null) { CircularProgressIndicator(Modifier.size(22.dp), color = Lime, strokeWidth = 2.dp); return }
    LensCard {
        MetricPair("Covered ${category.label.lowercase()} schemes", r.schemes.size.toString(), "Known fund houses", r.amcCount.toString(), Lime)
        Caption("${r.stocks.size} recognized NSE stocks · ${monthLabel(r.month)}. Coverage is saved disclosures, not the entire mutual fund industry.")
        if (r.matchedSchemes > 0) Caption("Monthly changes: ${r.matchedSchemes} schemes with recognized equities in both ${monthLabel(YearMonth.parse(month).minusMonths(1).toString())} and ${monthLabel(month)}. New coverage does not count as new interest.")
        else Caption("Monthly changes need the same schemes in the immediately preceding month. No missing period is treated as zero.")
        if (r.excludedSnapshots > 0) Caption("${r.excludedSnapshots} monthly scheme snapshots have no recognized NSE equity positions and are excluded.")
    }
    var showCoverage by rememberSaveable { mutableStateOf(false) }
    var coverageLimit by rememberSaveable(month, categoryName) { mutableIntStateOf(10) }
    Action(if (showCoverage) "Hide scheme coverage" else "Inspect coverage & categories", { showCoverage = !showCoverage }, icon = Icons.Rounded.FactCheck)
    if (showCoverage) {
        Caption("Explicit small/mid/large/flexi names are inferred. Index/ETF and large & mid-cap schemes are Other. Review labels against the original scheme document. Unknown AMC identities are not merged across imports.")
        r.schemes.sortedBy { it.portfolio.name }.take(coverageLimit).forEach { scheme ->
            LensCard {
                Text(scheme.portfolio.name, color = Ink, fontWeight = FontWeight.SemiBold)
                Caption("${scheme.classification.category.label} · ${scheme.classification.amc.ifBlank { "Fund house unverified" }} · ${if (scheme.classification.manual) "Your classification" else "Inferred label"}")
                SourceNote(scheme.portfolio.source, scheme.portfolio.date, scheme.portfolio.fetched, open = a.url)
                Action("Edit scheme classification", { a.form(fundClassificationForm(scheme.portfolio, s, vm)) })
            }
        }
        if (r.schemes.size > coverageLimit) Action("Show 10 more schemes", { coverageLimit += 10 })
        if (r.schemes.isEmpty()) Caption("Use Explore to classify saved schemes, or import disclosures for this category and month.")
    }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf("Most held") }
    var universe by rememberSaveable { mutableStateOf("All stocks") }
    var minChoice by rememberSaveable { mutableStateOf("1+ schemes") }
    var limit by rememberSaveable(month, categoryName, query, sort, universe, minChoice) { mutableIntStateOf(20) }
    val directory = remember(vm) { vm.repo.legacy.stocks.values.associateBy { it.isin } }
    val watch = s.watch()
    OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
        label = { Text("Search stock, NSE symbol or ISIN") }, leadingIcon = { Icon(Icons.Rounded.Search, null) })
    ChoiceRow(listOf("All stocks", "Watchlist"), universe) { universe = it }
    ChoiceRow(listOf("Most held", "Net additions", "Average weight"), sort) { sort = it }
    ChoiceRow(listOf("1+ schemes", "3+ schemes", "5+ schemes", "10+ schemes"), minChoice) { minChoice = it }
    val minimum = minChoice.substringBefore('+').toInt()
    val rows = remember(r, query, sort, universe, minimum, watch) {
        val filtered = r.stocks.filter { row ->
            row.count >= minimum && (universe != "Watchlist" || directory[row.isin]?.symbol in watch) &&
                (row.name + " " + row.isin + " " + directory[row.isin]?.symbol.orEmpty()).contains(query.trim(), true)
        }
        when (sort) {
            "Net additions" -> filtered.sortedWith(compareByDescending<FundPopularity.Stock> { it.delta ?: Int.MIN_VALUE }.thenByDescending { it.count }.thenBy { it.isin })
            "Average weight" -> filtered.sortedWith(compareByDescending<FundPopularity.Stock> { if (it.averageWeight.isFinite()) it.averageWeight else -1.0 }.thenByDescending { it.count }.thenBy { it.isin })
            else -> filtered
        }
    }
    SectionTitle("${rows.size} stocks", "${category.label} · ${monthLabel(month)}")
    if (rows.isEmpty()) EmptyState("No stocks match this view", "Lower the minimum, choose Overall or another month, or add official disclosures. An empty view is not evidence that no fund holds a stock.")
    if (rows.isNotEmpty()) Action("Export ranking CSV", { a.export("fund-radar-$month-${category.name.lowercase()}.csv", FundPopularity.csv(r, rows), "text/csv") }, icon = Icons.Rounded.Download)
    Caption("Average weight = mean disclosed % of fund assets among holding schemes with a valid weight. It is not % of the company, AUM-weighted exposure or a buy signal. Monthly additions/absences refer to recognized rows; incomplete files can affect comparisons.")
    rows.take(limit).forEachIndexed { index, row ->
        key(row.isin) {
            var expanded by rememberSaveable(month, categoryName, row.isin) { mutableStateOf(false) }
            var holderLimit by rememberSaveable(month, categoryName, row.isin) { mutableIntStateOf(10) }
            val stock = directory[row.isin]
            LensCard(accent = if (index < 3) Lime else null) {
                Label("#${index + 1} · ${stock?.symbol ?: row.isin}", Lime)
                Text(stock?.name?.ifBlank { row.name } ?: row.name, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                AllocationBar("Held by ${row.count} of ${r.schemes.size} covered schemes", row.count.toDouble(), r.schemes.size.toDouble())
                MetricPair("Known fund houses", row.amcCount.toString(), "Avg. fund asset weight", percent(row.averageWeight))
                Caption("AMC identified for ${row.knownAmcHolders}/${row.count} holders · weight available for ${row.knownWeights.size}/${row.count}.")
                if (row.delta != null) Caption("Matched-month breadth: ${if (row.delta!! > 0) "+" else ""}${row.delta} net · ${row.added} added / ${row.absent} absent", if (row.delta!! > 0) Lime else Muted)
                if (category == FundPopularity.Category.OVERALL) {
                    Caption(FundPopularity.Category.entries.filter { it != FundPopularity.Category.OVERALL }.mapNotNull { c ->
                        row.holders.count { it.scheme.classification.category == c }.takeIf { it > 0 }?.let { "${c.label} $it" }
                    }.joinToString(" · "))
                }
                if (stock != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Action("Open stock", { a.stock(stock.symbol) }, icon = Icons.Rounded.NorthEast)
                    Action(if (stock.symbol in watch) "Following" else "Follow", { vm.toggleWatch(stock.symbol) }, icon = if (stock.symbol in watch) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder)
                }
                Action(if (expanded) "Hide holding schemes" else "See holding schemes", { expanded = !expanded }, icon = Icons.Rounded.Layers)
                if (expanded) {
                    row.holders.take(holderLimit).forEach { holder ->
                        HorizontalDivider(color = Line)
                        Text(holder.scheme.portfolio.name, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Caption("${holder.scheme.classification.category.label} · ${holder.scheme.classification.amc.ifBlank { "AMC unverified" }} · ${percent(holder.weight)} of fund assets")
                        SourceNote(holder.scheme.portfolio.source, holder.scheme.portfolio.date, holder.scheme.portfolio.fetched, open = a.url)
                    }
                    if (row.count > holderLimit) Action("Show 10 more holding schemes", { holderLimit += 10 })
                }
            }
        }
    }
    if (rows.size > limit) Action("Show 20 more stocks", { limit += 20 }, modifier = Modifier.fillMaxWidth())
    Caption("One scheme = one vote. Direct/regular plans and growth/IDCW options with the same normalized name and known AMC are merged; the latest dated snapshot in the selected month wins. Differently named or unverified imports need identity review. Historical periods include retained disclosures of currently saved schemes.")
}

private fun monthLabel(value: String) = YearMonth.parse(value).format(DateTimeFormatter.ofPattern("MMM uuuu", Locale.ENGLISH))

internal fun fundClassificationForm(p: Data.Portfolio, s: LensState, vm: LensViewModel): FormSpec {
    val c = FundPopularity.classification(p, s.records)
    val categories = FundPopularity.Category.entries.filter { it != FundPopularity.Category.OVERALL }
    return FormSpec("Scheme classification", "${p.name}\nUse the original scheme category and a consistent AMC name. These labels apply to this scheme's saved history. They do not change the source disclosure.",
        listOf(FormField("category", "Scheme category", c.category.label, categories.map { it.label }),
            FormField("amc", "Fund house / AMC", c.amc, helper = "Use the same label for every scheme from a fund house, e.g. HDFC. Blank uses a recognized official host, otherwise the identity stays unverified.")), "Save classification") { values ->
        require(values.getValue("amc").length <= 100) { "AMC name is too long" }
        vm.save("fundClassification", "fundClass:${p.id}", JSONObject().put("category", categories.first { it.label == values.getValue("category") }.name).put("amc", values.getValue("amc")))
    }
}
