package io.github.darinc.amssync.events

import org.bukkit.configuration.file.FileConfiguration
import java.util.UUID

/**
 * Configuration for server event announcements to Discord.
 *
 * @property enabled Master enable/disable for event announcements
 * @property channelId Text channel ID for announcements
 * @property webhookUrl Optional webhook URL for prettier messages (uses bot if empty)
 * @property useEmbeds Use rich embeds (true) or plain text (false)
 * @property showAvatars Include player avatars in embeds/webhooks
 * @property avatarProvider Avatar service: "mc-heads" or "crafatar"
 * @property serverStart Server start announcement settings
 * @property serverStop Server stop announcement settings
 * @property playerDeaths Player death announcement settings
 * @property achievements Achievement announcement settings
 */
data class EventAnnouncementConfig(
    val enabled: Boolean,
    val channelId: String,
    val webhookUrl: String?,
    val useEmbeds: Boolean,
    val showAvatars: Boolean,
    val avatarProvider: String,
    val serverStart: ServerStartConfig,
    val serverStop: ServerStopConfig,
    val playerDeaths: PlayerDeathConfig,
    val achievements: AchievementConfig
) {
    companion object {
        /**
         * Load event announcement configuration from Bukkit config file.
         */
        fun fromConfig(config: FileConfiguration): EventAnnouncementConfig {
            val webhookUrl = config.getString("event-announcements.webhook.url", "") ?: ""

            return EventAnnouncementConfig(
                enabled = config.getBoolean("event-announcements.enabled", false),
                channelId = config.getString("event-announcements.server-events.channel-id", "") ?: "",
                webhookUrl = webhookUrl.ifBlank { null },
                useEmbeds = config.getBoolean("event-announcements.webhook.use-embeds", true),
                showAvatars = config.getBoolean("event-announcements.webhook.show-avatars", true),
                avatarProvider = config.getString("event-announcements.webhook.avatar-provider", "mc-heads")
                    ?: "mc-heads",
                serverStart = ServerStartConfig(
                    enabled = config.getBoolean("event-announcements.server-events.start.enabled", true),
                    message = config.getString(
                        "event-announcements.server-events.start.message",
                        "Server is now online!"
                    ) ?: "Server is now online!"
                ),
                serverStop = ServerStopConfig(
                    enabled = config.getBoolean("event-announcements.server-events.stop.enabled", true),
                    message = config.getString(
                        "event-announcements.server-events.stop.message",
                        "Server is shutting down..."
                    ) ?: "Server is shutting down..."
                ),
                playerDeaths = PlayerDeathConfig(
                    enabled = config.getBoolean("event-announcements.player-deaths.enabled", true),
                    commentaryEnabled = config.getBoolean(
                        "event-announcements.player-deaths.commentary.enabled",
                        true
                    ),
                    mcmmoRoastsEnabled = config.getBoolean(
                        "event-announcements.player-deaths.commentary.mcmmo-roasts",
                        true
                    ),
                    mcmmoRoastThreshold = config.getInt(
                        "event-announcements.player-deaths.commentary.mcmmo-roast-threshold",
                        DeathCommentaryRepository.DEFAULT_ELITE_POWER_THRESHOLD
                    )
                ),
                achievements = AchievementConfig(
                    enabled = config.getBoolean("event-announcements.achievements.enabled", true),
                    excludeRecipes = config.getBoolean("event-announcements.achievements.exclude-recipes", true)
                )
            )
        }

        /**
         * Get avatar URL for a player.
         *
         * @param playerName Minecraft username
         * @param uuid Player UUID
         * @param provider Avatar service ("mc-heads" or "crafatar")
         * @return URL to player's avatar image
         */
        fun getAvatarUrl(playerName: String, uuid: UUID, provider: String): String {
            return when (provider.lowercase()) {
                "crafatar" -> {
                    // Crafatar requires UUID without dashes
                    val id = uuid.toString().replace("-", "")
                    "https://crafatar.com/avatars/$id?size=64&overlay"
                }
                else -> "https://mc-heads.net/avatar/$playerName/64"
            }
        }
    }
}

/**
 * Configuration for server start announcements.
 */
data class ServerStartConfig(
    val enabled: Boolean,
    val message: String
)

/**
 * Configuration for server stop announcements.
 */
data class ServerStopConfig(
    val enabled: Boolean,
    val message: String
)

/**
 * Configuration for player death announcements.
 *
 * @property enabled Enable/disable death announcements
 * @property commentaryEnabled Enable funny commentary after death messages
 * @property mcmmoRoastsEnabled Enable extra roasts for high-level MCMMO players
 * @property mcmmoRoastThreshold Power level threshold for MCMMO roasts
 */
data class PlayerDeathConfig(
    val enabled: Boolean,
    val commentaryEnabled: Boolean = true,
    val mcmmoRoastsEnabled: Boolean = true,
    val mcmmoRoastThreshold: Int = DeathCommentaryRepository.DEFAULT_ELITE_POWER_THRESHOLD
)

/**
 * Configuration for achievement announcements.
 */
data class AchievementConfig(
    val enabled: Boolean,
    val excludeRecipes: Boolean
)
