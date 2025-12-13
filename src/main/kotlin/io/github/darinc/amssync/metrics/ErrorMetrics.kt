package io.github.darinc.amssync.metrics

import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.time.Instant

/**
 * Tracks error metrics and command execution statistics for monitoring.
 *
 * This provides basic observability into plugin health without external dependencies.
 * Metrics can be accessed via the `/amssync metrics` command or programmatically.
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
    private val commandDurations = ConcurrentHashMap<String, ArrayDeque<Long>>()

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

    // Compression metrics
    private val compressionSuccessCount = AtomicLong(0)
    private val compressionFailureCount = AtomicLong(0)
    private val compressionCatchupCount = AtomicLong(0)
    private val compressionScheduledCount = AtomicLong(0)
    private val compressionDurations = ArrayDeque<Long>()
    @Volatile private var lastCompressionStats: CompressionStats? = null

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
     *
     * Uses ArrayDeque for O(1) removal from front when trimming old samples.
     */
    private fun recordDuration(commandName: String, durationMs: Long) {
        val durations = commandDurations.computeIfAbsent(commandName) { ArrayDeque() }

        synchronized(durations) {
            durations.addLast(durationMs)
            // Keep only the most recent samples - O(1) removal from front
            while (durations.size > maxDurationSamples) {
                durations.removeFirst()
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
     * Record a successful compression run.
     *
     * @param stats Compression statistics from the run
     */
    fun recordCompressionRun(stats: CompressionStats) {
        compressionSuccessCount.incrementAndGet()
        if (stats.wasCatchup) {
            compressionCatchupCount.incrementAndGet()
        } else {
            compressionScheduledCount.incrementAndGet()
        }

        synchronized(compressionDurations) {
            compressionDurations.addLast(stats.totalDurationMs)
            while (compressionDurations.size > maxDurationSamples) {
                compressionDurations.removeFirst()
            }
        }

        lastCompressionStats = stats
    }

    /**
     * Record a failed compression run.
     *
     * @param errorType Type of error that caused the failure
     */
    fun recordCompressionFailure(errorType: String) {
        compressionFailureCount.incrementAndGet()
        errorTypeCount
            .computeIfAbsent("compression_$errorType") { AtomicLong(0) }
            .incrementAndGet()
    }

    /**
     * Get average compression duration in milliseconds.
     */
    fun getCompressionAverageLatency(): Double? {
        synchronized(compressionDurations) {
            if (compressionDurations.isEmpty()) return null
            return compressionDurations.toList().average()
        }
    }

    /**
     * Get P95 compression duration in milliseconds.
     */
    fun getCompressionP95Latency(): Long? {
        synchronized(compressionDurations) {
            if (compressionDurations.isEmpty()) return null
            val sorted = compressionDurations.toList().sorted()
            val index = (sorted.size * 0.95).toInt().coerceAtMost(sorted.size - 1)
            return sorted[index]
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
            return durations.toList().average()
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
            val sorted = durations.toList().sorted()
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
            ),
            compressionStats = CompressionMetrics(
                successCount = compressionSuccessCount.get(),
                failureCount = compressionFailureCount.get(),
                catchupCount = compressionCatchupCount.get(),
                scheduledCount = compressionScheduledCount.get(),
                avgTotalDurationMs = getCompressionAverageLatency(),
                p95TotalDurationMs = getCompressionP95Latency(),
                lastRunStats = lastCompressionStats
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
        compressionSuccessCount.set(0)
        compressionFailureCount.set(0)
        compressionCatchupCount.set(0)
        compressionScheduledCount.set(0)
        synchronized(compressionDurations) {
            compressionDurations.clear()
        }
        lastCompressionStats = null
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
    val connectionStats: ConnectionStats,
    val compressionStats: CompressionMetrics
) {
    /**
     * Format metrics for console/chat display.
     */
    fun toDisplayString(): String {
        return buildString {
            appendLine("=== AMSSync Metrics ===")
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

            appendLine()
            appendLine("-- Compression --")
            val totalRuns = compressionStats.successCount + compressionStats.failureCount
            if (totalRuns > 0) {
                appendLine("  Runs: ${compressionStats.successCount} successful" +
                    " (${compressionStats.scheduledCount} scheduled, ${compressionStats.catchupCount} catch-up)")
                if (compressionStats.failureCount > 0) {
                    appendLine("  Failures: ${compressionStats.failureCount}")
                }
                compressionStats.avgTotalDurationMs?.let {
                    appendLine("  Avg Duration: ${String.format("%.0f", it)}ms")
                }
                compressionStats.p95TotalDurationMs?.let {
                    appendLine("  P95 Duration: ${it}ms")
                }
                compressionStats.lastRunStats?.let { last ->
                    appendLine("  Last Run:")
                    appendLine("    Total: ${last.totalDurationMs}ms")
                    appendLine("    Phases: raw→hourly ${last.rawToHourlyMs}ms, " +
                        "hourly→daily ${last.hourlyToDailyMs}ms, " +
                        "daily→weekly ${last.dailyToWeeklyMs}ms, " +
                        "cleanup ${last.cleanupMs}ms")
                    appendLine("    Records: hourly+${last.hourlyCreated}, " +
                        "daily+${last.dailyCreated}, weekly+${last.weeklyCreated}")
                    if (last.weeklyDeleted > 0 || last.levelupsDeleted > 0) {
                        appendLine("    Deleted: weekly=${last.weeklyDeleted}, levelups=${last.levelupsDeleted}")
                    }
                }
            } else {
                appendLine("  No compression runs yet")
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

/**
 * Statistics from a single compression run.
 */
data class CompressionStats(
    val totalDurationMs: Long,
    val rawToHourlyMs: Long,
    val hourlyToDailyMs: Long,
    val dailyToWeeklyMs: Long,
    val cleanupMs: Long,
    val hourlyCreated: Int,
    val dailyCreated: Int,
    val weeklyCreated: Int,
    val weeklyDeleted: Int,
    val levelupsDeleted: Int,
    val wasCatchup: Boolean,
    val timestamp: Instant
)

/**
 * Aggregated compression metrics.
 */
data class CompressionMetrics(
    val successCount: Long,
    val failureCount: Long,
    val catchupCount: Long,
    val scheduledCount: Long,
    val avgTotalDurationMs: Double?,
    val p95TotalDurationMs: Long?,
    val lastRunStats: CompressionStats?
)
