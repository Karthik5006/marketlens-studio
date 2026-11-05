package app.marketlens

import org.json.JSONObject
import java.net.URI
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

/** Monthly scheme breadth, never the company's shareholding percentage or an AUM estimate. */
internal object FundPopularity {
    enum class Category(val label: String) {
        OVERALL("Overall"), SMALL("Small-cap"), MID("Mid-cap"), LARGE("Large-cap"),
        FLEXI("Flexi-cap"), OTHER("Other"), UNKNOWN("Unclassified")
    }

    data class Classification(val category: Category, val amc: String, val manual: Boolean = false)
    data class Scheme(val key: String, val portfolio: Data.Portfolio, val classification: Classification,
                      val weights: Map<String, Double>)
    data class Holder(val scheme: Scheme, val weight: Double)
    data class Stock(val isin: String, val name: String, val holders: List<Holder>,
                     val added: Int?, val absent: Int?) {
        val count get() = holders.size
        val amcCount get() = holders.map { it.scheme.classification.amc.lowercase(Locale.ROOT) }.filter { it.isNotBlank() }.distinct().size
        val knownAmcHolders get() = holders.count { it.scheme.classification.amc.isNotBlank() }
        val knownWeights get() = holders.map { it.weight }.filter { it.isFinite() }
        val averageWeight get() = knownWeights.takeIf { it.isNotEmpty() }?.average() ?: Double.NaN
        val delta get() = if (added != null && absent != null) added - absent else null
    }
    data class Report(val month: String, val category: Category, val schemes: List<Scheme>,
                      val matchedSchemes: Int, val stocks: List<Stock>, val excludedSnapshots: Int) {
        val amcCount get() = schemes.map { it.classification.amc.lowercase(Locale.ROOT) }.filter { it.isNotBlank() }.distinct().size
    }

    fun classification(p: Data.Portfolio, records: List<LocalRecord>): Classification {
        val override = records.firstOrNull { it.id == "fundClass:${p.id}" }
            ?.let { runCatching { JSONObject(it.json) }.getOrNull() }
        val category = override?.optString("category")?.let { value ->
            Category.entries.firstOrNull { it.name == value && it != Category.OVERALL }
        }
        val host = runCatching { URI(p.source).host.orEmpty().lowercase(Locale.ROOT) }.getOrDefault("")
        val name = p.name.lowercase(Locale.ROOT)
        val amc = when {
            host == "hdfcfund.com" || host.endsWith(".hdfcfund.com") -> "HDFC"
            host == "ppfas.com" || host.endsWith(".ppfas.com") -> "PPFAS"
            else -> ""
        }
        // Infer only explicit scheme categories. An index/ETF or large-and-mid fund is a different category.
        val inferred = when {
            Regex("\\b(index|etf|large[ -]*(?:(?:&|and)[ -]*)?mid[ -]*cap)\\b").containsMatchIn(name) -> Category.OTHER
            Regex("\\bsmall[ -]*cap\\b").containsMatchIn(name) -> Category.SMALL
            Regex("\\bmid[ -]*cap\\b").containsMatchIn(name) -> Category.MID
            Regex("\\blarge[ -]*cap\\b").containsMatchIn(name) -> Category.LARGE
            Regex("\\bflexi[ -]*cap\\b").containsMatchIn(name) -> Category.FLEXI
            else -> Category.UNKNOWN
        }
        return Classification(category ?: inferred, override?.optString("amc")?.trim()?.takeIf { it.isNotEmpty() } ?: amc, override != null)
    }

