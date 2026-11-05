package app.marketlens

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatterBuilder
import java.time.format.ResolverStyle
import java.util.Locale

/** Read named columns from an official BSE table; never infer an issue from a fuzzy company match. */
internal object BseIssues {
    const val PAGE = "https://www.bseindia.com/publicissue"
    fun trusted(url: String): Boolean = runCatching {
        val u = URI(url)
        u.scheme == "https" && u.userInfo == null && (u.port == -1 || u.port == 443) &&
            u.host in setOf("www.bseindia.com", "bseindia.com")
    }.getOrDefault(false)
    fun canCapture(url: String): Boolean = trusted(url) && runCatching {
        val u = URI(url); val path = u.path.lowercase(Locale.ROOT)
        path.startsWith("/publicissue") || path.startsWith("/markets/publicissues/") ||
            (path in setOf("", "/") && u.fragment.orEmpty().lowercase(Locale.ROOT).startsWith("/publicissue"))
    }.getOrDefault(false)

    fun date(value: String): String {
        val text = value.trim()
        runCatching { return LocalDate.parse(text).toString() }
        for (pattern in listOf("d-MMM-uuuu", "d MMM uuuu", "d MMMM uuuu", "dd/MM/uuuu", "dd-MM-uuuu")) {
            runCatching {
                return LocalDate.parse(text, DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(pattern)
                    .toFormatter(Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT)).toString()
            }
        }
        return ""
    }

