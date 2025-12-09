# Error Handling

**Complexity**: Intermediate
**Key File**: [`exceptions/AMSSyncExceptions.kt`](../../src/main/kotlin/io/github/darinc/amssync/exceptions/AMSSyncExceptions.kt)

## Overview

AMSSync uses a sealed class exception hierarchy for type-safe, exhaustive error handling. This ensures all error cases are explicitly handled at compile time.

## Exception Hierarchy

```
AMSSyncException (sealed)
├── DiscordConnectionException (sealed)
│   ├── DiscordAuthenticationException
│   ├── DiscordTimeoutException
│   └── DiscordNetworkException
├── DiscordApiException (sealed)
│   ├── DiscordRateLimitException
│   ├── DiscordPermissionException
│   ├── DiscordCommandRegistrationException
│   └── CircuitBreakerOpenException
├── McmmoQueryException (sealed)
│   ├── LeaderboardTimeoutException
│   ├── PlayerDataNotFoundException
│   └── InvalidSkillException
├── UserMappingException (sealed)
│   ├── DuplicateMappingException
│   ├── MappingNotFoundException
│   └── InvalidDiscordIdException
└── ConfigurationException (sealed)
    ├── MissingConfigurationException
    ├── InvalidConfigurationException
    ├── InvalidBotTokenException
    └── InvalidGuildIdException
```

## Why Sealed Classes?

### Compile-Time Exhaustiveness

```kotlin
// Compiler ensures all cases are handled
when (exception) {
    is DiscordAuthenticationException -> handleAuth()
    is DiscordTimeoutException -> handleTimeout()
    is DiscordNetworkException -> handleNetwork()
    // ERROR if missing any DiscordConnectionException subclass!
}
```

### Hierarchical Handling

Handle at different granularities:

```kotlin
try {
    connectToDiscord()
} catch (e: DiscordAuthenticationException) {
    // Handle specific: bad token
} catch (e: DiscordConnectionException) {
    // Handle category: any connection issue
} catch (e: AMSSyncException) {
    // Handle all plugin errors
}
```

## Exception Categories

### Discord Connection Exceptions

Occur during initial connection:

```kotlin
sealed class DiscordConnectionException(message: String, cause: Throwable? = null)
    : AMSSyncException(message, cause)

class DiscordAuthenticationException(
    message: String = "Invalid Discord bot token or authentication failed",
    cause: Throwable? = null
) : DiscordConnectionException(message, cause)

class DiscordTimeoutException(
    val timeoutMs: Long,
    message: String = "Discord operation exceeded timeout (${timeoutMs}ms)",
    cause: Throwable? = null
) : DiscordConnectionException(message, cause)

class DiscordNetworkException(
    message: String,
    cause: Throwable? = null
) : DiscordConnectionException(message, cause)
```

### Discord API Exceptions

Occur during API operations after connection:

```kotlin
class DiscordRateLimitException(
    val retryAfterMs: Long,  // When to retry
    message: String = "Rate limit exceeded (retry after ${retryAfterMs}ms)"
) : DiscordApiException(message)

class DiscordPermissionException(
    val requiredPermission: String,  // Missing permission
    message: String = "Missing Discord permission: $requiredPermission"
) : DiscordApiException(message)

class CircuitBreakerOpenException(
    message: String = "Circuit breaker is OPEN - requests blocked"
) : DiscordApiException(message)
```

### MCMMO Query Exceptions

Data access failures:

```kotlin
class PlayerDataNotFoundException(
    val playerName: String,
    message: String = "Player not found or has no MCMMO data: $playerName"
) : McmmoQueryException(message)

class InvalidSkillException(
    val skillName: String,
    val validSkills: List<String>,
    message: String = "Invalid skill: $skillName (valid: ${validSkills.joinToString()})"
) : McmmoQueryException(message)
```

### Configuration Exceptions

Startup validation failures:

```kotlin
class InvalidBotTokenException(
    val maskedToken: String,  // First 10 chars for debugging
    message: String = "Invalid token format: $maskedToken..."
) : ConfigurationException(message)

class InvalidGuildIdException(
    val guildId: String,
    message: String = "Invalid guild ID: '$guildId' (must be 17-19 digits)"
) : ConfigurationException(message)
```

## Rich Exception Data

Exceptions carry contextual data:

```kotlin
// Rate limit with retry timing
class DiscordRateLimitException(
    val retryAfterMs: Long,
    ...
)

// Usage
catch (e: DiscordRateLimitException) {
    delay(e.retryAfterMs)
    retry()
}

// Invalid skill with valid options
class InvalidSkillException(
    val skillName: String,
    val validSkills: List<String>,
    ...
)

// Usage
catch (e: InvalidSkillException) {
    reply("Unknown skill '${e.skillName}'. Try: ${e.validSkills.joinToString()}")
}
```

## Handling Patterns

### Command Handlers

