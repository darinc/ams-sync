package io.github.darinc.amssync.features

import io.github.darinc.amssync.AMSSyncPlugin
import io.github.darinc.amssync.progression.ProgressionDatabase
import io.github.darinc.amssync.progression.ProgressionRetentionTask
import io.github.darinc.amssync.progression.ProgressionSnapshotTask
import io.github.darinc.amssync.progression.ProgressionTrackingConfig
import java.util.logging.Logger

/**
 * Coordinates progression tracking services.
 * Manages database, snapshot task, and retention task lifecycle.
 */
class ProgressionTrackingFeature(
    private val logger: Logger,
    val config: ProgressionTrackingConfig
) : Feature {

    var database: ProgressionDatabase? = null
        private set

    var snapshotTask: ProgressionSnapshotTask? = null
        private set

    var retentionTask: ProgressionRetentionTask? = null
        private set

    override val isEnabled: Boolean
        get() = config.enabled

    override fun initialize() {
        if (!isEnabled) {
            logger.info("Progression tracking is disabled in config")
        }
        // Full initialization requires plugin reference - use initializeWithPlugin()
    }

    /**
     * Initialize progression tracking with plugin reference.
     * Creates database, snapshot task, and retention task as configured.
     *
     * @return true if initialization succeeded, false otherwise
     */
    fun initializeWithPlugin(plugin: AMSSyncPlugin): Boolean {
        if (!isEnabled) {
            return false
        }

        // Initialize the SQLite database
        val db = ProgressionDatabase(plugin.dataFolder, config.databaseFile, logger)
        if (!db.initialize()) {
            logger.warning("Failed to initialize progression database - feature disabled")
            return false
        }
        database = db

        // Record retention config in history (for hybrid query tier boundaries)
        val tiers = config.retention.tiers
        db.recordConfigIfChanged(
            rawDays = tiers.rawDays,
            hourlyDays = tiers.hourlyDays,
            dailyDays = tiers.dailyDays,
            weeklyYears = tiers.weeklyYears
        )

        // Initialize snapshot task if enabled
        if (config.snapshots.enabled) {
            snapshotTask = ProgressionSnapshotTask(plugin, config.snapshots, db).also { it.start() }
        }

        // Initialize retention task if enabled
        if (config.retention.enabled) {
            retentionTask = ProgressionRetentionTask(
                plugin,
                config.retention,
                db,
                plugin.errorMetrics
            ).also { it.start() }
        }

        logInitialization()
        return true
    }

    private fun logInitialization() {
        val tiers = config.retention.tiers
        val features = mutableListOf<String>()
        if (config.events.enabled) features.add("events")
        if (config.snapshots.enabled) {
            features.add("snapshots (${config.snapshots.intervalMinutes}min)")
        }
        if (config.retention.enabled) {
            features.add("retention (${tiers.rawDays}d raw, ${tiers.hourlyDays}d hourly, " +
                "${tiers.dailyDays}d daily, ${tiers.weeklyYears}y weekly)")
        }
        logger.info("Progression tracking enabled: ${features.joinToString(", ")}")
    }

    /**
     * Check if progression tracking is active (enabled and database initialized).
     */
    fun isActive(): Boolean = isEnabled && database != null

    override fun shutdown() {
        snapshotTask?.stop()
        retentionTask?.stop()
        database?.close()
        snapshotTask = null
        retentionTask = null
        database = null
    }
}
