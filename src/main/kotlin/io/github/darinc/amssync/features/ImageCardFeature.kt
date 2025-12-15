package io.github.darinc.amssync.features

import io.github.darinc.amssync.discord.commands.AmsStatsCommand
import io.github.darinc.amssync.discord.commands.AmsTopCommand
import io.github.darinc.amssync.discord.commands.SlashCommandHandler
import io.github.darinc.amssync.image.AvatarFetcher
import io.github.darinc.amssync.image.ImageConfig
import io.github.darinc.amssync.image.PlayerCardRenderer
import io.github.darinc.amssync.AMSSyncPlugin
import java.util.logging.Logger

/**
 * Coordinates image card generation services.
 * Manages AvatarFetcher, PlayerCardRenderer, and related commands lifecycle.
 */
class ImageCardFeature(
    private val logger: Logger,
    val config: ImageConfig
) : Feature {

    var avatarFetcher: AvatarFetcher? = null
        private set

    var cardRenderer: PlayerCardRenderer? = null
        private set

    var statsCommand: AmsStatsCommand? = null
        private set

    var topCommand: AmsTopCommand? = null
        private set

    override val isEnabled: Boolean
        get() = config.enabled

    override fun initialize() {
        if (!isEnabled) {
            logger.info("Image cards are disabled in config")
            return
        }

        // Initialize avatar fetcher with caching
        avatarFetcher = AvatarFetcher(
            logger = logger,
            cacheMaxSize = config.avatarCacheMaxSize,
            cacheTtlMs = config.getCacheTtlMs()
        )

        // Initialize card renderer
        cardRenderer = PlayerCardRenderer(config.serverName)

        logger.info("Image cards enabled (provider=${config.avatarProvider}, cache=${config.avatarCacheTtlSeconds}s)")
    }

    /**
     * Initialize slash commands after base services are ready.
     * Must be called after initialize() and requires plugin reference.
     */
    fun initializeCommands(plugin: AMSSyncPlugin) {
        if (!isEnabled) return

        val fetcher = avatarFetcher ?: return
        val renderer = cardRenderer ?: return

        statsCommand = AmsStatsCommand(plugin, config, fetcher, renderer)
        topCommand = AmsTopCommand(plugin, config, fetcher, renderer)
    }

    /**
     * Get registered slash command handlers.
     */
    fun getCommandHandlers(): Map<String, SlashCommandHandler> {
        val handlers = mutableMapOf<String, SlashCommandHandler>()
        statsCommand?.let { handlers["amsstats"] = it }
        topCommand?.let { handlers["amstop"] = it }
        return handlers
    }

    override fun shutdown() {
        // AvatarFetcher and PlayerCardRenderer don't require explicit shutdown
        avatarFetcher = null
        cardRenderer = null
        statsCommand = null
        topCommand = null
    }
}
