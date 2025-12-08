package io.github.darinc.amssync.discord

import io.github.darinc.amssync.metrics.ErrorMetrics
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
 * - Enhanced error logging with circuit breaker state context
 * - Thread-safe operation
 *
 * @property circuitBreaker Circuit breaker instance for failure protection
 * @property logger Logger for errors and metrics
 */
class DiscordApiWrapper(
    private val circuitBreaker: CircuitBreaker?,
    private val logger: Logger,
    private val errorMetrics: ErrorMetrics? = null
) {
    /**
     * Formats circuit breaker state for inclusion in log messages.
     * Provides context about the current circuit state when logging errors.
     *
     * @return String describing current circuit breaker state, or empty if disabled
     */
    private fun getCircuitStateContext(): String {
        val cb = circuitBreaker ?: return ""
        val metrics = cb.getMetrics()

        return when (metrics.state) {
            CircuitBreaker.State.CLOSED -> {
                if (metrics.failureCount > 0) {
                    " [Circuit: CLOSED, failures: ${metrics.failureCount}/${metrics.failureThreshold}]"
                } else {
                    " [Circuit: CLOSED]"
                }
            }
            CircuitBreaker.State.OPEN -> {
                val remainingCooldown = maxOf(0, 30000 - metrics.timeSinceStateChangeMs) / 1000
                " [Circuit: OPEN, cooldown remaining: ~${remainingCooldown}s]"
            }
            CircuitBreaker.State.HALF_OPEN -> {
                " [Circuit: HALF_OPEN, recovery progress: ${metrics.successCount}/${metrics.successThreshold}]"
            }
        }
    }

    /**
     * Logs an error with circuit breaker state context.
     *
     * @param operation Name of the operation that failed
     * @param error Error message or exception message
     * @param level Log level (defaults to WARNING)
     */
    private fun logWithCircuitState(operation: String, error: String, severe: Boolean = false) {
        val message = "$operation: $error${getCircuitStateContext()}"
        if (severe) {
            logger.severe(message)
        } else {
            logger.warning(message)
        }
    }

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
                    logWithCircuitState("Discord API error (sendMessage)", error.message ?: "Unknown error")
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
                    logWithCircuitState("Discord API error (sendMessageEmbed)", error.message ?: "Unknown error")
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
            logWithCircuitState("Failed to send Discord message, attempting fallback", error.message ?: "Unknown error")

            // Attempt fallback message
            try {
                hook.sendMessage(
                    "⚠️ **Service Temporarily Unavailable**\n\n" +
                    "The Discord bot is experiencing technical difficulties.\n" +
                    "Please try again in a few moments."
                ).setEphemeral(true).queue(
                    { logger.info("Sent fallback error message") },
                    { fallbackError -> logWithCircuitState("Failed to send fallback message", fallbackError.message ?: "Unknown error", severe = true) }
                )
            } catch (e: Exception) {
                logWithCircuitState("Critical error: Unable to send any Discord message", e.message ?: "Unknown error", severe = true)
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
            logWithCircuitState("Failed to send Discord embed, attempting fallback", error.message ?: "Unknown error")

            // Attempt fallback message
            try {
                hook.sendMessage(
                    "⚠️ **Service Temporarily Unavailable**\n\n" +
                    "The Discord bot is experiencing technical difficulties.\n" +
                    "Please try again in a few moments."
                ).setEphemeral(true).queue(
                    { logger.info("Sent fallback error message") },
                    { fallbackError -> logWithCircuitState("Failed to send fallback message", fallbackError.message ?: "Unknown error", severe = true) }
                )
            } catch (e: Exception) {
                logWithCircuitState("Critical error: Unable to send any Discord message", e.message ?: "Unknown error", severe = true)
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
                errorMetrics?.recordDiscordApiSuccess()
                CompletableFuture.completedFuture(circuitResult.value)
            }
            is CircuitBreaker.CircuitResult.Failure -> {
                errorMetrics?.recordDiscordApiFailure("api_error")
                logWithCircuitState("$operationName failed", circuitResult.exception.message ?: "Unknown error")
                CompletableFuture.failedFuture(circuitResult.exception)
            }
            is CircuitBreaker.CircuitResult.Rejected -> {
                errorMetrics?.recordDiscordApiRejected()
                logWithCircuitState("$operationName rejected by circuit breaker", circuitResult.message)
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
