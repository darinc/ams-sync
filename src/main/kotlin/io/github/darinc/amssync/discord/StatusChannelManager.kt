package io.github.darinc.amssync.discord

import io.github.darinc.amssync.AMSSyncPlugin
import org.bukkit.Bukkit
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Manages a Discord voice channel name to display current player count.
 *
 * Updates the channel name (e.g., "5 AMS Online") when players join or leave.
 * Rate-limited to respect Discord's strict limit of 2 channel renames per 10 minutes.
 *
 * @property plugin The parent plugin instance
 * @property config Status channel configuration
 */
class StatusChannelManager(
    private val plugin: AMSSyncPlugin,
    private val config: StatusChannelConfig
) : Listener {

    // Thread-safe state tracking
    private val lastUpdateTime = AtomicLong(0L)
    private val lastPlayerCount = AtomicInteger(-1)
    private val pendingUpdate = AtomicReference<ScheduledFuture<*>?>(null)

    // Executor for scheduling debounced updates
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "AMSSync-StatusChannel").apply { isDaemon = true }
    }

    // Debounce delay - wait 30 seconds after player change before updating
    // This batches rapid joins/quits
    private val debounceMs = 30_000L

    /**
     * Initialize the status channel manager.
     * Registers event listeners and sets initial channel name.
     */
    fun initialize() {
        if (!config.enabled) {
            plugin.logger.info("Status channel manager is disabled")
            return
        }

        if (config.channelId.isBlank()) {
            plugin.logger.warning("Status channel enabled but no voice-channel-id configured")
            return
        }

        // Register Bukkit event listener
        plugin.server.pluginManager.registerEvents(this, plugin)

        // Set initial channel name
        updateChannelNow()

        plugin.logger.info(
            "Status channel manager initialized (channel=${config.channelId}, interval=${config.updateIntervalMs / 1000}s)"
        )
    }

    /**
     * Handle player join event - schedule channel update.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    @Suppress("UNUSED_PARAMETER")
    fun onPlayerJoin(event: PlayerJoinEvent) {
        scheduleUpdate(0)
    }

    /**
     * Handle player quit event - schedule channel update.
     * Note: Player is still in getOnlinePlayers() when this fires, so we adjust by -1
     */
    @EventHandler(priority = EventPriority.MONITOR)
    @Suppress("UNUSED_PARAMETER")
    fun onPlayerQuit(event: PlayerQuitEvent) {
        scheduleUpdate(-1)
    }

    /**
     * Schedule a debounced channel update.
     * Cancels any pending update and schedules a new one after debounce delay.
     *
     * @param adjustment Adjustment to current player count (e.g., -1 for quit events
     *                   where player is still in the online list)
     */
    private fun scheduleUpdate(adjustment: Int) {
        if (!config.enabled) return

        val currentCount = Bukkit.getOnlinePlayers().size + adjustment

        // Skip if count unchanged
        if (currentCount == lastPlayerCount.get()) {
            plugin.logger.fine("Skipping status channel schedule - count unchanged at $currentCount")
            return
        }

        // Cancel pending debounce
        pendingUpdate.get()?.cancel(false)

        // Schedule new debounced update
        val future = executor.schedule({
            executeUpdate()
        }, debounceMs, TimeUnit.MILLISECONDS)

        pendingUpdate.set(future)
        plugin.logger.fine("Scheduled status channel update in ${debounceMs}ms (expected count: $currentCount)")
    }

    /**
     * Execute a channel update with rate limiting.
     * Reschedules if minimum interval hasn't passed.
     */
    private fun executeUpdate() {
        val now = System.currentTimeMillis()
        val timeSinceLastUpdate = now - lastUpdateTime.get()

        // Enforce minimum interval (Discord rate limit: 2 per 10 minutes)
        if (timeSinceLastUpdate < config.updateIntervalMs && lastUpdateTime.get() > 0) {
            val delay = config.updateIntervalMs - timeSinceLastUpdate
            plugin.logger.fine("Status channel update rate limited, rescheduling in ${delay}ms")
            val future = executor.schedule({ executeUpdate() }, delay, TimeUnit.MILLISECONDS)
            pendingUpdate.set(future)
            return
        }

        // Get current count
        val currentCount = Bukkit.getOnlinePlayers().size

        // Skip if count hasn't actually changed
        if (currentCount == lastPlayerCount.get()) {
            plugin.logger.fine("Skipping status channel update - count unchanged at $currentCount")
            return
        }

        updateChannel(currentCount)
        lastUpdateTime.set(now)
        lastPlayerCount.set(currentCount)
    }

    /**
     * Force an immediate channel update, bypassing debounce.
     * Used for initial channel name on plugin enable.
     */
    fun updateChannelNow() {
        val count = Bukkit.getOnlinePlayers().size
        updateChannel(count)
        lastUpdateTime.set(System.currentTimeMillis())
        lastPlayerCount.set(count)
    }

    /**
     * Update Discord voice channel name with current player count.
     */
    private fun updateChannel(playerCount: Int) {
        val jda = plugin.discordManager.getJda() ?: return
        if (!plugin.discordManager.isConnected()) {
            plugin.logger.fine("Skipping status channel update - Discord not connected")
            return
        }

        val channel = jda.getVoiceChannelById(config.channelId)
        if (channel == null) {
            plugin.logger.warning("Status channel not found: ${config.channelId}")
            return
        }

        val newName = formatTemplate(config.template, playerCount)

        try {
            val circuitBreaker = plugin.circuitBreaker

            if (circuitBreaker != null) {
                val result = circuitBreaker.execute("Update status channel") {
                    channel.manager.setName(newName).queue(
                        { plugin.logger.info("Updated status channel: $newName") },
                        { error -> plugin.logger.warning("Failed to update status channel: ${error.message}") }
                    )
                }
                when (result) {
                    is CircuitBreaker.CircuitResult.Rejected -> {
                        plugin.logger.fine("Status channel update rejected by circuit breaker")
                    }
                    else -> { /* handled in queue callbacks */ }
                }
            } else {
                channel.manager.setName(newName).queue(
                    { plugin.logger.info("Updated status channel: $newName") },
                    { error -> plugin.logger.warning("Failed to update status channel: ${error.message}") }
                )
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error updating status channel: ${e.message}")
        }
    }

    /**
     * Format a template string with player count.
     *
     * @param template Template with {count} and {max} placeholders
     * @param playerCount Current player count
     */
    private fun formatTemplate(template: String, playerCount: Int): String {
        return template
            .replace("{count}", playerCount.toString())
            .replace("{max}", Bukkit.getMaxPlayers().toString())
    }

    /**
     * Gracefully shutdown the status channel manager.
     * Cancels pending updates and stops the executor.
     */
    fun shutdown() {
        plugin.logger.info("Shutting down StatusChannelManager...")

        // Cancel any pending update
        pendingUpdate.get()?.cancel(false)

        // Shutdown executor
        executor.shutdown()
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
}

/**
 * Configuration for status channel display.
 *
 * @property enabled Enable/disable status channel updates
 * @property channelId Voice channel ID to update
 * @property template Channel name template with {count} and {max} placeholders
 * @property updateIntervalMs Minimum milliseconds between channel name updates
 */
data class StatusChannelConfig(
    val enabled: Boolean,
    val channelId: String,
    val template: String,
    val updateIntervalMs: Long
) {
    companion object {
        /**
         * Load status channel configuration from Bukkit config file.
         *
         * @param config The Bukkit FileConfiguration to load from
         * @return StatusChannelConfig with loaded or default values
         */
        fun fromConfig(config: FileConfiguration): StatusChannelConfig {
            return StatusChannelConfig(
                enabled = config.getBoolean("discord.status-channel.enabled", false),
                channelId = config.getString("discord.status-channel.voice-channel-id", "") ?: "",
                template = config.getString("discord.status-channel.template", "{count} AMS Online")
                    ?: "{count} AMS Online",
                updateIntervalMs = config.getInt("discord.status-channel.update-interval-seconds", 300) * 1000L
            )
        }
    }
}
