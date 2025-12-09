# Retry with Exponential Backoff

**Complexity**: Intermediate
**Key File**: [`discord/RetryManager.kt`](../../src/main/kotlin/io/github/darinc/amssync/discord/RetryManager.kt)

## What Problem Does This Solve?

Network operations fail temporarily. A momentary Discord outage, a brief network hiccup, or a transient server error shouldn't cause permanent failure. But naive retry logic creates problems:

```kotlin
// Bad: Immediate retry hammers the service
while (!success) {
    try { connectToDiscord() }
    catch (e: Exception) { /* try again immediately */ }
}
```

**Problems with naive retry**:
- Floods the failing service with requests
- Wastes resources during extended outages
- Can trigger rate limiting
- No recovery time for the service

## Exponential Backoff Solution

Instead of retrying immediately, wait longer between each attempt:

```
Attempt 1: Fail → Wait 5s
Attempt 2: Fail → Wait 10s  (5 × 2^1)
Attempt 3: Fail → Wait 20s  (5 × 2^2)
Attempt 4: Fail → Wait 40s  (5 × 2^3)
Attempt 5: Fail → Give up
```

**Formula**: `delay = min(initialDelay × multiplier^(attempt-1), maxDelay)`

## Implementation

### The RetryManager Class

```kotlin
class RetryManager(
    private val maxAttempts: Int = 5,
    private val initialDelayMs: Long = 5000L,
    private val maxDelayMs: Long = 300000L,    // Cap at 5 minutes
    private val backoffMultiplier: Double = 2.0,
    private val logger: Logger
) {
    // Sealed class for type-safe results
    sealed class RetryResult<out T> {
        data class Success<T>(val value: T) : RetryResult<T>()
        data class Failure(val lastException: Exception, val attempts: Int) : RetryResult<Nothing>()
    }
}
```

### The Execute Method

```kotlin
fun <T> executeWithRetry(operationName: String, operation: () -> T): RetryResult<T> {
    var attempt = 0
    var lastException: Exception? = null

    while (attempt < maxAttempts) {
        attempt++

        try {
            logger.info("$operationName - Attempt $attempt/$maxAttempts")
            val result = operation()

            // Log recovery if it took multiple attempts
            if (attempt > 1) {
                logger.info("$operationName succeeded after $attempt attempts")
            }

            return RetryResult.Success(result)

        } catch (e: Exception) {
            lastException = e

            if (attempt >= maxAttempts) {
                logger.severe("$operationName failed after $maxAttempts attempts")
                break
            }

            val delay = calculateDelay(attempt)
            logger.warning("$operationName failed (attempt $attempt): ${e.message}. Retrying in ${delay}ms...")

            // Sleep with interrupt handling
            try {
                Thread.sleep(delay)
            } catch (ie: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
    }

    return RetryResult.Failure(lastException ?: Exception("Unknown error"), attempt)
}
```

### Delay Calculation

```kotlin
private fun calculateDelay(attemptNumber: Int): Long {
    // Exponential: initialDelay × multiplier^(attempt-1)
    val exponentialDelay = initialDelayMs * backoffMultiplier.pow(attemptNumber - 1).toLong()

    // Cap at maximum to prevent absurd delays
    return min(exponentialDelay, maxDelayMs)
}
```

**Example with defaults** (`initial=5s`, `multiplier=2.0`, `max=300s`):

| Attempt | Calculation | Delay |
|---------|-------------|-------|
| 1 | 5000 × 2^0 = 5000 | 5s |
| 2 | 5000 × 2^1 = 10000 | 10s |
| 3 | 5000 × 2^2 = 20000 | 20s |
| 4 | 5000 × 2^3 = 40000 | 40s |
| 5 | 5000 × 2^4 = 80000 | 80s |
| 6 | 5000 × 2^5 = 160000 | 160s |
| 7 | 5000 × 2^6 = 320000 | **300s** (capped) |

## Interrupt Handling

Retry operations can be interrupted (e.g., server shutdown):

```kotlin
try {
    Thread.sleep(delay)
} catch (ie: InterruptedException) {
    logger.warning("Retry sleep interrupted, aborting retry attempts")
    Thread.currentThread().interrupt()  // Preserve interrupt status
    break
}
```

**Why preserve interrupt status?** Higher-level code may need to know the thread was interrupted. Swallowing the interrupt silently can cause issues during graceful shutdown.

## Sealed Class Result Types

Like the Circuit Breaker, RetryManager uses sealed classes instead of exceptions:

```kotlin
sealed class RetryResult<out T> {
    data class Success<T>(val value: T) : RetryResult<T>()
    data class Failure(val lastException: Exception, val attempts: Int) : RetryResult<Nothing>()
}
```

**Usage**:
```kotlin
val result = retryManager.executeWithRetry("Connect to Discord") {
    jdaBuilder.build().awaitReady()
}

when (result) {
    is RetryResult.Success -> {
        jda = result.value
        logger.info("Connected!")
    }
    is RetryResult.Failure -> {
        logger.severe("Failed after ${result.attempts} attempts: ${result.lastException.message}")
        // Continue in degraded mode
    }
}
```

