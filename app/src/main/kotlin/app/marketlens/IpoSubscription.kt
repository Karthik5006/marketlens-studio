package app.marketlens

import kotlin.math.floor

internal object IpoSubscription {
    fun multiple(bidShares: Double, offeredShares: Double): Double =
        if (bidShares.isFinite() && bidShares >= 0 && offeredShares.isFinite() && offeredShares > 0)
            (bidShares / offeredShares).takeIf { it.isFinite() } ?: Double.NaN else Double.NaN

    fun reported(issue: Research.Ipo): Double = issue.subscription.takeIf { it.isFinite() && it >= 0 }
        ?: multiple(issue.bids, issue.offered)

    fun percentage(multiple: Double): Double = if (multiple.isFinite() && multiple >= 0)
        (multiple * 100).takeIf { it.isFinite() } ?: Double.NaN else Double.NaN

    data class Lottery(val capacity: Long, val applications: Long, val probability: Double)
    /** Conditional equal-chance minimum-unit model. Not 1 / share-based subscription. */
    fun lottery(offeredShares: Double, lotShares: Double, minimumLots: Double, applications: Double): Lottery? {
        val values = listOf(offeredShares, lotShares, minimumLots, applications)
        if (!values.all { it.isFinite() && it > 0 && it <= 9e15 && it == floor(it) }) return null
        val minimum = lotShares * minimumLots
        if (!minimum.isFinite() || minimum > 9e15) return null
        val capacity = offeredShares.toLong() / (lotShares.toLong() * minimumLots.toLong())
        if (capacity < 1) return null
        return Lottery(capacity, applications.toLong(), minOf(1.0, capacity.toDouble() / applications) * 100)
    }
}
