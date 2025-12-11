package io.github.darinc.amssync.progression

import org.bukkit.configuration.file.FileConfiguration

/**
 * Configuration for skill progression tracking.
 *
 * @property enabled Master toggle for progression tracking
 * @property databaseFile SQLite database filename (relative to plugin data folder)
 * @property events Event tracking configuration
 * @property snapshots Periodic snapshot configuration
 * @property retention Data retention and tiered compaction configuration
 */
data class ProgressionTrackingConfig(
    val enabled: Boolean,
    val databaseFile: String,
    val events: EventTrackingConfig,
    val snapshots: SnapshotConfig,
    val retention: RetentionConfig
) {
    companion object {
        private const val PREFIX = "progression-tracking"

        fun fromConfig(config: FileConfiguration): ProgressionTrackingConfig {
            return ProgressionTrackingConfig(
                enabled = config.getBoolean("$PREFIX.enabled", false),
                databaseFile = config.getString("$PREFIX.database-file", "progression.db")
                    ?: "progression.db",
                events = EventTrackingConfig.fromConfig(config),
                snapshots = SnapshotConfig.fromConfig(config),
                retention = RetentionConfig.fromConfig(config)
            )
        }
    }
}

/**
 * Configuration for level-up event tracking.
 *
 * @property enabled Whether to track level-up events
 */
data class EventTrackingConfig(
    val enabled: Boolean
) {
    companion object {
        private const val PREFIX = "progression-tracking.events"

        fun fromConfig(config: FileConfiguration): EventTrackingConfig {
            return EventTrackingConfig(
                enabled = config.getBoolean("$PREFIX.enabled", true)
            )
        }
    }
}

/**
 * Configuration for periodic skill snapshots.
 *
 * @property enabled Whether to take periodic snapshots
 * @property intervalMinutes Interval between snapshots in minutes
 * @property onlineOnly Whether to only snapshot online players
 */
data class SnapshotConfig(
    val enabled: Boolean,
    val intervalMinutes: Int,
    val onlineOnly: Boolean
) {
    companion object {
        private const val PREFIX = "progression-tracking.snapshots"

        fun fromConfig(config: FileConfiguration): SnapshotConfig {
            return SnapshotConfig(
                enabled = config.getBoolean("$PREFIX.enabled", true),
                intervalMinutes = config.getInt("$PREFIX.interval-minutes", 5),
                onlineOnly = config.getBoolean("$PREFIX.online-only", true)
            )
        }
    }

    /**
     * Convert interval minutes to Bukkit ticks (20 ticks = 1 second).
     */
    fun getIntervalTicks(): Long = intervalMinutes * 60L * 20L
}

/**
 * Configuration for tiered data retention and compaction.
 *
 * Implements a logarithmic retention pattern:
 * - Raw snapshots (every N minutes) for recent data
 * - Hourly aggregates for medium-term data
 * - Daily aggregates for long-term data
 * - Weekly aggregates for historical data
 *
 * @property enabled Whether retention/cleanup is enabled
 * @property cleanupIntervalHours How often to run the cleanup task
 * @property tiers Tiered retention configuration
 */
data class RetentionConfig(
    val enabled: Boolean,
    val cleanupIntervalHours: Int,
    val tiers: RetentionTiers
) {
    companion object {
        private const val PREFIX = "progression-tracking.retention"

        fun fromConfig(config: FileConfiguration): RetentionConfig {
            return RetentionConfig(
                enabled = config.getBoolean("$PREFIX.enabled", true),
                cleanupIntervalHours = config.getInt("$PREFIX.cleanup-interval-hours", 24),
                tiers = RetentionTiers.fromConfig(config)
            )
        }
    }

    /**
     * Convert cleanup interval hours to Bukkit ticks.
     */
    fun getCleanupIntervalTicks(): Long = cleanupIntervalHours * 60L * 60L * 20L
}

/**
 * Tiered retention configuration for snapshot compaction.
 *
 * Each tier specifies how long data is kept at that granularity before
 * being compacted to the next tier (or deleted).
 *
 * Data flow: raw -> hourly -> daily -> weekly -> deleted
 *
 * @property rawDays Keep raw snapshots (every N minutes) for this many days
 * @property hourlyDays Keep hourly aggregates for this many days
 * @property dailyDays Keep daily aggregates for this many days
 * @property weeklyYears Keep weekly aggregates for this many years
 */
data class RetentionTiers(
    val rawDays: Int,
    val hourlyDays: Int,
    val dailyDays: Int,
    val weeklyYears: Int
) {
    companion object {
        private const val PREFIX = "progression-tracking.retention.tiers"

        fun fromConfig(config: FileConfiguration): RetentionTiers {
            return RetentionTiers(
                rawDays = config.getInt("$PREFIX.raw-days", 7),
                hourlyDays = config.getInt("$PREFIX.hourly-days", 30),
                dailyDays = config.getInt("$PREFIX.daily-days", 180),
                weeklyYears = config.getInt("$PREFIX.weekly-years", 5)
            )
        }
    }

    /**
     * Total retention in days (for level-up events).
     * Events are kept for the full retention period.
     */
    fun getTotalRetentionDays(): Int = dailyDays + (weeklyYears * 365)
}
