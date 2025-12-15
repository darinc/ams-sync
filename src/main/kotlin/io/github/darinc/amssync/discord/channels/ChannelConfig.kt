package io.github.darinc.amssync.discord.channels

/**
 * Configuration for a Discord channel with support for ID lookup and auto-creation.
 *
 * Resolution priority:
 * 1. If channelId is set and valid, use it directly
 * 2. If channelName is set, look up existing channel by name
 * 3. If auto-create is enabled and channel doesn't exist, create it
 *
 * @property channelId Explicit channel ID (takes priority if non-blank)
 * @property channelName Channel name for lookup or creation
 * @property channelType Whether this is a TEXT or VOICE channel
 */
data class ChannelConfig(
    val channelId: String,
    val channelName: String,
    val channelType: ChannelType
) {
    /**
     * Type of Discord channel.
     */
    enum class ChannelType {
        TEXT,
        VOICE
    }

    /**
     * Check if this config has any channel specification.
     * @return true if either channelId or channelName is set
     */
    fun hasChannelSpec(): Boolean = channelId.isNotBlank() || channelName.isNotBlank()

    /**
     * Check if auto-creation is possible (has a name to create with).
     * @return true if channelName is set
     */
    fun canAutoCreate(): Boolean = channelName.isNotBlank()

    /**
     * Check if an explicit channel ID is configured.
     * @return true if channelId is set
     */
    fun hasExplicitId(): Boolean = channelId.isNotBlank()
}
