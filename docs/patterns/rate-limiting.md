# Rate Limiting

**Complexity**: Intermediate
**Key File**: [`discord/RateLimiter.kt`](../../src/main/kotlin/io/github/darinc/amssync/discord/RateLimiter.kt)

## What Problem Does This Solve?

Without rate limiting, a malicious or overly enthusiastic user can:
- Spam Discord commands, degrading experience for everyone
- Trigger Discord's own rate limits, affecting the entire bot
- Consume server resources processing repeated requests

Rate limiting protects against abuse while allowing normal usage.

## Design Overview

AMSSync uses a **burst + penalty cooldown** model:

```
Normal Usage                  Abuse Detected
     │                             │
     ▼                             ▼
┌──────────┐   exceeded    ┌───────────────┐
│  Burst   │──────────────►│   Penalty     │
│  Window  │               │   Cooldown    │
│ (60 req/ │◄──────────────│  (10 sec)     │
│  minute) │   cooldown    └───────────────┘
└──────────┘   expires
```

**Why this model?**
- Burst window allows normal usage patterns
- Penalty cooldown discourages abuse more effectively than per-request delays
- Clear feedback to users: "You're sending too fast, wait 10 seconds"

## Sealed Result Type

```kotlin
sealed class RateLimitResult {
    /** Request is allowed to proceed */
    object Allowed : RateLimitResult()

    /** User in penalty cooldown - must wait */
    data class Cooldown(val remainingMs: Long) : RateLimitResult() {
        val remainingSeconds: Double get() = remainingMs / 1000.0
    }

    /** Burst limit exceeded - penalty applied */
    data class BurstLimited(val retryAfterMs: Long) : RateLimitResult() {
        val retryAfterSeconds: Double get() = retryAfterMs / 1000.0
    }
}
```

## Usage Examples

### Basic Rate Limit Check

```kotlin
when (val result = rateLimiter.checkRateLimit(userId)) {
    is RateLimitResult.Allowed -> {
        // Process the request
        handleCommand(event)
    }
    is RateLimitResult.Cooldown -> {
        event.reply("You're in timeout. Wait ${result.remainingSeconds.toInt()} seconds.")
            .setEphemeral(true)
            .queue()
    }
    is RateLimitResult.BurstLimited -> {
        event.reply("Too many requests! Wait ${result.retryAfterSeconds.toInt()} seconds.")
            .setEphemeral(true)
            .queue()
    }
}
```

### Integration with Slash Commands

```kotlin
// In SlashCommandListener
override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
    val rateLimiter = plugin.rateLimiter

    if (rateLimiter != null) {
        val userId = event.user.id
        when (val result = rateLimiter.checkRateLimit(userId)) {
            is RateLimitResult.Allowed -> { /* continue */ }
            is RateLimitResult.Cooldown -> {
                event.reply("Please wait ${result.remainingSeconds.toInt()}s")
                    .setEphemeral(true)
                    .queue()
                return
            }
            is RateLimitResult.BurstLimited -> {
                event.reply("Rate limit exceeded. Wait ${result.retryAfterSeconds.toInt()}s")
                    .setEphemeral(true)
                    .queue()
                return
            }
        }
    }

    // Process command...
}
```

## Implementation Details

### Thread-Safe State

```kotlin
class RateLimiter(
    private val penaltyCooldownMs: Long = 10000L,
    private val maxRequests: Int = 60,
    private val windowMs: Long = 60000L,
    private val logger: Logger? = null
) {
    // Thread-safe maps for concurrent access from JDA threads
    private val penaltyExpiry = ConcurrentHashMap<String, Long>()
    private val requestTimestamps = ConcurrentHashMap<String, MutableList<Long>>()
}
```

### Rate Limit Algorithm

```kotlin
fun checkRateLimit(userId: String): RateLimitResult {
    val now = System.currentTimeMillis()

    // 1. Check if user is in penalty cooldown
    val expiry = penaltyExpiry[userId]
    if (expiry != null && now < expiry) {
        return RateLimitResult.Cooldown(expiry - now)
    } else if (expiry != null) {
        penaltyExpiry.remove(userId)  // Expired, remove it
    }

    // 2. Check burst limit
    val timestamps = requestTimestamps.getOrPut(userId) { mutableListOf() }

    synchronized(timestamps) {
        // Remove timestamps outside the window
        val windowStart = now - windowMs
        timestamps.removeIf { it < windowStart }

        if (timestamps.size >= maxRequests) {
            // Apply penalty cooldown
            penaltyExpiry[userId] = now + penaltyCooldownMs
            return RateLimitResult.BurstLimited(penaltyCooldownMs)
        }

        // Record this request
        timestamps.add(now)
    }

    return RateLimitResult.Allowed
}
```

