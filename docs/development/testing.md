# Testing

**Complexity**: Intermediate
**Key Files**: [`src/test/kotlin/`](../../src/test/kotlin/)

## Test Framework

AMSSync uses:
- **Kotest** - Kotlin-native test framework with expressive DSL
- **MockK** - Kotlin-native mocking library
- **JUnit 5** - Test runner platform

## Running Tests

```bash
# Run all tests
./gradlew test

# Run with verbose output
./gradlew test --info

# Run specific test class
./gradlew test --tests "CircuitBreakerTest"

# Run specific test
./gradlew test --tests "CircuitBreakerTest.CLOSED state*"
```

Test report: `build/reports/tests/test/index.html`

## Test Structure

### Kotest DescribeSpec Style

```kotlin
class CircuitBreakerTest : DescribeSpec({

    val logger = mockk<Logger>(relaxed = true)

    describe("CircuitBreaker") {

        describe("CLOSED state") {

            it("starts in CLOSED state") {
                val cb = CircuitBreaker(logger = logger)
                cb.getState() shouldBe CircuitBreaker.State.CLOSED
            }

            it("executes operations successfully") {
                val cb = CircuitBreaker(logger = logger)
                val result = cb.execute("test") { "success" }

                result.shouldBeInstanceOf<CircuitBreaker.CircuitResult.Success<String>>()
                (result as CircuitBreaker.CircuitResult.Success).value shouldBe "success"
            }
        }

        describe("CLOSED to OPEN transition") {
            it("transitions after reaching failure threshold") {
                val cb = CircuitBreaker(failureThreshold = 3, logger = logger)

                repeat(3) {
                    cb.execute("test") { throw RuntimeException("fail") }
                }

                cb.getState() shouldBe CircuitBreaker.State.OPEN
            }
        }
    }
})
```

### Test Organization

```
src/test/kotlin/
└── io/github/darinc/amssync/
    ├── discord/
    │   ├── CircuitBreakerTest.kt
    │   ├── RateLimiterTest.kt
    │   ├── RetryManagerTest.kt
    │   └── ChatBridgeConfigTest.kt
    ├── linking/
    │   └── UserMappingServiceTest.kt
    ├── validation/
    │   └── ValidatorsTest.kt
    └── image/
        └── SkillCategoriesTest.kt
```

## Mocking with MockK

### Relaxed Mocks

```kotlin
// Relaxed mock returns default values for unmocked methods
val logger = mockk<Logger>(relaxed = true)
```

### Verification

```kotlin
val logger = mockk<Logger>(relaxed = true)
val cb = CircuitBreaker(logger = logger)

cb.execute("test") { throw Exception("fail") }

// Verify logger was called
verify { logger.warning(any()) }
```

### Stubbing

```kotlin
val mockPlugin = mockk<AMSSyncPlugin>()
every { mockPlugin.logger } returns mockLogger
every { mockPlugin.config } returns mockConfig
```

## Kotest Matchers

### Basic Matchers

```kotlin
// Equality
result shouldBe expected
result shouldNotBe other

// Type checking
result.shouldBeInstanceOf<CircuitBreaker.CircuitResult.Success<String>>()

// Nullability
result.shouldBeNull()
result.shouldNotBeNull()

// Collections
list shouldContain element
list shouldHaveSize 3
list.shouldBeEmpty()
```

### Custom Matchers

```kotlin
// Ranges
value shouldBeInRange 1..10

// Strings
text shouldStartWith "prefix"
text shouldContain "substring"

// Exceptions
shouldThrow<IllegalArgumentException> {
    riskyOperation()
}
```

## Test Examples

### Circuit Breaker Test

```kotlin
class CircuitBreakerTest : DescribeSpec({

    describe("OPEN state") {

        it("rejects requests when OPEN") {
            val cb = CircuitBreaker(
                failureThreshold = 1,
                cooldownMs = 10000L,
                logger = mockk(relaxed = true)
            )

            // Open the circuit
            cb.execute("test") { throw RuntimeException("fail") }

            // Should reject
            val result = cb.execute("test") { "should not run" }
            result.shouldBeInstanceOf<CircuitBreaker.CircuitResult.Rejected>()
        }
    }
})
```

### Rate Limiter Test

```kotlin
class RateLimiterTest : DescribeSpec({

    describe("checkRateLimit") {

        it("allows requests within limit") {
            val limiter = RateLimiter(maxRequestsPerMinute = 60)
            val result = limiter.checkRateLimit("user123")
            result shouldBe RateLimitResult.Allowed
        }

        it("applies cooldown after exceeding limit") {
            val limiter = RateLimiter(
                maxRequestsPerMinute = 2,
                penaltyCooldownMs = 5000
            )

            // Make requests up to limit
            repeat(2) { limiter.checkRateLimit("user123") }

            // Third should be rate limited
            val result = limiter.checkRateLimit("user123")
            result.shouldBeInstanceOf<RateLimitResult.BurstLimited>()
        }
    }
})
```

### Config Test

