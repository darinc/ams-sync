package io.github.darinc.amssync.features

import io.github.darinc.amssync.image.AvatarFetcher
import io.github.darinc.amssync.image.ImageConfig
import io.github.darinc.amssync.image.PlayerCardRenderer
import java.util.logging.Logger

/**
 * Coordinates image card generation services.
 * Manages AvatarFetcher and PlayerCardRenderer lifecycle.
 */
class ImageCardFeature(
    private val logger: Logger,
    private val config: ImageConfig
) : Feature {

    var avatarFetcher: AvatarFetcher? = null
        private set

    var cardRenderer: PlayerCardRenderer? = null
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

    override fun shutdown() {
        // AvatarFetcher and PlayerCardRenderer don't require explicit shutdown
        avatarFetcher = null
        cardRenderer = null
    }

    /**
     * Get the image configuration.
     */
    fun getConfig(): ImageConfig = config
}
