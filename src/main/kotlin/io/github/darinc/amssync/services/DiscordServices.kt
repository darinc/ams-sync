package io.github.darinc.amssync.services

import io.github.darinc.amssync.discord.DiscordApiWrapper
import io.github.darinc.amssync.discord.DiscordManager
import io.github.darinc.amssync.discord.WebhookManager

/**
 * Groups core Discord services: connection management and event webhook.
 * Feature-specific services (presence, chat bridge) are managed by their respective Features.
 */
data class DiscordServices(
    val manager: DiscordManager,
    val apiWrapper: DiscordApiWrapper?,
    val webhookManager: WebhookManager?
) {
    /**
     * Shutdown Discord services.
     * Note: Feature services (presence, chat bridge) are shutdown by their respective Features.
     */
    fun shutdown() {
        webhookManager?.shutdown()
        manager.shutdown()
    }

    /**
     * Check if Discord is connected.
     */
    fun isConnected(): Boolean = manager.isConnected()
}
