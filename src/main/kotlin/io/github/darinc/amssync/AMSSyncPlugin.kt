package io.github.darinc.amssync

import io.github.darinc.amssync.audit.AuditLogger
import io.github.darinc.amssync.commands.AMSSyncCommand
import io.github.darinc.amssync.config.ConfigValidator
import io.github.darinc.amssync.discord.*
import io.github.darinc.amssync.discord.commands.AmsStatsCommand
import io.github.darinc.amssync.discord.commands.AmsTopCommand
import io.github.darinc.amssync.events.*
import io.github.darinc.amssync.image.AvatarFetcher
import io.github.darinc.amssync.image.ImageConfig
import io.github.darinc.amssync.image.PlayerCardRenderer
import io.github.darinc.amssync.linking.UserMappingService
import io.github.darinc.amssync.mcmmo.AnnouncementConfig
import io.github.darinc.amssync.mcmmo.McMMOEventListener
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

    lateinit var auditLogger: AuditLogger
        private set

    var rateLimiter: RateLimiter? = null
        private set

    var statusChannelManager: StatusChannelManager? = null
        private set

    var mcmmoEventListener: McMMOEventListener? = null
        private set

    var chatBridge: ChatBridge? = null
        private set

    var chatWebhookManager: ChatWebhookManager? = null
        private set

    var webhookManager: WebhookManager? = null
        private set

    var serverEventListener: ServerEventListener? = null
        private set

    var playerDeathListener: PlayerDeathListener? = null
        private set

    var achievementListener: AchievementListener? = null
        private set

    // Image card components
    var imageConfig: ImageConfig? = null
        private set

    var avatarFetcher: AvatarFetcher? = null
        private set

    var cardRenderer: PlayerCardRenderer? = null
        private set

    var amsStatsCommand: AmsStatsCommand? = null
        private set

    var amsTopCommand: AmsTopCommand? = null
        private set

    override fun onEnable() {
        // Save default config if it doesn't exist
        saveDefaultConfig()

        // Initialize services
        logger.info("Initializing AMSSync plugin...")

        // Initialize error metrics
        errorMetrics = ErrorMetrics()
        logger.info("Error metrics initialized")

        // Initialize audit logger
        auditLogger = AuditLogger(this)
        logger.info("Audit logger initialized")

        // Initialize rate limiter if enabled
        val rateLimitEnabled = config.getBoolean("rate-limiting.enabled", true)
        if (rateLimitEnabled) {
            val rateLimiterConfig = RateLimiterConfig(
                enabled = true,
                penaltyCooldownMs = config.getLong("rate-limiting.penalty-cooldown-ms", 10000L),
                maxRequestsPerMinute = config.getInt("rate-limiting.max-requests-per-minute", 60)
            )
            rateLimiter = rateLimiterConfig.toRateLimiter(logger)
            logger.info("Rate limiting enabled: penalty-cooldown=${rateLimiterConfig.penaltyCooldownMs}ms, max=${rateLimiterConfig.maxRequestsPerMinute}/min")
        } else {
            logger.info("Rate limiting disabled")
        }

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
        // Environment variables take precedence over config file
        val token = System.getenv("AMS_DISCORD_TOKEN")
            ?: config.getString("discord.token")
            ?: ""
        val guildId = System.getenv("AMS_GUILD_ID")
            ?: config.getString("discord.guild-id")
            ?: ""

        // Log configuration source (without exposing secrets)
        if (System.getenv("AMS_DISCORD_TOKEN") != null) {
            logger.info("Discord token loaded from environment variable AMS_DISCORD_TOKEN")
        } else {
            logger.info("Discord token loaded from config.yml")
        }
        if (System.getenv("AMS_GUILD_ID") != null) {
            logger.info("Guild ID loaded from environment variable AMS_GUILD_ID")
        }

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

        // Initialize image card components
        initializeImageCards()

        // Initialize Discord manager (passing image card commands if available)
        discordManager = DiscordManager(this, amsStatsCommand, amsTopCommand)

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

        // Announce server stop before disconnecting
        serverEventListener?.announceServerStop()

        // Shutdown player count presence
        playerCountPresence?.shutdown()

        // Shutdown status channel manager
        statusChannelManager?.shutdown()

        // Shutdown webhook managers
        webhookManager?.shutdown()
        chatWebhookManager?.shutdown()

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
     * Initialize image card components for /amsstats and /amstop commands.
     */
    private fun initializeImageCards() {
        imageConfig = ImageConfig.fromConfig(config)
        val imgConfig = imageConfig!!

        if (!imgConfig.enabled) {
            logger.info("Image cards are disabled in config")
            return
        }

        // Initialize avatar fetcher with caching
        avatarFetcher = AvatarFetcher(
            logger = logger,
            cacheMaxSize = imgConfig.avatarCacheMaxSize,
            cacheTtlMs = imgConfig.getCacheTtlMs()
        )

        // Initialize card renderer
        cardRenderer = PlayerCardRenderer(imgConfig.serverName)

        // Initialize commands
        amsStatsCommand = AmsStatsCommand(this, imgConfig, avatarFetcher!!, cardRenderer!!)
        amsTopCommand = AmsTopCommand(this, imgConfig, avatarFetcher!!, cardRenderer!!)

        logger.info("Image cards enabled (provider=${imgConfig.avatarProvider}, cache=${imgConfig.avatarCacheTtlSeconds}s)")
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

        // Initialize status channel manager
        initializeStatusChannel()

        // Initialize MCMMO event listener
        initializeMcMMOAnnouncements()

        // Initialize chat bridge
        initializeChatBridge()

        // Initialize event announcements (deaths, achievements, server start/stop)
        initializeEventAnnouncements()
    }

    /**
     * Initialize status channel manager for voice channel player count display.
     */
    private fun initializeStatusChannel() {
        val statusConfig = StatusChannelConfig.fromConfig(config)
        if (statusConfig.enabled) {
            if (statusConfig.channelId.isBlank()) {
                logger.warning("Status channel enabled but no voice-channel-id configured")
                return
            }
            statusChannelManager = StatusChannelManager(this, statusConfig)
            statusChannelManager?.initialize()
        } else {
            logger.info("Status channel manager is disabled in config")
        }
    }

    /**
     * Initialize MCMMO event listener for milestone announcements.
     */
    private fun initializeMcMMOAnnouncements() {
        val announcementConfig = AnnouncementConfig.fromConfig(config)
        if (announcementConfig.enabled) {
            if (announcementConfig.channelId.isBlank()) {
                logger.warning("MCMMO announcements enabled but no text-channel-id configured")
                return
            }
            mcmmoEventListener = McMMOEventListener(this, announcementConfig)
            server.pluginManager.registerEvents(mcmmoEventListener!!, this)
            logger.info("MCMMO milestone announcements enabled (skill interval=${announcementConfig.skillMilestoneInterval}, power interval=${announcementConfig.powerMilestoneInterval})")
        } else {
            logger.info("MCMMO announcements are disabled in config")
        }
    }

    /**
     * Initialize chat bridge for two-way Minecraft/Discord chat relay.
     */
    private fun initializeChatBridge() {
        val chatConfig = ChatBridgeConfig.fromConfig(config)
        if (chatConfig.enabled) {
            if (chatConfig.channelId.isBlank()) {
                logger.warning("Chat bridge enabled but no channel-id configured")
                return
            }

            // Initialize webhook manager if webhook is enabled
            if (chatConfig.useWebhook) {
                if (chatConfig.webhookUrl.isNullOrBlank()) {
                    logger.warning("Chat bridge use-webhook is true but no webhook-url configured")
                    logger.warning("Chat bridge falling back to bot messages")
                } else {
                    chatWebhookManager = ChatWebhookManager(this, chatConfig.webhookUrl)
                    if (chatWebhookManager?.isWebhookAvailable() == true) {
                        logger.info("Chat bridge using webhook for rich messages")
                    }
                }
            }

            chatBridge = ChatBridge(this, chatConfig, chatWebhookManager)
            // Register as Bukkit listener for MC->Discord
            server.pluginManager.registerEvents(chatBridge!!, this)
            // Register as JDA listener for Discord->MC
            discordManager.getJda()?.addEventListener(chatBridge)
            logger.info("Chat bridge enabled (MC->Discord=${chatConfig.minecraftToDiscord}, Discord->MC=${chatConfig.discordToMinecraft})")
        } else {
            logger.info("Chat bridge is disabled in config")
        }
    }

    /**
     * Initialize event announcements (deaths, achievements, server start/stop).
     */
    private fun initializeEventAnnouncements() {
        val eventConfig = EventAnnouncementConfig.fromConfig(config)
        if (!eventConfig.enabled) {
            logger.info("Event announcements are disabled in config")
            return
        }

        if (eventConfig.channelId.isBlank()) {
            logger.warning("Event announcements enabled but no text-channel-id configured")
            return
        }

        // Initialize webhook manager
        webhookManager = WebhookManager(this, eventConfig.webhookUrl, eventConfig.channelId)
        if (eventConfig.webhookUrl != null) {
            logger.info("Event announcements using webhook")
        } else {
            logger.info("Event announcements using bot messages")
        }

        // Initialize server event listener
        serverEventListener = ServerEventListener(this, eventConfig, webhookManager!!)
        serverEventListener?.announceServerStart()

        // Initialize player death listener
        if (eventConfig.playerDeaths.enabled) {
            playerDeathListener = PlayerDeathListener(this, eventConfig, webhookManager!!)
            server.pluginManager.registerEvents(playerDeathListener!!, this)
            logger.info("Player death announcements enabled")
        }

        // Initialize achievement listener
        if (eventConfig.achievements.enabled) {
            achievementListener = AchievementListener(this, eventConfig, webhookManager!!)
            server.pluginManager.registerEvents(achievementListener!!, this)
            logger.info("Achievement announcements enabled (exclude-recipes=${eventConfig.achievements.excludeRecipes})")
        }
    }
}
