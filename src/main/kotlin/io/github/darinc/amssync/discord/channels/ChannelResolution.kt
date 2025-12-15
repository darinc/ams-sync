package io.github.darinc.amssync.discord.channels

/**
 * Result of channel resolution/creation attempt.
 *
 * Represents all possible outcomes when resolving a Discord channel:
 * - Found by explicit ID
 * - Found by name lookup
 * - Created automatically
 * - Not found (auto-create disabled or no spec)
 * - Creation failed (permission denied, etc.)
 *
 * @param T The channel type (TextChannel or VoiceChannel)
 */
sealed class ChannelResolution<T> {

    /**
     * Channel found by explicit ID lookup.
     * @property channel The resolved channel
     * @property channelId The channel's ID
     */
    data class FoundById<T>(val channel: T, val channelId: String) : ChannelResolution<T>()

    /**
     * Channel found by name lookup.
     * @property channel The resolved channel
     * @property channelId The channel's ID (for potential config persistence)
     */
    data class FoundByName<T>(val channel: T, val channelId: String) : ChannelResolution<T>()

    /**
     * Channel was created automatically.
     * @property channel The newly created channel
     * @property channelId The new channel's ID (for config persistence)
     */
    data class Created<T>(val channel: T, val channelId: String) : ChannelResolution<T>()

    /**
     * Channel not found and auto-create is disabled or not possible.
     * @property reason Human-readable explanation
     */
    data class NotFound<T>(val reason: String) : ChannelResolution<T>()

    /**
     * Channel creation failed (permission denied, rate limit, etc.).
     * @property reason Human-readable explanation
     * @property exception The underlying exception if available
     */
    data class CreationFailed<T>(val reason: String, val exception: Exception?) : ChannelResolution<T>()

    /**
     * Get the channel if resolution was successful.
     * @return The channel or null if resolution failed
     */
    fun resolvedChannel(): T? = when (this) {
        is FoundById -> channel
        is FoundByName -> channel
        is Created -> channel
        is NotFound -> null
        is CreationFailed -> null
    }

    /**
     * Get the channel ID if resolution was successful.
     * @return The channel ID or null if resolution failed
     */
    fun resolvedChannelId(): String? = when (this) {
        is FoundById -> channelId
        is FoundByName -> channelId
        is Created -> channelId
        is NotFound -> null
        is CreationFailed -> null
    }

    /**
     * Check if resolution was successful.
     * @return true if a channel was found or created
     */
    fun isSuccess(): Boolean = this is FoundById || this is FoundByName || this is Created

    /**
     * Check if this resolution resulted in a newly created channel.
     * @return true if the channel was created
     */
    fun wasCreated(): Boolean = this is Created
}
