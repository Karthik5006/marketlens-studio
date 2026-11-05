package app.marketlens

import java.time.LocalDate

internal object MarketExtremes {
    fun hasYearHistory(hit: Data.Hit, session: LocalDate): Boolean = !hit.priorRangeMissing() &&
        runCatching { val listed = LocalDate.parse(hit.stock.listed); !listed.isAfter(session.minusYears(1)) }.getOrDefault(hit.stock.listed.isBlank())

    fun note(hit: Data.Hit, high: Boolean, both: Boolean, session: LocalDate): String {
        val pieces = mutableListOf("Session touched ${if (high) "high" else "low"} ${rupees(hit.level)}")
        val listed = runCatching { LocalDate.parse(hit.stock.listed) }.getOrNull()
        if (listed != null && !listed.isAfter(session) && listed > session.minusYears(1))
            pieces += "Listed $listed · range since listing, under 1 year"
        else if (hit.priorRangeMissing()) pieces += "Previous range unavailable"
        if (both) pieces += "NSE reports both intraday high and low events"
        return pieces.joinToString(" · ")
    }
}
