# Initialization Flow

**Complexity**: Intermediate
**Key File**: [`AMSSyncPlugin.kt`](../../src/main/kotlin/io/github/darinc/amssync/AMSSyncPlugin.kt)

## Overview

AMSSync follows a carefully ordered initialization sequence in `onEnable()` to ensure all service dependencies are satisfied. The plugin supports graceful degradation - if Discord connection fails, Minecraft features continue working.

## Initialization Sequence

```
onEnable()
    │
    ├─► Save default config
    │
    ├─► ErrorMetrics         (no dependencies)
    ├─► AuditLogger          (no dependencies)
    ├─► RateLimiter          (optional, config-driven)
    │
    ├─► UserMappingService   (loads from config)
    ├─► McmmoApiWrapper      (config for cache/limits)
    │
    ├─► Register Minecraft commands
    │
    ├─► Validate Discord config
    │   └─► FAIL? → Disable plugin
    │
    ├─► RetryManager config
    ├─► TimeoutManager       (optional)
    ├─► CircuitBreaker       (optional)
    ├─► DiscordApiWrapper    (wraps circuit breaker)
    │
    ├─► Image card components (optional)
    ├─► DiscordManager       (JDA lifecycle)
    │
    └─► Connect to Discord
        ├─► SUCCESS → Initialize Discord features
        │   ├─► PlayerCountPresence
        │   ├─► StatusChannelManager
        │   ├─► McMMOEventListener
        │   ├─► ChatBridge
        │   └─► Event announcements
        │
        └─► FAIL → Log warning, continue in degraded mode
```

## Service Categories

### Core Services (Always Initialized)

```kotlin
// No dependencies - initialize first
errorMetrics = ErrorMetrics()
auditLogger = AuditLogger(this)

// Config-only dependencies
userMappingService = UserMappingService(this)
mcmmoApi = McmmoApiWrapper(this, maxPlayersToScan, cacheTtlSeconds)
```

### Optional Resilience Services

```kotlin
// Optional based on config
var timeoutManager: TimeoutManager? = null
var circuitBreaker: CircuitBreaker? = null
var rateLimiter: RateLimiter? = null

// In onEnable():
if (config.getBoolean("discord.timeout.enabled", true)) {
    timeoutManager = timeoutConfig.toTimeoutManager(logger)
}
```

**Why nullable?** Server operators can disable resilience features for simpler setups or debugging.

### Discord-Dependent Services

These are only initialized after successful Discord connection:

```kotlin
private fun initializePlayerCountPresence() {
    if (!discordManager.isConnected()) {
        logger.fine("Skipping presence initialization - Discord not connected")
        return
    }
    // ... initialize presence, chat bridge, announcements
}
```

## Configuration Loading Pattern

All configurable components follow this pattern:

```kotlin
// 1. Load configuration values with defaults
val retryConfig = RetryManager.RetryConfig(
    enabled = config.getBoolean("discord.retry.enabled", true),
    maxAttempts = config.getInt("discord.retry.max-attempts", 5),
    initialDelaySeconds = config.getInt("discord.retry.initial-delay-seconds", 5),
    // ...
)

// 2. Create runtime object if enabled
if (retryConfig.enabled) {
    val retryManager = retryConfig.toRetryManager(logger)
}
```

### Environment Variable Override

Discord credentials can be overridden via environment variables:

```kotlin
val token = System.getenv("AMS_DISCORD_TOKEN")
    ?: config.getString("discord.token")
    ?: ""

val guildId = System.getenv("AMS_GUILD_ID")
    ?: config.getString("discord.guild-id")
    ?: ""
```

This allows production deployments to keep secrets out of config files.

## Discord Connection with Resilience

The plugin wraps Discord connection with multiple resilience layers:

```kotlin
// Layer 1: Timeout protection
val connectionResult = timeoutManager?.executeWithTimeout("Discord connection") {
    // Layer 2: Retry with backoff
    retryManager.executeWithRetry("Discord connection") {
        // Layer 3: Actual connection
        discordManager.initialize(token, guildId)
    }
}
```

### Handling Connection Results

```kotlin
when (connectionResult) {
    is TimeoutResult.Success -> {
        when (val retryResult = connectionResult.value) {
            is RetryResult.Success -> {
                errorMetrics.recordConnectionAttempt(success = true)
                logger.info("Discord bot successfully connected!")
                initializePlayerCountPresence()  // Initialize Discord features
            }
            is RetryResult.Failure -> {
                errorMetrics.recordConnectionAttempt(success = false)
                // Log warning, continue in degraded mode
            }
        }
    }
    is TimeoutResult.Timeout -> {
        // Connection timed out - degraded mode
    }
    is TimeoutResult.Failure -> {
        // Unexpected error
    }
}
```

## Graceful Degradation

When Discord connection fails, the plugin continues running:

```
┌─────────────────────────────────────────────────────┐
│ PLUGIN RUNNING IN DEGRADED MODE                     │
│                                                     │
│ Working:                                            │
│ ✓ Minecraft /amssync commands                       │
│ ✓ User mapping storage                              │
│ ✓ MCMMO data access                                 │
│                                                     │
│ Not Available:                                      │
│ ✗ Discord slash commands                            │
│ ✗ Chat bridge                                       │
│ ✗ Milestone announcements                           │
│ ✗ Player count presence                             │
└─────────────────────────────────────────────────────┘
```

## Shutdown Sequence

`onDisable()` reverses the initialization order:

```kotlin
override fun onDisable() {
    // 1. Announce server stop (if Discord connected)
    serverEventListener?.announceServerStop()

    // 2. Shutdown Discord features
    playerCountPresence?.shutdown()
    statusChannelManager?.shutdown()
    webhookManager?.shutdown()
    chatWebhookManager?.shutdown()
    mcmmoEventListener?.shutdown()

    // 3. Disconnect from Discord
    if (::discordManager.isInitialized) {
        discordManager.shutdown()
    }

    // 4. Shutdown resilience components
    timeoutManager?.shutdown()

    // 5. Save user mappings
    if (::userMappingService.isInitialized) {
        userMappingService.saveMappings()
    }
}
```

> **IMPORTANT**: `shutdownNow()` is used for JDA to ensure immediate disconnection and prevent background thread issues.

## Initialization Timing

Typical initialization times:

| Phase | Duration | Notes |
|-------|----------|-------|
| Config loading | <10ms | File I/O |
| Service creation | <50ms | In-memory |
| Discord connection | 1-5s | Network dependent |
| Command registration | <100ms | After connection |
| Total (success) | 2-6s | Varies with network |
| Total (timeout) | 10s | Hard timeout limit |

## Common Issues

### Plugin Disabled After Config Error

```
[SEVERE] DISCORD CONFIGURATION INVALID
[SEVERE]   • Discord token is empty or not configured
[SEVERE] Plugin will be disabled. Fix config.yml and restart.
```

**Solution**: Set `discord.token` in config.yml or `AMS_DISCORD_TOKEN` environment variable.

### Degraded Mode After Connection Failure

```
[SEVERE] Failed to connect to Discord after 5 attempts
[SEVERE] PLUGIN RUNNING IN DEGRADED MODE
```

**Solution**: Check bot token validity, network connectivity, and Discord API status.

## Related Documentation

- [Architecture Overview](overview.md) - High-level component diagram
- [Threading & Concurrency](threading.md) - Thread safety considerations
- [Timeout Protection](../patterns/timeout-protection.md) - Timeout manager details
- [Retry with Backoff](../patterns/retry-backoff.md) - Retry logic details
