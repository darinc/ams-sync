# Built-in Metrics

**Complexity**: Intermediate
**Key File**: [`metrics/ErrorMetrics.kt`](../../src/main/kotlin/io/github/darinc/amssync/metrics/ErrorMetrics.kt)

## Overview

AMSSync includes a built-in metrics system for monitoring plugin health without external dependencies. Access metrics via `/amssync metrics` or programmatically.

## What's Tracked

### Command Metrics
- Success/failure counts per command
- Execution latency (average and P95)
- Success rate percentage

### Discord API Metrics
- Successful API calls
- Failed API calls
- Requests rejected by circuit breaker
- Overall success rate

### Circuit Breaker Metrics
- Number of circuit trips (CLOSED → OPEN)
- Number of recoveries (HALF_OPEN → CLOSED)

### Connection Metrics
- Total connection attempts
- Successful connections
- Failed connections

## Viewing Metrics

### In-Game Command

```
/amssync metrics
```

Output:
```
=== AMSSync Metrics ===
Uptime: 2d 5h 30m

-- Discord API --
  Success: 1542
  Failures: 12
  Rejected: 3
  Success Rate: 99.2%

-- Circuit Breaker --
  Trips: 1
  Recoveries: 1

-- Commands --
  mcstats:
    Success: 234, Failures: 5
    Success Rate: 97.9%
    Avg Latency: 145ms
    P95 Latency: 320ms
  mctop:
    Success: 156, Failures: 2
    Success Rate: 98.7%
    Avg Latency: 890ms
    P95 Latency: 1200ms

-- Errors by Type --
  PlayerDataNotFoundException: 5
  discord_timeout: 3
```

### Programmatic Access

```kotlin
val metrics = plugin.errorMetrics.getSnapshot()

// Access specific data
println("Uptime: ${metrics.uptimeFormatted}")
println("Discord success rate: ${metrics.discordApiStats.successRate}")

// Per-command stats
metrics.commandStats["mcstats"]?.let { stats ->
    println("mcstats avg latency: ${stats.avgLatencyMs}ms")
}
```

## Recording Metrics

### Command Execution

```kotlin
fun handleCommand(event: SlashCommandInteractionEvent) {
    val startTime = System.currentTimeMillis()

    try {
        // ... command logic ...

        val duration = System.currentTimeMillis() - startTime
        plugin.errorMetrics.recordCommandSuccess("mcstats", duration)

    } catch (e: PlayerDataNotFoundException) {
        val duration = System.currentTimeMillis() - startTime
        plugin.errorMetrics.recordCommandFailure(
            commandName = "mcstats",
            errorType = e::class.simpleName ?: "Unknown",
            durationMs = duration
        )
        throw e
    }
}
```

### Discord API Calls

```kotlin
fun executeDiscordOperation(operation: () -> Unit) {
    try {
        operation()
        plugin.errorMetrics.recordDiscordApiSuccess()
    } catch (e: Exception) {
        plugin.errorMetrics.recordDiscordApiFailure(
            when (e) {
                is SocketTimeoutException -> "timeout"
                is RateLimitedException -> "rate_limit"
                else -> "other"
            }
        )
        throw e
    }
}
```

### Circuit Breaker Events

```kotlin
// When circuit opens
plugin.errorMetrics.recordCircuitBreakerTrip()

// When circuit recovers
plugin.errorMetrics.recordCircuitBreakerRecovery()
```

### Connection Attempts

```kotlin
try {
    connectToDiscord()
    plugin.errorMetrics.recordConnectionAttempt(success = true)
} catch (e: Exception) {
    plugin.errorMetrics.recordConnectionAttempt(success = false)
}
```

## Implementation Details

### Thread Safety

All counters use atomic operations:

```kotlin
// Thread-safe counters
private val discordApiSuccessCount = AtomicLong(0)
private val commandSuccessCount = ConcurrentHashMap<String, AtomicLong>()

// Safe increment from any thread
discordApiSuccessCount.incrementAndGet()
commandSuccessCount.computeIfAbsent(commandName) { AtomicLong(0) }.incrementAndGet()
```

### Bounded Duration History

Latency samples are bounded to prevent memory growth:

