package io.github.darinc.amsdiscord.discord

import io.github.darinc.amsdiscord.exceptions.CircuitBreakerOpenException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.logging.Logger

/**
 * Circuit breaker implementation to prevent cascading failures.
 *
 * The circuit breaker pattern protects against repeated failures by:
 * 1. **CLOSED** - Normal operation, requests pass through
 * 2. **OPEN** - Too many failures, all requests fail fast without executing
 * 3. **HALF_OPEN** - Testing recovery, limited requests allowed through
 *
 * State transitions:
 * - CLOSED → OPEN: After failureThreshold failures within timeWindowMs
 * - OPEN → HALF_OPEN: After cooldownMs duration
 * - HALF_OPEN → CLOSED: After successThreshold consecutive successes
 * - HALF_OPEN → OPEN: On any failure during half-open state
 *
 * Thread-safe implementation using atomic operations.
 *
 * @property failureThreshold Number of failures before opening circuit (default 5)
 * @property timeWindowMs Time window in ms for counting failures (default 60000ms = 1 minute)
 * @property cooldownMs Time in ms before attempting recovery (default 30000ms = 30 seconds)
 * @property successThreshold Consecutive successes needed to close circuit (default 2)
 * @property logger Logger for state transitions and metrics
 */
class CircuitBreaker(
    private val failureThreshold: Int = 5,
    private val timeWindowMs: Long = 60000L,
    private val cooldownMs: Long = 30000L,
    private val successThreshold: Int = 2,
    private val logger: Logger
) {
    /**
     * Circuit breaker states
     */
    enum class State {
        /**
         * Normal operation - all requests pass through
         */
        CLOSED,

        /**
         * Circuit is open - all requests fail fast
         */
        OPEN,

        /**
         * Testing recovery - limited requests allowed
         */
        HALF_OPEN
    }

    /**
     * Result of a circuit breaker operation
     */
    sealed class CircuitResult<out T> {
        /**
         * Operation succeeded
         */
        data class Success<T>(val value: T) : CircuitResult<T>()

        /**
         * Operation failed
         */
        data class Failure(
            val exception: Exception,
            val state: State
        ) : CircuitResult<Nothing>()

        /**
         * Circuit is open - request rejected without executing
         */
        data class Rejected(
            val state: State,
            val message: String
        ) : CircuitResult<Nothing>()
    }

    // Thread-safe state management
    private val state = AtomicReference(State.CLOSED)
    private val failureCount = AtomicInteger(0)
    private val successCount = AtomicInteger(0)
    private val lastFailureTime = AtomicLong(0L)
    private val stateChangedTime = AtomicLong(System.currentTimeMillis())

    /**
     * Execute an operation protected by the circuit breaker.
     *
     * @param operationName Descriptive name for logging
     * @param operation The operation to execute
     * @return CircuitResult indicating success, failure, or rejection
     */
    fun <T> execute(operationName: String, operation: () -> T): CircuitResult<T> {
        val currentState = state.get()

        // Check if circuit should transition to HALF_OPEN
        if (currentState == State.OPEN) {
            val timeSinceStateChange = System.currentTimeMillis() - stateChangedTime.get()
            if (timeSinceStateChange >= cooldownMs) {
                transitionToHalfOpen()
            } else {
                // Circuit still open - reject request
                logger.fine("Circuit breaker OPEN - rejecting '$operationName' (cooldown: ${timeSinceStateChange}ms / ${cooldownMs}ms)")
                return CircuitResult.Rejected(
                    State.OPEN,
                    "Circuit breaker is OPEN. Service temporarily unavailable due to repeated failures. " +
                    "Retry after ${(cooldownMs - timeSinceStateChange) / 1000}s"
                )
            }
        }

        // Execute operation and handle result
        return try {
            val result = operation()
            onSuccess(operationName)
            CircuitResult.Success(result)

        } catch (e: Exception) {
            onFailure(operationName, e)
            CircuitResult.Failure(e, state.get())
        }
    }

    /**
     * Handle successful operation
     */
    private fun onSuccess(operationName: String) {
        val currentState = state.get()

        when (currentState) {
            State.HALF_OPEN -> {
                val successes = successCount.incrementAndGet()
                logger.info("Circuit breaker HALF_OPEN - success #$successes for '$operationName'")

                if (successes >= successThreshold) {
                    transitionToClosed()
                }
            }
            State.CLOSED -> {
                // Reset failure tracking on success
                resetFailures()
            }
            State.OPEN -> {
                // Should not happen, but handle gracefully
                logger.warning("Circuit breaker OPEN but operation succeeded - this should not happen")
            }
        }
    }

    /**
     * Handle failed operation
     */
    private fun onFailure(operationName: String, exception: Exception) {
        val currentState = state.get()
        val now = System.currentTimeMillis()

        when (currentState) {
            State.HALF_OPEN -> {
                // Any failure in HALF_OPEN immediately reopens circuit
                logger.warning("Circuit breaker HALF_OPEN - failure for '$operationName': ${exception.message}")
                transitionToOpen()
            }
            State.CLOSED -> {
                lastFailureTime.set(now)
                val failures = failureCount.incrementAndGet()

                // Check if failures are within time window
                val timeSinceFirstFailure = now - getFirstFailureTime()
                if (timeSinceFirstFailure > timeWindowMs) {
                    // Time window expired - reset counter
                    resetFailures()
                    failureCount.set(1)
                    logger.fine("Circuit breaker - failure #1 for '$operationName' (time window reset)")
                } else {
                    logger.warning("Circuit breaker - failure #$failures for '$operationName': ${exception.message}")

                    if (failures >= failureThreshold) {
                        transitionToOpen()
                    }
                }
            }
            State.OPEN -> {
                // Already open, increment failure count for metrics
                failureCount.incrementAndGet()
                logger.fine("Circuit breaker OPEN - additional failure for '$operationName'")
            }
        }
    }

    /**
     * Transition to OPEN state
     */
    private fun transitionToOpen() {
        if (state.compareAndSet(State.CLOSED, State.OPEN) ||
            state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
            stateChangedTime.set(System.currentTimeMillis())
            logger.severe(
                "⚠️ Circuit breaker transitioned to OPEN state\n" +
                "Failure count: ${failureCount.get()} (threshold: $failureThreshold)\n" +
                "Cooldown period: ${cooldownMs}ms\n" +
                "All requests will be rejected until cooldown expires"
            )
        }
    }

    /**
     * Transition to HALF_OPEN state
     */
    private fun transitionToHalfOpen() {
        if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
            stateChangedTime.set(System.currentTimeMillis())
            successCount.set(0)
            logger.info(
                "Circuit breaker transitioned to HALF_OPEN state\n" +
                "Testing recovery - need $successThreshold consecutive successes to close circuit"
            )
        }
    }

    /**
     * Transition to CLOSED state
     */
    private fun transitionToClosed() {
        if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
            stateChangedTime.set(System.currentTimeMillis())
            resetFailures()
            successCount.set(0)
            logger.info("✅ Circuit breaker transitioned to CLOSED state - service recovered")
        }
    }

    /**
     * Reset failure tracking
     */
    private fun resetFailures() {
        failureCount.set(0)
        lastFailureTime.set(0L)
    }

    /**
     * Get the time of the first failure in current window
     */
    private fun getFirstFailureTime(): Long {
        val lastFailure = lastFailureTime.get()
        val failures = failureCount.get()

        // Estimate first failure time based on current failure count
        // This is approximate since we only track the last failure time
        return if (failures > 0) {
            lastFailure - (timeWindowMs / failureThreshold) * (failures - 1)
        } else {
            lastFailure
        }
    }

    /**
     * Get current circuit breaker state
     */
    fun getState(): State = state.get()

    /**
     * Get current failure count
     */
    fun getFailureCount(): Int = failureCount.get()

    /**
     * Get metrics summary
     */
    fun getMetrics(): CircuitBreakerMetrics {
        val currentState = state.get()
        val timeSinceStateChange = System.currentTimeMillis() - stateChangedTime.get()

        return CircuitBreakerMetrics(
            state = currentState,
            failureCount = failureCount.get(),
            successCount = successCount.get(),
            timeSinceStateChangeMs = timeSinceStateChange,
            failureThreshold = failureThreshold,
            successThreshold = successThreshold
        )
    }

    /**
     * Reset circuit breaker to CLOSED state (for manual recovery)
     */
    fun reset() {
        state.set(State.CLOSED)
        resetFailures()
        successCount.set(0)
        stateChangedTime.set(System.currentTimeMillis())
        logger.info("Circuit breaker manually reset to CLOSED state")
    }
}

