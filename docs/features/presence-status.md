# Presence & Status

**Complexity**: Intermediate
**Key Files**:
- [`discord/PlayerCountPresence.kt`](../../src/main/kotlin/io/github/darinc/amssync/discord/PlayerCountPresence.kt)
- [`discord/PresenceConfig.kt`](../../src/main/kotlin/io/github/darinc/amssync/discord/PresenceConfig.kt)

## Overview

PlayerCountPresence displays the current player count in Discord through:

1. **Bot Activity** - Status like "Playing with 5 players"
2. **Bot Nickname** - Guild nickname like "[5] AMSSync"

Updates are event-driven (player join/quit) with debouncing to respect Discord API limits.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    PlayerCountPresence                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  PlayerJoinEvent ─┬─► scheduleUpdate() ─► debounce ─► executeUpdate()
│  PlayerQuitEvent ─┘                           │              │
│                                               │              ▼
│                                    ┌─────────┴────────┐ updatePresence()
│                                    │ Rate limit check │      │
│                                    │ (30s minimum)    │      ├─► updateActivity()
│                                    └──────────────────┘      └─► updateNickname()
│                                                                  │
│                                                                  ▼
│                                                           Discord API
└─────────────────────────────────────────────────────────────────┘
```

## Activity Types

Discord supports four activity types:

```kotlin
val activity = when (config.activity.type.lowercase()) {
    "playing" -> Activity.playing(message)     // "Playing 5 players online"
    "watching" -> Activity.watching(message)   // "Watching 5 players online"
    "listening" -> Activity.listening(message) // "Listening to 5 players"
    "competing" -> Activity.competing(message) // "Competing in 5 players"
    else -> Activity.playing(message)
}
```

## Template Placeholders

| Placeholder | Description | Example |
|-------------|-------------|---------|
| `{count}` | Current online players | `5` |
| `{max}` | Server max players | `100` |
| `{name}` | Original bot name (nickname only) | `AMSSync` |

### Examples

```yaml
# Activity templates
template: "{count} players online"        # "5 players online"
template: "{count}/{max} players"         # "5/100 players"
template: "Minecraft - {count} online"    # "Minecraft - 5 online"

# Nickname templates
template: "[{count}] {name}"              # "[5] AMSSync"
template: "{name} ({count}/{max})"        # "AMSSync (5/100)"
```

## Event Handling

### Player Join

```kotlin
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
fun onPlayerJoin(event: PlayerJoinEvent) {
    scheduleUpdate(0)  // No adjustment needed
}
```

### Player Quit

```kotlin
@EventHandler(priority = EventPriority.MONITOR)
fun onPlayerQuit(event: PlayerQuitEvent) {
    scheduleUpdate(-1)  // Player still in list when event fires
}
```

> **Why -1?** When `PlayerQuitEvent` fires, `getOnlinePlayers()` still includes the leaving player. We adjust by -1 so the scheduled count check is accurate.

## Debouncing

Rapid join/quit events are debounced:

```kotlin
private fun scheduleUpdate(adjustment: Int) {
    val currentCount = Bukkit.getOnlinePlayers().size + adjustment

    // Skip if count unchanged
    if (currentCount == lastPlayerCount.get()) return

    // Cancel pending update
    pendingUpdate.get()?.cancel(false)

    // Schedule new debounced update
    val future = executor.schedule({
        executeUpdate()
    }, config.debounceMs, TimeUnit.MILLISECONDS)

    pendingUpdate.set(future)
}
```

**Why debounce?**
- Player joins and immediately disconnects → only one update
- Multiple players join simultaneously → batched into one update
- Prevents hitting Discord rate limits

## Rate Limiting

```kotlin
private fun executeUpdate() {
    val now = System.currentTimeMillis()
    val timeSinceLastUpdate = now - lastUpdateTime.get()

    // Enforce minimum interval (default 30s)
    if (timeSinceLastUpdate < config.minIntervalMs && lastUpdateTime.get() > 0) {
        val delay = config.minIntervalMs - timeSinceLastUpdate
        // Reschedule for later
        executor.schedule({ executeUpdate() }, delay, TimeUnit.MILLISECONDS)
        return
    }

    updatePresence(Bukkit.getOnlinePlayers().size)
    lastUpdateTime.set(now)
}
```

## Thread Safety

```kotlin
// Atomic state for thread-safe access
private val lastUpdateTime = AtomicLong(0L)
private val lastPlayerCount = AtomicInteger(-1)
private val pendingUpdate = AtomicReference<ScheduledFuture<*>?>(null)
private val nicknameDisabled = AtomicBoolean(false)
```

Updates can come from:
- Bukkit event handlers (main thread)
- Scheduled executor (background thread)

Atomic types ensure consistency without explicit locking.

## Nickname Updates

```kotlin
private fun updateNickname(jda: JDA, playerCount: Int) {
    val guildId = plugin.config.getString("discord.guild-id") ?: return
    val guild = jda.getGuildById(guildId) ?: return

    val nickname = formatTemplate(config.nickname.template, playerCount, originalBotName)

    guild.selfMember.modifyNickname(nickname).queue(
        { logger.fine("Updated nickname: $nickname") },
        { error -> handleNicknameError(error) }
    )
}
```

### Graceful Fallback

If nickname updates fail (permissions issue), behavior depends on config:

```kotlin
private fun handleNicknameError(error: Throwable) {
    logger.warning("Failed to update nickname: ${error.message}")

    if (!config.nickname.gracefulFallback) {
        // Disable further attempts
        nicknameDisabled.set(true)
    }
    // If gracefulFallback=true, keep trying
}
```

## Configuration

```yaml
discord:
  presence:
    enabled: true
    min-update-interval-seconds: 30  # Minimum time between updates
    debounce-seconds: 5              # Wait after player event

    activity:
      enabled: true
      type: playing                  # playing, watching, listening, competing
      template: "{count} players online"

    nickname:
      enabled: false                 # Requires CHANGE_NICKNAME permission
      template: "[{count}] {name}"
      graceful-fallback: true        # Keep trying on errors
