package io.github.darinc.amssync.features

import io.github.darinc.amssync.discord.PlayerCountPresence
import io.github.darinc.amssync.discord.PresenceConfig
import io.github.darinc.amssync.discord.StatusChannelConfig
import io.github.darinc.amssync.discord.StatusChannelManager
import java.util.logging.Logger

/**
 * Coordinates player count display services.
 * Manages bot presence (activity/status) and voice channel name updates.
 */
class PlayerCountDisplayFeature(
    private val logger: Logger,
    val presenceConfig: PresenceConfig,
    val statusChannelConfig: StatusChannelConfig
) : Feature {

    var presence: PlayerCountPresence? = null
        private set

    var statusChannel: StatusChannelManager? = null
        private set

    override val isEnabled: Boolean
        get() = presenceConfig.enabled || statusChannelConfig.enabled

    override fun initialize() {
        // Player count display requires DiscordManager - use setServices() after connection
        if (!presenceConfig.enabled) {
            logger.info("Player count presence is disabled in config")
        }
        if (!statusChannelConfig.enabled) {
            logger.info("Status channel manager is disabled in config")
        }
    }

    /**
     * Set the presence and status channel after Discord connection.
     */
    fun setServices(presenceSvc: PlayerCountPresence?, statusChannelSvc: StatusChannelManager?) {
        presence = presenceSvc
        statusChannel = statusChannelSvc
    }

    override fun shutdown() {
        presence?.shutdown()
        statusChannel?.shutdown()
        presence = null
        statusChannel = null
    }
}