```kotlin
class ChatBridgeConfigTest : DescribeSpec({

    describe("ChatBridgeConfig.getAvatarUrl") {

        val playerName = "Steve"
        val uuid = UUID.randomUUID()

        it("returns mc-heads URL for mc-heads provider") {
            val url = ChatBridgeConfig.getAvatarUrl(playerName, uuid, "mc-heads")
            url shouldBe "https://mc-heads.net/avatar/$playerName/64"
        }

        it("returns crafatar URL with UUID without dashes") {
            val url = ChatBridgeConfig.getAvatarUrl(playerName, uuid, "crafatar")
            val uuidNoDashes = uuid.toString().replace("-", "")
            url shouldBe "https://crafatar.com/avatars/$uuidNoDashes?size=64&overlay"
        }
    }
})
```

### Validation Test

```kotlin
class ValidatorsTest : DescribeSpec({

    describe("Discord ID validation") {

        it("accepts valid 17-digit IDs") {
            isValidDiscordId("12345678901234567") shouldBe true
        }

        it("accepts valid 19-digit IDs") {
            isValidDiscordId("1234567890123456789") shouldBe true
        }

        it("rejects IDs with letters") {
            isValidDiscordId("12345678901234567a") shouldBe false
        }

        it("rejects too short IDs") {
            isValidDiscordId("1234567890123456") shouldBe false
        }
    }
})
```

## Testing Patterns

### Testing State Machines

```kotlin
describe("state transitions") {
    it("CLOSED -> OPEN after threshold") {
        // Arrange
        val cb = CircuitBreaker(failureThreshold = 3, ...)

        // Act - cause failures
        repeat(3) { cb.execute("test") { throw Exception() } }

        // Assert
        cb.getState() shouldBe State.OPEN
    }

    it("OPEN -> HALF_OPEN after cooldown") {
        // Arrange
        val cb = CircuitBreaker(cooldownMs = 100L, ...)
        // ... open circuit ...

        // Act - wait for cooldown
        Thread.sleep(150)
        cb.execute("test") { "success" }

        // Assert - should have transitioned
        cb.getState() shouldBe State.CLOSED  // or HALF_OPEN depending on test
    }
}
```

### Testing Exception Handling

```kotlin
describe("error handling") {
    it("throws PlayerDataNotFoundException for missing player") {
        val wrapper = McmmoApiWrapper(mockPlugin)

        val exception = shouldThrow<PlayerDataNotFoundException> {
            wrapper.getPlayerStats("nonexistent")
        }

        exception.playerName shouldBe "nonexistent"
    }
}
```

### Testing Sealed Classes

```kotlin
describe("CircuitResult handling") {
    it("handles Success") {
        val result: CircuitResult<Int> = CircuitResult.Success(42)

        when (result) {
            is CircuitResult.Success -> result.value shouldBe 42
            else -> fail("Expected Success")
        }
    }

    it("handles Failure with exception details") {
        val ex = RuntimeException("test error")
        val result = CircuitResult.Failure(ex, State.CLOSED)

        result.exception shouldBe ex
        result.state shouldBe State.CLOSED
    }
}
```

## Configuration

### build.gradle.kts

```kotlin
dependencies {
    testImplementation("io.kotest:kotest-runner-junit5:5.8.0")
    testImplementation("io.kotest:kotest-assertions-core:5.8.0")
    testImplementation("io.kotest:kotest-property:5.8.0")
    testImplementation("io.mockk:mockk:1.13.8")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
```

## Best Practices

### 1. Test Behavior, Not Implementation

```kotlin
// Good: Tests behavior
it("rejects requests when circuit is open") {
    // ...
}

// Bad: Tests implementation details
it("sets state to OPEN") {
    // ...
}
```

### 2. Use Descriptive Test Names

```kotlin
// Good
it("transitions to OPEN after 5 failures within 60 second window")

// Bad
it("test1")
```

### 3. Arrange-Act-Assert Pattern

```kotlin
it("applies rate limit penalty") {
    // Arrange
    val limiter = RateLimiter(maxRequestsPerMinute = 1)

    // Act
    limiter.checkRateLimit("user")
    val result = limiter.checkRateLimit("user")

    // Assert
    result.shouldBeInstanceOf<RateLimitResult.BurstLimited>()
}
```

### 4. Test Edge Cases

```kotlin
describe("edge cases") {
    it("handles empty input")
    it("handles maximum values")
    it("handles concurrent access")
    it("handles null values")
}
```

### 5. Isolate Tests

Each test should be independent:

```kotlin
describe("RateLimiter") {
    // Fresh limiter for each test
    lateinit var limiter: RateLimiter

    beforeEach {
        limiter = RateLimiter()
    }

    it("test 1") { /* uses fresh limiter */ }
    it("test 2") { /* uses fresh limiter */ }
}
```

## Related Documentation

- [Building](building.md) - Running tests
- [Circuit Breaker](../patterns/circuit-breaker.md) - What's being tested
- [Error Handling](../observability/error-handling.md) - Exception testing