## Configuration

```yaml
discord:
  retry:
    enabled: true
    max-attempts: 5
    initial-delay-seconds: 5
    max-delay-seconds: 300
    backoff-multiplier: 2.0
```

### Configuration Pattern

```kotlin
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
```

## Usage in AMSSync

### Discord Connection

```kotlin
// In AMSSyncPlugin.onEnable()
private fun connectToDiscord() {
    val retryConfig = RetryConfig(
        enabled = config.getBoolean("discord.retry.enabled", true),
        maxAttempts = config.getInt("discord.retry.max-attempts", 5),
        // ...
    )

    if (retryConfig.enabled) {
        val retryManager = retryConfig.toRetryManager(logger)

        val result = retryManager.executeWithRetry("Discord connection") {
            discordManager.connect()
        }

        when (result) {
            is RetryResult.Success -> logger.info("Discord connected!")
            is RetryResult.Failure -> {
                logger.severe("Discord connection failed - running in degraded mode")
                // Plugin continues without Discord
            }
        }
    } else {
        // Direct connection without retry
        discordManager.connect()
    }
}
```

## Combining with Other Patterns

### Retry + Circuit Breaker

These patterns complement each other:

```
Request
    │
    ▼
┌─────────────┐     Rejected (fast)     ┌─────────────┐
│   Circuit   │◄────────────────────────│   Already   │
│   Breaker   │                         │    Open     │
└──────┬──────┘                         └─────────────┘
       │ Allowed
       ▼
┌─────────────┐
│    Retry    │──── Attempt 1 ────►  Discord
│   Manager   │◄──── Fail ──────────┘
│             │
│             │──── Wait + Attempt 2 ─► Discord
│             │◄──── Fail ──────────┘
│             │
│             │──── Wait + Attempt 3 ─► Discord
└─────────────┘◄──── Success ─────────┘
       │
       ▼
    Return
```

**Flow**:
1. Circuit Breaker checks if Discord is healthy
2. If healthy, Retry Manager handles transient failures
3. If retry exhausted, failure feeds back to Circuit Breaker

### Retry + Timeout

```kotlin
// Wrap timeout around each retry attempt
retryManager.executeWithRetry("Discord connect") {
    timeoutManager.executeWithTimeout("connection") {
        jdaBuilder.build().awaitReady()
    }
}
```

## When to Use Retry

**Good use cases**:
- Network connections (Discord, HTTP, database)
- Operations that fail due to transient conditions
- Startup operations where waiting is acceptable

**Not good for**:
- User-facing requests (users expect fast response)
- Operations that fail due to bad input
- Operations where retry can cause duplicates

## Testing

```kotlin
class RetryManagerTest : DescribeSpec({
    describe("exponential backoff") {
        it("calculates delays correctly") {
            val manager = RetryManager(
                maxAttempts = 5,
                initialDelayMs = 1000L,
                maxDelayMs = 10000L,
                backoffMultiplier = 2.0,
                logger = mockLogger
            )

            // Test via reflection or expose for testing
            manager.calculateDelay(1) shouldBe 1000L
            manager.calculateDelay(2) shouldBe 2000L
            manager.calculateDelay(3) shouldBe 4000L
            manager.calculateDelay(4) shouldBe 8000L
            manager.calculateDelay(5) shouldBe 10000L  // Capped
        }
    }

    describe("retry execution") {
        it("succeeds on third attempt") {
            var attempts = 0
            val result = retryManager.executeWithRetry("test") {
                attempts++
                if (attempts < 3) throw Exception("fail")
                "success"
            }

            result shouldBe RetryResult.Success("success")
            attempts shouldBe 3
        }
    }
})
```

## Alternatives and Variations

### Jitter

Add randomness to prevent thundering herd:

```kotlin
val jitter = Random.nextLong(0, exponentialDelay / 4)
return min(exponentialDelay + jitter, maxDelayMs)
```

### Linear Backoff

For less aggressive growth:

```kotlin
val linearDelay = initialDelayMs * attemptNumber
```

### Decorrelated Jitter (AWS-style)

```kotlin
var sleep = initialDelayMs
fun nextDelay(): Long {
    sleep = min(maxDelayMs, Random.nextLong(initialDelayMs, sleep * 3))
    return sleep
}
```

## Related Patterns

- [Circuit Breaker](circuit-breaker.md) - Fail fast when retries won't help
- [Timeout Protection](timeout-protection.md) - Limit how long each attempt takes
- [Sealed Results](sealed-results.md) - The result type pattern used here

## Further Reading

- [AWS Architecture Blog - Exponential Backoff and Jitter](https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/)
- [Google Cloud - Retry Strategy](https://cloud.google.com/storage/docs/retry-strategy)