    /** Plan/option aliases are merged only inside a known or explicitly supplied fund house. */
    fun schemeKey(p: Data.Portfolio, c: Classification): String {
        if (c.amc.isBlank()) return "unverified:${p.id}"
        var name = p.name.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), " ").trim()
        repeat(3) {
            name = name.replace(Regex("\\s+(growth|idcw|dividend)(?:\\s+(option|reinvestment|payout))?$"), "")
                .replace(Regex("\\s+(direct|regular)(?:\\s+plan)?$"), "").trim()
        }
        return c.amc.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ") + ":" + name
    }

    fun catalog(current: List<Data.Portfolio>, snapshots: List<SourceSnapshot>,
                today: LocalDate = LocalDate.now(ZoneId.of("Asia/Kolkata"))): List<Data.Portfolio> {
        val active = current.map { it.id }.toSet()
        val archived = snapshots.asSequence().filter { it.key.startsWith("fund:") }.mapNotNull {
            runCatching { Data.Portfolio.from(JSONObject(it.payload)) }.getOrNull()
        }.filter { it.id in active }.toList()
        return (current + archived).filter { p ->
            p.id.isNotBlank() && runCatching { !LocalDate.parse(p.date).isAfter(today) }.getOrDefault(false)
        }.groupBy { it.id to it.date }.values.map { versions ->
            versions.maxWith(compareBy<Data.Portfolio> { it.fetched }.thenBy { !it.imported })
        }
    }

    fun months(portfolios: List<Data.Portfolio>) = portfolios.map { it.date.take(7) }.distinct().sortedDescending()

    fun rank(portfolios: List<Data.Portfolio>, records: List<LocalRecord>, month: String,
             category: Category, equityIsins: Set<String>): Report {
        val period = YearMonth.parse(month)
        fun schemes(m: String): List<Scheme> = portfolios.filter { it.date.startsWith("$m-") }.map { p ->
            val c = classification(p, records)
            // Positive evidence of a position is required. A zero row is not a holder.
            val weights = p.holdings.filter { h ->
                h.isin in equityIsins && h.isin.startsWith("INE") && Data.isin(h.isin) &&
                    (h.weight.isFinite() && h.weight > 0 || h.shares.isFinite() && h.shares > 0)
            }.groupBy { it.isin }.mapValues { (_, rows) ->
                // Unknown/invalid weights remain unknown, including when duplicate rows are present.
                if (rows.size == 1 && rows.single().weight.isFinite() && rows.single().weight in 0.0..100.0)
                    rows.single().weight else Double.NaN
            }
            Scheme(schemeKey(p, c), p, c, weights)
        }.groupBy { it.key }.values.map { versions ->
            versions.maxWith(compareBy<Scheme> { it.portfolio.date }.thenBy { it.portfolio.fetched }.thenBy { !it.portfolio.imported }.thenBy { it.portfolio.id })
        }
        val monthSchemes = schemes(month)
        fun inCategory(s: Scheme) = category == Category.OVERALL || s.classification.category == category
        val now = monthSchemes.filter { it.weights.isNotEmpty() && inCategory(it) }
        val prior = schemes(period.minusMonths(1).toString()).filter { it.weights.isNotEmpty() && inCategory(it) }.associateBy { it.key }
        val matched = now.filter { it.key in prior }
        val holders = linkedMapOf<String, MutableList<Holder>>()
        now.forEach { s -> s.weights.forEach { (isin, weight) -> holders.getOrPut(isin) { mutableListOf() }.add(Holder(s, weight)) } }
        val rows = holders.map { (isin, positions) ->
            val name = positions.asSequence().flatMap { it.scheme.portfolio.holdings.asSequence() }
                .firstOrNull { it.isin == isin && it.stock.isNotBlank() }?.stock ?: isin
            val added = if (matched.isEmpty()) null else matched.count { isin in it.weights && isin !in prior.getValue(it.key).weights }
            val absent = if (matched.isEmpty()) null else matched.count { isin !in it.weights && isin in prior.getValue(it.key).weights }
            Stock(isin, name, positions.sortedWith(compareByDescending<Holder> { if (it.weight.isFinite()) it.weight else -1.0 }.thenBy { it.scheme.portfolio.name }), added, absent)
        }.sortedWith(compareByDescending<Stock> { it.count }.thenBy { it.name.lowercase(Locale.ROOT) }.thenBy { it.isin })
        return Report(month, category, now, matched.size, rows, monthSchemes.count { it.weights.isEmpty() })
    }

    fun csv(report: Report, rows: List<Stock>): String {
        fun q(value: Any?) = "\"" + (value?.toString() ?: "").replace("\"", "\"\"") + "\""
        val header = "report_month,scheme_category,isin,stock,holding_schemes,covered_schemes,holding_known_amcs,amc_identified_holder_schemes,avg_fund_asset_weight_percent,weight_available_schemes,matched_schemes,added_recognized_positions,absent_recognized_positions,net_matched_change"
        return header + "\n" + rows.joinToString("\n") { r ->
            listOf(report.month, report.category.label, r.isin, r.name, r.count, report.schemes.size,
                r.amcCount, r.knownAmcHolders, r.averageWeight.takeIf { it.isFinite() }, r.knownWeights.size,
                report.matchedSchemes, r.added, r.absent, r.delta).joinToString(",") { q(it) }
        }
    }
}
