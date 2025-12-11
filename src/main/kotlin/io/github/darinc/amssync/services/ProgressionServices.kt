package io.github.darinc.amssync.services

import io.github.darinc.amssync.progression.ProgressionDatabase
import io.github.darinc.amssync.progression.ProgressionRetentionTask
import io.github.darinc.amssync.progression.ProgressionSnapshotTask
import io.github.darinc.amssync.progression.ProgressionTrackingConfig

/**
 * Groups progression tracking services together.
 *
 * @property config Progression tracking configuration
 * @property database SQLite database for progression data (null if disabled)
 * @property snapshotTask Periodic snapshot task (null if disabled)
 * @property retentionTask Data retention/compaction task (null if disabled)
 */
data class ProgressionServices(
    val config: ProgressionTrackingConfig,
    val database: ProgressionDatabase?,
    val snapshotTask: ProgressionSnapshotTask?,
    val retentionTask: ProgressionRetentionTask?
) {
    /**
     * Check if progression tracking is enabled and active.
     */
    fun isActive(): Boolean = config.enabled && database != null

    /**
     * Shutdown all progression services.
     */
    fun shutdown() {
        snapshotTask?.stop()
        retentionTask?.stop()
        database?.close()
    }

    companion object {
        /**
         * Create a disabled progression services instance.
         */
        fun disabled(): ProgressionServices = ProgressionServices(
            config = ProgressionTrackingConfig(
                enabled = false,
                databaseFile = "",
                events = io.github.darinc.amssync.progression.EventTrackingConfig(false),
                snapshots = io.github.darinc.amssync.progression.SnapshotConfig(false, 5, true),
                retention = io.github.darinc.amssync.progression.RetentionConfig(
                    enabled = false,
                    cleanupIntervalHours = 24,
                    tiers = io.github.darinc.amssync.progression.RetentionTiers(
                        rawDays = 7,
                        hourlyDays = 30,
                        dailyDays = 180,
                        weeklyYears = 5
                    )
                )
            ),
            database = null,
            snapshotTask = null,
            retentionTask = null
        )
    }
}