```kotlin
private val maxDurationSamples = 100
private val commandDurations = ConcurrentHashMap<String, ArrayDeque<Long>>()

private fun recordDuration(commandName: String, durationMs: Long) {
    val durations = commandDurations.computeIfAbsent(commandName) { ArrayDeque() }

    synchronized(durations) {
        durations.addLast(durationMs)
        // O(1) removal from front
        while (durations.size > maxDurationSamples) {
            durations.removeFirst()
        }
    }
}
```

**Why ArrayDeque?** Provides O(1) removal from front, unlike ArrayList which requires O(n) shifting.

### Percentile Calculation

```kotlin
fun getP95Latency(commandName: String): Long? {
    val durations = commandDurations[commandName] ?: return null
    synchronized(durations) {
        if (durations.isEmpty()) return null
        val sorted = durations.toList().sorted()
        val index = (sorted.size * 0.95).toInt().coerceAtMost(sorted.size - 1)
        return sorted[index]
    }
}
```

## Data Classes

### MetricsSnapshot

Complete snapshot of all metrics:

```kotlin
data class MetricsSnapshot(
    val uptimeSeconds: Long,
    val uptimeFormatted: String,
    val commandStats: Map<String, CommandStats>,
    val errorStats: Map<String, Long>,
    val discordApiStats: DiscordApiStats,
    val circuitBreakerStats: CircuitBreakerStats,
    val connectionStats: ConnectionStats
)
```

### CommandStats

Per-command statistics:

```kotlin
data class CommandStats(
    val successCount: Long,
    val failureCount: Long,
    val successRate: Double?,      // 0.0 to 1.0
    val avgLatencyMs: Double?,
    val p95LatencyMs: Long?
)
```

### DiscordApiStats

Discord API health:

```kotlin
data class DiscordApiStats(
    val successCount: Long,
    val failureCount: Long,
    val rejectedCount: Long,       // Circuit breaker rejections
    val successRate: Double?
)
```

## Uptime Formatting

```kotlin
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
// Output: "2d 5h 30m" or "0h 45m"
```

## Success Rate Calculation

```kotlin
fun getCommandSuccessRate(commandName: String): Double? {
    val successes = commandSuccessCount[commandName]?.get() ?: 0L
    val failures = commandFailureCount[commandName]?.get() ?: 0L
    val total = successes + failures

    if (total == 0L) return null  // No data yet
    return successes.toDouble() / total  // 0.0 to 1.0
}
```

## Reset Metrics

For testing or manual reset:

```kotlin
plugin.errorMetrics.reset()
```

Clears all counters and duration history.

## Display Formatting

```kotlin
fun toDisplayString(): String {
    return buildString {
        appendLine("=== AMSSync Metrics ===")
        appendLine("Uptime: $uptimeFormatted")

        appendLine("-- Discord API --")
        appendLine("  Success: ${discordApiStats.successCount}")
        discordApiStats.successRate?.let {
            appendLine("  Success Rate: ${String.format("%.1f", it * 100)}%")
        }

        // ... more sections
    }
}
```

## Use Cases

### Health Checks

```kotlin
val snapshot = plugin.errorMetrics.getSnapshot()

// Check if Discord is healthy
val discordHealthy = (snapshot.discordApiStats.successRate ?: 1.0) > 0.95

// Check for recent circuit breaker issues
val recentTrips = snapshot.circuitBreakerStats.tripCount > 0
```

### Performance Monitoring

```kotlin
val stats = snapshot.commandStats["mcstats"]
stats?.let {
    if ((it.avgLatencyMs ?: 0.0) > 1000) {
        logger.warning("mcstats latency high: ${it.avgLatencyMs}ms")
    }
}
```

### Error Analysis

```kotlin
snapshot.errorStats.forEach { (errorType, count) ->
    if (count > 10) {
        logger.warning("High error count for $errorType: $count")
    }
}
```

## Limitations

- **No persistence**: Metrics reset on server restart
- **No sliding windows**: Counts are cumulative, not per-time-period
- **No export**: No Prometheus/StatsD integration (yet)
- **Bounded history**: Only last 100 latency samples per command

## Related Documentation

- [Circuit Breaker](../patterns/circuit-breaker.md) - Source of trip/recovery events
- [Error Handling](error-handling.md) - Exception types tracked
- [Discord Commands](../features/discord-commands.md) - Commands being tracked
