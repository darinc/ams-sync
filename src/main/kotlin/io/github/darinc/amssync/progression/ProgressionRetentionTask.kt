package io.github.darinc.amssync.progression

import io.github.darinc.amssync.AMSSyncPlugin
import org.bukkit.scheduler.BukkitTask
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields

/**
 * Periodic task that handles tiered data retention and compaction.
 *
 * Implements a logarithmic retention pattern:
 * - Raw snapshots (every N minutes) -> kept for rawDays, then compacted to hourly
 * - Hourly aggregates -> kept for hourlyDays, then compacted to daily
 * - Daily aggregates -> kept for dailyDays, then compacted to weekly
 * - Weekly aggregates -> kept for weeklyYears, then deleted
 *
 * @property plugin Parent plugin instance
 * @property config Retention configuration
 * @property database Progression database
 */
class ProgressionRetentionTask(
    private val plugin: AMSSyncPlugin,
    private val config: RetentionConfig,
    private val database: ProgressionDatabase
) {
    private var task: BukkitTask? = null
    private val hourFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH")
        .withZone(ZoneId.systemDefault())

    /**
     * Start the periodic retention task.
     */
    fun start() {
        if (!config.enabled) {
            plugin.logger.info("Progression retention disabled in config")
            return
        }

        val intervalTicks = config.getCleanupIntervalTicks()

        // Run asynchronously to avoid blocking main thread
        // Start with a 1-minute delay to let the server settle
        task = plugin.server.scheduler.runTaskTimerAsynchronously(
            plugin,
            Runnable { runRetention() },
            20L * 60, // 1 minute initial delay
            intervalTicks
        )

        val tiers = config.tiers
        plugin.logger.info(
            "Progression retention task started - " +
                "raw: ${tiers.rawDays}d, hourly: ${tiers.hourlyDays}d, " +
                "daily: ${tiers.dailyDays}d, weekly: ${tiers.weeklyYears}y"
        )
    }

    /**
     * Stop the periodic retention task.
     */
    fun stop() {
        task?.cancel()
        task = null
    }

    /**
     * Run all retention operations in order.
     */
    private fun runRetention() {
        try {
            val tiers = config.tiers
            val stats = RetentionStats()

            // Step 1: Compact raw snapshots -> hourly (after rawDays)
            stats.hourlyCompacted = compactSnapshotsToHourly(tiers.rawDays)

            // Step 2: Compact hourly -> daily (after hourlyDays)
            stats.dailyCompacted = compactHourlyToDaily(tiers.hourlyDays)

            // Step 3: Compact daily -> weekly (after dailyDays)
            stats.weeklyCompacted = compactDailyToWeekly(tiers.dailyDays)

            // Step 4: Delete old weekly summaries (after weeklyYears)
            stats.weeklyDeleted = deleteOldWeeklySummaries(tiers.weeklyYears)

            // Step 5: Delete old level-up events (keep for full retention period)
            stats.levelUpsDeleted = deleteOldLevelUps(tiers.getTotalRetentionDays())

            // Log summary if anything was done
            if (stats.hasActivity()) {
                plugin.logger.info(
                    "Progression retention: " +
                        "hourly+${stats.hourlyCompacted}, daily+${stats.dailyCompacted}, " +
                        "weekly+${stats.weeklyCompacted}, weekly-${stats.weeklyDeleted}, " +
                        "events-${stats.levelUpsDeleted}"
                )
            } else {
                plugin.logger.fine("Progression retention: no data to compact or delete")
            }
        } catch (e: Exception) {
            plugin.logger.warning("Progression retention task failed: ${e.message}")
        }
    }

    /**
     * Compact raw snapshots older than [days] into hourly summaries.
     *
     * @return Number of hourly summaries created
     */
    private fun compactSnapshotsToHourly(days: Int): Int {
        val threshold = Instant.now().minus(days.toLong(), ChronoUnit.DAYS)
        val snapshots = database.getSnapshotsForHourlyCompaction(threshold)

        if (snapshots.isEmpty()) return 0

        var compacted = 0
        for (data in snapshots) {
            try {
                val skillsJson = database.buildAggregatedSkillsJson(
                    data.startSkillsJson,
                    data.endSkillsJson
                )
                database.insertHourlySummary(
                    hour = data.hour,
                    uuid = data.uuid,
                    playerName = data.playerName,
                    startPowerLevel = data.startPowerLevel,
                    endPowerLevel = data.endPowerLevel,
                    skillsJson = skillsJson
                )
                compacted++
            } catch (e: Exception) {
                plugin.logger.warning("Failed to compact to hourly: ${e.message}")
            }
        }

        // Delete the compacted raw snapshots
        if (compacted > 0) {
            database.deleteSnapshotsOlderThan(threshold)
        }

        return compacted
    }

    /**
     * Compact hourly summaries older than [days] into daily summaries.
     *
     * @return Number of daily summaries created
     */
    private fun compactHourlyToDaily(days: Int): Int {
        // Calculate the hour threshold (YYYY-MM-DDTHH format)
        val thresholdInstant = Instant.now().minus(days.toLong(), ChronoUnit.DAYS)
        val thresholdHour = hourFormatter.format(thresholdInstant)

        val hourlySummaries = database.getHourlySummariesForDailyCompaction(thresholdHour)

        if (hourlySummaries.isEmpty()) return 0

        var compacted = 0
        for (data in hourlySummaries) {
            try {
                val skillsJson = database.buildAggregatedSkillsJson(
                    data.startSkillsJson,
                    data.endSkillsJson
                )
                database.insertDailySummary(
                    date = data.date,
                    uuid = data.uuid,
                    playerName = data.playerName,
                    startPowerLevel = data.startPowerLevel,
                    endPowerLevel = data.endPowerLevel,
                    skillsJson = skillsJson
                )
                compacted++
            } catch (e: Exception) {
                plugin.logger.warning("Failed to compact to daily: ${e.message}")
            }
        }

        // Delete the compacted hourly summaries
        if (compacted > 0) {
            database.deleteHourlySummariesOlderThan(thresholdHour)
        }

        return compacted
    }

    /**
     * Compact daily summaries older than [days] into weekly summaries.
     *
     * @return Number of weekly summaries created
     */
    private fun compactDailyToWeekly(days: Int): Int {
        val thresholdDate = LocalDate.now(ZoneId.systemDefault()).minusDays(days.toLong())
        val thresholdStr = thresholdDate.toString()

        val dailySummaries = database.getDailySummariesForWeeklyCompaction(thresholdStr)

        if (dailySummaries.isEmpty()) return 0

        var compacted = 0
        for (data in dailySummaries) {
            try {
                val skillsJson = database.buildAggregatedSkillsJson(
                    data.startSkillsJson,
                    data.endSkillsJson
                )
                database.insertWeeklySummary(
                    week = data.week,
                    uuid = data.uuid,
                    playerName = data.playerName,
                    startPowerLevel = data.startPowerLevel,
                    endPowerLevel = data.endPowerLevel,
                    skillsJson = skillsJson
                )
                compacted++
            } catch (e: Exception) {
                plugin.logger.warning("Failed to compact to weekly: ${e.message}")
            }
        }

        // Delete the compacted daily summaries
        if (compacted > 0) {
            database.deleteDailySummariesOlderThan(thresholdDate)
        }

        return compacted
    }

    /**
     * Delete weekly summaries older than [years].
     *
     * @return Number of deleted records
     */
    private fun deleteOldWeeklySummaries(years: Int): Int {
        val thresholdDate = LocalDate.now(ZoneId.systemDefault()).minusYears(years.toLong())
        // Format as YYYY-Www (ISO week)
        val thresholdWeek = "${thresholdDate.year}-W${
            thresholdDate.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR).toString().padStart(2, '0')
        }"

        return database.deleteWeeklySummariesOlderThan(thresholdWeek)
    }

    /**
     * Delete level-up events older than [days].
     *
     * @return Number of deleted records
     */
    private fun deleteOldLevelUps(days: Int): Int {
        val threshold = Instant.now().minus(days.toLong(), ChronoUnit.DAYS)
        return database.deleteLevelUpsOlderThan(threshold)
    }

    /**
     * Statistics for a retention run.
     */
    private data class RetentionStats(
        var hourlyCompacted: Int = 0,
        var dailyCompacted: Int = 0,
        var weeklyCompacted: Int = 0,
        var weeklyDeleted: Int = 0,
        var levelUpsDeleted: Int = 0
    ) {
        fun hasActivity(): Boolean =
            hourlyCompacted > 0 || dailyCompacted > 0 || weeklyCompacted > 0 ||
                weeklyDeleted > 0 || levelUpsDeleted > 0
    }
}
