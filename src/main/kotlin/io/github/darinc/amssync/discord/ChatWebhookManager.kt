package io.github.darinc.amssync.discord

import io.github.darinc.amssync.AMSSyncPlugin

/**
 * Manages Discord webhook sending for chat bridge messages.
 *
 * Sends messages via webhook with custom username (player name) and avatar (player head).
 * When the webhook is unavailable, [ChatBridge] falls back to standard bot messages.
 *
 * @property plugin The parent plugin instance
 * @property webhookUrl Webhook URL (null = use bot messages)
 * @property discordManager Provides the JDA connection used to send webhook requests
 */
class ChatWebhookManager(
    private val plugin: AMSSyncPlugin,
    private val webhookUrl: String?,
    private val discordManager: DiscordManager?
) {
    private var webhookSender: WebhookSender? = null

    init {
        if (!webhookUrl.isNullOrBlank()) {
            if (WebhookSender.isValidUrl(webhookUrl)) {
                webhookSender = WebhookSender(
                    webhookUrl,
                    { discordManager?.getJda() },
                    { plugin.resilience.circuitBreaker },
                    plugin.logger
                )
                plugin.logger.info("Chat webhook client initialized")
            } else {
                plugin.logger.warning("Invalid chat webhook URL configured")
                plugin.logger.warning("Chat bridge falling back to bot messages")
            }
        }
    }

    /**
     * Check if webhook is available for use.
     */
    fun isWebhookAvailable(): Boolean = webhookSender != null

    /**
     * Send a chat message via webhook with custom username and avatar.
     *
     * @param content The message content
     * @param username Player name to display as sender
     * @param avatarUrl URL to player's head image
     */
    fun sendMessage(content: String, username: String, avatarUrl: String) {
        val sender = webhookSender
        if (sender == null) {
            plugin.logger.fine("Chat webhook not available, skipping message")
            return
        }
        sender.sendMessage(content, username, avatarUrl)
    }

    /**
     * Stop using the webhook. Requests already queued are finished by JDA.
     */
    fun shutdown() {
        webhookSender = null
    }
}
