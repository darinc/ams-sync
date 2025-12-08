package io.github.darinc.amsdiscord.metrics

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.time.Instant

/**
 * Tracks error metrics and command execution statistics for monitoring.
 *
 * This provides basic observability into plugin health without external dependencies.
 * Metrics can be accessed via the `/amslink metrics` command or programmatically.
 *
 * Features:
 * - Thread-safe counters using atomic operations
 * - Per-command and per-error-type tracking
 * - Uptime and timing statistics
 * - Memory-efficient with bounded history
 *
 * Future enhancements (Priority 3):
 * - Sliding window metrics for rate calculation
 * - Error budget tracking for SLOs
 * - Export to external monitoring systems (Prometheus, etc.)
 */
class ErrorMetrics {

    private val startTime = Instant.now()

    // Command execution counters
    private val commandSuccessCount = ConcurrentHashMap<String, AtomicLong>()
    private val commandFailureCount = ConcurrentHashMap<String, AtomicLong>()
    private val commandDurations = ConcurrentHashMap<String, MutableList<Long>>()

    // Error type counters
    private val errorTypeCount = ConcurrentHashMap<String, AtomicLong>()

    // Discord API metrics
    private val discordApiSuccessCount = AtomicLong(0)
    private val discordApiFailureCount = AtomicLong(0)
    private val discordApiRejectedCount = AtomicLong(0)

    // Circuit breaker metrics
    private val circuitBreakerTripsCount = AtomicLong(0)
    private val circuitBreakerRecoveryCount = AtomicLong(0)

    // Connection metrics
    private val connectionAttemptCount = AtomicLong(0)
    private val connectionSuccessCount = AtomicLong(0)
    private val connectionFailureCount = AtomicLong(0)

    // Maximum duration samples to keep per command (prevent unbounded memory growth)
    private val maxDurationSamples = 100

    /**
     * Record a successful command execution.
     *
     * @param commandName Name of the command (e.g., "mcstats", "mctop", "amslink")
     * @param durationMs Execution duration in milliseconds
     */
    fun recordCommandSuccess(commandName: String, durationMs: Long) {
        commandSuccessCount
            .computeIfAbsent(commandName) { AtomicLong(0) }
            .incrementAndGet()

        recordDuration(commandName, durationMs)
    }

    /**
     * Record a failed command execution.
     *
     * @param commandName Name of the command
     * @param errorType Type/class of error that occurred
     * @param durationMs Execution duration in milliseconds
     */
    fun recordCommandFailure(commandName: String, errorType: String, durationMs: Long) {
        commandFailureCount
            .computeIfAbsent(commandName) { AtomicLong(0) }
            .incrementAndGet()

        errorTypeCount
            .computeIfAbsent(errorType) { AtomicLong(0) }
            .incrementAndGet()

        recordDuration(commandName, durationMs)
    }

    /**
     * Record command duration for latency tracking.
     */
    private fun recordDuration(commandName: String, durationMs: Long) {
        val durations = commandDurations.computeIfAbsent(commandName) {
            java.util.Collections.synchronizedList(mutableListOf())
        }

        synchronized(durations) {
            durations.add(durationMs)
            // Keep only the most recent samples
            while (durations.size > maxDurationSamples) {
                durations.removeAt(0)
            }
        }
    }

    /**
     * Record a Discord API success.
     */
    fun recordDiscordApiSuccess() {
        discordApiSuccessCount.incrementAndGet()
    }

    /**
     * Record a Discord API failure.
     *
     * @param errorType Type of error (e.g., "timeout", "rate_limit", "network")
     */
    fun recordDiscordApiFailure(errorType: String) {
        discordApiFailureCount.incrementAndGet()
        errorTypeCount
            .computeIfAbsent("discord_$errorType") { AtomicLong(0) }
            .incrementAndGet()
    }

    /**
     * Record a request rejected by circuit breaker.
     */
    fun recordDiscordApiRejected() {
        discordApiRejectedCount.incrementAndGet()
    }

