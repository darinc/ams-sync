# Architecture Overview

**Complexity**: Beginner

This document provides a high-level view of AMSSync's architecture, explaining how components interact and why design decisions were made.

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                         AMSSyncPlugin                                │
│                    (Plugin Lifecycle Manager)                        │
└─────────────────────┬───────────────────────────────────────────────┘
                      │
          ┌───────────┼───────────┬───────────────┬─────────────┐
          ▼           ▼           ▼               ▼             ▼
   ┌────────────┐ ┌─────────┐ ┌─────────────┐ ┌─────────┐ ┌──────────┐
   │  Discord   │ │  MCMMO  │ │  Resilience │ │ Image   │ │Observa-  │
   │  Services  │ │Services │ │   Layer     │ │ System  │ │bility    │
   └────────────┘ └─────────┘ └─────────────┘ └─────────┘ └──────────┘
```

## Core Components

### 1. AMSSyncPlugin

**File**: `AMSSyncPlugin.kt`

The central coordinator that:
- Manages plugin lifecycle (`onEnable`, `onDisable`)
- Initializes all services in correct order
- Handles configuration loading
- Coordinates shutdown sequence

```kotlin
class AMSSyncPlugin : JavaPlugin() {
    // Services initialized in onEnable()
    lateinit var discordManager: DiscordManager
    var circuitBreaker: CircuitBreaker? = null  // Optional
    var timeoutManager: TimeoutManager? = null   // Optional
    // ... more services
}
```

**Key Pattern**: Uses `lateinit` for required services, nullable types for optional ones.

### 2. Discord Services

Handles all Discord integration:

| Component | Purpose |
|-----------|---------|
| `DiscordManager` | JDA lifecycle, slash command registration |
| `DiscordApiWrapper` | Circuit breaker integration for API calls |
| `SlashCommandListener` | Routes Discord commands to handlers |
| `ChatBridge` | Two-way message relay |
| `PlayerCountPresence` | Bot status/nickname updates |
| `WebhookManager` | Rich webhook messages |

**Flow**: Discord Event → JDA → Listener → Handler → Response

### 3. MCMMO Services

Interfaces with MCMMO data:

| Component | Purpose |
|-----------|---------|
| `McmmoApiWrapper` | Data access with caching |
| `McMMOEventListener` | Level-up event handling |

**Critical Pattern**: Always use `DatabaseManager.loadPlayerProfile()` for offline players, not `UserManager`.

### 4. Resilience Layer

Production-grade failure handling:

| Component | Pattern | Purpose |
|-----------|---------|---------|
| `CircuitBreaker` | Circuit Breaker | Fail-fast during outages |
| `RetryManager` | Retry with Backoff | Handle transient failures |
| `TimeoutManager` | Timeout | Prevent hanging operations |
| `RateLimiter` | Rate Limiting | Prevent abuse |

**Composition**: These wrap Discord operations in layers:
```
Request → RateLimiter → CircuitBreaker → Timeout → Retry → Discord API
```

### 5. Image System

Visual card generation:

| Component | Purpose |
|-----------|---------|
| `PlayerCardRenderer` | Stats cards |
| `MilestoneCardRenderer` | Celebration cards |
| `AvatarFetcher` | Avatar download/caching |

### 6. Observability

Monitoring and audit:

| Component | Purpose |
|-----------|---------|
| `ErrorMetrics` | Performance tracking, success rates |
| `AuditLogger` | Admin action logging |

## Data Flow

### Discord Command Flow

```
User types /mcstats
        │
        ▼
┌───────────────────┐
│ Discord Gateway   │ ── JDA receives event
└───────────────────┘
        │
        ▼
┌───────────────────┐
│SlashCommandListener│ ── Routes by command name
└───────────────────┘
        │
        ▼
┌───────────────────┐
│  RateLimiter      │ ── Check user rate limit
└───────────────────┘
        │
        ▼
┌───────────────────┐
│  CircuitBreaker   │ ── Check if Discord healthy
└───────────────────┘
        │
        ▼
┌───────────────────┐
│  McmmoApiWrapper  │ ── Fetch player data (cached)
└───────────────────┘
        │
        ▼
┌───────────────────┐
│  Command Handler  │ ── Build response embed/image
└───────────────────┘
        │
        ▼
┌───────────────────┐
│  Discord Reply    │ ── Send to user
└───────────────────┘
```

### Chat Bridge Flow

```
Minecraft Chat Event                    Discord Message Event
        │                                       │
        ▼                                       ▼
┌───────────────┐                      ┌───────────────┐
│ AsyncChatEvent│                      │MessageReceived│
│   (Paper)     │                      │   (JDA)       │
└───────────────┘                      └───────────────┘
        │                                       │
        ▼                                       ▼
┌───────────────┐                      ┌───────────────┐
│  ChatBridge   │◄────────────────────►│  ChatBridge   │
│  onAsyncChat  │                      │onMessageRecvd │
└───────────────┘                      └───────────────┘
        │                                       │
        ▼                                       ▼
