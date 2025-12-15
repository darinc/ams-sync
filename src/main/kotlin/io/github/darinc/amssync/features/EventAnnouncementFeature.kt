package io.github.darinc.amssync.features

import io.github.darinc.amssync.events.AchievementListener
import io.github.darinc.amssync.events.EventAnnouncementConfig
import io.github.darinc.amssync.events.PlayerDeathListener
import io.github.darinc.amssync.events.ServerEventListener
import io.github.darinc.amssync.mcmmo.McMMOEventListener
import java.util.logging.Logger

/**
 * Coordinates event announcement services.
 * Manages server events, death announcements, achievements, and MCMMO milestones.
 */
class EventAnnouncementFeature(
    private val logger: Logger,
    val config: EventAnnouncementConfig
) : Feature {

    var mcmmoListener: McMMOEventListener? = null
        private set

    var serverListener: ServerEventListener? = null
        private set

    var deathListener: PlayerDeathListener? = null
        private set

    var achievementListener: AchievementListener? = null
        private set

    override val isEnabled: Boolean
        get() = config.enabled

    override fun initialize() {
        // Event initialization requires DiscordManager - use setListeners() after connection
        if (!isEnabled) {
            logger.info("Event announcements are disabled in config")
        }
    }

    /**
     * Set the MCMMO listener (initialized separately from main event config).
     */
    fun setMcmmoListener(listener: McMMOEventListener?) {
        mcmmoListener = listener
    }

    /**
     * Set event listeners after Discord connection is established.
     */
    fun setListeners(
        server: ServerEventListener?,
        death: PlayerDeathListener?,
        achievement: AchievementListener?
    ) {
        serverListener = server
        deathListener = death
        achievementListener = achievement
    }

    /**
     * Announce server stop event.
     * Should be called before shutdown() to ensure message is sent.
     */
    fun announceServerStop() {
        serverListener?.announceServerStop()
    }

    override fun shutdown() {
        mcmmoListener?.shutdown()
        mcmmoListener = null
        serverListener = null
        deathListener = null
        achievementListener = null
    }
}
