package io.github.darinc.amssync.discord.channels

import io.github.darinc.amssync.discord.CircuitBreaker
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.concrete.Category
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.logging.Logger

/**
 * Resolves and optionally creates Discord channels based on configuration.
 *
 * Resolution priority:
 * 1. If channelId is set, look up by ID
 * 2. If channelName is set, look up existing channel by name
 * 3. If auto-create is enabled and channel doesn't exist, create it
 *
 * Thread Safety: All JDA operations use async callbacks; this class
 * returns CompletableFuture to allow callers to handle results.
 *
 * @property logger Logger for resolution and creation events
 * @property globalConfig Global channel creation settings
 * @property circuitBreaker Optional circuit breaker for Discord API calls
 * @property configFile Config file for persisting created channel IDs
 */
class ChannelResolver(
    private val logger: Logger,
    private val globalConfig: ChannelCreationConfig,
    private val circuitBreaker: CircuitBreaker?,
    private val configFile: File
) {
    // Cache for resolved category to avoid repeated lookups
    private var resolvedCategory: Category? = null
    private var categoryResolutionAttempted = false

    /**
     * Resolve a text channel using the given configuration.
     *
     * @param guild Guild to resolve channel in
     * @param channelConfig Channel-specific configuration
     * @param featureName Name of feature (for logging)
     * @param configPath Config path for persisting channel ID (e.g., "chat-bridge.channel-id")
     * @return CompletableFuture with resolution result
     */
    fun resolveTextChannel(
        guild: Guild,
        channelConfig: ChannelConfig,
        featureName: String,
        configPath: String
    ): CompletableFuture<ChannelResolution<TextChannel>> {
        return resolveChannel(
            guild = guild,
            channelConfig = channelConfig,
            featureName = featureName,
            configPath = configPath,
            lookupById = { id -> guild.getTextChannelById(id) },
            lookupByName = { name -> guild.getTextChannelsByName(name, true) },
            createChannel = { name, category -> createTextChannel(guild, name, category) },
            getChannelId = { channel -> channel.id }
        )
    }

    /**
     * Resolve a voice channel using the given configuration.
     *
     * @param guild Guild to resolve channel in
     * @param channelConfig Channel-specific configuration
     * @param featureName Name of feature (for logging)
     * @param configPath Config path for persisting channel ID (e.g., "player-count-display.status-channel.channel-id")
     * @return CompletableFuture with resolution result
     */
    fun resolveVoiceChannel(
        guild: Guild,
        channelConfig: ChannelConfig,
        featureName: String,
        configPath: String
    ): CompletableFuture<ChannelResolution<VoiceChannel>> {
        return resolveChannel(
            guild = guild,
            channelConfig = channelConfig,
            featureName = featureName,
            configPath = configPath,
            lookupById = { id -> guild.getVoiceChannelById(id) },
            lookupByName = { name -> guild.getVoiceChannelsByName(name, true) },
            createChannel = { name, category -> createVoiceChannel(guild, name, category) },
            getChannelId = { channel -> channel.id }
        )
    }

    /**
     * Generic channel resolution logic.
     */
    private fun <T> resolveChannel(
        guild: Guild,
        channelConfig: ChannelConfig,
        featureName: String,
        configPath: String,
        lookupById: (String) -> T?,
        lookupByName: (String) -> List<T>,
        createChannel: (String, Category?) -> CompletableFuture<T>,
        getChannelId: (T) -> String
    ): CompletableFuture<ChannelResolution<T>> {
        val future = CompletableFuture<ChannelResolution<T>>()

        // Step 1: Try lookup by ID
        if (channelConfig.channelId.isNotBlank()) {
            val channel = lookupById(channelConfig.channelId)
            if (channel != null) {
                logger.fine("$featureName: Channel found by ID ${channelConfig.channelId}")
                future.complete(ChannelResolution.FoundById(channel, channelConfig.channelId))
                return future
            }
            logger.warning("$featureName: Channel ID ${channelConfig.channelId} not found, trying by name")
        }

        // Step 2: Try lookup by name
        if (channelConfig.channelName.isNotBlank()) {
            val channels = lookupByName(channelConfig.channelName)
            if (channels.isNotEmpty()) {
                val channel = channels.first()
                val channelId = getChannelId(channel)
                logger.info("$featureName: Channel found by name '${channelConfig.channelName}' (ID: $channelId)")

                // Optionally persist the found channel ID
                if (globalConfig.persistChannelIds && !channelConfig.hasExplicitId()) {
                    persistChannelId(configPath, channelId)
                }

                future.complete(ChannelResolution.FoundByName(channel, channelId))
                return future
            }
        }

        // Step 3: Auto-create if enabled
        if (globalConfig.autoCreate && channelConfig.canAutoCreate()) {
            // Check bot permissions first
            if (!guild.selfMember.hasPermission(Permission.MANAGE_CHANNEL)) {
                val reason = "Bot lacks MANAGE_CHANNELS permission - cannot auto-create channel"
                logger.warning("$featureName: $reason")
                future.complete(ChannelResolution.CreationFailed(reason, null))
                return future
            }

            logger.info("$featureName: Auto-creating channel '${channelConfig.channelName}'")

            // Resolve category first (if configured)
            resolveOrCreateCategory(guild).thenCompose { category ->
                createChannel(channelConfig.channelName, category)
            }.thenAccept { channel ->
                val channelId = getChannelId(channel)
                logger.info("$featureName: Created channel '${channelConfig.channelName}' (ID: $channelId)")

                // Persist the created channel ID
                if (globalConfig.persistChannelIds) {
                    persistChannelId(configPath, channelId)
                }

                future.complete(ChannelResolution.Created(channel, channelId))
            }.exceptionally { error ->
                val reason = "Failed to create channel: ${error.message}"
                logger.warning("$featureName: $reason")
                future.complete(ChannelResolution.CreationFailed(reason, error as? Exception))
                null
            }
            return future
        }

        // Step 4: Not found and can't create
        val reason = when {
            !channelConfig.hasChannelSpec() -> "No channel-id or channel-name configured"
            !globalConfig.autoCreate -> "Channel '${channelConfig.channelName}' not found and auto-create is disabled"
            else -> "Channel not found"
        }
        logger.warning("$featureName: $reason")
        future.complete(ChannelResolution.NotFound(reason))
        return future
    }

    /**
     * Resolve or create the category for grouping channels.
     *
     * @param guild Guild to resolve category in
     * @return CompletableFuture with the category (or null if no category configured)
     */
    private fun resolveOrCreateCategory(guild: Guild): CompletableFuture<Category?> {
        val future = CompletableFuture<Category?>()

        // Return cached result if already resolved
        if (categoryResolutionAttempted) {
            future.complete(resolvedCategory)
            return future
        }

        // No category configured
        if (!globalConfig.hasCategory()) {
            categoryResolutionAttempted = true
            resolvedCategory = null
            future.complete(null)
            return future
        }

        // Try by explicit ID first
        if (globalConfig.hasExplicitCategoryId()) {
            val category = guild.getCategoryById(globalConfig.categoryId)
            if (category != null) {
                categoryResolutionAttempted = true
                resolvedCategory = category
                logger.fine("Category found by ID: ${globalConfig.categoryId}")
                future.complete(category)
                return future
            }
            logger.warning("Category ID ${globalConfig.categoryId} not found, trying by name")
        }

        // Try by name
        val categories = guild.getCategoriesByName(globalConfig.categoryName, true)
        if (categories.isNotEmpty()) {
            val category = categories.first()
            categoryResolutionAttempted = true
            resolvedCategory = category
            logger.info("Category found by name '${globalConfig.categoryName}' (ID: ${category.id})")
            future.complete(category)
            return future
        }

        // Create category if auto-create is enabled
        if (globalConfig.autoCreate) {
            if (!guild.selfMember.hasPermission(Permission.MANAGE_CHANNEL)) {
                logger.warning("Bot lacks MANAGE_CHANNELS permission - cannot create category")
                categoryResolutionAttempted = true
                resolvedCategory = null
                future.complete(null)
                return future
            }

            logger.info("Creating category '${globalConfig.categoryName}'")

            val createAction = guild.createCategory(globalConfig.categoryName)

            if (circuitBreaker != null) {
                val result = circuitBreaker.execute("Create category") {
                    createAction.complete()
                }
                when (result) {
                    is CircuitBreaker.CircuitResult.Success -> {
                        categoryResolutionAttempted = true
                        resolvedCategory = result.value
                        logger.info("Created category '${globalConfig.categoryName}' (ID: ${result.value.id})")
                        future.complete(result.value)
                    }
                    is CircuitBreaker.CircuitResult.Failure -> {
                        logger.warning("Failed to create category: ${result.exception.message}")
                        categoryResolutionAttempted = true
                        resolvedCategory = null
                        future.complete(null)
                    }
                    is CircuitBreaker.CircuitResult.Rejected -> {
                        logger.fine("Category creation rejected by circuit breaker")
                        categoryResolutionAttempted = true
                        resolvedCategory = null
                        future.complete(null)
                    }
                }
            } else {
                createAction.queue(
                    { category ->
                        categoryResolutionAttempted = true
                        resolvedCategory = category
                        logger.info("Created category '${globalConfig.categoryName}' (ID: ${category.id})")
                        future.complete(category)
                    },
                    { error ->
                        logger.warning("Failed to create category: ${error.message}")
                        categoryResolutionAttempted = true
                        resolvedCategory = null
                        future.complete(null)
                    }
                )
            }
        } else {
            logger.fine("Category '${globalConfig.categoryName}' not found and auto-create is disabled")
            categoryResolutionAttempted = true
            resolvedCategory = null
            future.complete(null)
        }

        return future
    }

    /**
     * Create a text channel in the guild.
     */
    private fun createTextChannel(
        guild: Guild,
        name: String,
        category: Category?
    ): CompletableFuture<TextChannel> {
        val future = CompletableFuture<TextChannel>()

        val action = if (category != null) {
            category.createTextChannel(name)
        } else {
            guild.createTextChannel(name)
        }

        if (circuitBreaker != null) {
            val result = circuitBreaker.execute("Create text channel") {
                action.complete()
            }
            when (result) {
                is CircuitBreaker.CircuitResult.Success -> future.complete(result.value)
                is CircuitBreaker.CircuitResult.Failure -> future.completeExceptionally(result.exception)
                is CircuitBreaker.CircuitResult.Rejected -> {
                    future.completeExceptionally(Exception("Circuit breaker rejected: ${result.message}"))
                }
            }
        } else {
            action.queue(
                { channel -> future.complete(channel) },
                { error -> future.completeExceptionally(error) }
            )
        }

        return future
    }

    /**
     * Create a voice channel in the guild.
     */
    private fun createVoiceChannel(
        guild: Guild,
        name: String,
        category: Category?
    ): CompletableFuture<VoiceChannel> {
        val future = CompletableFuture<VoiceChannel>()

        val action = if (category != null) {
            category.createVoiceChannel(name)
        } else {
            guild.createVoiceChannel(name)
        }

        if (circuitBreaker != null) {
            val result = circuitBreaker.execute("Create voice channel") {
                action.complete()
            }
            when (result) {
                is CircuitBreaker.CircuitResult.Success -> future.complete(result.value)
                is CircuitBreaker.CircuitResult.Failure -> future.completeExceptionally(result.exception)
                is CircuitBreaker.CircuitResult.Rejected -> {
                    future.completeExceptionally(Exception("Circuit breaker rejected: ${result.message}"))
                }
            }
        } else {
            action.queue(
                { channel -> future.complete(channel) },
                { error -> future.completeExceptionally(error) }
            )
        }

        return future
    }

    /**
     * Persist a resolved/created channel ID to the config file.
     *
     * @param configPath Config path to save to (e.g., "chat-bridge.channel-id")
     * @param channelId The channel ID to save
     */
    private fun persistChannelId(configPath: String, channelId: String) {
        try {
            // Reload config from disk to get any manual changes
            val config = if (configFile.exists()) {
                YamlConfiguration.loadConfiguration(configFile)
            } else {
                logger.warning("Config file not found, cannot persist channel ID")
                return
            }

            config.set(configPath, channelId)
            config.save(configFile)
            logger.info("Saved channel ID $channelId to config: $configPath")
        } catch (e: Exception) {
            logger.warning("Failed to persist channel ID to config: ${e.message}")
        }
    }

    /**
     * Check if the bot has the required permissions for channel creation.
     *
     * @param guild Guild to check permissions in
     * @return List of missing permission names (empty if all permissions present)
     */
    fun checkRequiredPermissions(guild: Guild): List<String> {
        val missing = mutableListOf<String>()
        val self = guild.selfMember

        if (!self.hasPermission(Permission.MANAGE_CHANNEL)) {
            missing.add("MANAGE_CHANNELS (required for creating channels)")
        }
        if (!self.hasPermission(Permission.VIEW_CHANNEL)) {
            missing.add("VIEW_CHANNEL (required to see channels)")
        }

        return missing
    }

    /**
     * Reset the category resolution cache.
     * Call this if the category is deleted or moved.
     */
    fun resetCategoryCache() {
        categoryResolutionAttempted = false
        resolvedCategory = null
    }
}
