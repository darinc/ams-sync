package io.github.darinc.amssync

import io.github.darinc.amssync.audit.AuditLogger
import io.github.darinc.amssync.discord.channels.ChannelCreationConfig
import io.github.darinc.amssync.discord.channels.ChannelResolver
import io.github.darinc.amssync.discord.channels.ChannelResolution
import java.io.File
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
import io.github.darinc.amssync.discord.commands.AmsProgressCommand
import io.github.darinc.amssync.discord.commands.DiscordLinkCommand
import io.github.darinc.amssync.discord.commands.DiscordWhitelistCommand
import io.github.darinc.amssync.discord.commands.McStatsCommand
import io.github.darinc.amssync.discord.commands.McTopCommand
import io.github.darinc.amssync.discord.commands.SlashCommandHandler
import io.github.darinc.amssync.events.AchievementListener
import io.github.darinc.amssync.events.EventAnnouncementConfig
import io.github.darinc.amssync.events.PlayerDeathListener
import io.github.darinc.amssync.events.ServerEventListener
import io.github.darinc.amssync.image.AvatarFetcher
import io.github.darinc.amssync.image.ImageConfig
import io.github.darinc.amssync.image.MilestoneCardRenderer
import io.github.darinc.amssync.image.ProgressionChartRenderer
import io.github.darinc.amssync.linking.UserMappingService
import io.github.darinc.amssync.mcmmo.AnnouncementConfig
import io.github.darinc.amssync.mcmmo.McMMOEventListener
import io.github.darinc.amssync.mcmmo.McmmoApiWrapper
import io.github.darinc.amssync.metrics.ErrorMetrics
import io.github.darinc.amssync.progression.ProgressionQueryService
import io.github.darinc.amssync.progression.ProgressionTrackingConfig
import io.github.darinc.amssync.discord.CircuitBreaker
import io.github.darinc.amssync.discord.RateLimiter
import io.github.darinc.amssync.features.ChatBridgeFeature
import io.github.darinc.amssync.features.EventAnnouncementFeature
import io.github.darinc.amssync.features.ImageCardFeature
import io.github.darinc.amssync.features.PlayerCountDisplayFeature
import io.github.darinc.amssync.features.ProgressionTrackingFeature
import io.github.darinc.amssync.services.DiscordServices
import io.github.darinc.amssync.services.ResilienceServices
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin

class AMSSyncPlugin : JavaPlugin() {

    // Track registered listeners for cleanup during reload
    private val registeredListeners = mutableListOf<Listener>()

    // Core services (always required)
    lateinit var userMappingService: UserMappingService
        private set

    lateinit var mcmmoApi: McmmoApiWrapper
        private set

    lateinit var errorMetrics: ErrorMetrics
        private set

    lateinit var auditLogger: AuditLogger
        private set

    var rateLimiter: RateLimiter? = null
        private set

    var channelResolver: ChannelResolver? = null
        private set

    // Grouped services
    lateinit var resilience: ResilienceServices
        private set

    lateinit var discord: DiscordServices
        private set

    var imageFeature: ImageCardFeature? = null
        private set

    var eventsFeature: EventAnnouncementFeature? = null
        private set

    var chatBridgeFeature: ChatBridgeFeature? = null
        private set

    var playerCountFeature: PlayerCountDisplayFeature? = null
        private set

    var progressionFeature: ProgressionTrackingFeature? = null
        private set

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

        // Initialize progression tracking (SQLite-based)
        initializeProgressionTracking()

        // Load and validate Discord configuration
        val discordConfig = loadDiscordConfig() ?: return

        // Load resilience configurations and initialize components
        val retryConfig = loadRetryConfig()
        initializeResilienceComponents()

        // Initialize image cards
        initializeImageCards()

        // Initialize events feature (listeners added after Discord connection)
        initializeEventsFeature()

        // Initialize chat bridge feature (bridge added after Discord connection)
        initializeChatBridgeFeature()

        // Initialize player count display feature (services added after Discord connection)
        initializePlayerCountFeature()

        // Create Discord API wrapper (before DiscordManager for proper dependency order)
        val discordApiWrapper = DiscordApiWrapper(resilience.circuitBreaker, logger, errorMetrics)

