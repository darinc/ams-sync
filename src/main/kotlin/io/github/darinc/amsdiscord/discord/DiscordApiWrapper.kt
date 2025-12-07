package io.github.darinc.amsdiscord.discord

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction
import java.util.concurrent.CompletableFuture
import java.util.logging.Logger

/**
 * Wrapper for Discord API calls with circuit breaker protection.
 *
 * Wraps common JDA queue operations to prevent cascading failures.
 * When the circuit breaker is open, operations fail fast instead of
 * queuing potentially failing requests.
 *
 * Features:
 * - Circuit breaker protection for all Discord API calls
 * - Automatic fallback error messages when circuit is open
 * - Metrics tracking for Discord API failures
 * - Thread-safe operation
 *
 * @property circuitBreaker Circuit breaker instance for failure protection
 * @property logger Logger for errors and metrics
 */
class DiscordApiWrapper(
    private val circuitBreaker: CircuitBreaker?,
    private val logger: Logger
) {
    /**
     * Send a message using InteractionHook with circuit breaker protection.
     *
     * @param hook The interaction hook
     * @param message The message content
     * @param ephemeral Whether the message should be ephemeral
     * @return CompletableFuture<Message> that completes when message is sent
     */
    fun sendMessage(
        hook: InteractionHook,
        message: String,
        ephemeral: Boolean = false
    ): CompletableFuture<Message> {
        return executeWithCircuitBreaker("Discord sendMessage") {
            val future = CompletableFuture<Message>()

            val action = hook.sendMessage(message).setEphemeral(ephemeral)
            action.queue(
                { msg -> future.complete(msg) },
                { error ->
                    logger.warning("Discord API error (sendMessage): ${error.message}")
                    future.completeExceptionally(error)
                }
            )

            future
        }
    }

    /**
     * Send an embed using InteractionHook with circuit breaker protection.
     *
     * @param hook The interaction hook
     * @param embed The message embed
     * @param ephemeral Whether the message should be ephemeral
     * @return CompletableFuture<Message> that completes when message is sent
     */
    fun sendMessageEmbed(
        hook: InteractionHook,
        embed: MessageEmbed,
        ephemeral: Boolean = false
    ): CompletableFuture<Message> {
        return executeWithCircuitBreaker("Discord sendMessageEmbed") {
            val future = CompletableFuture<Message>()

            val action = hook.sendMessageEmbeds(embed).setEphemeral(ephemeral)
            action.queue(
                { msg -> future.complete(msg) },
                { error ->
                    logger.warning("Discord API error (sendMessageEmbed): ${error.message}")
                    future.completeExceptionally(error)
                }
            )

            future
        }
    }

    /**
     * Send a message with fallback error handling.
     *
     * If the circuit breaker is open, sends a generic error message instead.
     * If the operation fails, sends a fallback error message.
     *
     * @param hook The interaction hook
     * @param message The message content
     * @param ephemeral Whether the message should be ephemeral
     */
    fun sendMessageSafe(
        hook: InteractionHook,
        message: String,
        ephemeral: Boolean = false
    ) {
        val result = sendMessage(hook, message, ephemeral)
        result.exceptionally { error ->
            logger.warning("Failed to send Discord message, attempting fallback: ${error.message}")

            // Attempt fallback message
            try {
                hook.sendMessage(
                    "⚠️ **Service Temporarily Unavailable**\n\n" +
                    "The Discord bot is experiencing technical difficulties.\n" +
                    "Please try again in a few moments."
                ).setEphemeral(true).queue(
                    { logger.info("Sent fallback error message") },
                    { fallbackError -> logger.severe("Failed to send fallback message: ${fallbackError.message}") }
                )
            } catch (e: Exception) {
                logger.severe("Critical error: Unable to send any Discord message: ${e.message}")
            }

            null
        }
    }

    /**
     * Send an embed with fallback error handling.
     *
     * If the circuit breaker is open, sends a generic error message instead.
     * If the operation fails, sends a fallback error message.
     *
     * @param hook The interaction hook
     * @param embed The message embed
     * @param ephemeral Whether the message should be ephemeral
     */
    fun sendMessageEmbedSafe(
        hook: InteractionHook,
        embed: MessageEmbed,
        ephemeral: Boolean = false
    ) {
        val result = sendMessageEmbed(hook, embed, ephemeral)
        result.exceptionally { error ->
            logger.warning("Failed to send Discord embed, attempting fallback: ${error.message}")

            // Attempt fallback message
            try {
                hook.sendMessage(
                    "⚠️ **Service Temporarily Unavailable**\n\n" +
                    "The Discord bot is experiencing technical difficulties.\n" +
                    "Please try again in a few moments."
                ).setEphemeral(true).queue(
                    { logger.info("Sent fallback error message") },
                    { fallbackError -> logger.severe("Failed to send fallback message: ${fallbackError.message}") }
                )
            } catch (e: Exception) {
                logger.severe("Critical error: Unable to send any Discord message: ${e.message}")
            }

            null
        }
    }

    /**
     * Execute an operation with circuit breaker protection.
     *
     * @param operationName Descriptive name for logging
     * @param operation The operation that returns a CompletableFuture
     * @return CompletableFuture<T> that completes with the operation result or failure
     */
    private fun <T> executeWithCircuitBreaker(
        operationName: String,
        operation: () -> CompletableFuture<T>
    ): CompletableFuture<T> {
        // If no circuit breaker, execute directly
        if (circuitBreaker == null) {
            return try {
                operation()
            } catch (e: Exception) {
                CompletableFuture.failedFuture(e)
            }
        }

        // Check circuit breaker state
        val circuitResult = circuitBreaker.execute(operationName) {
            operation().get() // Blocks until Discord API call completes
        }

        return when (circuitResult) {
            is CircuitBreaker.CircuitResult.Success -> {
                CompletableFuture.completedFuture(circuitResult.value)
            }
            is CircuitBreaker.CircuitResult.Failure -> {
                logger.warning("$operationName failed: ${circuitResult.exception.message}")
                CompletableFuture.failedFuture(circuitResult.exception)
            }
            is CircuitBreaker.CircuitResult.Rejected -> {
                logger.warning("$operationName rejected by circuit breaker: ${circuitResult.message}")
                CompletableFuture.failedFuture(
                    Exception("Circuit breaker rejected request: ${circuitResult.message}")
                )
            }
        }
    }

    /**
     * Get circuit breaker metrics
     */
    fun getMetrics(): CircuitBreakerMetrics? {
        return circuitBreaker?.getMetrics()
    }

    /**
     * Get current circuit breaker state
     */
    fun getCircuitState(): CircuitBreaker.State? {
        return circuitBreaker?.getState()
    }

    /**
     * Check if circuit breaker is enabled
     */
    fun isCircuitBreakerEnabled(): Boolean {
        return circuitBreaker != null
    }
}
