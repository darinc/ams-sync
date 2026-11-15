package io.github.darinc.amssync.discord

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.IncomingWebhookClient
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.Webhook
import net.dv8tion.jda.api.entities.WebhookClient
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction
import net.dv8tion.jda.api.utils.FileUpload
import java.util.logging.Logger

/**
 * Sends messages to a Discord webhook URL using JDA's built-in webhook client.
 *
 * Requests go through the bot's JDA instance, so they share JDA's rate limiting and
 * are only possible while the bot is connected. The client is created per send from
 * the current JDA instance, which keeps it valid across reconnects and reloads.
 *
 * @property webhookUrl Discord webhook URL (must match [Webhook.WEBHOOK_URL])
 * @property jdaProvider Supplies the current JDA instance, or null when not connected
 * @property circuitBreakerProvider Supplies the circuit breaker gating sends, if any
 * @property logger Logger for send results
 * @property clientFactory Creates the webhook client (overridable for tests)
 */
class WebhookSender(
    private val webhookUrl: String,
    private val jdaProvider: () -> JDA?,
    private val circuitBreakerProvider: () -> CircuitBreaker?,
    private val logger: Logger,
    private val clientFactory: (JDA, String) -> IncomingWebhookClient = { jda, url -> WebhookClient.createClient(jda, url) }
) {
    init {
        require(isValidUrl(webhookUrl)) { "Not a Discord webhook URL" }
    }

    fun sendMessage(content: String, username: String? = null, avatarUrl: String? = null) =
        send("webhook message", username, avatarUrl) { it.sendMessage(content) }

    fun sendEmbed(embed: MessageEmbed, username: String? = null, avatarUrl: String? = null) =
        send("webhook embed", username, avatarUrl) { it.sendMessageEmbeds(embed) }

    fun sendFile(bytes: ByteArray, filename: String, username: String? = null, avatarUrl: String? = null) =
        send("webhook file $filename", username, avatarUrl) { it.sendFiles(FileUpload.fromData(bytes, filename)) }

    /**
     * Queue a webhook request.
     *
     * @return true if the request was queued, false if skipped (not connected,
     *   rejected by the circuit breaker, or failed to build)
     */
    private fun send(
        label: String,
        username: String?,
        avatarUrl: String?,
        create: (IncomingWebhookClient) -> WebhookMessageCreateAction<Message>
    ): Boolean {
        val jda = jdaProvider()
        if (jda == null || jda.status != JDA.Status.CONNECTED) {
            logger.fine("Skipping $label - Discord not connected")
            return false
        }

        val sendAction = {
            val action = create(clientFactory(jda, webhookUrl))
            username?.let { action.setUsername(it) }
            avatarUrl?.let { action.setAvatarUrl(it) }
            action.queue(
                { logger.fine("Sent $label") },
                { e -> logger.warning("Failed to send $label: ${e.message}") }
            )
        }

        return try {
            val circuitBreaker = circuitBreakerProvider()
            if (circuitBreaker == null) {
                sendAction()
                true
            } else {
                when (val result = circuitBreaker.execute("Send $label", sendAction)) {
                    is CircuitBreaker.CircuitResult.Success -> true
                    is CircuitBreaker.CircuitResult.Rejected -> {
                        logger.fine("$label rejected by circuit breaker")
                        false
                    }
                    is CircuitBreaker.CircuitResult.Failure -> {
                        logger.warning("Error sending $label: ${result.exception.message}")
                        false
                    }
                }
            }
        } catch (e: Exception) {
            logger.warning("Error sending $label: ${e.message}")
            false
        }
    }

    companion object {
        fun isValidUrl(url: String?): Boolean =
            !url.isNullOrBlank() && Webhook.WEBHOOK_URL.matcher(url).matches()
    }
}