### Memory Cleanup

To prevent memory leaks from inactive users:

```kotlin
fun cleanup() {
    val now = System.currentTimeMillis()
    val windowStart = now - windowMs

    // Remove expired penalties
    penaltyExpiry.entries.removeIf { it.value < now }

    // Clean up old timestamps
    requestTimestamps.entries.removeIf { (_, timestamps) ->
        synchronized(timestamps) {
            timestamps.removeIf { it < windowStart }
            timestamps.isEmpty()
        }
    }
}
```

Call periodically (e.g., every minute) via Bukkit scheduler.

## Configuration

```yaml
rate-limiting:
  enabled: true
  penalty-cooldown-ms: 10000      # 10 second cooldown after exceeding limit
  max-requests-per-minute: 60     # Allow 60 requests per minute
```

### Configuration Data Class

```kotlin
data class RateLimiterConfig(
    val enabled: Boolean = true,
    val penaltyCooldownMs: Long = 10000L,
    val maxRequestsPerMinute: Int = 60
) {
    fun toRateLimiter(logger: Logger? = null): RateLimiter {
        return RateLimiter(
            penaltyCooldownMs = penaltyCooldownMs,
            maxRequests = maxRequestsPerMinute,
            windowMs = 60000L,
            logger = logger
        )
    }
}
```

## Utility Methods

### Query Current State

```kotlin
// Check how many requests a user has made in current window
val count = rateLimiter.getRequestCount(userId)
logger.info("User $userId has made $count requests this minute")
```

### Administrative Reset

```kotlin
// Reset specific user (admin command)
rateLimiter.reset(userId)

// Reset all users (plugin reload)
rateLimiter.resetAll()
```

## Recommended Settings

| Server Type | Max Requests | Penalty |
|-------------|--------------|---------|
| Small (< 20 players) | 30/min | 10s |
| Medium (20-100) | 60/min | 10s |
| Large (100+) | 60/min | 30s |

**Notes**:
- Discord's own rate limits are ~50 requests per second per endpoint
- These settings are per-user, not global
- Penalty should be noticeable but not frustrating

## Audit Logging Integration

Rate limit events can be logged for security monitoring:

```kotlin
if (result is RateLimitResult.BurstLimited) {
    auditLogger.logSecurityEvent(
        event = SecurityEvent.RATE_LIMITED,
        actor = userId,
        actorType = ActorType.DISCORD_USER,
        details = mapOf(
            "command" to commandName,
            "requestCount" to rateLimiter.getRequestCount(userId)
        )
    )
}
```

## Testing

```kotlin
class RateLimiterTest : DescribeSpec({
    describe("RateLimiter") {
        it("allows requests within limit") {
            val limiter = RateLimiter(maxRequests = 3)

            repeat(3) {
                limiter.checkRateLimit("user1") shouldBe RateLimitResult.Allowed
            }
        }

        it("applies penalty after exceeding limit") {
            val limiter = RateLimiter(maxRequests = 3, penaltyCooldownMs = 1000L)

            repeat(3) { limiter.checkRateLimit("user1") }

            val result = limiter.checkRateLimit("user1")
            result.shouldBeInstanceOf<RateLimitResult.BurstLimited>()
        }

        it("returns cooldown while in penalty") {
            val limiter = RateLimiter(maxRequests = 1, penaltyCooldownMs = 5000L)

            limiter.checkRateLimit("user1")  // First allowed
            limiter.checkRateLimit("user1")  // Triggers penalty

            val result = limiter.checkRateLimit("user1")
            result.shouldBeInstanceOf<RateLimitResult.Cooldown>()
        }
    }
})
```

## Related Patterns

- [Circuit Breaker](circuit-breaker.md) - System-wide protection
- [Audit Logging](../observability/audit-logging.md) - Security event tracking
- [Sealed Results](sealed-results.md) - Result type pattern
