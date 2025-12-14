package io.github.darinc.amssync.discord

import club.minnced.discord.webhook.WebhookClient
import club.minnced.discord.webhook.WebhookClientBuilder
import club.minnced.discord.webhook.send.WebhookEmbed
import club.minnced.discord.webhook.send.WebhookEmbedBuilder
import club.minnced.discord.webhook.send.WebhookMessageBuilder
import io.github.darinc.amssync.AMSSyncPlugin
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import java.time.Instant

/**
 * Manages Discord webhook sending with fallback to regular bot messages.
 *
 * When a webhook URL is configured, messages are sent via webhook which allows
 * custom usernames and avatars per message. When not configured, falls back
 * to standard bot channel messages.
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
    private var webhookClient: WebhookClient? = null

    init {
        if (!webhookUrl.isNullOrBlank()) {
            try {
                webhookClient = WebhookClientBuilder(webhookUrl)
                    .setThreadFactory { r ->
                        val thread = Thread(r, "AMSSync-Webhook")
                        thread.isDaemon = true
                        thread
                    }
                    .setWait(false)
                    .build()
                plugin.logger.info("Webhook client initialized")
            } catch (e: Exception) {
                plugin.logger.warning("Failed to initialize webhook client: ${e.message}")
                plugin.logger.warning("Falling back to bot messages")
            }
        }
    }

    /**
     * Check if webhook is available for use.
     */
    fun isWebhookAvailable(): Boolean = webhookClient != null

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
        val client = webhookClient
        if (client != null) {
            sendViaWebhook(embed, username, avatarUrl, client)
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
        val client = webhookClient
        if (client != null) {
            sendMessageViaWebhook(content, username, avatarUrl, client)
        } else {
            sendMessageViaBot(content)
        }
    }

    private fun sendViaWebhook(
        embed: MessageEmbed,
        username: String?,
        avatarUrl: String?,
        client: WebhookClient
    ) {
        try {
            val circuitBreaker = plugin.resilience.circuitBreaker

            val sendAction = {
                val webhookEmbed = convertToWebhookEmbed(embed)
                val message = WebhookMessageBuilder()
                    .apply {
                        username?.let { setUsername(it) }
                        avatarUrl?.let { setAvatarUrl(it) }
                    }
                    .addEmbeds(webhookEmbed)
                    .build()

                client.send(message)
                    .thenAccept { plugin.logger.fine("Sent webhook embed") }
                    .exceptionally { e ->
                        plugin.logger.warning("Failed to send webhook embed: ${e.message}")
                        null
                    }
                Unit
            }

            if (circuitBreaker != null) {
                val result = circuitBreaker.execute("Send webhook embed", sendAction)
                if (result is CircuitBreaker.CircuitResult.Rejected) {
                    plugin.logger.fine("Webhook embed rejected by circuit breaker")
                }
            } else {
                sendAction()
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error sending webhook embed: ${e.message}")
        }
    }

    private fun sendMessageViaWebhook(
        content: String,
        username: String?,
        avatarUrl: String?,
        client: WebhookClient
    ) {
        try {
            val circuitBreaker = plugin.resilience.circuitBreaker

            val sendAction = {
                val message = WebhookMessageBuilder()
                    .setContent(content)
                    .apply {
                        username?.let { setUsername(it) }
                        avatarUrl?.let { setAvatarUrl(it) }
                    }
                    .build()

                client.send(message)
                    .thenAccept { plugin.logger.fine("Sent webhook message") }
                    .exceptionally { e ->
                        plugin.logger.warning("Failed to send webhook message: ${e.message}")
                        null
                    }
                Unit
            }

            if (circuitBreaker != null) {
                val result = circuitBreaker.execute("Send webhook message", sendAction)
                if (result is CircuitBreaker.CircuitResult.Rejected) {
                    plugin.logger.fine("Webhook message rejected by circuit breaker")
                }
            } else {
                sendAction()
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error sending webhook message: ${e.message}")
        }
    }

    private fun sendViaBot(embed: MessageEmbed) {
        val channel = getChannel() ?: return

        try {
            val circuitBreaker = plugin.resilience.circuitBreaker

            val sendAction = {
                channel.sendMessageEmbeds(embed).queue(
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
                channel.sendMessage(content).queue(
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
     * Convert JDA MessageEmbed to webhook library WebhookEmbed.
     */
    private fun convertToWebhookEmbed(embed: MessageEmbed): WebhookEmbed {
        val builder = WebhookEmbedBuilder()

        embed.title?.let { builder.setTitle(WebhookEmbed.EmbedTitle(it, embed.url)) }
        embed.description?.let { builder.setDescription(it) }
        embed.colorRaw.let { builder.setColor(it) }
        embed.timestamp?.let { builder.setTimestamp(it) }
        embed.thumbnail?.url?.let { builder.setThumbnailUrl(it) }
        embed.image?.url?.let { builder.setImageUrl(it) }
        embed.footer?.let { footer ->
            builder.setFooter(WebhookEmbed.EmbedFooter(footer.text ?: "", footer.iconUrl))
        }
        embed.author?.let { author ->
            builder.setAuthor(WebhookEmbed.EmbedAuthor(author.name ?: "", author.iconUrl, author.url))
        }

        embed.fields.forEach { field ->
            builder.addField(WebhookEmbed.EmbedField(field.isInline, field.name ?: "", field.value ?: ""))
        }

        return builder.build()
    }

    /**
     * Shutdown the webhook client.
     */
    fun shutdown() {
        webhookClient?.close()
        webhookClient = null
    }
}
