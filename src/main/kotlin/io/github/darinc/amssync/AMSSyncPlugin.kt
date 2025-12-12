package io.github.darinc.amssync

import io.github.darinc.amssync.audit.AuditLogger
import io.github.darinc.amssync.commands.AMSSyncCommand
import io.github.darinc.amssync.config.ConfigMigrator
import io.github.darinc.amssync.config.ConfigValidator
import io.github.darinc.amssync.discord.ChatBridge
import io.github.darinc.amssync.discord.ChatBridgeConfig
import io.github.darinc.amssync.discord.ChatWebhookManager
import io.github.darinc.amssync.discord.CircuitBreakerConfig
import io.github.darinc.amssync.discord.DiscordApiWrapper
import io.github.darinc.amssync.discord.DiscordManager
import io.github.darinc.amssync.discord.PlayerCountPresence
import io.github.darinc.amssync.discord.PresenceConfig
import io.github.darinc.amssync.discord.RateLimiterConfig
import io.github.darinc.amssync.discord.RetryManager
import io.github.darinc.amssync.discord.StatusChannelConfig
import io.github.darinc.amssync.discord.StatusChannelManager
import io.github.darinc.amssync.discord.TimeoutConfig
import io.github.darinc.amssync.discord.TimeoutManager
import io.github.darinc.amssync.discord.WebhookManager
import io.github.darinc.amssync.discord.commands.AmsStatsCommand
import io.github.darinc.amssync.discord.commands.AmsTopCommand
import io.github.darinc.amssync.events.AchievementListener
import io.github.darinc.amssync.events.EventAnnouncementConfig
import io.github.darinc.amssync.events.PlayerDeathListener
import io.github.darinc.amssync.events.ServerEventListener
import io.github.darinc.amssync.image.AvatarFetcher
import io.github.darinc.amssync.image.ImageConfig
import io.github.darinc.amssync.image.MilestoneCardRenderer
import io.github.darinc.amssync.image.PlayerCardRenderer
import io.github.darinc.amssync.linking.UserMappingService
import io.github.darinc.amssync.mcmmo.AnnouncementConfig
import io.github.darinc.amssync.mcmmo.McMMOEventListener
import io.github.darinc.amssync.mcmmo.McmmoApiWrapper
import io.github.darinc.amssync.metrics.ErrorMetrics
import io.github.darinc.amssync.services.DiscordServices
import io.github.darinc.amssync.services.EventServices
import io.github.darinc.amssync.services.ImageServices
import io.github.darinc.amssync.services.ResilienceServices
import io.github.darinc.amssync.services.ServiceRegistry
import org.bukkit.plugin.java.JavaPlugin

class AMSSyncPlugin : JavaPlugin() {

    /**
     * Central service registry holding all plugin services.
     */
    val services = ServiceRegistry()

    override fun onEnable() {
        // Ensure data folder exists for migration
        if (!dataFolder.exists()) {
            dataFolder.mkdirs()
        }

        // Migrate and save config
        handleConfigMigration()
        saveDefaultConfig()

        // Initialize core services
        logger.info("Initializing AMSSync plugin...")
        initializeCoreServices()

        // Load and validate Discord configuration
        val discordConfig = loadDiscordConfig() ?: return

        // Load resilience configurations and initialize components
        val retryConfig = loadRetryConfig()
        initializeResilienceComponents()

        // Initialize image cards
        initializeImageCards()

        // Initialize Discord manager and connect
        val whitelistEnabled = config.getBoolean("whitelist.enabled", true)
        logger.info(if (whitelistEnabled) "Whitelist management enabled" else "Whitelist management disabled in config")

        val discordManager = DiscordManager(this, services.image.statsCommand, services.image.topCommand, whitelistEnabled)
        connectToDiscord(discordManager, discordConfig.first, discordConfig.second, retryConfig)

        logger.info("AMSSync plugin enabled successfully!")
    }

