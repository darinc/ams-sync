# Contributing Guide

**Complexity**: Beginner
**Key Files**:
- [`build.gradle.kts`](../../build.gradle.kts)
- [`detekt-config.yml`](../../detekt-config.yml)

## Development Prerequisites

### Required

- **Java 21 JDK** - Required for building and running
- **Gradle** - Build tool (wrapper included)
- **Git** - Version control

### MCMMO Local Installation

MCMMO must be installed to local Maven repository:

```bash
# Clone mcMMO
git clone https://github.com/mcMMO-Dev/mcMMO.git
cd mcMMO

# Install to local Maven repo (skip tests for speed)
mvn install -DskipTests
```

This is required because mcMMO isn't published to Maven Central.

## Build Commands

| Command | Description |
|---------|-------------|
| `./gradlew shadowJar` | Build shaded plugin JAR |
| `./gradlew clean build` | Clean and full build |
| `./gradlew test` | Run unit tests |
| `./gradlew detekt` | Run static analysis |
| `./gradlew check` | Run all checks (test + detekt) |

### Build Output

```
build/libs/ams-sync-<version>.jar
```

The shadowJar task creates a "fat JAR" with all dependencies included.

## Testing Locally

1. Build: `./gradlew shadowJar`
2. Copy JAR to test server's `plugins/` directory
3. Start server to generate default config
4. Edit `plugins/AMSSync/config.yml` with real credentials
5. Restart server and verify Discord connection

### Environment Variables

For local testing without editing config:

```bash
export AMS_DISCORD_TOKEN="your-bot-token"
export AMS_GUILD_ID="your-guild-id"
```

## Code Style

### Kotlin Version

- Kotlin 1.9.21
- Java 21 toolchain

### Detekt Rules

Static analysis via Detekt with project-specific thresholds:

| Rule | Threshold | Notes |
|------|-----------|-------|
| Cyclomatic complexity | 25 | Command handlers have many branches |
| Method length | 180 lines | Plugin initialization is long |
| Parameter count | 10 | Graphics functions need many params |
| Functions per class | 22 | Service classes have many methods |
| Line length | 160 chars | Accommodates log messages |
| Return count | 6 | Command handlers use early returns |

### Naming Conventions

```kotlin
// Variables: camelCase
val playerCount = 5

// Private variables: optional underscore prefix
private val _internalState = mutableListOf<String>()

// Classes: PascalCase
class PlayerCountPresence

// Constants: SCREAMING_SNAKE_CASE
const val MAX_RETRY_ATTEMPTS = 5
```

### Disabled Rules

Some rules are intentionally disabled:

- **TooGenericExceptionCaught** - Resilience code catches `Exception` by design
- **SwallowedException** - Timeout/retry code handles without rethrowing
- **MagicNumber** - Too many false positives for colors and time constants

## Architecture Principles

### Sealed Classes for Results

Prefer sealed classes over exceptions for expected outcomes:

```kotlin
// ✅ Preferred
sealed class Result<out T> {
    data class Success<T>(val value: T) : Result<T>()
    data class Failure(val error: Exception) : Result<Nothing>()
}

// Usage forces handling all cases
when (val result = operation()) {
    is Result.Success -> handleSuccess(result.value)
    is Result.Failure -> handleFailure(result.error)
}
```

### Nullable Types for Optional Services

```kotlin
// Required services: lateinit
lateinit var discordManager: DiscordManager

// Optional services: nullable
var circuitBreaker: CircuitBreaker? = null
var timeoutManager: TimeoutManager? = null
```

### Configuration Data Classes

```kotlin
data class SomeConfig(
    val enabled: Boolean,
    val threshold: Int
) {
    // Factory to create runtime object
    fun toService(logger: Logger): SomeService {
        return SomeService(threshold, logger)
    }

    companion object {
        // Factory to load from config
        fun fromConfig(config: FileConfiguration): SomeConfig {
            return SomeConfig(
                enabled = config.getBoolean("feature.enabled", true),
                threshold = config.getInt("feature.threshold", 5)
            )
        }
    }
}
```

### Thread Safety