        // Build slash command handlers and initialize Discord manager
        val commandHandlers = buildSlashCommandHandlers()
        val discordManager = DiscordManager(
            logger,
            discordApiWrapper,
            rateLimiter,
            auditLogger,
            commandHandlers
        )
        connectToDiscord(discordManager, discordApiWrapper, discordConfig.first, discordConfig.second, retryConfig)

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
        errorMetrics = ErrorMetrics()
        logger.info("Error metrics initialized")

        // Initialize audit logger
        auditLogger = AuditLogger(dataFolder, logger)
        logger.info("Audit logger initialized")

        // Initialize rate limiter if enabled
        initializeRateLimiter()

        // Load user mappings
        val configFile = File(dataFolder, "config.yml")
        userMappingService = UserMappingService(configFile, logger)
        userMappingService.loadMappings()
        logger.info("Loaded ${userMappingService.getMappingCount()} user mapping(s)")

        // Initialize MCMMO API wrapper with configuration
        val maxPlayersToScan = config.getInt("mcmmo.leaderboard.max-players-to-scan", 1000)
        val cacheTtlSeconds = config.getInt("mcmmo.leaderboard.cache-ttl-seconds", 60)
        mcmmoApi = McmmoApiWrapper(logger, maxPlayersToScan, cacheTtlSeconds * 1000L)
        logger.info("MCMMO leaderboard limits: max-scan=$maxPlayersToScan, cache-ttl=${cacheTtlSeconds}s")

