package io.github.darinc.amssync.progression

import com.gmail.nossr50.datatypes.skills.PrimarySkillType
import com.gmail.nossr50.mcMMO
import io.github.darinc.amssync.AMSSyncPlugin
import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitTask

/**
 * Periodic task that captures skill snapshots for online players.
 *
 * Runs at configurable intervals and records the current skill state
 * for all online players to the progression database.
 *
 * @property plugin Parent plugin instance
 * @property config Snapshot configuration
 * @property database Progression database for storing snapshots
 */
class ProgressionSnapshotTask(
    private val plugin: AMSSyncPlugin,
    private val config: SnapshotConfig,
    private val database: ProgressionDatabase
) {
    private var task: BukkitTask? = null

    /**
     * Start the periodic snapshot task.
     */
    fun start() {
        if (!config.enabled) {
            plugin.logger.info("Progression snapshots disabled in config")
            return
        }

        val intervalTicks = config.getIntervalTicks()

        // Run asynchronously to avoid blocking main thread
        task = plugin.server.scheduler.runTaskTimerAsynchronously(
            plugin,
            Runnable { captureSnapshots() },
            intervalTicks, // Initial delay (wait one interval before first snapshot)
            intervalTicks  // Repeat interval
        )

        plugin.logger.info("Progression snapshot task started (interval: ${config.intervalMinutes} minutes)")
    }

    /**
     * Stop the periodic snapshot task.
     */
    fun stop() {
        task?.cancel()
        task = null
    }

    /**
     * Capture snapshots for all online players.
     */
    private fun captureSnapshots() {
        val players = if (config.onlineOnly) {
            Bukkit.getOnlinePlayers().toList()
        } else {
            // For now, only support online players
            // Scanning all offline players would be expensive
            Bukkit.getOnlinePlayers().toList()
        }

        if (players.isEmpty()) {
            plugin.logger.fine("No players online for progression snapshot")
            return
        }

        var snapshotCount = 0

        for (player in players) {
            try {
                val profile = mcMMO.getDatabaseManager().loadPlayerProfile(player.uniqueId)
                if (!profile.isLoaded) {
                    plugin.logger.fine("Skipping snapshot for ${player.name} - profile not loaded")
                    continue
                }

                // Collect all skill levels (excluding child skills)
                val skills = PrimarySkillType.values()
                    .filter { !it.isChildSkill }
                    .associate { it.name to profile.getSkillLevel(it) }

                // Calculate power level (sum of all non-child skills)
                val powerLevel = skills.values.sum()

                database.insertSnapshot(
                    uuid = player.uniqueId,
                    playerName = player.name,
                    powerLevel = powerLevel,
                    skills = skills
                )

                snapshotCount++
            } catch (e: Exception) {
                plugin.logger.warning("Failed to capture snapshot for ${player.name}: ${e.message}")
            }
        }

        if (snapshotCount > 0) {
            plugin.logger.fine("Captured $snapshotCount progression snapshot(s)")
        }
    }
}
