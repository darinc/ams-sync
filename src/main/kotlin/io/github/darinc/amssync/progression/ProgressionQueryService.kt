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
 * Uses hybrid multi-tier queries to combine data from:
 * - Raw snapshots (most recent data, within rawDays threshold)
 * - Hourly summaries (older data, within hourlyDays threshold)
 * - Daily summaries (even older, within dailyDays threshold)
 * - Weekly summaries (oldest data)
 *
 * This ensures charts always show the most recent data, even if
 * compression hasn't run yet to move data to higher tiers.
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

        /** Default retention thresholds (used if no config history exists) */
        private const val DEFAULT_RAW_DAYS = 7
        private const val DEFAULT_HOURLY_DAYS = 30
        private const val DEFAULT_DAILY_DAYS = 180
    }

    /**
     * Get progression trend for a player using hybrid multi-tier queries.
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
            val points = queryHybrid(uuid, skill, timeframe)

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
     * Query data from multiple tiers and merge results.
     *
     * For any timeframe, we query:
     * - Snapshots for data within rawDays of now
     * - Hourly summaries for data between rawDays and hourlyDays ago
     * - Daily summaries for data between hourlyDays and dailyDays ago
     * - Weekly summaries for data older than dailyDays
     *
     * This ensures we always get the most recent data (from snapshots)
     * even if compression hasn't happened yet.
     */
    private fun queryHybrid(uuid: UUID, skill: String, timeframe: Timeframe): List<TrendPoint> {
        val now = Instant.now()
        val config = database.getCurrentConfig()

        // Get thresholds from config history or use defaults
        val rawDays = config?.rawDays ?: DEFAULT_RAW_DAYS
        val hourlyDays = config?.hourlyDays ?: DEFAULT_HOURLY_DAYS
        val dailyDays = config?.dailyDays ?: DEFAULT_DAILY_DAYS

        // Calculate the start of the requested timeframe
        val timeframeStart = if (timeframe == Timeframe.ALL_TIME) {
            Instant.EPOCH
        } else {
            now.minus(timeframe.days.toLong(), ChronoUnit.DAYS)
        }

        // Calculate tier boundaries (relative to now)
        val rawBoundary = now.minus(rawDays.toLong(), ChronoUnit.DAYS)
        val hourlyBoundary = now.minus(hourlyDays.toLong(), ChronoUnit.DAYS)
        val dailyBoundary = now.minus(dailyDays.toLong(), ChronoUnit.DAYS)

        val allPoints = mutableListOf<TrendPoint>()

        // Query from snapshots (most recent data, within rawDays)
        // Range: max(timeframeStart, rawBoundary) to now
        val snapshotsStart = maxOf(timeframeStart, rawBoundary)
        if (snapshotsStart.isBefore(now)) {
            val snapshotPoints = querySnapshotsBetween(uuid, skill, snapshotsStart, now)
            allPoints.addAll(snapshotPoints)
            logger.fine("Hybrid query: ${snapshotPoints.size} points from snapshots")
        }

        // Query from hourly summaries (data between rawDays and hourlyDays ago)
        // Range: max(timeframeStart, hourlyBoundary) to rawBoundary
        if (timeframeStart.isBefore(rawBoundary)) {
            val hourlyStart = maxOf(timeframeStart, hourlyBoundary)
            val hourlyEnd = rawBoundary
            if (hourlyStart.isBefore(hourlyEnd)) {
                val hourlyPoints = queryHourlyBetween(uuid, skill, hourlyStart, hourlyEnd)
                allPoints.addAll(hourlyPoints)
                logger.fine("Hybrid query: ${hourlyPoints.size} points from hourly")
            }
        }

        // Query from daily summaries (data between hourlyDays and dailyDays ago)
        // Range: max(timeframeStart, dailyBoundary) to hourlyBoundary
        if (timeframeStart.isBefore(hourlyBoundary)) {
            val dailyStart = maxOf(timeframeStart, dailyBoundary)
            val dailyEnd = hourlyBoundary
            if (dailyStart.isBefore(dailyEnd)) {
                val dailyPoints = queryDailyBetween(uuid, skill, dailyStart, dailyEnd)
                allPoints.addAll(dailyPoints)
                logger.fine("Hybrid query: ${dailyPoints.size} points from daily")
            }
        }

        // Query from weekly summaries (data older than dailyDays)
        // Range: timeframeStart to dailyBoundary
        if (timeframeStart.isBefore(dailyBoundary)) {
            val weeklyPoints = queryWeeklyBetween(uuid, skill, timeframeStart, dailyBoundary)
            allPoints.addAll(weeklyPoints)
            logger.fine("Hybrid query: ${weeklyPoints.size} points from weekly")
        }

        // Sort by timestamp and deduplicate (prefer earlier entries if same timestamp)
        return allPoints
            .sortedBy { it.timestamp }
            .distinctBy { it.timestamp }
    }

    /**
     * Query snapshots between two timestamps.
     */
    private fun querySnapshotsBetween(
        uuid: UUID,
        skill: String,
        after: Instant,
        before: Instant
    ): List<TrendPoint> {
        return if (skill == POWER_SKILL) {
            database.getPowerLevelFromSnapshotsBetween(uuid, after, before)
        } else {
            database.getSkillLevelFromSnapshotsBetween(uuid, skill, after, before)
        }
    }

    /**
     * Query hourly summaries between two timestamps.
     */
    private fun queryHourlyBetween(
        uuid: UUID,
        skill: String,
        after: Instant,
        before: Instant
    ): List<TrendPoint> {
        val afterHour = hourFormatter.format(after)
        val beforeHour = hourFormatter.format(before)

        return if (skill == POWER_SKILL) {
            database.getPowerLevelFromHourlyBetween(uuid, afterHour, beforeHour)
        } else {
            database.getSkillLevelFromHourlyBetween(uuid, skill, afterHour, beforeHour)
        }
    }

    /**
     * Query daily summaries between two timestamps.
     */
    private fun queryDailyBetween(
        uuid: UUID,
        skill: String,
        after: Instant,
        before: Instant
    ): List<TrendPoint> {
        val zone = ZoneId.systemDefault()
        val afterDate = LocalDate.ofInstant(after, zone).toString()
        val beforeDate = LocalDate.ofInstant(before, zone).toString()

        return if (skill == POWER_SKILL) {
            database.getPowerLevelFromDailyBetween(uuid, afterDate, beforeDate)
        } else {
            database.getSkillLevelFromDailyBetween(uuid, skill, afterDate, beforeDate)
        }
    }

    /**
     * Query weekly summaries between two timestamps.
     */
    private fun queryWeeklyBetween(
        uuid: UUID,
        skill: String,
        after: Instant,
        before: Instant
    ): List<TrendPoint> {
        val zone = ZoneId.systemDefault()

        val afterDate = if (after == Instant.EPOCH) {
            LocalDate.of(2000, 1, 1)
        } else {
            LocalDate.ofInstant(after, zone)
        }

        val afterWeek = formatWeek(afterDate)

        return if (skill == POWER_SKILL) {
            database.getPowerLevelFromWeekly(uuid, afterWeek)
                .filter { it.timestamp.isBefore(before) }
        } else {
            database.getSkillLevelFromWeekly(uuid, skill, afterWeek)
                .filter { it.timestamp.isBefore(before) }
        }
    }

    /**
     * Format a LocalDate as ISO week string (YYYY-Www).
     */
    private fun formatWeek(date: LocalDate): String {
        val year = date.get(IsoFields.WEEK_BASED_YEAR)
        val week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        return "$year-W${week.toString().padStart(2, '0')}"
    }
}
