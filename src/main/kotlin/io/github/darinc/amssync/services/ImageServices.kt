package io.github.darinc.amssync.services

import io.github.darinc.amssync.discord.commands.AmsStatsCommand
import io.github.darinc.amssync.discord.commands.AmsTopCommand
import io.github.darinc.amssync.image.AvatarFetcher
import io.github.darinc.amssync.image.ImageConfig
import io.github.darinc.amssync.image.PlayerCardRenderer

/**
 * Groups image card-related services: configuration, avatar fetching, card rendering.
 */
data class ImageServices(
    val config: ImageConfig?,
    val avatarFetcher: AvatarFetcher?,
    val cardRenderer: PlayerCardRenderer?,
    val statsCommand: AmsStatsCommand?,
    val topCommand: AmsTopCommand?
) {
    /**
     * Check if image cards are enabled.
     */
    fun isEnabled(): Boolean = config?.enabled == true

    companion object {
        /**
         * Create disabled image services.
         */
        fun disabled(): ImageServices = ImageServices(null, null, null, null, null)
    }
}