    private fun handleConfigMigration() {
        val migrator = ConfigMigrator(this, logger)
        when (val result = migrator.migrateIfNeeded()) {
            is ConfigMigrator.MigrationResult.FreshInstall -> {
                logger.info("First-time setup - creating default config")
            }
            is ConfigMigrator.MigrationResult.UpToDate -> {
                logger.fine("Config is up to date")
            }
            is ConfigMigrator.MigrationResult.Migrated -> {
                logger.info("Config migrated from v${result.fromVersion} to v${result.toVersion}")
                logger.info("Backup saved as: ${result.backupPath}")
                if (result.addedKeys.isNotEmpty()) {
                    logger.info("Added ${result.addedKeys.size} new config option(s)")
                }
            }
            is ConfigMigrator.MigrationResult.Failed -> {
                logger.warning("Config migration failed: ${result.reason}")
                logger.warning("Using existing config - some new options may use defaults")
            }
        }
    }

    private fun initializeCoreServices() {
        // Initialize error metrics
        services.errorMetrics = ErrorMetrics()
        logger.info("Error metrics initialized")

        // Initialize audit logger
        services.auditLogger = AuditLogger(this)
        logger.info("Audit logger initialized")

        // Initialize rate limiter if enabled
        initializeRateLimiter()

        // Load user mappings
        services.userMappingService = UserMappingService(this)
        services.userMappingService.loadMappings()
        logger.info("Loaded ${services.userMappingService.getMappingCount()} user mapping(s)")

        // Initialize MCMMO API wrapper with configuration
        val maxPlayersToScan = config.getInt("mcmmo.leaderboard.max-players-to-scan", 1000)
        val cacheTtlSeconds = config.getInt("mcmmo.leaderboard.cache-ttl-seconds", 60)
        services.mcmmoApi = McmmoApiWrapper(this, maxPlayersToScan, cacheTtlSeconds * 1000L)
        logger.info("MCMMO leaderboard limits: max-scan=$maxPlayersToScan, cache-ttl=${cacheTtlSeconds}s")

        // Register commands
        val syncCommand = AMSSyncCommand(this)
        getCommand("amssync")?.setExecutor(syncCommand)
        getCommand("amssync")?.tabCompleter = syncCommand
    }

    private fun initializeRateLimiter() {
        val rateLimitEnabled = config.getBoolean("rate-limiting.enabled", true)
        if (rateLimitEnabled) {
            val rateLimiterConfig = RateLimiterConfig(
                enabled = true,
                penaltyCooldownMs = config.getLong("rate-limiting.penalty-cooldown-ms", 10000L),
                maxRequestsPerMinute = config.getInt("rate-limiting.max-requests-per-minute", 60)
            )
            services.rateLimiter = rateLimiterConfig.toRateLimiter(logger)
            logger.info("Rate limiting enabled: penalty-cooldown=${rateLimiterConfig.penaltyCooldownMs}ms, max=${rateLimiterConfig.maxRequestsPerMinute}/min")
        } else {
            logger.info("Rate limiting disabled")
        }
    }

