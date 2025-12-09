# Circuit Breaker Pattern

**Complexity**: Advanced
**Key File**: [`discord/CircuitBreaker.kt`](../../src/main/kotlin/io/github/darinc/amssync/discord/CircuitBreaker.kt)

## What Problem Does This Solve?

When Discord's API becomes unavailable or slow, naive retry logic can make things worse:
- Threads pile up waiting for timeouts
- Server resources get consumed by failing requests
- When Discord recovers, a flood of requests can overwhelm it again

The **Circuit Breaker** pattern prevents cascading failures by failing fast when a service is known to be unhealthy.

## The Three States

```
                ┌─────────────────────────────────────────────────────┐
                │                                                     │
                ▼                                                     │
         ┌──────────┐                                                 │
         │  CLOSED  │◄────────────────────────────────┐               │
         │ (Normal) │                                 │               │
         └────┬─────┘                                 │               │
              │                                       │               │
              │ failureThreshold                      │               │
              │ failures in                           │ successThreshold
              │ timeWindow                            │ consecutive
              │                                       │ successes
              ▼                                       │
         ┌──────────┐        cooldown          ┌─────┴─────┐
         │   OPEN   │─────────expires─────────►│ HALF_OPEN │
         │(Failing  │                          │ (Testing) │
         │  fast)   │◄─────any failure─────────│           │
         └──────────┘                          └───────────┘
```

### CLOSED (Normal Operation)
- All requests pass through to Discord
- Failures are counted within a sliding time window
- If failures exceed threshold, transitions to OPEN

### OPEN (Failing Fast)
- All requests are **immediately rejected** without attempting Discord
- No network calls made - instant failure response
- After cooldown period, transitions to HALF_OPEN

### HALF_OPEN (Testing Recovery)
- Allows limited requests through to test if Discord recovered
- If requests succeed, transitions to CLOSED
- If any request fails, transitions back to OPEN

## Implementation Deep Dive

### Thread-Safe State Management

```kotlin
// All state uses atomic types for lock-free thread safety
private val state = AtomicReference(State.CLOSED)
private val failureCount = AtomicInteger(0)
private val successCount = AtomicInteger(0)
private val lastFailureTime = AtomicLong(0L)
private val stateChangedTime = AtomicLong(System.currentTimeMillis())
```

**Why Atomics?** Multiple threads (JDA thread pool, Bukkit scheduler) may call the circuit breaker simultaneously. Atomic operations ensure consistent state without explicit locking.

### The Execute Method

```kotlin
fun <T> execute(operationName: String, operation: () -> T): CircuitResult<T> {
    val currentState = state.get()

    // Check if we should test recovery
    if (currentState == State.OPEN) {
        val timeSinceStateChange = System.currentTimeMillis() - stateChangedTime.get()
        if (timeSinceStateChange >= cooldownMs) {
            transitionToHalfOpen()
        } else {
            // Fail fast - no network call made
            return CircuitResult.Rejected(
                State.OPEN,
                "Circuit breaker is OPEN. Retry after ${(cooldownMs - timeSinceStateChange) / 1000}s"
            )
        }
    }

    // Execute the actual operation
    return try {
        val result = operation()
        onSuccess(operationName)
        CircuitResult.Success(result)
    } catch (e: Exception) {
        onFailure(operationName, e)
        CircuitResult.Failure(e, state.get())
    }
}
```

### Atomic State Transitions

```kotlin
private fun transitionToOpen() {
    // compareAndSet ensures only one thread can make this transition
    if (state.compareAndSet(State.CLOSED, State.OPEN) ||
        state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
        stateChangedTime.set(System.currentTimeMillis())
        logger.severe("⚠️ Circuit breaker transitioned to OPEN state")
    }
}
```

**Why `compareAndSet`?** If two threads simultaneously detect threshold exceeded, only one will successfully transition. This prevents duplicate log messages and ensures state consistency.

## Sealed Class Result Types

Instead of throwing exceptions, the circuit breaker returns a sealed class:

```kotlin
sealed class CircuitResult<out T> {
    data class Success<T>(val value: T) : CircuitResult<T>()
    data class Failure(val exception: Exception, val state: State) : CircuitResult<Nothing>()
    data class Rejected(val state: State, val message: String) : CircuitResult<Nothing>()
}
```

**Usage**:
```kotlin
when (val result = circuitBreaker.execute("send message") { channel.sendMessage(msg) }) {
    is CircuitResult.Success -> {
        // Message sent successfully
        val value = result.value
    }
    is CircuitResult.Failure -> {
        // Discord call failed - circuit may open
        logger.warning("Failed: ${result.exception.message}")
    }
    is CircuitResult.Rejected -> {
        // Circuit is open - request not even attempted
        logger.info("Rejected: ${result.message}")
    }
}
```