        // Register commands
        val syncCommand = AMSSyncCommand(this)
        getCommand("amssync")?.setExecutor(syncCommand)
        getCommand("amssync")?.tabCompleter = syncCommand
    }

    private fun initializeProgressionTracking() {
        val progressionConfig = ProgressionTrackingConfig.fromConfig(config)
        val feature = ProgressionTrackingFeature(logger, progressionConfig)
        feature.initialize()
        feature.initializeWithPlugin(this)
        progressionFeature = feature
    }

    private fun initializeRateLimiter() {
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

        resilience = ResilienceServices(timeoutMgr, circuitBrkr)
    }

    private fun initializeImageCards() {
        val imgConfig = ImageConfig.fromConfig(config)
        val feature = ImageCardFeature(logger, imgConfig)
        feature.initialize()
        feature.initializeCommands(this)
        imageFeature = feature
    }

    private fun initializeEventsFeature() {
        val eventConfig = EventAnnouncementConfig.fromConfig(config)
        val feature = EventAnnouncementFeature(logger, eventConfig)
        feature.initialize()
        eventsFeature = feature
    }

    private fun initializeChatBridgeFeature() {
        val chatConfig = ChatBridgeConfig.fromConfig(config)
        val feature = ChatBridgeFeature(logger, chatConfig)
        feature.initialize()
        chatBridgeFeature = feature
    }

    private fun initializePlayerCountFeature() {
        val presenceConfig = PresenceConfig.fromConfig(config)
        val statusConfig = StatusChannelConfig.fromConfig(config)
        val feature = PlayerCountDisplayFeature(logger, presenceConfig, statusConfig)
        feature.initialize()
        playerCountFeature = feature
    }

    /**
     * Build the map of slash command handlers for Discord.
     *
     * This factory method creates and registers all slash command handlers.
     * Commands are conditionally registered based on config settings.
     *
     * @return Map of command names to their handlers
     */
    private fun buildSlashCommandHandlers(): Map<String, SlashCommandHandler> {
        val handlers = mutableMapOf<String, SlashCommandHandler>()

        // Always-enabled commands
        handlers["mcstats"] = McStatsCommand(this)
        handlers["mctop"] = McTopCommand(this)
        handlers["amssync"] = DiscordLinkCommand(this)

        // Image card commands (only if enabled)
        imageFeature?.getCommandHandlers()?.let { handlers.putAll(it) }

        // Whitelist command (only if enabled)
        val whitelistEnabled = config.getBoolean("whitelist.enabled", true)
        if (whitelistEnabled) {
            handlers["amswhitelist"] = DiscordWhitelistCommand(this)
            logger.info("Whitelist management enabled")
        } else {
            logger.info("Whitelist management disabled in config")
        }

        // Progression chart command (only if progression tracking and image cards are both enabled)
        val progressionDb = progressionFeature?.database
        val imgFeature = imageFeature
        val imgConfig = imgFeature?.config
        val avatarFetcher = imgFeature?.avatarFetcher
        if (progressionDb != null && imgConfig != null && avatarFetcher != null) {
            val queryService = ProgressionQueryService(progressionDb, logger)
            val chartRenderer = ProgressionChartRenderer(imgConfig.serverName)
            handlers["amsprogress"] = AmsProgressCommand(
                this,
                imgConfig,
                avatarFetcher,
                chartRenderer,
                queryService
            )
            logger.info("Progression chart command enabled")
        } else {
            if (progressionDb == null) {
                logger.fine("Progression chart command disabled: progression tracking not enabled")
            }
            if (imgConfig == null || avatarFetcher == null) {
                logger.fine("Progression chart command disabled: image cards not enabled")
            }
        }

        logger.info("Registered ${handlers.size} slash command handler(s)")
        return handlers
    }

    private fun connectToDiscord(
        discordManager: DiscordManager,
        apiWrapper: DiscordApiWrapper,
        token: String,
        guildId: String,
        retryConfig: RetryManager.RetryConfig
    ) {
        if (retryConfig.enabled) {
            connectWithRetry(discordManager, apiWrapper, token, guildId, retryConfig)
        } else {
            connectWithoutRetry(discordManager, apiWrapper, token, guildId)
        }
    }

    private fun connectWithRetry(
        discordManager: DiscordManager,
        apiWrapper: DiscordApiWrapper,
        token: String,
        guildId: String,
        retryConfig: RetryManager.RetryConfig
    ) {
        val retryManager = retryConfig.toRetryManager(logger)
        val timeoutEnabled = config.getBoolean("discord.timeout.enabled", true)
        val tm = resilience.timeoutManager

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

        handleConnectionResult(discordManager, apiWrapper, connectionResult)
    }

    private fun handleConnectionResult(
        discordManager: DiscordManager,
        apiWrapper: DiscordApiWrapper,
        connectionResult: TimeoutManager.TimeoutResult<RetryManager.RetryResult<Unit>>
    ) {
        when (connectionResult) {
            is TimeoutManager.TimeoutResult.Success -> {
                when (val retryResult = connectionResult.value) {
                    is RetryManager.RetryResult.Success -> {
                        errorMetrics.recordConnectionAttempt(success = true)
                        logger.info("Discord bot successfully connected!")
                        finalizeDiscordServices(discordManager, apiWrapper)
                    }
                    is RetryManager.RetryResult.Failure -> {
                        errorMetrics.recordConnectionAttempt(success = false)
                        logDegradedModeError(
                            "Failed to connect to Discord after ${retryResult.attempts} attempts",
                            "Last error: ${retryResult.lastException.message}",
                            "Check your bot token and network connection"
                        )
                        // Still create discord services with disconnected manager
                        finalizeDiscordServicesDisconnected(discordManager, apiWrapper)
                    }
                }
            }
            is TimeoutManager.TimeoutResult.Timeout -> {
                errorMetrics.recordConnectionAttempt(success = false)
                logDegradedModeError(
                    "Discord connection timed out after ${connectionResult.timeoutMs}ms",
                    null,
                    "This may indicate network issues or Discord API problems"
                )
                finalizeDiscordServicesDisconnected(discordManager, apiWrapper)
            }
            is TimeoutManager.TimeoutResult.Failure -> {
                errorMetrics.recordConnectionAttempt(success = false)
                logger.severe("Discord connection failed unexpectedly: ${connectionResult.exception.message}")
                connectionResult.exception.printStackTrace()
                finalizeDiscordServicesDisconnected(discordManager, apiWrapper)
            }
        }
    }

    private fun connectWithoutRetry(
        discordManager: DiscordManager,
        apiWrapper: DiscordApiWrapper,
        token: String,
        guildId: String
    ) {
        try {
            discordManager.initialize(token, guildId)
            errorMetrics.recordConnectionAttempt(success = true)
            logger.info("Discord bot successfully connected!")
            finalizeDiscordServices(discordManager, apiWrapper)
        } catch (e: Exception) {
            errorMetrics.recordConnectionAttempt(success = false)
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
    private fun finalizeDiscordServices(discordManager: DiscordManager, apiWrapper: DiscordApiWrapper) {
        // Initialize channel resolver for auto-creating channels
        initializeChannelResolver()

        // Initialize feature services that depend on Discord connection
        initializePlayerCountServices(discordManager)
        initializeChatBridgeServices(discordManager)
        val webhookMgr = initializeEventAnnouncements(discordManager)

        discord = DiscordServices(
            manager = discordManager,
            apiWrapper = apiWrapper,
            webhookManager = webhookMgr
        )
    }

    /**
     * Initialize the channel resolver for automatic channel creation.
     */
    private fun initializeChannelResolver() {
        val channelCreationConfig = ChannelCreationConfig.fromConfig(config)
        val configFile = File(dataFolder, "config.yml")
        channelResolver = ChannelResolver(logger, channelCreationConfig, resilience.circuitBreaker, configFile)

        if (channelCreationConfig.autoCreate) {
            logger.info("Automatic channel creation enabled")
            if (channelCreationConfig.categoryName.isNotBlank()) {
                logger.info("Channels will be created in category: ${channelCreationConfig.categoryName}")
            }
        } else {
            logger.info("Automatic channel creation disabled - using existing channels only")
        }
    }

    /**
     * Create Discord services when connection failed (degraded mode).
     */
    private fun finalizeDiscordServicesDisconnected(discordManager: DiscordManager, apiWrapper: DiscordApiWrapper) {
        discord = DiscordServices(
            manager = discordManager,
            apiWrapper = apiWrapper,
            webhookManager = null
        )
        // Features are already initialized but without services in disconnected mode
    }

    private fun initializePlayerCountServices(discordManager: DiscordManager) {
        val feature = playerCountFeature ?: return
        if (!discordManager.isConnected()) return

        // Initialize presence
        var presenceSvc: PlayerCountPresence? = null
        if (feature.presenceConfig.enabled) {
            presenceSvc = PlayerCountPresence(this, feature.presenceConfig)
            presenceSvc.initialize(discordManager)
        }

        // Initialize status channel with channel resolution
        var statusChannelSvc: StatusChannelManager? = null
        if (feature.statusChannelConfig.enabled) {
            val resolvedConfig = resolveStatusChannelConfig(discordManager, feature.statusChannelConfig)
            if (resolvedConfig != null) {
                statusChannelSvc = StatusChannelManager(this, resolvedConfig)
                statusChannelSvc.initialize(discordManager)
            }
        }

        feature.setServices(presenceSvc, statusChannelSvc)
    }

    /**
     * Resolve the status channel, creating it if necessary.
     * Returns updated config with resolved channel ID, or null if resolution failed.
     */
    private fun resolveStatusChannelConfig(
        discordManager: DiscordManager,
        originalConfig: StatusChannelConfig
    ): StatusChannelConfig? {
        val resolver = channelResolver ?: return null
        val jda = discordManager.getJda() ?: return null
        val guildId = config.getString("discord.guild-id", "") ?: ""
        val guild = jda.getGuildById(guildId) ?: run {
            logger.warning("Cannot resolve status channel: Guild $guildId not found")
            return null
        }

        val channelConfig = originalConfig.toChannelConfig()

        // Skip if no channel spec provided
        if (!channelConfig.hasChannelSpec()) {
            logger.warning("Status channel enabled but no channel-id or channel-name configured")
            return null
        }

        // Resolve the channel (blocking)
        val resolution = try {
            resolver.resolveVoiceChannel(
                guild = guild,
                channelConfig = channelConfig,
                featureName = "Status Channel",
                configPath = "player-count-display.status-channel.channel-id"
            ).get()
        } catch (e: Exception) {
            logger.warning("Failed to resolve status channel: ${e.message}")
            return null
        }

        return when (resolution) {
            is ChannelResolution.FoundById,
            is ChannelResolution.FoundByName,
            is ChannelResolution.Created -> {
                val channelId = resolution.resolvedChannelId()!!
                // Return config with resolved channel ID
                originalConfig.copy(channelId = channelId)
            }
            is ChannelResolution.NotFound -> {
                logger.warning("Status channel not found: ${resolution.reason}")
                null
            }
            is ChannelResolution.CreationFailed -> {
                logger.warning("Failed to create status channel: ${resolution.reason}")
                null
            }
        }
    }

    private fun initializeChatBridgeServices(discordManager: DiscordManager) {
        val feature = chatBridgeFeature ?: return
        if (!discordManager.isConnected()) return

        val chatConfig = feature.config
        if (!chatConfig.enabled) {
            return
        }

        // Resolve the chat channel
        val resolvedConfig = resolveChatBridgeChannelConfig(discordManager, chatConfig)
        if (resolvedConfig == null) {
            return
        }

        // Initialize webhook manager if webhook is enabled
        var chatWebhook: ChatWebhookManager? = null
        if (resolvedConfig.useWebhook) {
            if (resolvedConfig.webhookUrl.isNullOrBlank()) {
                logger.warning("Chat bridge use-webhook is true but no webhook-url configured")
                logger.warning("Chat bridge falling back to bot messages")
            } else {
                chatWebhook = ChatWebhookManager(this, resolvedConfig.webhookUrl)
                if (chatWebhook.isWebhookAvailable()) {
                    logger.info("Chat bridge using webhook for rich messages")
                }
            }
        }

        val bridge = ChatBridge(this, resolvedConfig, chatWebhook, discordManager)
        // Register as Bukkit listener for MC->Discord
        server.pluginManager.registerEvents(bridge, this)
        registeredListeners.add(bridge)
        // Register as JDA listener for Discord->MC
        discordManager.getJda()?.addEventListener(bridge)
        logger.info("Chat bridge enabled (MC->Discord=${resolvedConfig.minecraftToDiscord}, Discord->MC=${resolvedConfig.discordToMinecraft})")

        feature.setBridge(bridge, chatWebhook)
    }

    /**
     * Resolve the chat bridge channel, creating it if necessary.
     * Returns updated config with resolved channel ID, or null if resolution failed.
     */
    private fun resolveChatBridgeChannelConfig(
        discordManager: DiscordManager,
        originalConfig: ChatBridgeConfig
    ): ChatBridgeConfig? {
        val resolver = channelResolver ?: return null
        val jda = discordManager.getJda() ?: return null
        val guildId = config.getString("discord.guild-id", "") ?: ""
        val guild = jda.getGuildById(guildId) ?: run {
            logger.warning("Cannot resolve chat bridge channel: Guild $guildId not found")
            return null
        }

        val channelConfig = originalConfig.toChannelConfig()

        // Skip if no channel spec provided
        if (!channelConfig.hasChannelSpec()) {
            logger.warning("Chat bridge enabled but no channel-id or channel-name configured")
            return null
        }

        // Resolve the channel (blocking)
        val resolution = try {
            resolver.resolveTextChannel(
                guild = guild,
                channelConfig = channelConfig,
                featureName = "Chat Bridge",
                configPath = "chat-bridge.channel-id"
            ).get()
        } catch (e: Exception) {
            logger.warning("Failed to resolve chat bridge channel: ${e.message}")
            return null
        }

        return when (resolution) {
            is ChannelResolution.FoundById,
            is ChannelResolution.FoundByName,
            is ChannelResolution.Created -> {
                val channelId = resolution.resolvedChannelId()!!
                // Return config with resolved channel ID
                originalConfig.copy(channelId = channelId)
            }
            is ChannelResolution.NotFound -> {
                logger.warning("Chat bridge channel not found: ${resolution.reason}")
                null
            }
            is ChannelResolution.CreationFailed -> {
                logger.warning("Failed to create chat bridge channel: ${resolution.reason}")
                null
            }
        }
    }

    private fun initializeEventAnnouncements(discordManager: DiscordManager): WebhookManager? {
        val feature = eventsFeature ?: return null
        if (!discordManager.isConnected()) return null

        // Initialize MCMMO event listener
        val mcmmoListener = initializeMcMMOListener(discordManager)
        feature.setMcmmoListener(mcmmoListener)

        val eventConfig = feature.config
        if (!eventConfig.enabled) {
            return null
        }

        // Resolve the event channel
        val resolvedConfig = resolveEventAnnouncementChannelConfig(discordManager, eventConfig)
        if (resolvedConfig == null) {
            return null
        }

        // Initialize webhook manager
        val webhookMgr = WebhookManager(this, resolvedConfig.webhookUrl, resolvedConfig.channelId, discordManager)
        if (resolvedConfig.webhookUrl != null) {
            logger.info("Event announcements using webhook")
        } else {
            logger.info("Event announcements using bot messages")
        }

        // Initialize server event listener
        val serverListener = ServerEventListener(this, resolvedConfig, webhookMgr)
        serverListener.announceServerStart()

        // Initialize player death listener
        val deathListener = if (resolvedConfig.playerDeaths.enabled) {
            val listener = PlayerDeathListener(this, resolvedConfig, webhookMgr)
            server.pluginManager.registerEvents(listener, this)
            registeredListeners.add(listener)
            logger.info("Player death announcements enabled")
            listener
        } else null

        // Initialize achievement listener
        val achievementListener = if (resolvedConfig.achievements.enabled) {
            val listener = AchievementListener(this, resolvedConfig, webhookMgr)
            server.pluginManager.registerEvents(listener, this)
            registeredListeners.add(listener)
            logger.info("Achievement announcements enabled (exclude-recipes=${resolvedConfig.achievements.excludeRecipes})")
            listener
        } else null

        feature.setListeners(serverListener, deathListener, achievementListener)
        return webhookMgr
    }

    /**
     * Resolve the event announcement channel, creating it if necessary.
     * Returns updated config with resolved channel ID, or null if resolution failed.
     */
    private fun resolveEventAnnouncementChannelConfig(
        discordManager: DiscordManager,
        originalConfig: EventAnnouncementConfig
    ): EventAnnouncementConfig? {
        val resolver = channelResolver ?: return null
        val jda = discordManager.getJda() ?: return null
        val guildId = config.getString("discord.guild-id", "") ?: ""
        val guild = jda.getGuildById(guildId) ?: run {
            logger.warning("Cannot resolve event announcement channel: Guild $guildId not found")
            return null
        }

        val channelConfig = originalConfig.toChannelConfig()

        // Skip if no channel spec provided
        if (!channelConfig.hasChannelSpec()) {
            logger.warning("Event announcements enabled but no channel-id or channel-name configured")
            return null
        }

        // Resolve the channel (blocking)
        val resolution = try {
            resolver.resolveTextChannel(
                guild = guild,
                channelConfig = channelConfig,
                featureName = "Event Announcements",
                configPath = "event-announcements.server-events.channel-id"
            ).get()
        } catch (e: Exception) {
            logger.warning("Failed to resolve event announcement channel: ${e.message}")
            return null
        }

        return when (resolution) {
            is ChannelResolution.FoundById,
            is ChannelResolution.FoundByName,
            is ChannelResolution.Created -> {
                val channelId = resolution.resolvedChannelId()!!
                // Return config with resolved channel ID
                originalConfig.copy(channelId = channelId)
            }
            is ChannelResolution.NotFound -> {
                logger.warning("Event announcement channel not found: ${resolution.reason}")
                null
            }
            is ChannelResolution.CreationFailed -> {
                logger.warning("Failed to create event announcement channel: ${resolution.reason}")
                null
            }
        }
    }

    private fun initializeMcMMOListener(discordManager: DiscordManager): McMMOEventListener? {
        if (!discordManager.isConnected()) return null

        val announcementConfig = AnnouncementConfig.fromConfig(config)
        if (!announcementConfig.enabled) {
            logger.info("MCMMO announcements are disabled in config")
            return null
        }

        // Resolve the MCMMO milestone channel
        val resolvedConfig = resolveMcmmoMilestoneChannelConfig(discordManager, announcementConfig)
        if (resolvedConfig == null) {
            return null
        }

        // Initialize milestone card renderer if image cards are enabled
        val milestoneCardRenderer = if (resolvedConfig.useImageCards) {
            val serverName = imageFeature?.config?.serverName ?: "Minecraft Server"
            MilestoneCardRenderer(serverName)
        } else null

        // Use existing avatar fetcher or create one for milestones
        val milestoneAvatarFetcher = if (resolvedConfig.useImageCards && resolvedConfig.showAvatars) {
            imageFeature?.avatarFetcher ?: AvatarFetcher(
                logger,
                imageFeature?.config?.avatarCacheMaxSize ?: 100,
                (imageFeature?.config?.avatarCacheTtlSeconds ?: 300) * 1000L
            )
        } else null

        val listener = McMMOEventListener(
            this,
            resolvedConfig,
            milestoneAvatarFetcher,
            milestoneCardRenderer,
            discordManager
        )
        server.pluginManager.registerEvents(listener, this)
        registeredListeners.add(listener)

        val imageMode = if (resolvedConfig.useImageCards) "image cards" else "embeds"
        val webhookMode = if (resolvedConfig.webhookUrl != null) " via webhook" else ""
        logger.info(
            "MCMMO milestone announcements enabled ($imageMode$webhookMode, " +
                "skill=${resolvedConfig.skillMilestoneInterval}, power=${resolvedConfig.powerMilestoneInterval})"
        )

        return listener
    }

    /**
     * Resolve the MCMMO milestone channel, creating it if necessary.
     * Returns updated config with resolved channel ID, or null if resolution failed.
     */
    private fun resolveMcmmoMilestoneChannelConfig(
        discordManager: DiscordManager,
        originalConfig: AnnouncementConfig
    ): AnnouncementConfig? {
        val resolver = channelResolver ?: return null
        val jda = discordManager.getJda() ?: return null
        val guildId = config.getString("discord.guild-id", "") ?: ""
        val guild = jda.getGuildById(guildId) ?: run {
            logger.warning("Cannot resolve MCMMO milestone channel: Guild $guildId not found")
            return null
        }

        val channelConfig = originalConfig.toChannelConfig()

        // Skip if no channel spec provided
        if (!channelConfig.hasChannelSpec()) {
            logger.warning("MCMMO announcements enabled but no channel-id or channel-name configured")
            return null
        }

        // Resolve the channel (blocking)
        val resolution = try {
            resolver.resolveTextChannel(
                guild = guild,
                channelConfig = channelConfig,
                featureName = "MCMMO Milestones",
                configPath = "event-announcements.mcmmo-milestones.channel-id"
            ).get()
        } catch (e: Exception) {
            logger.warning("Failed to resolve MCMMO milestone channel: ${e.message}")
            return null
        }

        return when (resolution) {
            is ChannelResolution.FoundById,
            is ChannelResolution.FoundByName,
            is ChannelResolution.Created -> {
                val channelId = resolution.resolvedChannelId()!!
                // Return config with resolved channel ID
                originalConfig.copy(channelId = channelId)
            }
            is ChannelResolution.NotFound -> {
                logger.warning("MCMMO milestone channel not found: ${resolution.reason}")
                null
            }
            is ChannelResolution.CreationFailed -> {
                logger.warning("Failed to create MCMMO milestone channel: ${resolution.reason}")
                null
            }
        }
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
        shutdownServices()
        logger.info("AMSSync plugin disabled")
    }

    /**
     * Shutdown all services in proper order.
     */
    private fun shutdownServices() {
        // Announce server stop before disconnecting
        eventsFeature?.announceServerStop()

        // Shutdown features before Discord connection
        eventsFeature?.shutdown()
        chatBridgeFeature?.shutdown()
        playerCountFeature?.shutdown()

        // Shutdown Discord services
        if (::discord.isInitialized) {
            discord.shutdown()
        }

        // Shutdown resilience services
        if (::resilience.isInitialized) {
            resilience.shutdown()
        }

        // Shutdown progression feature
        progressionFeature?.shutdown()

        // Save user mappings
        if (::userMappingService.isInitialized) {
            userMappingService.saveMappings()
        }
    }

    /**
     * Perform a full reload of all plugin services.
     *
     * This method:
     * 1. Shuts down all current services in reverse order
     * 2. Unregisters all event listeners
     * 3. Reloads configuration from disk
     * 4. Reinitializes all services in proper order
     * 5. Reconnects to Discord
     *
     * Note: This is a slow operation (~5-10 seconds) due to Discord reconnection.
     *
     * @return true if reload was successful, false if there were errors
     */
    fun reloadAllServices(): Boolean {
        logger.info("=".repeat(50))
        logger.info("Starting full plugin reload...")
        logger.info("=".repeat(50))

        var success = true

        try {
            // Phase 1: Cleanup
            logger.info("[1/4] Shutting down services...")
            shutdownForReload()

            // Phase 2: Reload config
            logger.info("[2/4] Reloading configuration...")
            reloadConfig()
            handleConfigMigration()

            // Phase 3: Reinitialize core services
            logger.info("[3/4] Reinitializing core services...")
            reinitializeCoreServices()

            // Phase 4: Reinitialize Discord and features
            logger.info("[4/4] Reconnecting to Discord...")
            val discordConfig = loadDiscordConfig()
            if (discordConfig == null) {
                logger.severe("Discord configuration invalid - running in degraded mode")
                success = false
            } else {
                reinitializeDiscordAndFeatures(discordConfig)
            }

            logger.info("=".repeat(50))
            logger.info("Plugin reload complete!")
            logger.info("=".repeat(50))
        } catch (e: Exception) {
            logger.severe("Reload failed with exception: ${e.message}")
            logger.severe(e.stackTraceToString())
            success = false
        }

        return success
    }

    /**
     * Shutdown services for reload (different from full shutdown - no server stop announcement).
     */
    private fun shutdownForReload() {
        // Unregister all Bukkit event listeners
        registeredListeners.forEach { listener ->
            HandlerList.unregisterAll(listener)
            logger.fine("Unregistered listener: ${listener.javaClass.simpleName}")
        }
        registeredListeners.clear()

        // Shutdown features (but don't announce server stop)
        eventsFeature?.shutdown()
        chatBridgeFeature?.shutdown()
        playerCountFeature?.shutdown()
        imageFeature?.shutdown()
        progressionFeature?.shutdown()

        // Clear feature references
        eventsFeature = null
        chatBridgeFeature = null
        playerCountFeature = null
        imageFeature = null
        progressionFeature = null

        // Shutdown Discord services (disconnects JDA)
        if (::discord.isInitialized) {
            discord.shutdown()
        }

        // Shutdown resilience services (stops TimeoutManager executor)
        if (::resilience.isInitialized) {
            resilience.shutdown()
        }

        // Save user mappings before reinitializing
        if (::userMappingService.isInitialized) {
            userMappingService.saveMappings()
        }
    }

    /**
     * Reinitialize core services after config reload.
     */
    private fun reinitializeCoreServices() {
        // Reinitialize rate limiter with potentially new config
        initializeRateLimiter()

        // Reload user mappings (may have been edited while running)
        val configFile = File(dataFolder, "config.yml")
        userMappingService = UserMappingService(configFile, logger)
        userMappingService.loadMappings()
        logger.info("Loaded ${userMappingService.getMappingCount()} user mapping(s)")

        // Reinitialize MCMMO API wrapper with potentially new settings
        val maxPlayersToScan = config.getInt("mcmmo.leaderboard.max-players-to-scan", 1000)
        val cacheTtlSeconds = config.getInt("mcmmo.leaderboard.cache-ttl-seconds", 60)
        mcmmoApi = McmmoApiWrapper(logger, maxPlayersToScan, cacheTtlSeconds * 1000L)
        logger.info("MCMMO leaderboard limits: max-scan=$maxPlayersToScan, cache-ttl=${cacheTtlSeconds}s")

        // Reinitialize progression tracking
        initializeProgressionTracking()

        // Reinitialize resilience components
        initializeResilienceComponents()
    }

    /**
     * Reinitialize Discord connection and all dependent features.
     */
    private fun reinitializeDiscordAndFeatures(discordConfig: Pair<String, String>) {
        val retryConfig = loadRetryConfig()

        // Initialize features (they'll get services after Discord connects)
        initializeImageCards()
        initializeEventsFeature()
        initializeChatBridgeFeature()
        initializePlayerCountFeature()

        // Create Discord API wrapper
        val discordApiWrapper = DiscordApiWrapper(resilience.circuitBreaker, logger, errorMetrics)

        // Build slash command handlers and initialize Discord manager
        val commandHandlers = buildSlashCommandHandlers()
        val discordManager = DiscordManager(
            logger,
            discordApiWrapper,
            rateLimiter,
            auditLogger,
            commandHandlers
        )

        // Connect to Discord
        connectToDiscord(discordManager, discordApiWrapper, discordConfig.first, discordConfig.second, retryConfig)
    }
}