```kotlin
fun handleMcStats(event: SlashCommandInteractionEvent, playerName: String) {
    try {
        val stats = mcmmoApiWrapper.getPlayerStats(playerName)
        // ... build and send response

    } catch (e: PlayerDataNotFoundException) {
        event.reply("Player '${e.playerName}' not found or has no MCMMO stats")
            .setEphemeral(true).queue()

    } catch (e: McmmoQueryException) {
        // Catch any MCMMO error
        event.reply("Error fetching stats: ${e.message}")
            .setEphemeral(true).queue()

    } catch (e: Exception) {
        // Unexpected error
        logger.severe("Unexpected error in mcstats: ${e.message}")
        event.reply("An unexpected error occurred")
            .setEphemeral(true).queue()
    }
}
```

### Connection Logic

```kotlin
fun connectToDiscord() {
    try {
        jdaBuilder.build().awaitReady()

    } catch (e: DiscordAuthenticationException) {
        logger.severe("Invalid bot token - check config.yml")
        throw e  // Can't recover from bad token

    } catch (e: DiscordTimeoutException) {
        logger.warning("Connection timed out after ${e.timeoutMs}ms")
        // May retry

    } catch (e: DiscordNetworkException) {
        logger.warning("Network error: ${e.message}")
        // May retry
    }
}
```

### Exhaustive Handling

```kotlin
fun handleConnectionError(e: DiscordConnectionException) {
    when (e) {
        is DiscordAuthenticationException -> {
            logger.severe("Auth failed - invalid token")
            // Don't retry
        }
        is DiscordTimeoutException -> {
            logger.warning("Timeout after ${e.timeoutMs}ms")
            // Retry with backoff
        }
        is DiscordNetworkException -> {
            logger.warning("Network error: ${e.message}")
            // Retry with backoff
        }
        // Compiler ensures all cases handled!
    }
}
```

## Creating New Exceptions

### Adding to Existing Category

```kotlin
// Add new MCMMO exception
class SkillCapReachedException(
    val skillName: String,
    val currentLevel: Int,
    val maxLevel: Int,
    message: String = "$skillName is at max level ($maxLevel)"
) : McmmoQueryException(message)
```

### Creating New Category

```kotlin
// New category for rate limiting
sealed class RateLimitException(
    message: String,
    cause: Throwable? = null
) : AMSSyncException(message, cause)

class UserRateLimitException(
    val userId: String,
    val cooldownMs: Long
) : RateLimitException("User $userId rate limited for ${cooldownMs}ms")

class GlobalRateLimitException(
    val retryAfterMs: Long
) : RateLimitException("Global rate limit, retry after ${retryAfterMs}ms")
```

## Logging Integration

```kotlin
fun logException(e: AMSSyncException) {
    when (e) {
        is DiscordConnectionException -> logger.severe("Connection: ${e.message}")
        is DiscordApiException -> logger.warning("API: ${e.message}")
        is McmmoQueryException -> logger.info("Query: ${e.message}")
        is UserMappingException -> logger.info("Mapping: ${e.message}")
        is ConfigurationException -> logger.severe("Config: ${e.message}")
    }
}
```

## Metrics Integration

```kotlin
fun recordException(e: AMSSyncException) {
    val errorType = e::class.simpleName ?: "Unknown"
    errorMetrics.recordError(errorType)

    // Category-specific tracking
    when (e) {
        is DiscordApiException -> errorMetrics.recordDiscordApiFailure(errorType)
        is McmmoQueryException -> errorMetrics.recordQueryFailure(errorType)
        // ...
    }
}
```

## User-Facing Messages

Transform technical exceptions to user-friendly messages:

```kotlin
fun getUserMessage(e: AMSSyncException): String {
    return when (e) {
        is PlayerDataNotFoundException ->
            "Player '${e.playerName}' not found. Make sure they've played on this server."

        is InvalidSkillException ->
            "Unknown skill '${e.skillName}'. Valid skills: ${e.validSkills.take(5).joinToString()}"

        is CircuitBreakerOpenException ->
            "Discord is temporarily unavailable. Please try again later."

        is DiscordRateLimitException ->
            "Too many requests. Please wait ${e.retryAfterMs / 1000} seconds."

        else -> "An error occurred. Please try again."
    }
}
```

## Testing Exceptions

```kotlin
class ExceptionTest : DescribeSpec({
    describe("PlayerDataNotFoundException") {
        it("includes player name in message") {
            val e = PlayerDataNotFoundException("Steve")
            e.playerName shouldBe "Steve"
            e.message shouldContain "Steve"
        }
    }

    describe("InvalidSkillException") {
        it("includes valid skills") {
            val e = InvalidSkillException("invalid", listOf("MINING", "WOODCUTTING"))
            e.skillName shouldBe "invalid"
            e.validSkills shouldContain "MINING"
        }
    }
})
```

## Related Documentation

- [Sealed Results](../patterns/sealed-results.md) - Result types vs exceptions
- [Metrics](metrics.md) - Error tracking
- [MCMMO API](../integrations/mcmmo-api.md) - Query exceptions
