package io.github.darinc.amssync.discord

import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import java.util.concurrent.*
import java.util.logging.Logger

/**
 * Manages operation timeouts with warning callbacks.
 *
 * Provides utilities to execute operations with configurable timeouts,
 * preventing hanging operations from blocking the plugin.
 *
 * Features:
 * - Warning callback at configurable threshold (default 3s)
 * - Hard timeout with automatic cancellation (default 10s)
 * - Support for both generic operations and Bukkit-scheduled operations
 * - Thread-safe execution with proper cleanup
 *
 * @property warningThresholdMs Milliseconds before warning callback fires (default 3000ms)
 * @property hardTimeoutMs Milliseconds before operation is forcefully cancelled (default 10000ms)
 * @property logger Logger for timeout warnings and errors
 */
class TimeoutManager(
    private val warningThresholdMs: Long = 3000L,
    private val hardTimeoutMs: Long = 10000L,
    private val logger: Logger
) {
    /**
     * Sealed class representing the result of a timeout-protected operation.
     */
    sealed class TimeoutResult<out T> {
        /**
         * Operation completed successfully within the timeout.
         */
        data class Success<T>(val value: T) : TimeoutResult<T>()

        /**
         * Operation exceeded the hard timeout and was cancelled.
         */
        data class Timeout(
            val operationName: String,
            val timeoutMs: Long
        ) : TimeoutResult<Nothing>()

        /**
         * Operation failed with an exception.
         */
        data class Failure(
            val exception: Exception,
            val operationName: String
        ) : TimeoutResult<Nothing>()
    }

    /**
     * Callback invoked when operation exceeds warning threshold.
     */
    fun interface WarningCallback {
        fun onWarning(operationName: String, elapsedMs: Long)
    }

    // Executor for timeout scheduling
    private val timeoutExecutor: ScheduledExecutorService = Executors.newScheduledThreadPool(
        2,
        ThreadFactory { runnable ->
            Thread(runnable, "AMSSync-Timeout").apply {
                isDaemon = true
            }
        }
    )

    /**
     * Execute an operation with timeout protection.
     *
     * The operation is executed on a separate thread with configurable timeout.
     * If the operation takes longer than warningThresholdMs, a warning is logged.
     * If it takes longer than hardTimeoutMs, it is cancelled and a TimeoutResult.Timeout is returned.
     *
     * @param operationName Descriptive name for logging
     * @param warningCallback Optional callback when warning threshold is exceeded
     * @param operation The operation to execute
     * @return TimeoutResult indicating success, timeout, or failure
     */
    fun <T> executeWithTimeout(
        operationName: String,
        warningCallback: WarningCallback? = null,
        operation: () -> T
    ): TimeoutResult<T> {
        val startTime = System.currentTimeMillis()
        val future = CompletableFuture.supplyAsync(operation, timeoutExecutor)

        // Schedule warning callback
        val warningFuture = timeoutExecutor.schedule({
            if (!future.isDone) {
                val elapsed = System.currentTimeMillis() - startTime
                logger.warning("⚠️ Operation '$operationName' is taking longer than expected (${elapsed}ms)")
                warningCallback?.onWarning(operationName, elapsed)
            }
        }, warningThresholdMs, TimeUnit.MILLISECONDS)

        return try {
            // Wait for completion with hard timeout
            val result = future.get(hardTimeoutMs, TimeUnit.MILLISECONDS)
            warningFuture.cancel(false) // Cancel warning if completed successfully
            TimeoutResult.Success(result)

        } catch (e: TimeoutException) {
            // Operation exceeded hard timeout
            future.cancel(true) // Attempt to cancel the operation
            warningFuture.cancel(false)
            val elapsed = System.currentTimeMillis() - startTime
            logger.severe("❌ Operation '$operationName' timed out after ${elapsed}ms (limit: ${hardTimeoutMs}ms)")
            TimeoutResult.Timeout(operationName, elapsed)

        } catch (e: ExecutionException) {
            // Operation failed with exception
            warningFuture.cancel(false)
            val cause = e.cause as? Exception ?: Exception("Unknown error", e)
            logger.warning("❌ Operation '$operationName' failed: ${cause.message}")
            TimeoutResult.Failure(cause, operationName)

        } catch (e: Exception) {
            // Unexpected error (InterruptedException, etc.)
            warningFuture.cancel(false)
            future.cancel(true)
            logger.warning("❌ Unexpected error in operation '$operationName': ${e.message}")
            TimeoutResult.Failure(e, operationName)
        }
    }

    /**
     * Execute an operation on the Bukkit main thread with timeout protection.
     *
     * This method schedules the operation on Bukkit's main thread (required for most
     * Bukkit API calls) while monitoring it from a separate timeout thread.
     *
     * The operation is submitted to Bukkit's scheduler and monitored for timeout.
     * If the operation takes longer than the configured timeout, it cannot be forcefully
     * cancelled (Bukkit limitation), but a timeout result is returned and logged.
     *
     * @param plugin The plugin instance for Bukkit scheduler access
     * @param operationName Descriptive name for logging
     * @param warningCallback Optional callback when warning threshold is exceeded
     * @param operation The operation to execute on Bukkit's main thread
     * @return TimeoutResult indicating success, timeout, or failure
     */
    fun <T> executeOnBukkitWithTimeout(
        plugin: Plugin,
        operationName: String,
        warningCallback: WarningCallback? = null,
        operation: () -> T
    ): TimeoutResult<T> {
        val startTime = System.currentTimeMillis()
        val result = CompletableFuture<T>()

        // Schedule the operation on Bukkit's main thread
        Bukkit.getScheduler().runTask(plugin, Runnable {
            try {
                val value = operation()
                result.complete(value)
            } catch (e: Exception) {
                result.completeExceptionally(e)
            }
        })

        // Schedule warning callback
        val warningFuture = timeoutExecutor.schedule({
            if (!result.isDone) {
                val elapsed = System.currentTimeMillis() - startTime
                logger.warning("⚠️ Bukkit operation '$operationName' is taking longer than expected (${elapsed}ms)")
                warningCallback?.onWarning(operationName, elapsed)
            }
        }, warningThresholdMs, TimeUnit.MILLISECONDS)

        return try {
            // Wait for completion with hard timeout
            val value = result.get(hardTimeoutMs, TimeUnit.MILLISECONDS)
            warningFuture.cancel(false)
            TimeoutResult.Success(value)

        } catch (e: TimeoutException) {
            // Operation exceeded hard timeout
            // Note: We cannot cancel Bukkit tasks once started, but we can return early
            warningFuture.cancel(false)
            val elapsed = System.currentTimeMillis() - startTime
            logger.severe(
                "❌ Bukkit operation '$operationName' timed out after ${elapsed}ms (limit: ${hardTimeoutMs}ms)\n" +
                "⚠️ WARNING: Bukkit task cannot be forcefully cancelled and may still be running!"
            )
            TimeoutResult.Timeout(operationName, elapsed)

        } catch (e: ExecutionException) {
            // Operation failed with exception
            warningFuture.cancel(false)
            val cause = e.cause as? Exception ?: Exception("Unknown error", e)
            logger.warning("❌ Bukkit operation '$operationName' failed: ${cause.message}")
            TimeoutResult.Failure(cause, operationName)

        } catch (e: Exception) {
            // Unexpected error
            warningFuture.cancel(false)
            logger.warning("❌ Unexpected error in Bukkit operation '$operationName': ${e.message}")
            TimeoutResult.Failure(e, operationName)
        }
    }

    /**
     * Shutdown the timeout executor.
     *
     * Should be called when the plugin is disabled to properly cleanup resources.
     */
    fun shutdown() {
        logger.info("Shutting down TimeoutManager...")
        timeoutExecutor.shutdown()
        try {
            if (!timeoutExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warning("TimeoutManager did not terminate gracefully, forcing shutdown...")
                timeoutExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            logger.warning("TimeoutManager shutdown interrupted")
            timeoutExecutor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
}

/**
 * Configuration for TimeoutManager loaded from config.yml
 */
data class TimeoutConfig(
    val enabled: Boolean,
    val warningThresholdSeconds: Int,
    val hardTimeoutSeconds: Int
) {
    /**
     * Create a TimeoutManager from this configuration
     */
    fun toTimeoutManager(logger: Logger): TimeoutManager {
        return TimeoutManager(
            warningThresholdMs = warningThresholdSeconds * 1000L,
            hardTimeoutMs = hardTimeoutSeconds * 1000L,
            logger = logger
        )
    }

    companion object {
        /**
         * Load timeout configuration from config file
         */
        fun fromConfig(config: org.bukkit.configuration.ConfigurationSection): TimeoutConfig {
            return TimeoutConfig(
                enabled = config.getBoolean("discord.timeout.enabled", true),
                warningThresholdSeconds = config.getInt("discord.timeout.warning-threshold-seconds", 3),
                hardTimeoutSeconds = config.getInt("discord.timeout.hard-timeout-seconds", 10)
            )
        }
    }
}