    private fun loadDiscordConfig(): Pair<String, String>? {
        // Environment variables take precedence over config file
        val token = System.getenv("AMS_DISCORD_TOKEN")
            ?: config.getString("discord.token")
            ?: ""
        val guildId = System.getenv("AMS_GUILD_ID")
            ?: config.getString("discord.guild-id")
            ?: ""

        // Log configuration source (without exposing secrets)
        logger.info(
            if (System.getenv("AMS_DISCORD_TOKEN") != null) "Discord token loaded from environment variable AMS_DISCORD_TOKEN"
            else "Discord token loaded from config.yml"
        )
        if (System.getenv("AMS_GUILD_ID") != null) {
            logger.info("Guild ID loaded from environment variable AMS_GUILD_ID")
        }

        // Pre-validate Discord configuration before attempting connection
        val validationResult = ConfigValidator.validateDiscordConfig(token, guildId, logger)

        if (!validationResult.valid) {
            logger.severe("=".repeat(60))
            logger.severe("DISCORD CONFIGURATION INVALID")
            logger.severe("")
            validationResult.errors.forEach { logger.severe("  * $it") }
            logger.severe("")
            logger.severe("Plugin will be disabled. Fix config.yml and restart.")
            logger.severe("=".repeat(60))
            server.pluginManager.disablePlugin(this)
            return null
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

        return Pair(token, guildId)
    }

    private fun loadRetryConfig(): RetryManager.RetryConfig {
        val retryEnabled = config.getBoolean("discord.retry.enabled", true)
        return RetryManager.RetryConfig(
            enabled = retryEnabled,
            maxAttempts = config.getInt("discord.retry.max-attempts", 5),
            initialDelaySeconds = config.getInt("discord.retry.initial-delay-seconds", 5),
            maxDelaySeconds = config.getInt("discord.retry.max-delay-seconds", 300),
            backoffMultiplier = config.getDouble("discord.retry.backoff-multiplier", 2.0)
        )
    }

    private fun initializeResilienceComponents() {
        var timeoutMgr: TimeoutManager? = null
        var circuitBrkr: io.github.darinc.amssync.discord.CircuitBreaker? = null

        // Load and initialize timeout manager
        val timeoutEnabled = config.getBoolean("discord.timeout.enabled", true)
        if (timeoutEnabled) {
            val timeoutConfig = TimeoutConfig(
                enabled = true,
                warningThresholdSeconds = config.getInt("discord.timeout.warning-threshold-seconds", 3),
                hardTimeoutSeconds = config.getInt("discord.timeout.hard-timeout-seconds", 10)
            )
            timeoutMgr = timeoutConfig.toTimeoutManager(logger)
            logger.info("Timeout protection enabled: warning=${timeoutConfig.warningThresholdSeconds}s, hard=${timeoutConfig.hardTimeoutSeconds}s")
        }

        // Load and initialize circuit breaker
        val circuitBreakerEnabled = config.getBoolean("discord.circuit-breaker.enabled", true)
        if (circuitBreakerEnabled) {
            val circuitBreakerConfig = CircuitBreakerConfig(
                enabled = true,
                failureThreshold = config.getInt("discord.circuit-breaker.failure-threshold", 5),
                timeWindowSeconds = config.getInt("discord.circuit-breaker.time-window-seconds", 60),
                cooldownSeconds = config.getInt("discord.circuit-breaker.cooldown-seconds", 30),
                successThreshold = config.getInt("discord.circuit-breaker.success-threshold", 2)
            )
            circuitBrkr = circuitBreakerConfig.toCircuitBreaker(logger)
            logger.info(
                "Circuit breaker enabled: failures=${circuitBreakerConfig.failureThreshold}/" +
                    "${circuitBreakerConfig.timeWindowSeconds}s, cooldown=${circuitBreakerConfig.cooldownSeconds}s"
            )
        }

        services.resilience = ResilienceServices(timeoutMgr, circuitBrkr)
    }

    private fun initializeImageCards() {
        val imgConfig = ImageConfig.fromConfig(config)

        if (!imgConfig.enabled) {
            logger.info("Image cards are disabled in config")
            services.image = ImageServices.disabled()
            return
        }

        // Initialize avatar fetcher with caching
        val fetcher = AvatarFetcher(
            logger = logger,
            cacheMaxSize = imgConfig.avatarCacheMaxSize,
            cacheTtlMs = imgConfig.getCacheTtlMs()
        )

        // Initialize card renderer
        val renderer = PlayerCardRenderer(imgConfig.serverName)

        // Initialize commands
        val statsCmd = AmsStatsCommand(this, imgConfig, fetcher, renderer)
        val topCmd = AmsTopCommand(this, imgConfig, fetcher, renderer)

        services.image = ImageServices(imgConfig, fetcher, renderer, statsCmd, topCmd)
        logger.info("Image cards enabled (provider=${imgConfig.avatarProvider}, cache=${imgConfig.avatarCacheTtlSeconds}s)")
    }

    private fun connectToDiscord(
        discordManager: DiscordManager,
        token: String,
        guildId: String,
        retryConfig: RetryManager.RetryConfig
    ) {
        if (retryConfig.enabled) {
            connectWithRetry(discordManager, token, guildId, retryConfig)
        } else {
            connectWithoutRetry(discordManager, token, guildId)
        }
    }

    private fun connectWithRetry(
        discordManager: DiscordManager,
        token: String,
        guildId: String,
        retryConfig: RetryManager.RetryConfig
    ) {
        val retryManager = retryConfig.toRetryManager(logger)
        val timeoutEnabled = config.getBoolean("discord.timeout.enabled", true)
        val tm = services.resilience.timeoutManager

        // Wrap retry+initialization with timeout protection
        val connectionResult = if (timeoutEnabled && tm != null) {
            tm.executeWithTimeout("Discord connection with retries") {
                retryManager.executeWithRetry("Discord connection") {
                    discordManager.initialize(token, guildId)
                }
            }
        } else {
            TimeoutManager.TimeoutResult.Success(
                retryManager.executeWithRetry("Discord connection") {
                    discordManager.initialize(token, guildId)
                }
            )
        }

        handleConnectionResult(discordManager, connectionResult)
    }

    private fun handleConnectionResult(
        discordManager: DiscordManager,
        connectionResult: TimeoutManager.TimeoutResult<RetryManager.RetryResult<Unit>>
    ) {
        when (connectionResult) {
            is TimeoutManager.TimeoutResult.Success -> {
                when (val retryResult = connectionResult.value) {
                    is RetryManager.RetryResult.Success -> {
                        services.errorMetrics.recordConnectionAttempt(success = true)
                        logger.info("Discord bot successfully connected!")
                        finalizeDiscordServices(discordManager)
                    }
                    is RetryManager.RetryResult.Failure -> {
                        services.errorMetrics.recordConnectionAttempt(success = false)
                        logDegradedModeError(
                            "Failed to connect to Discord after ${retryResult.attempts} attempts",
                            "Last error: ${retryResult.lastException.message}",
                            "Check your bot token and network connection"
                        )
                        // Still create discord services with disconnected manager
                        finalizeDiscordServicesDisconnected(discordManager)
                    }
                }
            }
            is TimeoutManager.TimeoutResult.Timeout -> {
                services.errorMetrics.recordConnectionAttempt(success = false)
                logDegradedModeError(
                    "Discord connection timed out after ${connectionResult.timeoutMs}ms",
                    null,
                    "This may indicate network issues or Discord API problems"
                )
                finalizeDiscordServicesDisconnected(discordManager)
            }
            is TimeoutManager.TimeoutResult.Failure -> {
                services.errorMetrics.recordConnectionAttempt(success = false)
                logger.severe("Discord connection failed unexpectedly: ${connectionResult.exception.message}")
                connectionResult.exception.printStackTrace()
                finalizeDiscordServicesDisconnected(discordManager)
            }
        }
    }

    private fun connectWithoutRetry(discordManager: DiscordManager, token: String, guildId: String) {
        try {
            discordManager.initialize(token, guildId)
            services.errorMetrics.recordConnectionAttempt(success = true)
            logger.info("Discord bot successfully connected!")
            finalizeDiscordServices(discordManager)
        } catch (e: Exception) {
            services.errorMetrics.recordConnectionAttempt(success = false)
            logger.severe("Failed to initialize Discord bot: ${e.message}")
            logger.severe("Retry logic is disabled. Plugin will be disabled.")
            logger.severe("Enable retry in config.yml: discord.retry.enabled = true")
            logger.severe("Stack trace: ${e.stackTraceToString()}")
            server.pluginManager.disablePlugin(this)
        }
    }

    /**
     * Finalize Discord services after successful connection.
     */
    private fun finalizeDiscordServices(discordManager: DiscordManager) {
        // Initialize Discord API wrapper
        val apiWrapper = DiscordApiWrapper(services.resilience.circuitBreaker, logger, services.errorMetrics)

        // Initialize presence and other Discord-dependent services
        val presence = initializePresence(discordManager)
        val statusChannel = initializeStatusChannel(discordManager)
        val (chatBridge, chatWebhook) = initializeChatBridge(discordManager)
        val (webhookMgr, eventServices) = initializeEventAnnouncements(discordManager)

        services.discord = DiscordServices(
            manager = discordManager,
            apiWrapper = apiWrapper,
            chatBridge = chatBridge,
            chatWebhookManager = chatWebhook,
            webhookManager = webhookMgr,
            presence = presence,
            statusChannel = statusChannel
        )

        services.events = eventServices
    }

    /**
     * Create Discord services when connection failed (degraded mode).
     */
    private fun finalizeDiscordServicesDisconnected(discordManager: DiscordManager) {
        val apiWrapper = DiscordApiWrapper(services.resilience.circuitBreaker, logger, services.errorMetrics)
        services.discord = DiscordServices(
            manager = discordManager,
            apiWrapper = apiWrapper,
            chatBridge = null,
            chatWebhookManager = null,
            webhookManager = null,
            presence = null,
            statusChannel = null
        )
        services.events = EventServices.empty()
    }

    private fun initializePresence(discordManager: DiscordManager): PlayerCountPresence? {
        if (!discordManager.isConnected()) {
            logger.fine("Skipping presence initialization - Discord not connected")
            return null
        }

        val presenceConfig = PresenceConfig.fromConfig(config)
        return if (presenceConfig.enabled) {
            val presence = PlayerCountPresence(this, presenceConfig)
            presence.initialize()
            presence
        } else {
            logger.info("Player count presence is disabled in config")
            null
        }
    }

    private fun initializeStatusChannel(discordManager: DiscordManager): StatusChannelManager? {
        if (!discordManager.isConnected()) return null

        val statusConfig = StatusChannelConfig.fromConfig(config)
        return if (statusConfig.enabled) {
            if (statusConfig.channelId.isBlank()) {
                logger.warning("Status channel enabled but no voice-channel-id configured")
                null
            } else {
                val manager = StatusChannelManager(this, statusConfig)
                manager.initialize()
                manager
            }
        } else {
            logger.info("Status channel manager is disabled in config")
            null
        }
    }

    private fun initializeChatBridge(discordManager: DiscordManager): Pair<ChatBridge?, ChatWebhookManager?> {
        if (!discordManager.isConnected()) return Pair(null, null)

        val chatConfig = ChatBridgeConfig.fromConfig(config)
        if (!chatConfig.enabled) {
            logger.info("Chat bridge is disabled in config")
            return Pair(null, null)
        }

        if (chatConfig.channelId.isBlank()) {
            logger.warning("Chat bridge enabled but no channel-id configured")
            return Pair(null, null)
        }

        // Initialize webhook manager if webhook is enabled
        var chatWebhook: ChatWebhookManager? = null
        if (chatConfig.useWebhook) {
            if (chatConfig.webhookUrl.isNullOrBlank()) {
                logger.warning("Chat bridge use-webhook is true but no webhook-url configured")
                logger.warning("Chat bridge falling back to bot messages")
            } else {
                chatWebhook = ChatWebhookManager(this, chatConfig.webhookUrl)
                if (chatWebhook.isWebhookAvailable()) {
                    logger.info("Chat bridge using webhook for rich messages")
                }
            }
        }

        val bridge = ChatBridge(this, chatConfig, chatWebhook)
        // Register as Bukkit listener for MC->Discord
        server.pluginManager.registerEvents(bridge, this)
        // Register as JDA listener for Discord->MC
        discordManager.getJda()?.addEventListener(bridge)
        logger.info("Chat bridge enabled (MC->Discord=${chatConfig.minecraftToDiscord}, Discord->MC=${chatConfig.discordToMinecraft})")

        return Pair(bridge, chatWebhook)
    }

    private fun initializeEventAnnouncements(discordManager: DiscordManager): Pair<WebhookManager?, EventServices> {
        if (!discordManager.isConnected()) return Pair(null, EventServices.empty())

        // Initialize MCMMO event listener
        val mcmmoListener = initializeMcMMOListener(discordManager)

        val eventConfig = EventAnnouncementConfig.fromConfig(config)
        if (!eventConfig.enabled) {
            logger.info("Event announcements are disabled in config")
            return Pair(null, EventServices(mcmmoListener, null, null, null))
        }

        if (eventConfig.channelId.isBlank()) {
            logger.warning("Event announcements enabled but no text-channel-id configured")
            return Pair(null, EventServices(mcmmoListener, null, null, null))
        }

        // Initialize webhook manager
        val webhookMgr = WebhookManager(this, eventConfig.webhookUrl, eventConfig.channelId)
        if (eventConfig.webhookUrl != null) {
            logger.info("Event announcements using webhook")
        } else {
            logger.info("Event announcements using bot messages")
        }

        // Initialize server event listener
        val serverListener = ServerEventListener(this, eventConfig, webhookMgr)
        serverListener.announceServerStart()

        // Initialize player death listener
        val deathListener = if (eventConfig.playerDeaths.enabled) {
            val listener = PlayerDeathListener(this, eventConfig, webhookMgr)
            server.pluginManager.registerEvents(listener, this)
            logger.info("Player death announcements enabled")
            listener
        } else null

        // Initialize achievement listener
        val achievementListener = if (eventConfig.achievements.enabled) {
            val listener = AchievementListener(this, eventConfig, webhookMgr)
            server.pluginManager.registerEvents(listener, this)
            logger.info("Achievement announcements enabled (exclude-recipes=${eventConfig.achievements.excludeRecipes})")
            listener
        } else null

        return Pair(webhookMgr, EventServices(mcmmoListener, serverListener, deathListener, achievementListener))
    }

    private fun initializeMcMMOListener(discordManager: DiscordManager): McMMOEventListener? {
        if (!discordManager.isConnected()) return null

        val announcementConfig = AnnouncementConfig.fromConfig(config)
        if (!announcementConfig.enabled) {
            logger.info("MCMMO announcements are disabled in config")
            return null
        }

        if (announcementConfig.channelId.isBlank()) {
            logger.warning("MCMMO announcements enabled but no text-channel-id configured")
            return null
        }

        // Initialize milestone card renderer if image cards are enabled
        val milestoneCardRenderer = if (announcementConfig.useImageCards) {
            val serverName = services.image.config?.serverName ?: "Minecraft Server"
            MilestoneCardRenderer(serverName)
        } else null

        // Use existing avatar fetcher or create one for milestones
        val milestoneAvatarFetcher = if (announcementConfig.useImageCards && announcementConfig.showAvatars) {
            services.image.avatarFetcher ?: AvatarFetcher(
                logger,
                services.image.config?.avatarCacheMaxSize ?: 100,
                (services.image.config?.avatarCacheTtlSeconds ?: 300) * 1000L
            )
        } else null

        val listener = McMMOEventListener(
            this,
            announcementConfig,
            milestoneAvatarFetcher,
            milestoneCardRenderer
        )
        server.pluginManager.registerEvents(listener, this)

        val imageMode = if (announcementConfig.useImageCards) "image cards" else "embeds"
        val webhookMode = if (announcementConfig.webhookUrl != null) " via webhook" else ""
        logger.info(
            "MCMMO milestone announcements enabled ($imageMode$webhookMode, " +
                "skill=${announcementConfig.skillMilestoneInterval}, power=${announcementConfig.powerMilestoneInterval})"
        )

        return listener
    }

    private fun logDegradedModeError(mainError: String, details: String?, hint: String) {
        logger.severe("=".repeat(60))
        logger.severe(mainError)
        if (details != null) {
            logger.severe(details)
        }
        logger.severe("")
        logger.severe("PLUGIN RUNNING IN DEGRADED MODE")
        logger.severe("- Minecraft features will continue to work")
        logger.severe("- Discord integration is unavailable")
        logger.severe("- $hint")
        logger.severe("- Use /reload to retry connection after fixing issues")
        logger.severe("=".repeat(60))
    }

    override fun onDisable() {
        logger.info("Shutting down AMSSync plugin...")
        services.shutdown()
        logger.info("AMSSync plugin disabled")
    }

    fun reloadPluginConfig() {
        reloadConfig()
        services.userMappingService.loadMappings()
        logger.info("Configuration reloaded")
    }
}
