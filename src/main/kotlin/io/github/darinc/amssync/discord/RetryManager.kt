package io.github.darinc.amssync.discord

import java.util.logging.Logger
import kotlin.math.min
import kotlin.math.pow

/**
 * Manages retry logic with exponential backoff for operations that may fail transiently.
 *
 * This class implements a configurable retry mechanism that increases the delay between
 * retry attempts exponentially, up to a maximum delay. This prevents overwhelming external
 * services while allowing for automatic recovery from temporary failures.
 *
 * @property maxAttempts Maximum number of retry attempts (including initial attempt)
 * @property initialDelayMs Initial delay in milliseconds before first retry
 * @property maxDelayMs Maximum delay in milliseconds between retries
 * @property backoffMultiplier Multiplier for exponential backoff (typically 2.0)
 * @property logger Logger instance for retry attempt logging
 */
class RetryManager(
    private val maxAttempts: Int = 5,
    private val initialDelayMs: Long = 5000L,
    private val maxDelayMs: Long = 300000L,
    private val backoffMultiplier: Double = 2.0,
    private val logger: Logger
) {
    /**
     * Result of a retry operation.
     */
    sealed class RetryResult<out T> {
        data class Success<T>(val value: T) : RetryResult<T>()
        data class Failure(val lastException: Exception, val attempts: Int) : RetryResult<Nothing>()
    }

    /**
     * Executes an operation with retry logic and exponential backoff.
     *
     * @param operationName Human-readable name for logging purposes
     * @param operation The operation to execute, should throw an exception on failure
     * @return RetryResult.Success with the operation result, or RetryResult.Failure with details
     */
    fun <T> executeWithRetry(operationName: String, operation: () -> T): RetryResult<T> {
        var attempt = 0
        var lastException: Exception? = null

        while (attempt < maxAttempts) {
            attempt++

            try {
                logger.info("$operationName - Attempt $attempt/$maxAttempts")
                val result = operation()

                if (attempt > 1) {
                    logger.info("$operationName succeeded after $attempt attempts")
                } else {
                    logger.info("$operationName succeeded on first attempt")
                }

                return RetryResult.Success(result)

            } catch (e: Exception) {
                lastException = e

                if (attempt >= maxAttempts) {
                    logger.severe("$operationName failed after $maxAttempts attempts. Last error: ${e.message}")
                    break
                }

                val delay = calculateDelay(attempt)
                logger.warning(
                    "$operationName failed (attempt $attempt/$maxAttempts): ${e.message}. " +
                    "Retrying in ${delay}ms..."
                )

                // Sleep with interrupt handling
                try {
                    Thread.sleep(delay)
                } catch (ie: InterruptedException) {
                    logger.warning("Retry sleep interrupted, aborting retry attempts")
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }

        return RetryResult.Failure(
            lastException ?: Exception("Unknown error"),
            attempt
        )
    }

    /**
     * Calculates the delay before the next retry attempt using exponential backoff.
     *
     * @param attemptNumber The current attempt number (1-indexed)
     * @return Delay in milliseconds, capped at maxDelayMs
     */
    private fun calculateDelay(attemptNumber: Int): Long {
        // Exponential backoff: initialDelay * (multiplier ^ (attempt - 1))
        val exponentialDelay = initialDelayMs * backoffMultiplier.pow(attemptNumber - 1).toLong()

        // Cap at maximum delay
        return min(exponentialDelay, maxDelayMs)
    }

    /**
     * Configuration data class for creating a RetryManager from config values.
     */
    data class RetryConfig(
        val enabled: Boolean = true,
        val maxAttempts: Int = 5,
        val initialDelaySeconds: Int = 5,
        val maxDelaySeconds: Int = 300,
        val backoffMultiplier: Double = 2.0
    ) {
        fun toRetryManager(logger: Logger): RetryManager {
            return RetryManager(
                maxAttempts = maxAttempts,
                initialDelayMs = initialDelaySeconds * 1000L,
                maxDelayMs = maxDelaySeconds * 1000L,
                backoffMultiplier = backoffMultiplier,
                logger = logger
            )
        }
    }
}
