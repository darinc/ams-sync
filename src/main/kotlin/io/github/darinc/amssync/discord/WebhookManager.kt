package io.github.darinc.amssync.discord

import io.github.darinc.amssync.AMSSyncPlugin
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.utils.FileUpload

/**
 * Manages Discord webhook sending with fallback to regular bot messages.
 *
 * When a valid webhook URL is configured, messages are sent via webhook which allows
 * custom usernames and avatars per message. When not configured, falls back
 * to standard bot channel messages. Both paths use the bot's JDA connection.
 *
 * Announcements carry player-controlled text (names, renamed items), so mentions are
 * disabled on every send: an item named "@everyone" must not ping the server.
 *
 * @property plugin The parent plugin instance
 * @property webhookUrl Optional webhook URL (null = use bot messages)
 * @property channelId Channel ID for fallback bot messages
 */
class WebhookManager(
    private val plugin: AMSSyncPlugin,
    private val webhookUrl: String?,
    private val channelId: String,
    private val discordManager: DiscordManager? = null
) {
    private var webhookSender: WebhookSender? = null

    private companion object {
        val NO_MENTIONS: Set<Message.MentionType> = emptySet()
    }

    init {
        if (!webhookUrl.isNullOrBlank()) {
            if (WebhookSender.isValidUrl(webhookUrl)) {
                webhookSender = WebhookSender(
                    webhookUrl,
                    { discordManager?.getJda() },
                    { plugin.resilience.circuitBreaker },
                    plugin.logger,
                    allowedMentions = NO_MENTIONS
                )
                plugin.logger.info("Webhook client initialized")
            } else {
                plugin.logger.warning("Invalid webhook URL configured; falling back to bot messages")
            }
        }
    }

    /**
     * Check if webhook is available for use.
     */
    fun isWebhookAvailable(): Boolean = webhookSender != null

    /**
     * Send an embed message, using webhook if available or bot fallback.
     *
     * @param embed The embed to send
     * @param username Custom username for webhook (ignored for bot messages)
     * @param avatarUrl Custom avatar URL for webhook (ignored for bot messages)
     */
    fun sendEmbed(
        embed: MessageEmbed,
        username: String? = null,
        avatarUrl: String? = null
    ) {
        val sender = webhookSender
        if (sender != null) {
            sender.sendEmbed(embed, username, avatarUrl)
        } else {
            sendViaBot(embed)
        }
    }

    /**
     * Send a plain text message, using webhook if available or bot fallback.
     *
     * @param content The message content
     * @param username Custom username for webhook (ignored for bot messages)
     * @param avatarUrl Custom avatar URL for webhook (ignored for bot messages)
     */
    fun sendMessage(
        content: String,
        username: String? = null,
        avatarUrl: String? = null
    ) {
        val sender = webhookSender
        if (sender != null) {
            sender.sendMessage(content, username, avatarUrl)
        } else {
            sendMessageViaBot(content)
        }
    }

    /**
     * Send an image file, using webhook if available or bot fallback.
     *
     * @param imageBytes The image data as bytes
     * @param filename The filename for the attachment
     * @param username Custom username for webhook (ignored for bot messages)
     * @param avatarUrl Custom avatar URL for webhook (ignored for bot messages)
     */
    fun sendImage(
        imageBytes: ByteArray,
        filename: String,
        username: String? = null,
        avatarUrl: String? = null
    ) {
        val sender = webhookSender
        if (sender != null) {
            sender.sendFile(imageBytes, filename, username, avatarUrl)
        } else {
            sendImageViaBot(imageBytes, filename)
        }
    }

    private fun sendViaBot(embed: MessageEmbed) {
        val channel = getChannel() ?: return

        try {
            val circuitBreaker = plugin.resilience.circuitBreaker

            val sendAction = {
                channel.sendMessageEmbeds(embed).setAllowedMentions(NO_MENTIONS).queue(
                    { plugin.logger.fine("Sent bot embed") },
                    { e -> plugin.logger.warning("Failed to send bot embed: ${e.message}") }
                )
            }

            if (circuitBreaker != null) {
                val result = circuitBreaker.execute("Send bot embed", sendAction)
                if (result is CircuitBreaker.CircuitResult.Rejected) {
                    plugin.logger.fine("Bot embed rejected by circuit breaker")
                }
            } else {
                sendAction()
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error sending bot embed: ${e.message}")
        }
    }

    private fun sendMessageViaBot(content: String) {
        val channel = getChannel() ?: return

        try {
            val circuitBreaker = plugin.resilience.circuitBreaker

            val sendAction = {
                channel.sendMessage(content).setAllowedMentions(NO_MENTIONS).queue(
                    { plugin.logger.fine("Sent bot message") },
                    { e -> plugin.logger.warning("Failed to send bot message: ${e.message}") }
                )
            }

            if (circuitBreaker != null) {
                val result = circuitBreaker.execute("Send bot message", sendAction)
                if (result is CircuitBreaker.CircuitResult.Rejected) {
                    plugin.logger.fine("Bot message rejected by circuit breaker")
                }
            } else {
                sendAction()
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error sending bot message: ${e.message}")
        }
    }

    private fun sendImageViaBot(imageBytes: ByteArray, filename: String) {
        val channel = getChannel() ?: return

        try {
            val circuitBreaker = plugin.resilience.circuitBreaker

            val sendAction = {
                val fileUpload = FileUpload.fromData(imageBytes, filename)
                channel.sendFiles(fileUpload).setAllowedMentions(NO_MENTIONS).queue(
                    { plugin.logger.fine("Sent bot image: $filename") },
                    { e -> plugin.logger.warning("Failed to send bot image: ${e.message}") }
                )
            }

            if (circuitBreaker != null) {
                val result = circuitBreaker.execute("Send bot image", sendAction)
                if (result is CircuitBreaker.CircuitResult.Rejected) {
                    plugin.logger.fine("Bot image rejected by circuit breaker")
                }
            } else {
                sendAction()
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error sending bot image: ${e.message}")
        }
    }

    private fun getChannel(): TextChannel? {
        val manager = discordManager ?: return null
        val jda = manager.getJda()
        if (jda == null || !manager.isConnected()) {
            plugin.logger.fine("Skipping message - Discord not connected")
            return null
        }

        val channel = jda.getTextChannelById(channelId)
        if (channel == null) {
            plugin.logger.warning("Event announcement channel not found: $channelId")
            return null
        }

        return channel
    }

    /**
     * Stop using the webhook. Requests already queued are finished by JDA.
     */
    fun shutdown() {
        webhookSender = null
    }
}
