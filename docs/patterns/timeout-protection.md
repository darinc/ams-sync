# Timeout Protection

**Complexity**: Advanced
**Key File**: [`discord/TimeoutManager.kt`](../../src/main/kotlin/io/github/darinc/amssync/discord/TimeoutManager.kt)

## What Problem Does This Solve?

External operations (Discord API, network calls) can hang indefinitely, blocking threads and degrading server performance. Timeout protection:

- Prevents operations from running forever
- Provides early warning for slow operations
- Enables graceful handling of timeouts
- Supports both async and Bukkit main thread operations

## Architecture

```
┌────────────────────────────────────────────────────────────────┐
│                       TimeoutManager                            │
├────────────────────────────────────────────────────────────────┤
│                                                                 │
│  executeWithTimeout(operation)                                  │
│         │                                                       │
│         ▼                                                       │
│  ┌─────────────────┐     ┌─────────────────┐                   │
│  │ ScheduledExecutor│     │ Warning Timer   │                   │
│  │ (daemon threads) │     │ (3s default)    │                   │
│  └────────┬────────┘     └────────┬────────┘                   │
│           │                       │                             │
│           ▼                       ▼                             │
│  ┌─────────────────┐     ┌─────────────────┐                   │
│  │ CompletableFuture│     │ Log warning if  │                   │
│  │ .get(timeout)    │     │ still running   │                   │
│  └────────┬────────┘     └─────────────────┘                   │
│           │                                                     │
│           ▼                                                     │
│  ┌─────────────────────────────────────────┐                   │
│  │ TimeoutResult (Success/Timeout/Failure) │                   │
│  └─────────────────────────────────────────┘                   │
│                                                                 │
└────────────────────────────────────────────────────────────────┘
```

## Sealed Class Result Type

Instead of exceptions, TimeoutManager returns a sealed class:

```kotlin
sealed class TimeoutResult<out T> {
    // Operation completed successfully
    data class Success<T>(val value: T) : TimeoutResult<T>()

    // Operation exceeded hard timeout
    data class Timeout(
        val operationName: String,
        val timeoutMs: Long
    ) : TimeoutResult<Nothing>()

    // Operation failed with an exception
    data class Failure(
        val exception: Exception,
        val operationName: String
    ) : TimeoutResult<Nothing>()
}
```

**Benefits**:
- Compiler enforces handling all cases
- Clear distinction between timeout and failure
- No uncaught exception surprises
- Self-documenting code

## Usage Examples

### Basic Timeout Protection

```kotlin
val result = timeoutManager.executeWithTimeout("Fetch player data") {
    discordApi.fetchUserData(userId)
}

when (result) {
    is TimeoutResult.Success -> {
        val userData = result.value
        // Use the data
    }
    is TimeoutResult.Timeout -> {
        logger.warning("Operation timed out after ${result.timeoutMs}ms")
        // Return cached data or error response
    }
    is TimeoutResult.Failure -> {
        logger.warning("Operation failed: ${result.exception.message}")
        // Handle the error
    }
}
```

### With Warning Callback

```kotlin
val result = timeoutManager.executeWithTimeout(
    operationName = "Discord connection",
    warningCallback = { opName, elapsedMs ->
        logger.warning("$opName is slow ($elapsedMs ms)")
        metrics.recordSlowOperation(opName)
    }
) {
    jda.awaitReady()
}
```

### Bukkit Main Thread Operations

Some Bukkit API calls must run on the main thread. Use `executeOnBukkitWithTimeout`:

```kotlin
val result = timeoutManager.executeOnBukkitWithTimeout(
    plugin,
    "Load player inventory"
) {
    player.inventory.contents.toList()
}
```

> **WARNING**: Bukkit tasks cannot be forcefully cancelled once started. The timeout will return early, but the task may still complete in the background.

## Implementation Details

### Thread Management

```kotlin
private val timeoutExecutor: ScheduledExecutorService = Executors.newScheduledThreadPool(
    2,
    ThreadFactory { runnable ->
        Thread(runnable, "AMSSync-Timeout").apply {
            isDaemon = true  // Won't prevent JVM shutdown
        }
    }
)
```

### Execute Flow

