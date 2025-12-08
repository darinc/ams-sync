package io.github.darinc.amssync.discord

import io.github.darinc.amssync.AMSSyncPlugin
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Activity
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Manages Discord bot presence to display current player count.
 *
 * Updates are event-driven (player join/quit) with debouncing and rate limiting
 * to respect Discord API limits (~5 updates/minute).
 *
 * @property plugin The parent plugin instance
 * @property config Presence configuration
 */
class PlayerCountPresence(
    private val plugin: AMSSyncPlugin,
    private val config: PresenceConfig
) : Listener {

    // Thread-safe state tracking
    private val lastUpdateTime = AtomicLong(0L)
    private val lastPlayerCount = AtomicInteger(-1)
    private val pendingUpdate = AtomicReference<ScheduledFuture<*>?>(null)
    private val nicknameDisabled = AtomicBoolean(false)

    // Original bot name for nickname template
    private var originalBotName: String? = null

    // Executor for scheduling debounced updates
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "AMSSync-Presence").apply { isDaemon = true }
    }

    /**
     * Initialize the presence manager.
     * Registers event listeners and sets initial presence.
     */
    fun initialize() {
        if (!config.enabled) {
            plugin.logger.info("Player count presence is disabled")
            return
        }

        // Register Bukkit event listener
        plugin.server.pluginManager.registerEvents(this, plugin)

        // Store original bot name for nickname template
        originalBotName = plugin.discordManager.getJda()?.selfUser?.name

        // Set initial presence
        updatePresenceNow()

        plugin.logger.info(
            "Player count presence initialized (activity=${config.activity.enabled}, nickname=${config.nickname.enabled})"
        )
    }

    /**
     * Handle player join event - schedule presence update.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        scheduleUpdate(0)
    }

    /**
     * Handle player quit event - schedule presence update.
     * Note: Player is still in getOnlinePlayers() when this fires, so we adjust by -1
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        scheduleUpdate(-1)
    }

    /**
     * Schedule a debounced presence update.
     * Cancels any pending update and schedules a new one after debounce delay.
     *
     * @param adjustment Adjustment to current player count (e.g., -1 for quit events
     *                   where player is still in the online list)
     */
    private fun scheduleUpdate(adjustment: Int) {
        if (!config.enabled) return

        val currentCount = Bukkit.getOnlinePlayers().size + adjustment

        // Skip if count unchanged (handles rapid join/quit of same player)
        if (currentCount == lastPlayerCount.get()) {
            plugin.logger.fine("Skipping presence schedule - count unchanged at $currentCount")
            return
        }

        // Cancel pending debounce
        pendingUpdate.get()?.cancel(false)

        // Schedule new debounced update
        // Note: We don't pass the adjusted count - executeUpdate() will get fresh count
        // after the player has actually left
        val future = executor.schedule({
            executeUpdate()
        }, config.debounceMs, TimeUnit.MILLISECONDS)

        pendingUpdate.set(future)
        plugin.logger.fine("Scheduled presence update in ${config.debounceMs}ms (expected count: $currentCount)")
    }

    /**
     * Execute a presence update with rate limiting.
     * Reschedules if minimum interval hasn't passed.
     * Always gets fresh player count to ensure accuracy after debounce.
     */
    private fun executeUpdate() {
        val now = System.currentTimeMillis()
        val timeSinceLastUpdate = now - lastUpdateTime.get()

        // Enforce minimum interval
        if (timeSinceLastUpdate < config.minIntervalMs && lastUpdateTime.get() > 0) {
            val delay = config.minIntervalMs - timeSinceLastUpdate
            plugin.logger.fine("Presence update rate limited, rescheduling in ${delay}ms")
            val future = executor.schedule({ executeUpdate() }, delay, TimeUnit.MILLISECONDS)
            pendingUpdate.set(future)
            return
        }

        // Get current count (player should be removed from list by now after debounce)
        val currentCount = Bukkit.getOnlinePlayers().size

        // Skip if count hasn't actually changed
        if (currentCount == lastPlayerCount.get()) {
            plugin.logger.fine("Skipping presence update - count unchanged at $currentCount")
            return
        }

        updatePresence(currentCount)
        lastUpdateTime.set(now)
        lastPlayerCount.set(currentCount)
    }

    /**
     * Force an immediate presence update, bypassing debounce.
     * Used for initial presence on bot connect.
     */
    fun updatePresenceNow() {
        val count = Bukkit.getOnlinePlayers().size
        updatePresence(count)
        lastUpdateTime.set(System.currentTimeMillis())
        lastPlayerCount.set(count)
    }

    /**
     * Update Discord presence with current player count.
     */
    private fun updatePresence(playerCount: Int) {
        val jda = plugin.discordManager.getJda() ?: return
        if (!plugin.discordManager.isConnected()) {
            plugin.logger.fine("Skipping presence update - Discord not connected")
            return
        }

        // Update activity if enabled
        if (config.activity.enabled) {
            updateActivity(jda, playerCount)
        }

        // Update nickname if enabled (and not disabled due to errors)
        if (config.nickname.enabled && !nicknameDisabled.get()) {
            updateNickname(jda, playerCount)
        }
    }

    /**
     * Update bot activity/status.
     */
    private fun updateActivity(jda: JDA, playerCount: Int) {
        val message = formatTemplate(config.activity.template, playerCount)
        val activity = when (config.activity.type.lowercase()) {
            "playing" -> Activity.playing(message)
            "watching" -> Activity.watching(message)
            "listening" -> Activity.listening(message)
            "competing" -> Activity.competing(message)
            else -> Activity.playing(message)
        }

        try {
            val circuitBreaker = plugin.circuitBreaker

            if (circuitBreaker != null) {
                val result = circuitBreaker.execute("Update bot activity") {
                    jda.presence.activity = activity
                }
                when (result) {
                    is CircuitBreaker.CircuitResult.Success -> {
                        plugin.logger.fine("Updated activity: ${config.activity.type} \"$message\"")
                    }
                    is CircuitBreaker.CircuitResult.Failure -> {
                        plugin.logger.warning("Failed to update activity: ${result.exception.message}")
                    }
                    is CircuitBreaker.CircuitResult.Rejected -> {
                        plugin.logger.fine("Activity update rejected by circuit breaker")
                    }
                }
            } else {
                jda.presence.activity = activity
                plugin.logger.fine("Updated activity: ${config.activity.type} \"$message\"")
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error updating bot activity: ${e.message}")
        }
    }

    /**
     * Update bot nickname in the configured guild.
     */
    private fun updateNickname(jda: JDA, playerCount: Int) {
        val guildId = plugin.config.getString("discord.guild-id") ?: return
        if (guildId.isBlank() || guildId == "YOUR_GUILD_ID_HERE") {
            plugin.logger.fine("Skipping nickname update - no guild ID configured")
            return
        }

        val guild = jda.getGuildById(guildId) ?: run {
            plugin.logger.fine("Skipping nickname update - guild not found")
            return
        }

        val botName = originalBotName ?: jda.selfUser.name
        val nickname = formatTemplate(config.nickname.template, playerCount, botName)

        try {
            val circuitBreaker = plugin.circuitBreaker

            if (circuitBreaker != null) {
                val result = circuitBreaker.execute("Update bot nickname") {
                    guild.selfMember.modifyNickname(nickname).queue(
                        { plugin.logger.fine("Updated nickname: $nickname") },
                        { error -> handleNicknameError(error) }
                    )
                }
                when (result) {
                    is CircuitBreaker.CircuitResult.Rejected -> {
                        plugin.logger.fine("Nickname update rejected by circuit breaker")
                    }
                    else -> { /* handled in queue callbacks */ }
                }
            } else {
                guild.selfMember.modifyNickname(nickname).queue(
                    { plugin.logger.fine("Updated nickname: $nickname") },
                    { error -> handleNicknameError(error) }
                )
            }
        } catch (e: Exception) {
            handleNicknameError(e)
        }
    }

    /**
     * Handle nickname update errors.
     * Optionally disables nickname updates based on config.
     */
    private fun handleNicknameError(error: Throwable) {
        plugin.logger.warning("Failed to update bot nickname: ${error.message}")

        if (!config.nickname.gracefulFallback) {
            plugin.logger.warning("Disabling nickname updates due to error (graceful-fallback=false)")
            nicknameDisabled.set(true)
        }
    }

    /**
     * Format a template string with player count and optional bot name.
     *
     * @param template Template with {count}, {max}, and optionally {name} placeholders
     * @param playerCount Current player count
     * @param botName Original bot name (for nickname templates)
     */
    private fun formatTemplate(template: String, playerCount: Int, botName: String? = null): String {
        var result = template
            .replace("{count}", playerCount.toString())
            .replace("{max}", Bukkit.getMaxPlayers().toString())

        if (botName != null) {
            result = result.replace("{name}", botName)
        }

        return result
    }

    /**
     * Gracefully shutdown the presence manager.
     * Cancels pending updates and stops the executor.
     */
    fun shutdown() {
        plugin.logger.info("Shutting down PlayerCountPresence...")

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
