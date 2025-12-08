package io.github.darinc.amsdiscord.discord

import io.github.darinc.amsdiscord.AmsDiscordPlugin
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
    private val plugin: AmsDiscordPlugin,
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
        Thread(runnable, "AmsDiscord-Presence").apply { isDaemon = true }
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
        scheduleUpdate()
    }

    /**
     * Handle player quit event - schedule presence update.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        scheduleUpdate()
    }

    /**
     * Schedule a debounced presence update.
     * Cancels any pending update and schedules a new one after debounce delay.
     */
    private fun scheduleUpdate() {
        if (!config.enabled) return

        val currentCount = Bukkit.getOnlinePlayers().size

        // Skip if count unchanged (handles rapid join/quit of same player)
        if (currentCount == lastPlayerCount.get()) {
            plugin.logger.fine("Skipping presence schedule - count unchanged at $currentCount")
            return
        }

        // Cancel pending debounce
        pendingUpdate.get()?.cancel(false)

        // Schedule new debounced update
        val future = executor.schedule({
            executeUpdate(currentCount)
        }, config.debounceMs, TimeUnit.MILLISECONDS)

        pendingUpdate.set(future)
        plugin.logger.fine("Scheduled presence update in ${config.debounceMs}ms (count: $currentCount)")
    }

    /**
     * Execute a presence update with rate limiting.
     * Reschedules if minimum interval hasn't passed.
     */
    private fun executeUpdate(count: Int) {
        val now = System.currentTimeMillis()
        val timeSinceLastUpdate = now - lastUpdateTime.get()

        // Enforce minimum interval
        if (timeSinceLastUpdate < config.minIntervalMs && lastUpdateTime.get() > 0) {
            val delay = config.minIntervalMs - timeSinceLastUpdate
            plugin.logger.fine("Presence update rate limited, rescheduling in ${delay}ms")
            val future = executor.schedule({ executeUpdate(count) }, delay, TimeUnit.MILLISECONDS)
            pendingUpdate.set(future)
            return
        }

        // Get actual current count (may have changed during debounce)
        val actualCount = Bukkit.getOnlinePlayers().size

        // Double-check count hasn't changed back
        if (actualCount == lastPlayerCount.get()) {
            plugin.logger.fine("Skipping presence update - count unchanged at $actualCount")
            return
        }

        updatePresence(actualCount)
        lastUpdateTime.set(now)
        lastPlayerCount.set(actualCount)
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
