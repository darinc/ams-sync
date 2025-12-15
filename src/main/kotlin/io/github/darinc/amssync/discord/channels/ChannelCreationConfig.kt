package io.github.darinc.amssync.discord.channels

import org.bukkit.configuration.file.FileConfiguration

/**
 * Global configuration for automatic channel creation.
 *
 * @property autoCreate Master enable/disable for creating channels
 * @property categoryName Name of category to create/use for grouping channels
 * @property categoryId Optional explicit category ID (takes priority over categoryName)
 * @property persistChannelIds Whether to save created channel IDs back to config
 */
data class ChannelCreationConfig(
    val autoCreate: Boolean,
    val categoryName: String,
    val categoryId: String,
    val persistChannelIds: Boolean
) {
    companion object {
        private const val PREFIX = "discord.channels"

        /**
         * Load channel creation configuration from Bukkit config file.
         *
         * @param config The Bukkit FileConfiguration to load from
         * @return ChannelCreationConfig with loaded or default values
         */
        fun fromConfig(config: FileConfiguration): ChannelCreationConfig {
            return ChannelCreationConfig(
                autoCreate = config.getBoolean("$PREFIX.auto-create", true),
                categoryName = config.getString("$PREFIX.category-name", "AMS Sync") ?: "AMS Sync",
                categoryId = config.getString("$PREFIX.category-id", "") ?: "",
                persistChannelIds = config.getBoolean("$PREFIX.persist-channel-ids", true)
            )
        }
    }

    /**
     * Check if a specific category is configured (either by ID or name).
     * @return true if category should be used
     */
    fun hasCategory(): Boolean = categoryId.isNotBlank() || categoryName.isNotBlank()

    /**
     * Check if an explicit category ID is configured.
     * @return true if categoryId is set
     */
    fun hasExplicitCategoryId(): Boolean = categoryId.isNotBlank()
}