| Pattern | Use Case |
|---------|----------|
| `ConcurrentHashMap` | Multi-threaded access to collections |
| `AtomicReference` | Thread-safe state changes |
| `AtomicLong/Integer` | Thread-safe counters |
| `synchronized` | Simple mutual exclusion |

```kotlin
// JDA threads + Bukkit main thread = concurrent access
private val cache = ConcurrentHashMap<String, Data>()
private val lastUpdate = AtomicLong(0L)
```

## Testing

### Framework

- **Kotest** - Spec-style testing
- **MockK** - Mocking library

### Test Structure

```kotlin
class SomeServiceTest : DescribeSpec({
    describe("SomeService") {
        it("handles success case") {
            val service = SomeService(...)
            val result = service.doSomething()
            result shouldBe expected
        }

        it("handles failure case") {
            val service = SomeService(...)
            shouldThrow<SomeException> {
                service.doSomethingBad()
            }
        }
    }
})
```

### Mocking Bukkit

```kotlin
val mockPlugin = mockk<AMSSyncPlugin> {
    every { logger } returns mockk(relaxed = true)
    every { config } returns mockk {
        every { getBoolean(any(), any()) } returns true
    }
}
```

## Dependency Management

### Shadow Relocations

Dependencies are relocated to avoid conflicts:

```kotlin
// build.gradle.kts
shadowJar {
    relocate("net.dv8tion", "io.github.darinc.amssync.libs.jda")
    relocate("club.minnced", "io.github.darinc.amssync.libs.webhook")
    relocate("kotlin", "io.github.darinc.amssync.libs.kotlin")
    relocate("kotlinx", "io.github.darinc.amssync.libs.kotlinx")
    // Note: Don't relocate SLF4J - JDA needs original package
}
```

### Dependencies

| Dependency | Purpose | Scope |
|------------|---------|-------|
| paper-api | Minecraft server API | compileOnly |
| mcMMO | MCMMO API | compileOnly |
| JDA | Discord API | implementation |
| discord-webhooks | Webhook support | implementation |
| kotlinx-coroutines | Async utilities | implementation |
| slf4j | Logging for JDA | implementation |

## Pull Request Guidelines

### Before Submitting

1. Run all checks: `./gradlew check`
2. Test locally with a real Minecraft server
3. Verify Discord features work

### PR Description

Include:
- Summary of changes
- Testing performed
- Any breaking changes
- Related issues

### Commit Messages

```
Add rate limiting for Discord commands

- Implement RateLimiter with burst protection
- Add RateLimitResult sealed class
- Integrate with SlashCommandListener
- Add configuration options

Fixes #123
```

## Project Structure

```
src/
├── main/kotlin/io/github/darinc/amssync/
│   ├── AMSSyncPlugin.kt          # Plugin entry point
│   ├── audit/                    # Audit logging
│   ├── commands/                 # Minecraft commands
│   ├── config/                   # Configuration validation
│   ├── discord/                  # Discord integration
│   │   ├── commands/            # Slash command handlers
│   │   └── ...                  # Discord services
│   ├── events/                   # Event listeners
│   ├── exceptions/               # Custom exceptions
│   ├── image/                    # Image generation
│   ├── linking/                  # User mapping
│   ├── mcmmo/                    # MCMMO integration
│   └── metrics/                  # Error metrics
└── test/kotlin/                  # Unit tests
```

## Common Tasks

### Adding a New Discord Command

1. Create handler in `discord/commands/`
2. Register in `DiscordManager.registerSlashCommands()`
3. Route in `SlashCommandListener.onSlashCommandInteraction()`
4. Wrap with circuit breaker if making external calls

### Adding Configuration Options

1. Add to `src/main/resources/config.yml` with defaults
2. Create data class with `fromConfig()` factory
3. Load in `AMSSyncPlugin.onEnable()`
4. Document in README or docs

### Adding a New Resilience Pattern

1. Follow existing patterns (CircuitBreaker, RetryManager)
2. Use sealed class for result types
3. Create configuration data class
4. Make optional (nullable) in plugin
5. Document in `docs/patterns/`

## Related Documentation

- [Architecture Overview](../architecture/overview.md) - System design
- [Initialization Flow](../architecture/initialization.md) - Startup sequence
- [Testing](testing.md) - Test guidelines
- [Building](building.md) - Build details
