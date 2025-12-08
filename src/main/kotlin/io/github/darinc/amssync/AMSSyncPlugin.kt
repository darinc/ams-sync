package io.github.darinc.amssync

import io.github.darinc.amssync.commands.AMSSyncCommand
import io.github.darinc.amssync.config.ConfigValidator
import io.github.darinc.amssync.discord.*
import io.github.darinc.amssync.linking.UserMappingService
import io.github.darinc.amssync.mcmmo.McmmoApiWrapper
import io.github.darinc.amssync.metrics.ErrorMetrics
import org.bukkit.plugin.java.JavaPlugin

class AMSSyncPlugin : JavaPlugin() {

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

    var playerCountPresence: PlayerCountPresence? = null
        private set

    lateinit var errorMetrics: ErrorMetrics
        private set

    override fun onEnable() {
        // Save default config if it doesn't exist
        saveDefaultConfig()

        // Initialize services
        logger.info("Initializing AMSSync plugin...")

        // Initialize error metrics
        errorMetrics = ErrorMetrics()
        logger.info("Error metrics initialized")

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
        val syncCommand = AMSSyncCommand(this)
        getCommand("amssync")?.setExecutor(syncCommand)
        getCommand("amssync")?.tabCompleter = syncCommand

        // Initialize Discord with retry logic
        val token = config.getString("discord.token") ?: ""
        val guildId = config.getString("discord.guild-id") ?: ""

        // Pre-validate Discord configuration before attempting connection
        val validationResult = ConfigValidator.validateDiscordConfig(token, guildId, logger)

        if (!validationResult.valid) {
            logger.severe("=".repeat(60))
            logger.severe("DISCORD CONFIGURATION INVALID")
            logger.severe("")
            validationResult.errors.forEach { logger.severe("  • $it") }
            logger.severe("")
            logger.severe("Plugin will be disabled. Fix config.yml and restart.")
            logger.severe("=".repeat(60))
            server.pluginManager.disablePlugin(this)
            return
        }

        // Log any warnings (e.g., missing guild ID)
        validationResult.warnings.forEach { warning ->
            logger.warning(warning)
        }

        // Additional token format validation with detailed feedback
        if (!ConfigValidator.isValidBotTokenFormat(token)) {
            logger.warning("Bot token format may be invalid. If connection fails, verify your token.")
            logger.warning("Expected format: [base64_id].[timestamp].[hmac] (e.g., MTIz...XXX.XXXXXX.XXX...)")
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

        // Initialize Discord API wrapper with circuit breaker and metrics
        discordApiWrapper = DiscordApiWrapper(circuitBreaker, logger, errorMetrics)

        // Initialize Discord manager
        discordManager = DiscordManager(this)

        // Attempt connection with retry logic (if enabled)
        if (retryEnabled) {
            val retryManager = retryConfig.toRetryManager(logger)

            // Wrap retry+initialization with timeout protection
            val tm = timeoutManager  // Smart cast to non-null
            val connectionResult = if (timeoutEnabled && tm != null) {
                tm.executeWithTimeout("Discord connection with retries") {
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
                            errorMetrics.recordConnectionAttempt(success = true)
                            logger.info("Discord bot successfully connected!")
                            initializePlayerCountPresence()
                        }
                        is RetryManager.RetryResult.Failure -> {
                            errorMetrics.recordConnectionAttempt(success = false)
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
                    errorMetrics.recordConnectionAttempt(success = false)
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
                    errorMetrics.recordConnectionAttempt(success = false)
                    logger.severe("Discord connection failed unexpectedly: ${connectionResult.exception.message}")
                    connectionResult.exception.printStackTrace()
                }
            }
        } else {
            // Retry disabled - fail fast (original behavior)
            try {
                discordManager.initialize(token, guildId)
                errorMetrics.recordConnectionAttempt(success = true)
                logger.info("Discord bot successfully connected!")
                initializePlayerCountPresence()
            } catch (e: Exception) {
                errorMetrics.recordConnectionAttempt(success = false)
                logger.severe("Failed to initialize Discord bot: ${e.message}")
                logger.severe("Retry logic is disabled. Plugin will be disabled.")
                logger.severe("Enable retry in config.yml: discord.retry.enabled = true")
                e.printStackTrace()
                server.pluginManager.disablePlugin(this)
                return
            }
        }

        logger.info("AMSSync plugin enabled successfully!")
    }

    override fun onDisable() {
        logger.info("Shutting down AMSSync plugin...")

        // Shutdown player count presence
        playerCountPresence?.shutdown()

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

        logger.info("AMSSync plugin disabled")
    }

    /**
     * Reload configuration and user mappings
     */
    fun reloadPluginConfig() {
        reloadConfig()
        userMappingService.loadMappings()
        logger.info("Configuration reloaded")
    }

    /**
     * Initialize player count presence display if enabled and Discord is connected.
     */
    private fun initializePlayerCountPresence() {
        if (!discordManager.isConnected()) {
            logger.fine("Skipping presence initialization - Discord not connected")
            return
        }

        val presenceConfig = PresenceConfig.fromConfig(config)
        if (presenceConfig.enabled) {
            playerCountPresence = PlayerCountPresence(this, presenceConfig)
            playerCountPresence?.initialize()
        } else {
            logger.info("Player count presence is disabled in config")
        }
    }
}
