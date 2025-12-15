package io.github.darinc.amssync.discord

import org.bukkit.configuration.file.FileConfiguration

/**
 * Configuration for player count presence display.
 *
 * Loaded from config.yml under player-count-display.bot-presence section.
 *
 * @property enabled Master enable/disable for all presence features
 * @property minIntervalMs Minimum milliseconds between Discord API updates
 * @property debounceMs Debounce delay in milliseconds after player join/quit
 * @property activity Activity/status display configuration
 * @property nickname Nickname display configuration
 */
data class PresenceConfig(
    val enabled: Boolean,
    val minIntervalMs: Long,
    val debounceMs: Long,
    val activity: ActivityConfig,
    val nickname: NicknameConfig
) {
    companion object {
        private const val PREFIX = "player-count-display.bot-presence"

        /**
         * Load presence configuration from Bukkit config file.
         *
         * @param config The Bukkit FileConfiguration to load from
         * @return PresenceConfig with loaded or default values
         */
        fun fromConfig(config: FileConfiguration): PresenceConfig {
            return PresenceConfig(
                enabled = config.getBoolean("$PREFIX.enabled", true),
                minIntervalMs = config.getInt("$PREFIX.min-update-interval-seconds", 30) * 1000L,
                debounceMs = config.getInt("$PREFIX.debounce-seconds", 5) * 1000L,
                activity = ActivityConfig(
                    enabled = config.getBoolean("$PREFIX.activity.enabled", true),
                    type = config.getString("$PREFIX.activity.type", "playing") ?: "playing",
                    template = config.getString("$PREFIX.activity.template", "{count} players online")
                        ?: "{count} players online"
                ),
                nickname = NicknameConfig(
                    enabled = config.getBoolean("$PREFIX.nickname.enabled", false),
                    template = config.getString("$PREFIX.nickname.template", "[{count}] {name}")
                        ?: "[{count}] {name}",
                    gracefulFallback = config.getBoolean("$PREFIX.nickname.graceful-fallback", true)
                )
            )
        }
    }
}

/**
 * Configuration for bot activity/status display.
 *
 * @property enabled Enable/disable activity updates
 * @property type Activity type: playing, watching, listening, competing
 * @property template Message template with {count} and {max} placeholders
 */
data class ActivityConfig(
    val enabled: Boolean,
    val type: String,
    val template: String
)

/**
 * Configuration for bot nickname display.
 *
 * @property enabled Enable/disable nickname updates
 * @property template Nickname template with {count}, {name}, and {max} placeholders
 * @property gracefulFallback If true, continue on permission errors; if false, disable after first error
 */
data class NicknameConfig(
    val enabled: Boolean,
    val template: String,
    val gracefulFallback: Boolean
)