```

## Configuration Data Classes

```kotlin
data class PresenceConfig(
    val enabled: Boolean,
    val minIntervalMs: Long,
    val debounceMs: Long,
    val activity: ActivityConfig,
    val nickname: NicknameConfig
) {
    companion object {
        fun fromConfig(config: FileConfiguration): PresenceConfig {
            return PresenceConfig(
                enabled = config.getBoolean("discord.presence.enabled", true),
                minIntervalMs = config.getInt("discord.presence.min-update-interval-seconds", 30) * 1000L,
                debounceMs = config.getInt("discord.presence.debounce-seconds", 5) * 1000L,
                activity = ActivityConfig(
                    enabled = config.getBoolean("discord.presence.activity.enabled", true),
                    type = config.getString("discord.presence.activity.type", "playing") ?: "playing",
                    template = config.getString("discord.presence.activity.template", "{count} players online")
                        ?: "{count} players online"
                ),
                nickname = NicknameConfig(
                    enabled = config.getBoolean("discord.presence.nickname.enabled", false),
                    template = config.getString("discord.presence.nickname.template", "[{count}] {name}")
                        ?: "[{count}] {name}",
                    gracefulFallback = config.getBoolean("discord.presence.nickname.graceful-fallback", true)
                )
            )
        }
    }
}
```

## Circuit Breaker Integration

Presence updates are wrapped with circuit breaker:

```kotlin
if (circuitBreaker != null) {
    val result = circuitBreaker.execute("Update bot activity") {
        jda.presence.activity = activity
    }
    when (result) {
        is CircuitResult.Success -> logger.fine("Activity updated")
        is CircuitResult.Failure -> logger.warning("Activity failed: ${result.exception.message}")
        is CircuitResult.Rejected -> logger.fine("Activity rejected by circuit breaker")
    }
} else {
    jda.presence.activity = activity
}
```

## Initialization and Shutdown

```kotlin
fun initialize() {
    if (!config.enabled) return

    // Register Bukkit listener for join/quit events
    plugin.server.pluginManager.registerEvents(this, plugin)

    // Store original bot name for nickname template
    originalBotName = plugin.discordManager.getJda()?.selfUser?.name

    // Set initial presence
    updatePresenceNow()
}

fun shutdown() {
    pendingUpdate.get()?.cancel(false)
    executor.shutdown()
    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        executor.shutdownNow()
    }
}
```

## Discord Rate Limits

Discord limits presence updates to approximately 5 per minute. The default configuration:
- **Minimum interval**: 30 seconds
- **Debounce**: 5 seconds

This ensures at most 2 updates per minute under normal conditions.

## Common Issues

### Nickname Not Updating

1. **Missing permission**: Bot needs `CHANGE_NICKNAME` permission
2. **Bot role too low**: Bot can't change nickname if its role is below the server owner
3. **No guild ID**: `discord.guild-id` must be configured

### Activity Not Showing

1. **Check enabled**: `discord.presence.activity.enabled: true`
2. **Discord not connected**: Presence only works when bot is connected
3. **Template issue**: Ensure template has valid placeholders

## Related Documentation

- [JDA Discord](../integrations/jda-discord.md) - JDA lifecycle
- [Status Channel Manager](../features/discord-commands.md) - Voice channel player count
- [Circuit Breaker](../patterns/circuit-breaker.md) - Failure protection