┌───────────────┐                      ┌───────────────┐
│Webhook/Channel│                      │Bukkit.broadcast│
│   sendMessage │                      │   (main thread)│
└───────────────┘                      └───────────────┘
```

## Service Initialization Order

Order matters for dependency resolution:

1. **ErrorMetrics** - No dependencies
2. **AuditLogger** - No dependencies
3. **RateLimiter** - No dependencies (optional)
4. **UserMappingService** - Config only
5. **McmmoApiWrapper** - Config only
6. **Minecraft Commands** - Registers with Bukkit
7. **TimeoutManager** - No dependencies (optional)
8. **CircuitBreaker** - No dependencies (optional)
9. **DiscordApiWrapper** - Depends on CircuitBreaker
10. **Image Components** - Config only (optional)
11. **DiscordManager** - Depends on all above
12. **Discord Connection** - Depends on DiscordManager
13. **Feature Services** - Depend on Discord connection

**Why this order?** Each service only depends on services initialized before it. This ensures no null pointer exceptions during startup.

## Graceful Degradation

The plugin continues running even if Discord fails:

```kotlin
// In onEnable()
try {
    connectToDiscord()
} catch (e: Exception) {
    logger.severe("Discord connection failed - running in degraded mode")
    // Plugin continues, Minecraft commands still work
}
```

**Degraded mode behaviors**:
- Minecraft commands work normally
- Discord commands unavailable
- Chat bridge disabled
- Announcements disabled

## Configuration Loading Pattern

All configurable components follow this pattern:

```kotlin
// 1. Data class with companion factory
data class CircuitBreakerConfig(
    val enabled: Boolean,
    val failureThreshold: Int,
    // ... more fields
) {
    companion object {
        fun fromConfig(config: FileConfiguration): CircuitBreakerConfig {
            return CircuitBreakerConfig(
                enabled = config.getBoolean("discord.circuit-breaker.enabled", true),
                failureThreshold = config.getInt("discord.circuit-breaker.failure-threshold", 5),
                // ...
            )
        }
    }

    // 2. Factory method to create runtime object
    fun toCircuitBreaker(logger: Logger): CircuitBreaker {
        return CircuitBreaker(failureThreshold, timeWindowSeconds, ...)
    }
}

// 3. Usage in plugin
val config = CircuitBreakerConfig.fromConfig(config)
if (config.enabled) {
    circuitBreaker = config.toCircuitBreaker(logger)
}
```

**Benefits**:
- Clean separation of config parsing and object creation
- Testable without file I/O
- Self-documenting defaults

## Thread Safety Model

| Component | Thread Safety | Notes |
|-----------|---------------|-------|
| `ErrorMetrics` | Thread-safe | Uses AtomicLong, ConcurrentHashMap |
| `CircuitBreaker` | Thread-safe | Uses AtomicReference for state |
| `RateLimiter` | Thread-safe | Uses ConcurrentHashMap |
| `AvatarFetcher` | Thread-safe | Synchronized cache |
| `UserMappingService` | NOT thread-safe | Main thread only |

**Rule**: If accessed from JDA threads, must be thread-safe. Bukkit-only = main thread only.

## Key Design Decisions

### Why Sealed Classes for Results?

Instead of throwing exceptions:

```kotlin
// Traditional approach (exceptions)
try {
    val result = circuitBreaker.execute { ... }
} catch (e: CircuitBreakerOpenException) {
    // Handle open
} catch (e: Exception) {
    // Handle failure
}

// Sealed class approach (used in this plugin)
when (val result = circuitBreaker.execute("op") { ... }) {
    is CircuitResult.Success -> result.value
    is CircuitResult.Failure -> result.exception
    is CircuitResult.Rejected -> // Circuit open
}
```

**Benefits**: Compiler enforces handling all cases, no forgotten catch blocks.

### Why Optional Resilience Components?

```kotlin
var circuitBreaker: CircuitBreaker? = null
```

Server operators may not need all resilience features. Making them optional:
- Reduces resource usage when disabled
- Simplifies configuration for simple setups
- Allows incremental adoption

### Why Separate DiscordApiWrapper?

Instead of calling Discord directly:

```kotlin
// Direct call (problematic)
jda.getTextChannel(id)?.sendMessage(msg)

// Through wrapper (used in this plugin)
discordApiWrapper.executeWithCircuitBreaker("send message") {
    jda.getTextChannel(id)?.sendMessage(msg)
}
```

**Benefits**: Single point for applying circuit breaker, metrics, logging.

## Next Steps

- [Initialization Flow](initialization.md) - Detailed startup sequence
- [Threading & Concurrency](threading.md) - Deep dive into thread safety
- [Circuit Breaker Pattern](../patterns/circuit-breaker.md) - Resilience pattern details
