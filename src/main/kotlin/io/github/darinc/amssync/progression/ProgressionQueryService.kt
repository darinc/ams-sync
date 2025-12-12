package io.github.darinc.amssync.progression

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import java.util.UUID
import java.util.logging.Logger

/**
 * Service for querying progression trend data.
 *
 * Automatically selects the appropriate data tier based on timeframe:
 * - <= 7 days: raw snapshots (most granular)
 * - 7-30 days: hourly summaries
 * - 30-180 days: daily summaries
 * - > 180 days: weekly summaries
 *
 * @property database The progression database
 * @property logger Logger for query operations
 */
class ProgressionQueryService(
    private val database: ProgressionDatabase,
    private val logger: Logger
) {
    private val hourFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH")
        .withZone(ZoneId.systemDefault())

    companion object {
        /** Constant for power level queries (total of all skills) */
        const val POWER_SKILL = "POWER"
    }

    /**
     * Get progression trend for a player.
     *
     * @param uuid Player UUID
     * @param playerName Player name (for display in results)
     * @param skill Skill name or POWER_SKILL for total power level
     * @param timeframe Selected timeframe
     * @return TrendResult with points or error/no-data state
     */
    fun getTrend(
        uuid: UUID,
        playerName: String,
        skill: String,
        timeframe: Timeframe
    ): TrendResult {
        return try {
            val points = when {
                timeframe.days in 1..7 -> queryFromSnapshots(uuid, skill, timeframe)
                timeframe.days in 8..30 -> queryFromHourly(uuid, skill, timeframe)
                timeframe.days in 31..180 -> queryFromDaily(uuid, skill, timeframe)
                else -> queryFromWeekly(uuid, skill, timeframe)
            }

            if (points.isEmpty()) {
                TrendResult.NoData(
                    "No progression data found for $playerName in the ${timeframe.displayName.lowercase()} timeframe."
                )
            } else {
                TrendResult.Success(playerName, uuid, skill, points, timeframe)
            }
        } catch (e: Exception) {
            logger.warning("Error querying trend for $playerName: ${e.message}")
            TrendResult.Error("Failed to query progression data: ${e.message}")
        }
    }

    /**
     * Query from raw snapshots (for <= 7 days).
     */
    private fun queryFromSnapshots(uuid: UUID, skill: String, timeframe: Timeframe): List<TrendPoint> {
        val after = Instant.now().minus(timeframe.days.toLong(), ChronoUnit.DAYS)

        return if (skill == POWER_SKILL) {
            database.getPowerLevelFromSnapshots(uuid, after)
        } else {
            database.getSkillLevelFromSnapshots(uuid, skill, after)
        }
    }

    /**
     * Query from hourly summaries (for 7-30 days).
     */
    private fun queryFromHourly(uuid: UUID, skill: String, timeframe: Timeframe): List<TrendPoint> {
        val afterInstant = Instant.now().minus(timeframe.days.toLong(), ChronoUnit.DAYS)
        val afterHour = hourFormatter.format(afterInstant)

        return if (skill == POWER_SKILL) {
            database.getPowerLevelFromHourly(uuid, afterHour)
        } else {
            database.getSkillLevelFromHourly(uuid, skill, afterHour)
        }
    }

    /**
     * Query from daily summaries (for 30-180 days).
     */
    private fun queryFromDaily(uuid: UUID, skill: String, timeframe: Timeframe): List<TrendPoint> {
        val afterDate = LocalDate.now(ZoneId.systemDefault())
            .minusDays(timeframe.days.toLong())
            .toString()

        return if (skill == POWER_SKILL) {
            database.getPowerLevelFromDaily(uuid, afterDate)
        } else {
            database.getSkillLevelFromDaily(uuid, skill, afterDate)
        }
    }

    /**
     * Query from weekly summaries (for > 180 days or all time).
     */
    private fun queryFromWeekly(uuid: UUID, skill: String, timeframe: Timeframe): List<TrendPoint> {
        val afterWeek = if (timeframe == Timeframe.ALL_TIME) {
            // Start from a very old date
            "2000-W01"
        } else {
            val afterDate = LocalDate.now(ZoneId.systemDefault())
                .minusDays(timeframe.days.toLong())
            val year = afterDate.get(IsoFields.WEEK_BASED_YEAR)
            val week = afterDate.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
            "$year-W${week.toString().padStart(2, '0')}"
        }

        return if (skill == POWER_SKILL) {
            database.getPowerLevelFromWeekly(uuid, afterWeek)
        } else {
            database.getSkillLevelFromWeekly(uuid, skill, afterWeek)
        }
    }
}
