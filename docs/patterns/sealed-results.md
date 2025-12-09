# Sealed Class Result Types

**Complexity**: Intermediate
**Key File**: [`exceptions/AMSSyncExceptions.kt`](../../src/main/kotlin/io/github/darinc/amssync/exceptions/AMSSyncExceptions.kt)

## What Problem Does This Solve?

Traditional exception-based error handling has several issues:

```kotlin
// Traditional approach
try {
    val result = circuitBreaker.execute { discordApi.call() }
    // handle success
} catch (e: CircuitBreakerOpenException) {
    // handle open circuit
} catch (e: DiscordTimeoutException) {
    // handle timeout
} catch (e: Exception) {
    // handle other failures
}
```

**Problems**:
1. Easy to forget catch blocks
2. No compile-time enforcement of handling all cases
3. Exception types can be added without compiler warning
4. Unclear distinction between "operation failed" vs "operation rejected"

## The Sealed Class Solution

Kotlin's sealed classes provide exhaustive pattern matching:

```kotlin
sealed class CircuitResult<out T> {
    data class Success<T>(val value: T) : CircuitResult<T>()
    data class Failure(val exception: Exception, val state: State) : CircuitResult<Nothing>()
    data class Rejected(val state: State, val message: String) : CircuitResult<Nothing>()
}
```

**Usage**:
```kotlin
when (val result = circuitBreaker.execute("op") { ... }) {
    is CircuitResult.Success -> handleSuccess(result.value)
    is CircuitResult.Failure -> handleFailure(result.exception)
    is CircuitResult.Rejected -> handleRejection(result.message)
    // Compiler ERROR if any case is missing!
}
```

## Why Sealed Classes Work

### Compile-Time Exhaustiveness

```kotlin
// This won't compile - missing case!
when (val result = circuitBreaker.execute("op") { ... }) {
    is CircuitResult.Success -> {}
    is CircuitResult.Failure -> {}
    // ERROR: 'when' expression must be exhaustive,
    // add necessary 'is Rejected' branch
}
```

### Known Subclasses at Compile Time

Sealed classes can only be extended in the same file. The compiler knows all possible types:

```kotlin
// In AMSSyncExceptions.kt
sealed class AMSSyncException(message: String, cause: Throwable? = null) : Exception(message, cause)

// These are the ONLY possible subclasses
sealed class DiscordConnectionException(...) : AMSSyncException(...)
sealed class DiscordApiException(...) : AMSSyncException(...)
sealed class McmmoQueryException(...) : AMSSyncException(...)
sealed class UserMappingException(...) : AMSSyncException(...)
sealed class ConfigurationException(...) : AMSSyncException(...)
```

## Exception Hierarchy in AMSSync

### Top-Level Categories

```
AMSSyncException
├── DiscordConnectionException
│   ├── DiscordAuthenticationException
│   ├── DiscordTimeoutException
│   └── DiscordNetworkException
├── DiscordApiException
│   ├── DiscordRateLimitException
│   ├── DiscordPermissionException
│   ├── DiscordCommandRegistrationException
│   └── CircuitBreakerOpenException
├── McmmoQueryException
│   ├── LeaderboardTimeoutException
│   ├── PlayerDataNotFoundException
│   └── InvalidSkillException
├── UserMappingException
│   ├── DuplicateMappingException
│   ├── MappingNotFoundException
│   └── InvalidDiscordIdException
└── ConfigurationException
    ├── MissingConfigurationException
    ├── InvalidConfigurationException
    ├── InvalidBotTokenException
    └── InvalidGuildIdException
```

### Rich Exception Data

Each exception carries relevant context:

```kotlin
class DiscordRateLimitException(
    val retryAfterMs: Long,  // Specific data for this error type
    message: String = "Rate limit exceeded (retry after ${retryAfterMs}ms)",
    cause: Throwable? = null
) : DiscordApiException(message, cause)

class InvalidSkillException(
    val skillName: String,
    val validSkills: List<String>,  // Helps user understand what went wrong
    message: String = "Invalid skill: $skillName (valid: ${validSkills.joinToString()})"
) : McmmoQueryException(message)
```

**Usage**:
```kotlin
when (val e = result.exception) {
    is DiscordRateLimitException -> {
        delay(e.retryAfterMs)  // Access specific data
        retry()
    }
    is InvalidSkillException -> {
        reply("Unknown skill. Try: ${e.validSkills.joinToString()}")
    }
    // ...
}
```

## Pattern: Result Types for Operations

### CircuitBreaker Results

```kotlin
sealed class CircuitResult<out T> {
    data class Success<T>(val value: T) : CircuitResult<T>()
    data class Failure(val exception: Exception, val state: State) : CircuitResult<Nothing>()
    data class Rejected(val state: State, val message: String) : CircuitResult<Nothing>()
}
```

**Key distinction**: `Failure` means we tried and failed. `Rejected` means we didn't even try (circuit open).

### RetryManager Results

```kotlin
sealed class RetryResult<out T> {
    data class Success<T>(val value: T) : RetryResult<T>()
    data class Failure(val lastException: Exception, val attempts: Int) : RetryResult<Nothing>()
}
```

**Includes metadata**: `attempts` tells you how hard we tried.

### TimeoutManager Results

```kotlin
sealed class TimeoutResult<out T> {
    data class Success<T>(val value: T, val durationMs: Long) : TimeoutResult<T>()
    data class Timeout(val timeoutMs: Long) : TimeoutResult<Nothing>()
    data class Failure(val exception: Exception) : TimeoutResult<Nothing>()
}
```

