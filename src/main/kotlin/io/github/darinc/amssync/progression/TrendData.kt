package io.github.darinc.amssync.progression

import java.time.Instant
import java.util.UUID

/**
 * A single data point for progression charts.
 *
 * @property timestamp When the measurement was taken
 * @property level Skill or power level at that time
 */
data class TrendPoint(
    val timestamp: Instant,
    val level: Int
)

/**
 * Result of a progression trend query.
 */
sealed class TrendResult {
    /**
     * Successful query with data points.
     */
    data class Success(
        val playerName: String,
        val uuid: UUID,
        val skill: String,
        val points: List<TrendPoint>,
        val timeframe: Timeframe
    ) : TrendResult()

    /**
     * Query succeeded but no data found.
     */
    data class NoData(val reason: String) : TrendResult()

    /**
     * Query failed with an error.
     */
    data class Error(val message: String) : TrendResult()
}

/**
 * Timeframe options for progression charts.
 *
 * Each timeframe maps to a specific data tier:
 * - SEVEN_DAYS: raw snapshots
 * - THIRTY_DAYS: hourly summaries
 * - NINETY_DAYS, SIX_MONTHS: daily summaries
 * - ONE_YEAR, ALL_TIME: weekly summaries
 */
enum class Timeframe(
    val days: Int,
    val displayName: String,
    val choiceValue: String
) {
    SEVEN_DAYS(7, "7 Days", "7d"),
    THIRTY_DAYS(30, "30 Days", "30d"),
    NINETY_DAYS(90, "90 Days", "90d"),
    SIX_MONTHS(180, "6 Months", "180d"),
    ONE_YEAR(365, "1 Year", "1y"),
    ALL_TIME(-1, "All Time", "all");

    companion object {
        /**
         * Parse a choice value into a Timeframe.
         * Defaults to THIRTY_DAYS if not found.
         */
        fun fromChoiceValue(value: String?): Timeframe {
            if (value == null) return THIRTY_DAYS
            return entries.find { it.choiceValue == value } ?: THIRTY_DAYS
        }
    }
}