    /**
     * Record circuit breaker trip (transition to OPEN state).
     */
    fun recordCircuitBreakerTrip() {
        circuitBreakerTripsCount.incrementAndGet()
    }

    /**
     * Record circuit breaker recovery (transition to CLOSED state).
     */
    fun recordCircuitBreakerRecovery() {
        circuitBreakerRecoveryCount.incrementAndGet()
    }

    /**
     * Record a connection attempt.
     *
     * @param success Whether the connection succeeded
     */
    fun recordConnectionAttempt(success: Boolean) {
        connectionAttemptCount.incrementAndGet()
        if (success) {
            connectionSuccessCount.incrementAndGet()
        } else {
            connectionFailureCount.incrementAndGet()
        }
    }

    /**
     * Get uptime duration in seconds.
     */
    fun getUptimeSeconds(): Long {
        return java.time.Duration.between(startTime, Instant.now()).seconds
    }

    /**
     * Get formatted uptime string (e.g., "2d 5h 30m").
     */
    fun getUptimeFormatted(): String {
        val seconds = getUptimeSeconds()
        val days = seconds / 86400
        val hours = (seconds % 86400) / 3600
        val minutes = (seconds % 3600) / 60

        return buildString {
            if (days > 0) append("${days}d ")
            if (hours > 0 || days > 0) append("${hours}h ")
            append("${minutes}m")
        }.trim()
    }

    /**
     * Get average latency for a command in milliseconds.
     *
     * @param commandName Name of the command
     * @return Average latency or null if no data
     */
    fun getAverageLatency(commandName: String): Double? {
        val durations = commandDurations[commandName] ?: return null
        synchronized(durations) {
            if (durations.isEmpty()) return null
            return durations.average()
        }
    }

    /**
     * Get P95 latency for a command in milliseconds.
     *
     * @param commandName Name of the command
     * @return P95 latency or null if no data
     */
    fun getP95Latency(commandName: String): Long? {
        val durations = commandDurations[commandName] ?: return null
        synchronized(durations) {
            if (durations.isEmpty()) return null
            val sorted = durations.sorted()
            val index = (sorted.size * 0.95).toInt().coerceAtMost(sorted.size - 1)
            return sorted[index]
        }
    }

    /**
     * Get success rate for a command (0.0 to 1.0).
     *
     * @param commandName Name of the command
     * @return Success rate or null if no data
     */
    fun getCommandSuccessRate(commandName: String): Double? {
        val successes = commandSuccessCount[commandName]?.get() ?: 0L
        val failures = commandFailureCount[commandName]?.get() ?: 0L
        val total = successes + failures
        if (total == 0L) return null
        return successes.toDouble() / total
    }

    /**
     * Get Discord API success rate (0.0 to 1.0).
     */
    fun getDiscordApiSuccessRate(): Double? {
        val successes = discordApiSuccessCount.get()
        val failures = discordApiFailureCount.get()
        val total = successes + failures
        if (total == 0L) return null
        return successes.toDouble() / total
    }

    /**
     * Get comprehensive metrics snapshot.
     */
    fun getSnapshot(): MetricsSnapshot {
        return MetricsSnapshot(
            uptimeSeconds = getUptimeSeconds(),
            uptimeFormatted = getUptimeFormatted(),
            commandStats = getCommandStats(),
            errorStats = getErrorStats(),
            discordApiStats = DiscordApiStats(
                successCount = discordApiSuccessCount.get(),
                failureCount = discordApiFailureCount.get(),
                rejectedCount = discordApiRejectedCount.get(),
                successRate = getDiscordApiSuccessRate()
            ),
            circuitBreakerStats = CircuitBreakerStats(
                tripCount = circuitBreakerTripsCount.get(),
                recoveryCount = circuitBreakerRecoveryCount.get()
            ),
            connectionStats = ConnectionStats(
                attemptCount = connectionAttemptCount.get(),
                successCount = connectionSuccessCount.get(),
                failureCount = connectionFailureCount.get()
            )
        )
    }