    fun key(name: String, start: String): String = MessageDigest.getInstance("SHA-256")
        .digest((name.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ") + "|" + start).toByteArray())
        .take(12).joinToString("") { "%02x".format(it) }

    fun parse(raw: String, fetched: Long = System.currentTimeMillis()): List<Research.Ipo> {
        require(raw.length <= 1_000_000) { "The displayed tables are too large; use a smaller page." }
        val root = JSONObject(raw)
        val page = root.optString("url")
        require(canCapture(page)) { "Read tables from the original HTTPS BSE public-issues page." }
        val tables = root.optJSONArray("tables") ?: error("No displayed tables returned")
        val out = mutableListOf<Research.Ipo>()
        for (n in 0 until minOf(tables.length(), 40)) {
            val table = tables.optJSONObject(n) ?: continue
            val rows = table.optJSONArray("rows") ?: continue
            var headers = emptyList<String>()
            var headerIndex = -1
            fun index(vararg names: String) = headers.indexOfFirst { it in names }
            for (r in 0 until minOf(rows.length(), 12)) {
                val cells = rows.optJSONObject(r)?.optJSONArray("cells") ?: continue
                headers = (0 until cells.length()).map { Parse.header(cells.optString(it)) }
                if (index("companyname", "nameofcompany", "issuername", "nameofissuer", "securityname", "nameoftheissuer", "company") >= 0 &&
                    index("opendate", "openingdate", "issueopendate", "issuestartdate", "startdate", "issueopeningdate", "biddingstartdate") >= 0 &&
                    index("closedate", "closingdate", "issueclosedate", "issueenddate", "enddate", "issueclosingdate", "biddingenddate") >= 0) {
                    headerIndex = r; break
                }
            }
            if (headerIndex < 0) continue
            for (r in headerIndex + 1 until minOf(rows.length(), 501)) {
                val row = rows.optJSONObject(r) ?: continue
                val cells = row.optJSONArray("cells") ?: continue
                // Expanded/nested/virtualized rows can shift columns; reject instead of guessing offsets.
                if (cells.length() != headers.size) continue
                fun cell(vararg keys: String): String { val i = index(*keys); return if (i >= 0) cells.optString(i).trim() else "" }
                val name = cell("companyname", "nameofcompany", "issuername", "nameofissuer", "securityname", "nameoftheissuer", "company")
                val start = date(cell("opendate", "openingdate", "issueopendate", "issuestartdate", "startdate", "issueopeningdate", "biddingstartdate"))
                val end = date(cell("closedate", "closingdate", "issueclosedate", "issueenddate", "enddate", "issueclosingdate", "biddingenddate"))
                if (name.isBlank() || name.length > 300 || start.isBlank() || end.isBlank() || end < start) continue
                val type = cell("issuetype", "typeofissue", "securitytype").lowercase(Locale.ROOT)
                if (listOf("ncd", "debenture", "rights", "buyback", "fpo").any { it in type }) continue
                val issue = Research.Ipo()
                issue.exchange = "BSE"; issue.sourceId = key(name, start); issue.name = name
                issue.start = start; issue.end = end; issue.listing = date(cell("listingdate", "dateoflisting"))
                issue.symbol = Data.symbol(cell("symbol", "scripid", "securityid"))
                val board = cell("board", "platform", "market", "segment") + " " + type + " " + table.optString("context")
                issue.board = when { board.contains("SME", true) -> "SME"; board.contains("Mainboard", true) || board.contains("Main Board", true) -> "Mainboard"; else -> "Unclassified" }
                issue.series = if (issue.board == "SME") "SME" else ""
                issue.range = cell("priceband", "pricerange", "issueprice", "offerprice")
                val band = Research.band(issue.range); issue.low = band[0]; issue.high = band[1]
                issue.price = if (band[0] == band[1]) band[0] else Double.NaN
                issue.lot = numeric(cell("lotsize", "bidlot", "marketlot", "sharesperlot"))
                // Only explicitly named share-count columns are denominators. Rupee issue size is not shares.
                issue.offered = numeric(cell("sharesoffered", "noofsharesoffered", "numberofsharesoffered", "offeredshares"))
                issue.bids = numeric(cell("sharesbid", "noofsharesbid", "numberofsharesbid", "bidshares"))
                val rawSubscription = cell("timessubscribed", "subscriptiontimes", "subscriptionx", "nooftimes", "subscription", "totalsubscription")
                issue.subscription = numeric(rawSubscription)
                if (rawSubscription.endsWith("%") && issue.subscription.isFinite()) issue.subscription /= 100
                val pct = numeric(cell("subscriptionpercentage", "subscriptionpercent"))
                if (!issue.subscription.isFinite() && pct.isFinite()) issue.subscription = pct / 100.0
                issue.status = cell("status", "issuestatus")
                val links = row.optJSONArray("links")
                issue.sourceUrl = (0 until (links?.length() ?: 0)).map { links!!.optString(it) }.firstOrNull { trusted(it) } ?: page
                issue.sourceStamp = root.optString("stamp")
                issue.origin = "BSE page capture"; issue.captured = true; issue.fetched = fetched; issue.saved = true
                out += issue
            }
        }
        return out.groupBy { it.id() }.values.map { it.last() }.also {
            require(it.isNotEmpty()) { "No dated IPO table recognized. Wait for BSE's list to load, select its IPO tab, then read again. You can also add original-source facts manually." }
        }
    }

    private fun numeric(value: String): Double {
        val text = value.trim().replace(",", "").replace(Regex("(?i)(?:times|x|×|%)$"), "").trim()
        return text.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: Double.NaN
    }

    fun fromHtml(html: String, url: String, fetched: Long): List<Research.Ipo> {
        val tables = JSONArray()
        Regex("(?is)<table\\b[^>]*>.*?</table>").findAll(html).take(40).forEach { table ->
            val rows = JSONArray()
            Regex("(?is)<tr\\b[^>]*>.*?</tr>").findAll(table.value).take(501).forEach { row ->
                val cells = Research.cells(row.value)
                rows.put(JSONObject().put("cells", JSONArray(cells.map { Research.clean(it) }))
                    .put("links", JSONArray(cells.map { Research.href(it, PAGE) }.filter { it.isNotBlank() })))
            }
            val caption = Regex("(?is)<caption\\b[^>]*>(.*?)</caption>").find(table.value)?.groupValues?.get(1)?.let { Research.clean(it) }.orEmpty()
            tables.put(JSONObject().put("context", caption).put("rows", rows))
        }
        return parse(JSONObject().put("url", url).put("tables", tables).toString(), fetched).onEach { it.captured = false; it.origin = "BSE table download" }
    }
}