/**
 * Circuit breaker metrics
 */
data class CircuitBreakerMetrics(
    val state: CircuitBreaker.State,
    val failureCount: Int,
    val successCount: Int,
    val timeSinceStateChangeMs: Long,
    val failureThreshold: Int,
    val successThreshold: Int
) {
    override fun toString(): String {
        return "CircuitBreakerMetrics(state=$state, failures=$failureCount/$failureThreshold, " +
               "successes=$successCount/$successThreshold, timeSinceStateChange=${timeSinceStateChangeMs}ms)"
    }
}

/**
 * Configuration for CircuitBreaker loaded from config.yml
 */
data class CircuitBreakerConfig(
    val enabled: Boolean,
    val failureThreshold: Int,
    val timeWindowSeconds: Int,
    val cooldownSeconds: Int,
    val successThreshold: Int
) {
    /**
     * Create a CircuitBreaker from this configuration
     */
    fun toCircuitBreaker(logger: Logger): CircuitBreaker {
        return CircuitBreaker(
            failureThreshold = failureThreshold,
            timeWindowMs = timeWindowSeconds * 1000L,
            cooldownMs = cooldownSeconds * 1000L,
            successThreshold = successThreshold,
            logger = logger
        )
    }

    companion object {
        /**
         * Load circuit breaker configuration from config file
         */
        fun fromConfig(config: org.bukkit.configuration.ConfigurationSection): CircuitBreakerConfig {
            return CircuitBreakerConfig(
                enabled = config.getBoolean("discord.circuit-breaker.enabled", true),
                failureThreshold = config.getInt("discord.circuit-breaker.failure-threshold", 5),
                timeWindowSeconds = config.getInt("discord.circuit-breaker.time-window-seconds", 60),
                cooldownSeconds = config.getInt("discord.circuit-breaker.cooldown-seconds", 30),
                successThreshold = config.getInt("discord.circuit-breaker.success-threshold", 2)
            )
        }
    }
}