    /**
     * Get per-command statistics.
     */
    private fun getCommandStats(): Map<String, CommandStats> {
        val allCommands = (commandSuccessCount.keys + commandFailureCount.keys).distinct()

        return allCommands.associateWith { cmd ->
            CommandStats(
                successCount = commandSuccessCount[cmd]?.get() ?: 0,
                failureCount = commandFailureCount[cmd]?.get() ?: 0,
                successRate = getCommandSuccessRate(cmd),
                avgLatencyMs = getAverageLatency(cmd),
                p95LatencyMs = getP95Latency(cmd)
            )
        }
    }

    /**
     * Get error type statistics.
     */
    private fun getErrorStats(): Map<String, Long> {
        return errorTypeCount.mapValues { it.value.get() }
    }

    /**
     * Reset all metrics (useful for testing or manual reset).
     */
    fun reset() {
        commandSuccessCount.clear()
        commandFailureCount.clear()
        commandDurations.clear()
        errorTypeCount.clear()
        discordApiSuccessCount.set(0)
        discordApiFailureCount.set(0)
        discordApiRejectedCount.set(0)
        circuitBreakerTripsCount.set(0)
        circuitBreakerRecoveryCount.set(0)
        connectionAttemptCount.set(0)
        connectionSuccessCount.set(0)
        connectionFailureCount.set(0)
    }
}

/**
 * Snapshot of all metrics at a point in time.
 */
data class MetricsSnapshot(
    val uptimeSeconds: Long,
    val uptimeFormatted: String,
    val commandStats: Map<String, CommandStats>,
    val errorStats: Map<String, Long>,
    val discordApiStats: DiscordApiStats,
    val circuitBreakerStats: CircuitBreakerStats,
    val connectionStats: ConnectionStats
) {
    /**
     * Format metrics for console/chat display.
     */
    fun toDisplayString(): String {
        return buildString {
            appendLine("=== AMS Discord Metrics ===")
            appendLine("Uptime: $uptimeFormatted")
            appendLine()

            appendLine("-- Discord API --")
            appendLine("  Success: ${discordApiStats.successCount}")
            appendLine("  Failures: ${discordApiStats.failureCount}")
            appendLine("  Rejected: ${discordApiStats.rejectedCount}")
            discordApiStats.successRate?.let {
                appendLine("  Success Rate: ${String.format("%.1f", it * 100)}%")
            }
            appendLine()

            appendLine("-- Circuit Breaker --")
            appendLine("  Trips: ${circuitBreakerStats.tripCount}")
            appendLine("  Recoveries: ${circuitBreakerStats.recoveryCount}")
            appendLine()

            appendLine("-- Commands --")
            commandStats.forEach { (cmd, stats) ->
                appendLine("  $cmd:")
                appendLine("    Success: ${stats.successCount}, Failures: ${stats.failureCount}")
                stats.successRate?.let {
                    appendLine("    Success Rate: ${String.format("%.1f", it * 100)}%")
                }
                stats.avgLatencyMs?.let {
                    appendLine("    Avg Latency: ${String.format("%.0f", it)}ms")
                }
                stats.p95LatencyMs?.let {
                    appendLine("    P95 Latency: ${it}ms")
                }
            }

            if (errorStats.isNotEmpty()) {
                appendLine()
                appendLine("-- Errors by Type --")
                errorStats.forEach { (type, count) ->
                    appendLine("  $type: $count")
                }
            }
        }
    }
}

/**
 * Statistics for a single command.
 */
data class CommandStats(
    val successCount: Long,
    val failureCount: Long,
    val successRate: Double?,
    val avgLatencyMs: Double?,
    val p95LatencyMs: Long?
)

/**
 * Discord API statistics.
 */
data class DiscordApiStats(
    val successCount: Long,
    val failureCount: Long,
    val rejectedCount: Long,
    val successRate: Double?
)

/**
 * Circuit breaker statistics.
 */
data class CircuitBreakerStats(
    val tripCount: Long,
    val recoveryCount: Long
)

/**
 * Connection statistics.
 */
data class ConnectionStats(
    val attemptCount: Long,
    val successCount: Long,
    val failureCount: Long
)
