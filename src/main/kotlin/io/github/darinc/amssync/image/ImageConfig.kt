package io.github.darinc.amssync.image

import org.bukkit.configuration.file.FileConfiguration

/**
 * Configuration for image card generation.
 *
 * @property enabled Whether image cards are enabled
 * @property avatarProvider Avatar API provider ("mc-heads" or "crafatar")
 * @property serverName Server name shown in card footer
 * @property avatarCacheTtlSeconds TTL for cached avatars
 * @property avatarCacheMaxSize Maximum number of cached avatars
 */
data class ImageConfig(
    val enabled: Boolean,
    val avatarProvider: String,
    val serverName: String,
    val avatarCacheTtlSeconds: Int,
    val avatarCacheMaxSize: Int
) {
    companion object {
        /**
         * Load image configuration from Bukkit config file.
         *
         * @param config The Bukkit FileConfiguration to load from
         * @return ImageConfig with loaded or default values
         */
        fun fromConfig(config: FileConfiguration): ImageConfig {
            return ImageConfig(
                enabled = config.getBoolean("image-cards.enabled", true),
                avatarProvider = config.getString("image-cards.avatar-provider", "mc-heads")
                    ?: "mc-heads",
                serverName = config.getString("image-cards.server-name", "Minecraft Server")
                    ?: "Minecraft Server",
                avatarCacheTtlSeconds = config.getInt("image-cards.avatar-cache-ttl-seconds", 300),
                avatarCacheMaxSize = config.getInt("image-cards.avatar-cache-max-size", 100)
            )
        }
    }

    /**
     * Get cache TTL in milliseconds.
     */
    fun getCacheTtlMs(): Long = avatarCacheTtlSeconds * 1000L
}