**Three distinct outcomes**: Success, timeout, or exception.

### RateLimiter Results

```kotlin
sealed class RateLimitResult {
    object Allowed : RateLimitResult()
    data class Cooldown(val remainingMs: Long) : RateLimitResult()
    data class BurstLimited(val limit: Int) : RateLimitResult()
}
```

**No value needed**: Just checking if allowed.

## Handling Patterns

### Full Exhaustive Handling

```kotlin
fun handleCircuitResult(result: CircuitResult<String>) {
    when (result) {
        is CircuitResult.Success -> {
            println("Got: ${result.value}")
        }
        is CircuitResult.Failure -> {
            logger.warning("Failed: ${result.exception.message}")
            // Maybe retry later
        }
        is CircuitResult.Rejected -> {
            logger.info("Service unavailable: ${result.message}")
            // Fast fail to user
        }
    }
}
```

### Extracting Success Value

```kotlin
// Using when as expression
val value: String? = when (val result = operation()) {
    is CircuitResult.Success -> result.value
    is CircuitResult.Failure -> null
    is CircuitResult.Rejected -> null
}

// Or with helper extension
fun <T> CircuitResult<T>.getOrNull(): T? = when (this) {
    is CircuitResult.Success -> value
    else -> null
}

val value = operation().getOrNull()
```

### Mapping Results

```kotlin
fun <T, R> CircuitResult<T>.map(transform: (T) -> R): CircuitResult<R> = when (this) {
    is CircuitResult.Success -> CircuitResult.Success(transform(value))
    is CircuitResult.Failure -> this
    is CircuitResult.Rejected -> this
}

// Usage
val stringResult: CircuitResult<String> = intResult.map { it.toString() }
```

## Comparison with Alternatives

### vs Exceptions

| Aspect | Exceptions | Sealed Results |
|--------|------------|----------------|
| Compile-time safety | No | Yes |
| Forgetting to handle | Silent bug | Compiler error |
| Performance | Stack trace overhead | Zero overhead |
| Control flow | Non-local jumps | Linear flow |
| Documenting failures | JavaDoc/KDoc | Type signature |

### vs Kotlin Result<T>

```kotlin
// Built-in Result
val result: Result<String> = runCatching { riskyOperation() }
result.fold(
    onSuccess = { println(it) },
    onFailure = { println("Error: $it") }
)
```

**Sealed classes are better when**:
- You have more than success/failure (e.g., `Rejected`)
- You want to carry different data per outcome
- You want domain-specific naming

### vs Arrow's Either<L, R>

Arrow provides functional error handling:
```kotlin
val result: Either<Error, String> = operation()
result.fold(
    ifLeft = { error -> handleError(error) },
    ifRight = { value -> handleSuccess(value) }
)
```

**Sealed classes are better when**:
- You don't want additional dependencies
- You have more than two outcomes
- You want explicit, readable case names

## Best Practices

### 1. Include Relevant Context

```kotlin
// Good: Includes data needed to handle the error
data class Failure(
    val exception: Exception,
    val state: State,           // What state were we in?
    val operationName: String   // What were we doing?
) : CircuitResult<Nothing>()
```

### 2. Use Sealed Hierarchies for Categories

```kotlin
// Two-level hierarchy
sealed class AMSSyncException : Exception()
sealed class DiscordConnectionException : AMSSyncException()
class DiscordAuthenticationException : DiscordConnectionException()
class DiscordTimeoutException : DiscordConnectionException()
```

This allows handling at different granularities:
```kotlin
when (exception) {
    is DiscordAuthenticationException -> // specific
    is DiscordConnectionException -> // category
    is AMSSyncException -> // all plugin errors
}
```

### 3. Keep Result Types Covariant

```kotlin
sealed class Result<out T>  // 'out' makes it covariant
```

This allows:
```kotlin
val stringResult: Result<String> = Result.Success("hello")
val anyResult: Result<Any> = stringResult  // OK due to covariance
```

### 4. Prefer Exhaustive When

```kotlin
// Good: Compiler ensures all cases handled
when (result) {
    is Success -> ...
    is Failure -> ...
    is Rejected -> ...
}

// Avoid: 'else' defeats exhaustiveness checking
when (result) {
    is Success -> ...
    else -> ...  // Won't catch new cases!
}
```

## Testing Sealed Classes

```kotlin
class CircuitResultTest : DescribeSpec({
    describe("CircuitResult handling") {
        it("handles success") {
            val result: CircuitResult<Int> = CircuitResult.Success(42)

            when (result) {
                is CircuitResult.Success -> result.value shouldBe 42
                else -> fail("Expected Success")
            }
        }

        it("handles failure with exception") {
            val exception = RuntimeException("test")
            val result: CircuitResult<Int> = CircuitResult.Failure(exception, State.CLOSED)

            result.exception shouldBe exception
            result.state shouldBe State.CLOSED
        }
    }
})
```

## Related Patterns

- [Circuit Breaker](circuit-breaker.md) - Uses `CircuitResult`
- [Retry with Backoff](retry-backoff.md) - Uses `RetryResult`
- [Error Handling](../observability/error-handling.md) - Exception hierarchy details

## Further Reading

- [Kotlin Sealed Classes](https://kotlinlang.org/docs/sealed-classes.html)
- [Railway Oriented Programming](https://fsharpforfunandprofit.com/rop/) - Functional error handling concepts
- [Effective Kotlin - Error Handling](https://kt.academy/article/ek-exceptions)
