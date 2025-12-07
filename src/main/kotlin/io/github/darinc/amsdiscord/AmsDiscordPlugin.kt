package io.github.darinc.amsdiscord

import io.github.darinc.amsdiscord.commands.AmsLinkCommand
import io.github.darinc.amsdiscord.discord.*
import io.github.darinc.amsdiscord.linking.UserMappingService
import io.github.darinc.amsdiscord.mcmmo.McmmoApiWrapper
import org.bukkit.plugin.java.JavaPlugin

class AmsDiscordPlugin : JavaPlugin() {

    lateinit var discordManager: DiscordManager
        private set

    lateinit var userMappingService: UserMappingService
        private set

    lateinit var mcmmoApi: McmmoApiWrapper
        private set

    var timeoutManager: TimeoutManager? = null
        private set

    var circuitBreaker: CircuitBreaker? = null
        private set

    var discordApiWrapper: DiscordApiWrapper? = null
        private set

    override fun onEnable() {
        // Save default config if it doesn't exist
        saveDefaultConfig()

        // Initialize services
        logger.info("Initializing AMS Discord plugin...")

        // Load user mappings
        userMappingService = UserMappingService(this)
        userMappingService.loadMappings()
        logger.info("Loaded ${userMappingService.getMappingCount()} user mapping(s)")

        // Initialize MCMMO API wrapper with configuration
        val maxPlayersToScan = config.getInt("mcmmo.leaderboard.max-players-to-scan", 1000)
        val cacheTtlSeconds = config.getInt("mcmmo.leaderboard.cache-ttl-seconds", 60)
        mcmmoApi = McmmoApiWrapper(this, maxPlayersToScan, cacheTtlSeconds * 1000L)
        logger.info("MCMMO leaderboard limits: max-scan=$maxPlayersToScan, cache-ttl=${cacheTtlSeconds}s")

        // Register commands
        val linkCommand = AmsLinkCommand(this)
        getCommand("amslink")?.setExecutor(linkCommand)
        getCommand("amslink")?.tabCompleter = linkCommand

        // Initialize Discord with retry logic
        val token = config.getString("discord.token") ?: ""
        val guildId = config.getString("discord.guild-id") ?: ""

        if (token.isBlank() || token == "YOUR_BOT_TOKEN_HERE") {
            logger.severe("Discord bot token not configured! Please set 'discord.token' in config.yml")
            logger.severe("Plugin will be disabled.")
            server.pluginManager.disablePlugin(this)
            return
        }

        if (guildId.isBlank() || guildId == "YOUR_GUILD_ID_HERE") {
            logger.warning("Discord guild ID not configured. Slash commands will be registered globally (may take up to 1 hour to appear).")
        }

        // Load retry configuration
        val retryEnabled = config.getBoolean("discord.retry.enabled", true)
        val retryConfig = RetryManager.RetryConfig(
            enabled = retryEnabled,
            maxAttempts = config.getInt("discord.retry.max-attempts", 5),
            initialDelaySeconds = config.getInt("discord.retry.initial-delay-seconds", 5),
            maxDelaySeconds = config.getInt("discord.retry.max-delay-seconds", 300),
            backoffMultiplier = config.getDouble("discord.retry.backoff-multiplier", 2.0)
        )

        // Load timeout configuration
        val timeoutEnabled = config.getBoolean("discord.timeout.enabled", true)
        val timeoutConfig = TimeoutConfig(
            enabled = timeoutEnabled,
            warningThresholdSeconds = config.getInt("discord.timeout.warning-threshold-seconds", 3),
            hardTimeoutSeconds = config.getInt("discord.timeout.hard-timeout-seconds", 10)
        )

        // Initialize timeout manager
        if (timeoutEnabled) {
            timeoutManager = timeoutConfig.toTimeoutManager(logger)
            logger.info("Timeout protection enabled: warning=${timeoutConfig.warningThresholdSeconds}s, hard=${timeoutConfig.hardTimeoutSeconds}s")
        }

        // Load circuit breaker configuration
        val circuitBreakerEnabled = config.getBoolean("discord.circuit-breaker.enabled", true)
        val circuitBreakerConfig = CircuitBreakerConfig(
            enabled = circuitBreakerEnabled,
            failureThreshold = config.getInt("discord.circuit-breaker.failure-threshold", 5),
            timeWindowSeconds = config.getInt("discord.circuit-breaker.time-window-seconds", 60),
            cooldownSeconds = config.getInt("discord.circuit-breaker.cooldown-seconds", 30),
            successThreshold = config.getInt("discord.circuit-breaker.success-threshold", 2)
        )

        // Initialize circuit breaker
        if (circuitBreakerEnabled) {
            circuitBreaker = circuitBreakerConfig.toCircuitBreaker(logger)
            logger.info("Circuit breaker enabled: failures=${circuitBreakerConfig.failureThreshold}/${circuitBreakerConfig.timeWindowSeconds}s, cooldown=${circuitBreakerConfig.cooldownSeconds}s")
        }

        // Initialize Discord API wrapper with circuit breaker
        discordApiWrapper = DiscordApiWrapper(circuitBreaker, logger)

        // Initialize Discord manager
        discordManager = DiscordManager(this)

        // Attempt connection with retry logic (if enabled)
        if (retryEnabled) {
            val retryManager = retryConfig.toRetryManager(logger)

            // Wrap retry+initialization with timeout protection
            val connectionResult = if (timeoutEnabled && timeoutManager != null) {
                timeoutManager!!.executeWithTimeout("Discord connection with retries") {
                    retryManager.executeWithRetry("Discord connection") {
                        discordManager.initialize(token, guildId)
                    }
                }
            } else {
                // No timeout - execute retry directly
                TimeoutManager.TimeoutResult.Success(
                    retryManager.executeWithRetry("Discord connection") {
                        discordManager.initialize(token, guildId)
                    }
                )
            }

            // Handle timeout result
            when (connectionResult) {
                is TimeoutManager.TimeoutResult.Success -> {
                    // Check retry result
                    when (val retryResult = connectionResult.value) {
                        is RetryManager.RetryResult.Success -> {
                            logger.info("Discord bot successfully connected!")
                        }
                        is RetryManager.RetryResult.Failure -> {
                            logger.severe("=".repeat(60))
                            logger.severe("Failed to connect to Discord after ${retryResult.attempts} attempts")
                            logger.severe("Last error: ${retryResult.lastException.message}")
                            logger.severe("")
                            logger.severe("PLUGIN RUNNING IN DEGRADED MODE")
                            logger.severe("- Minecraft features will continue to work")
                            logger.severe("- Discord integration is unavailable")
                            logger.severe("- Check your bot token and network connection")
                            logger.severe("- Use /reload to retry connection after fixing issues")
                            logger.severe("=".repeat(60))
                        }
                    }
                }
                is TimeoutManager.TimeoutResult.Timeout -> {
                    logger.severe("=".repeat(60))
                    logger.severe("Discord connection timed out after ${connectionResult.timeoutMs}ms")
                    logger.severe("")
                    logger.severe("PLUGIN RUNNING IN DEGRADED MODE")
                    logger.severe("- Minecraft features will continue to work")
                    logger.severe("- Discord integration is unavailable")
                    logger.severe("- This may indicate network issues or Discord API problems")
                    logger.severe("- Use /reload to retry connection after fixing issues")
                    logger.severe("=".repeat(60))
                }
                is TimeoutManager.TimeoutResult.Failure -> {
                    logger.severe("Discord connection failed unexpectedly: ${connectionResult.exception.message}")
                    connectionResult.exception.printStackTrace()
                }
            }
        } else {
            // Retry disabled - fail fast (original behavior)
            try {
                discordManager.initialize(token, guildId)
                logger.info("Discord bot successfully connected!")
            } catch (e: Exception) {
                logger.severe("Failed to initialize Discord bot: ${e.message}")
                logger.severe("Retry logic is disabled. Plugin will be disabled.")
                logger.severe("Enable retry in config.yml: discord.retry.enabled = true")
                e.printStackTrace()
                server.pluginManager.disablePlugin(this)
                return
            }
        }

        logger.info("AMS Discord plugin enabled successfully!")
    }

    override fun onDisable() {
        logger.info("Shutting down AMS Discord plugin...")

        // Shutdown Discord gracefully
        if (::discordManager.isInitialized) {
            try {
                discordManager.shutdown()
                logger.info("Discord bot disconnected")
            } catch (e: Exception) {
                logger.warning("Error during Discord shutdown: ${e.message}")
            }
        }

        // Shutdown timeout manager
        timeoutManager?.shutdown()

        // Save user mappings
        if (::userMappingService.isInitialized) {
            userMappingService.saveMappings()
        }

        logger.info("AMS Discord plugin disabled")
    }

    /**
     * Reload configuration and user mappings
     */
    fun reloadPluginConfig() {
        reloadConfig()
        userMappingService.loadMappings()
        logger.info("Configuration reloaded")
    }
}
