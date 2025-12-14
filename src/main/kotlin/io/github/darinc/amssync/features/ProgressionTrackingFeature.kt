package io.github.darinc.amssync.features

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
    private val config: ProgressionTrackingConfig
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
        // Note: Actual initialization requires plugin instance for tasks
        // This will be fully implemented when integrating with AMSSyncPlugin
        if (!isEnabled) {
            logger.info("Progression tracking is disabled in config")
        }
    }

    /**
     * Set the progression services after initialization.
     */
    fun setServices(
        db: ProgressionDatabase?,
        snapshot: ProgressionSnapshotTask?,
        retention: ProgressionRetentionTask?
    ) {
        database = db
        snapshotTask = snapshot
        retentionTask = retention
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

    /**
     * Get the progression tracking configuration.
     */
    fun getConfig(): ProgressionTrackingConfig = config
}