**Benefits over exceptions**:
1. Compiler enforces handling all cases
2. Clear distinction between "failed" vs "rejected"
3. No forgotten catch blocks
4. Self-documenting code

## Configuration

```yaml
discord:
  circuit-breaker:
    enabled: true
    failure-threshold: 5      # Failures before opening
    time-window-seconds: 60   # Window for counting failures
    cooldown-seconds: 30      # Wait before testing recovery
    success-threshold: 2      # Successes to close circuit
```

### Configuration Loading Pattern

```kotlin
data class CircuitBreakerConfig(
    val enabled: Boolean,
    val failureThreshold: Int,
    val timeWindowSeconds: Int,
    val cooldownSeconds: Int,
    val successThreshold: Int
) {
    // Factory method to create runtime object
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
        // Factory method to load from config
        fun fromConfig(config: ConfigurationSection): CircuitBreakerConfig {
            return CircuitBreakerConfig(
                enabled = config.getBoolean("discord.circuit-breaker.enabled", true),
                failureThreshold = config.getInt("discord.circuit-breaker.failure-threshold", 5),
                // ...
            )
        }
    }
}
```

This pattern separates concerns:
- `fromConfig()` handles YAML parsing with defaults
- `toCircuitBreaker()` handles object construction
- Both are easily testable without file I/O

## Usage in AMSSync

### Wrapping Discord Operations

```kotlin
// In DiscordApiWrapper
fun <T> executeWithCircuitBreaker(operationName: String, operation: () -> T): T? {
    val result = circuitBreaker.execute(operationName, operation)

    return when (result) {
        is CircuitBreaker.CircuitResult.Success -> result.value
        is CircuitBreaker.CircuitResult.Failure -> {
            errorMetrics.recordDiscordApiFailure()
            null
        }
        is CircuitBreaker.CircuitResult.Rejected -> {
            errorMetrics.recordCircuitBreakerRejection()
            null
        }
    }
}
```

### In Command Handlers

```kotlin
// In SlashCommandListener
override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
    // Check circuit breaker before any processing
    if (plugin.circuitBreaker?.getState() == CircuitBreaker.State.OPEN) {
        event.reply("Discord services temporarily unavailable")
            .setEphemeral(true)
            .queue()
        return
    }

    // ... handle command
}
```

## Metrics and Observability

```kotlin
data class CircuitBreakerMetrics(
    val state: CircuitBreaker.State,
    val failureCount: Int,
    val successCount: Int,
    val timeSinceStateChangeMs: Long,
    val failureThreshold: Int,
    val successThreshold: Int
)

// Usage
val metrics = circuitBreaker.getMetrics()
logger.info("Circuit: $metrics")
// Output: CircuitBreakerMetrics(state=CLOSED, failures=2/5, successes=0/2, timeSinceStateChange=45000ms)
```

## When to Use Circuit Breaker

**Good use cases**:
- External API calls (Discord, HTTP services)
- Database operations that can fail
- Any operation with potential for sustained failures

**Not needed for**:
- In-memory operations
- Local file access (use timeout instead)
- Operations that fail independently (no cascading risk)

## Testing Considerations

```kotlin
class CircuitBreakerTest : DescribeSpec({
    describe("Circuit Breaker state transitions") {
        it("opens after failure threshold") {
            val breaker = CircuitBreaker(
                failureThreshold = 3,
                timeWindowMs = 60000L,
                cooldownMs = 1000L,
                successThreshold = 1,
                logger = mockLogger
            )

            // Trigger failures
            repeat(3) {
                breaker.execute("test") { throw Exception("fail") }
            }

            breaker.getState() shouldBe CircuitBreaker.State.OPEN
        }
    }
})
```

## Related Patterns

- [Retry with Backoff](retry-backoff.md) - Complements circuit breaker for transient failures
- [Timeout Protection](timeout-protection.md) - Prevents hanging before circuit breaker triggers
- [Sealed Results](sealed-results.md) - The result type pattern used here

## Further Reading

- [Martin Fowler - Circuit Breaker](https://martinfowler.com/bliki/CircuitBreaker.html)
- [Microsoft - Circuit Breaker Pattern](https://docs.microsoft.com/en-us/azure/architecture/patterns/circuit-breaker)
- [Release It! by Michael Nygard](https://pragprog.com/titles/mnee2/release-it-second-edition/) - Origin of the pattern
