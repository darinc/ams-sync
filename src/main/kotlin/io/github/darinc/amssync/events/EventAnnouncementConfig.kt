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
            val webhookUrl = config.getString("discord.events.webhook-url", "") ?: ""

            return EventAnnouncementConfig(
                enabled = config.getBoolean("discord.events.enabled", false),
                channelId = config.getString("discord.events.text-channel-id", "") ?: "",
                webhookUrl = webhookUrl.ifBlank { null },
                useEmbeds = config.getBoolean("discord.events.use-embeds", true),
                showAvatars = config.getBoolean("discord.events.show-avatars", true),
                avatarProvider = config.getString("discord.events.avatar-provider", "mc-heads") ?: "mc-heads",
                serverStart = ServerStartConfig(
                    enabled = config.getBoolean("discord.events.server-start.enabled", true),
                    message = config.getString("discord.events.server-start.message", "Server is now online!")
                        ?: "Server is now online!"
                ),
                serverStop = ServerStopConfig(
                    enabled = config.getBoolean("discord.events.server-stop.enabled", true),
                    message = config.getString("discord.events.server-stop.message", "Server is shutting down...")
                        ?: "Server is shutting down..."
                ),
                playerDeaths = PlayerDeathConfig(
                    enabled = config.getBoolean("discord.events.player-deaths.enabled", true)
                ),
                achievements = AchievementConfig(
                    enabled = config.getBoolean("discord.events.achievements.enabled", true),
                    excludeRecipes = config.getBoolean("discord.events.achievements.exclude-recipes", true)
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
 */
data class PlayerDeathConfig(
    val enabled: Boolean
)

/**
 * Configuration for achievement announcements.
 */
data class AchievementConfig(
    val enabled: Boolean,
    val excludeRecipes: Boolean
)
