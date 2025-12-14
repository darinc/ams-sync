package io.github.darinc.amssync.discord

import club.minnced.discord.webhook.WebhookClient
import club.minnced.discord.webhook.WebhookClientBuilder
import club.minnced.discord.webhook.send.WebhookMessageBuilder
import io.github.darinc.amssync.AMSSyncPlugin

/**
 * Manages Discord webhook sending for chat bridge messages.
 *
 * Sends messages via webhook with custom username (player name) and avatar (player head).
 * Falls back to standard bot messages if webhook is not configured or fails.
 *
 * @property plugin The parent plugin instance
 * @property webhookUrl Webhook URL (null = use bot messages)
 */
class ChatWebhookManager(
    private val plugin: AMSSyncPlugin,
    private val webhookUrl: String?
) {
    private var webhookClient: WebhookClient? = null

    init {
        if (!webhookUrl.isNullOrBlank()) {
            try {
                webhookClient = WebhookClientBuilder(webhookUrl)
                    .setThreadFactory { r ->
                        val thread = Thread(r, "AMSSync-ChatWebhook")
                        thread.isDaemon = true
                        thread
                    }
                    .setWait(false)
                    .build()
                plugin.logger.info("Chat webhook client initialized")
            } catch (e: Exception) {
                plugin.logger.warning("Failed to initialize chat webhook client: ${e.message}")
                plugin.logger.warning("Chat bridge falling back to bot messages")
            }
        }
    }

    /**
     * Check if webhook is available for use.
     */
    fun isWebhookAvailable(): Boolean = webhookClient != null

    /**
     * Send a chat message via webhook with custom username and avatar.
     *
     * @param content The message content
     * @param username Player name to display as sender
     * @param avatarUrl URL to player's head image
     */
    fun sendMessage(content: String, username: String, avatarUrl: String) {
        val client = webhookClient
        if (client == null) {
            plugin.logger.fine("Chat webhook not available, skipping message")
            return
        }

        try {
            val circuitBreaker = plugin.resilience.circuitBreaker

            val sendAction = {
                val message = WebhookMessageBuilder()
                    .setContent(content)
                    .setUsername(username)
                    .setAvatarUrl(avatarUrl)
                    .build()

                client.send(message)
                    .thenAccept { plugin.logger.fine("Sent chat webhook message from $username") }
                    .exceptionally { e ->
                        plugin.logger.warning("Failed to send chat webhook message: ${e.message}")
                        null
                    }
                Unit
            }

            if (circuitBreaker != null) {
                val result = circuitBreaker.execute("Send chat webhook", sendAction)
                if (result is CircuitBreaker.CircuitResult.Rejected) {
                    plugin.logger.fine("Chat webhook rejected by circuit breaker")
                }
            } else {
                sendAction()
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error sending chat webhook: ${e.message}")
        }
    }

    /**
     * Shutdown the webhook client.
     */
    fun shutdown() {
        webhookClient?.close()
        webhookClient = null
    }
}