```kotlin
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
            logger.warning("Operation '$operationName' taking longer than expected (${elapsed}ms)")
            warningCallback?.onWarning(operationName, elapsed)
        }
    }, warningThresholdMs, TimeUnit.MILLISECONDS)

    return try {
        val result = future.get(hardTimeoutMs, TimeUnit.MILLISECONDS)
        warningFuture.cancel(false)
        TimeoutResult.Success(result)
    } catch (e: TimeoutException) {
        future.cancel(true)
        warningFuture.cancel(false)
        TimeoutResult.Timeout(operationName, System.currentTimeMillis() - startTime)
    } catch (e: ExecutionException) {
        warningFuture.cancel(false)
        TimeoutResult.Failure(e.cause as? Exception ?: Exception("Unknown error", e), operationName)
    }
}
```

## Configuration

```yaml
discord:
  timeout:
    enabled: true
    warning-threshold-seconds: 3   # Log warning if operation exceeds this
    hard-timeout-seconds: 10       # Cancel operation if exceeds this
```

### Configuration Data Class

```kotlin
data class TimeoutConfig(
    val enabled: Boolean,
    val warningThresholdSeconds: Int,
    val hardTimeoutSeconds: Int
) {
    fun toTimeoutManager(logger: Logger): TimeoutManager {
        return TimeoutManager(
            warningThresholdMs = warningThresholdSeconds * 1000L,
            hardTimeoutMs = hardTimeoutSeconds * 1000L,
            logger = logger
        )
    }

    companion object {
        fun fromConfig(config: ConfigurationSection): TimeoutConfig {
            return TimeoutConfig(
                enabled = config.getBoolean("discord.timeout.enabled", true),
                warningThresholdSeconds = config.getInt("discord.timeout.warning-threshold-seconds", 3),
                hardTimeoutSeconds = config.getInt("discord.timeout.hard-timeout-seconds", 10)
            )
        }
    }
}
```

## Usage in AMSSync

### Discord Connection

```kotlin
val connectionResult = if (timeoutEnabled && timeoutManager != null) {
    timeoutManager.executeWithTimeout("Discord connection with retries") {
        retryManager.executeWithRetry("Discord connection") {
            discordManager.initialize(token, guildId)
        }
    }
} else {
    // No timeout protection
    TimeoutResult.Success(retryManager.executeWithRetry("Discord connection") {
        discordManager.initialize(token, guildId)
    })
}
```

### Graceful Shutdown

```kotlin
fun shutdown() {
    logger.info("Shutting down TimeoutManager...")
    timeoutExecutor.shutdown()
    try {
        if (!timeoutExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
            logger.warning("TimeoutManager did not terminate gracefully, forcing shutdown...")
            timeoutExecutor.shutdownNow()
        }
    } catch (e: InterruptedException) {
        timeoutExecutor.shutdownNow()
        Thread.currentThread().interrupt()
    }
}
```

## Best Practices

### Do

```kotlin
// ✅ Use descriptive operation names
timeoutManager.executeWithTimeout("Fetch leaderboard for skill=$skill") { ... }

// ✅ Handle all result cases
when (result) {
    is TimeoutResult.Success -> ...
    is TimeoutResult.Timeout -> ...
    is TimeoutResult.Failure -> ...
}

// ✅ Set appropriate timeouts for operation type
// Fast operation: 2-3 seconds
// Network call: 5-10 seconds
// Batch operation: 30+ seconds
```

### Don't

```kotlin
// ❌ Generic operation names
timeoutManager.executeWithTimeout("operation") { ... }

// ❌ Ignoring timeout results
val result = timeoutManager.executeWithTimeout(...) { ... }
if (result is TimeoutResult.Success) { ... }
// What about Timeout and Failure?

// ❌ Very short timeouts for network operations
// Discord API can have 1-2s latency
TimeoutManager(hardTimeoutMs = 500L, ...)
```

## When to Use Timeout Protection

**Good use cases**:
- Discord API calls
- Network requests
- File I/O on potentially slow storage
- Any external service calls

**Not needed for**:
- In-memory operations
- Simple calculations
- Operations with built-in timeouts

## Related Patterns

- [Circuit Breaker](circuit-breaker.md) - Fail-fast during sustained outages
- [Retry with Backoff](retry-backoff.md) - Handle transient failures
- [Sealed Results](sealed-results.md) - Result type pattern used here
