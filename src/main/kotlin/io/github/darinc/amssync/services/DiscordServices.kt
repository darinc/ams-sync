package io.github.darinc.amssync.services

import io.github.darinc.amssync.discord.ChatBridge
import io.github.darinc.amssync.discord.ChatWebhookManager
import io.github.darinc.amssync.discord.DiscordApiWrapper
import io.github.darinc.amssync.discord.DiscordManager
import io.github.darinc.amssync.discord.PlayerCountPresence
import io.github.darinc.amssync.discord.StatusChannelManager
import io.github.darinc.amssync.discord.WebhookManager

/**
 * Groups Discord-related services: connection management, presence, chat bridge, webhooks.
 */
data class DiscordServices(
    val manager: DiscordManager,
    val apiWrapper: DiscordApiWrapper?,
    val chatBridge: ChatBridge?,
    val chatWebhookManager: ChatWebhookManager?,
    val webhookManager: WebhookManager?,
    val presence: PlayerCountPresence?,
    val statusChannel: StatusChannelManager?
) {
    /**
     * Shutdown all Discord services in proper order.
     */
    fun shutdown() {
        presence?.shutdown()
        statusChannel?.shutdown()
        webhookManager?.shutdown()
        chatWebhookManager?.shutdown()
        manager.shutdown()
    }

    /**
     * Check if Discord is connected.
     */
    fun isConnected(): Boolean = manager.isConnected()
}
