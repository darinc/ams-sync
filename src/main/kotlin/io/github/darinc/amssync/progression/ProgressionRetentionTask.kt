package io.github.darinc.amssync.progression

import io.github.darinc.amssync.AMSSyncPlugin
import io.github.darinc.amssync.metrics.CompressionStats
import io.github.darinc.amssync.metrics.ErrorMetrics
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
    private val database: ProgressionDatabase,
    private val errorMetrics: ErrorMetrics? = null
) {
    private var task: BukkitTask? = null
    @Volatile private var isCatchUp = false
    private val hourFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH")
        .withZone(ZoneId.systemDefault())

    companion object {
        /** Metadata key for tracking last compression time */
        const val METADATA_LAST_COMPRESSION = "last_compression"

        /** Duration threshold for WARNING log (10 seconds) */
        private const val WARNING_DURATION_MS = 10_000L

        /** Duration threshold for SEVERE log (30 seconds) */
        private const val CRITICAL_DURATION_MS = 30_000L
    }

    /**
     * Start the periodic retention task.
     */
    fun start() {
        if (!config.enabled) {
            plugin.logger.info("Progression retention disabled in config")
            return
        }

        // Check if compression is overdue and run catch-up if needed
        checkAndRunCatchUp()

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
     * Check if compression is overdue and run catch-up if needed.
     * This ensures data compression happens even if the server was offline
     * during the scheduled compression time.
     */
    private fun checkAndRunCatchUp() {
        val lastCompressionStr = database.getMetadata(METADATA_LAST_COMPRESSION)
        val lastCompression = lastCompressionStr?.let {
            try {
                Instant.parse(it)
            } catch (e: Exception) {
                plugin.logger.warning("Invalid last_compression timestamp: $it")
                null
            }
        }

        val threshold = Instant.now().minus(config.cleanupIntervalHours.toLong(), ChronoUnit.HOURS)

        if (lastCompression == null || lastCompression.isBefore(threshold)) {
            val lastStr = lastCompression?.toString() ?: "never"
            plugin.logger.info("Compression overdue (last: $lastStr), running catch-up...")
            isCatchUp = true
            plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable { runRetention() })
        }
    }

    /**
     * Run compression immediately (for manual or startup-triggered compression).
     */
    fun runImmediately() {
        if (!config.enabled) {
            plugin.logger.info("Progression retention disabled, skipping immediate run")
            return
        }
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable { runRetention() })
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
    @Suppress("LongMethod")
    private fun runRetention() {
        val startTime = System.currentTimeMillis()
        val wasCatchUp = isCatchUp
        isCatchUp = false // Reset for next run

        var rawToHourlyMs: Long
        var hourlyToDailyMs: Long
        var dailyToWeeklyMs: Long
        var cleanupMs: Long

        try {
            val tiers = config.tiers
            val stats = RetentionStats()

            // Step 1: Compact raw snapshots -> hourly (after rawDays)
            var phaseStart = System.currentTimeMillis()
            stats.hourlyCompacted = compactSnapshotsToHourly(tiers.rawDays)
            rawToHourlyMs = System.currentTimeMillis() - phaseStart

            // Step 2: Compact hourly -> daily (after hourlyDays)
            phaseStart = System.currentTimeMillis()
            stats.dailyCompacted = compactHourlyToDaily(tiers.hourlyDays)
            hourlyToDailyMs = System.currentTimeMillis() - phaseStart

            // Step 3: Compact daily -> weekly (after dailyDays)
            phaseStart = System.currentTimeMillis()
            stats.weeklyCompacted = compactDailyToWeekly(tiers.dailyDays)
            dailyToWeeklyMs = System.currentTimeMillis() - phaseStart

            // Step 4: Delete old data
            phaseStart = System.currentTimeMillis()
            stats.weeklyDeleted = deleteOldWeeklySummaries(tiers.weeklyYears)
            stats.levelUpsDeleted = deleteOldLevelUps(tiers.getTotalRetentionDays())
            cleanupMs = System.currentTimeMillis() - phaseStart

            val totalMs = System.currentTimeMillis() - startTime

            // Track successful compression time
            database.setMetadata(METADATA_LAST_COMPRESSION, Instant.now().toString())

            // Record metrics
            errorMetrics?.recordCompressionRun(
                CompressionStats(
                    totalDurationMs = totalMs,
                    rawToHourlyMs = rawToHourlyMs,
                    hourlyToDailyMs = hourlyToDailyMs,
                    dailyToWeeklyMs = dailyToWeeklyMs,
                    cleanupMs = cleanupMs,
                    hourlyCreated = stats.hourlyCompacted,
                    dailyCreated = stats.dailyCompacted,
                    weeklyCreated = stats.weeklyCompacted,
                    weeklyDeleted = stats.weeklyDeleted,
                    levelupsDeleted = stats.levelUpsDeleted,
                    wasCatchup = wasCatchUp,
                    timestamp = Instant.now()
                )
            )

            // Log warnings for slow compression
            when {
                totalMs > CRITICAL_DURATION_MS -> {
                    plugin.logger.severe(
                        "Compression critically slow: ${totalMs}ms - investigate database performance"
                    )
                }
                totalMs > WARNING_DURATION_MS -> {
                    plugin.logger.warning(
                        "Compression took ${totalMs}ms (threshold: ${WARNING_DURATION_MS}ms)"
                    )
                }
            }

            // Log summary if anything was done
            if (stats.hasActivity()) {
                plugin.logger.info(
                    "Progression retention (${totalMs}ms): " +
                        "hourly+${stats.hourlyCompacted}, daily+${stats.dailyCompacted}, " +
                        "weekly+${stats.weeklyCompacted}, weekly-${stats.weeklyDeleted}, " +
                        "events-${stats.levelUpsDeleted}"
                )
            } else {
                plugin.logger.fine("Progression retention (${totalMs}ms): no data to compact or delete")
            }
        } catch (e: Exception) {
            val totalMs = System.currentTimeMillis() - startTime
            plugin.logger.warning("Progression retention task failed after ${totalMs}ms: ${e.message}")
            errorMetrics?.recordCompressionFailure(e.javaClass.simpleName)
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
