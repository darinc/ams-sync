# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AMSSync is a Paper/Spigot Minecraft plugin that embeds a Discord bot directly into the server, providing MCMMO stats integration through Discord slash commands. The plugin uses JDA (Java Discord API) for Discord integration and interfaces directly with MCMMO's DatabaseManager for flatfile storage access.

## Build Commands

**Build the plugin:**
```bash
./gradlew shadowJar
```
Output: `build/libs/ams-sync-*.jar` (shaded JAR with all dependencies)

**Clean build:**
```bash
./gradlew clean build
```

**Run static analysis:**
```bash
./gradlew detekt
```
HTML report: `build/reports/detekt/detekt.html`

**Run all checks:**
```bash
./gradlew check
```

## Development Prerequisites

MCMMO must be installed to local Maven repository before building:
```bash
cd /path/to/mcMMO
mvn install -DskipTests
```

## Architecture

### Plugin Initialization Flow

1. `AMSSyncPlugin.onEnable()` loads config and initializes services in order:
   - `UserMappingService` - loads Discord - Minecraft mappings from config
   - `McmmoApiWrapper` - initializes with leaderboard limits and cache settings
   - `TimeoutManager` (optional) - configurable timeout protection for operations
   - `CircuitBreaker` (optional) - prevents cascading failures during Discord outages
   - `DiscordApiWrapper` - wraps Discord API calls with circuit breaker
   - `DiscordManager` - connects to Discord with retry logic and registers slash commands

2. Discord connection uses layered resilience:
   - `RetryManager` - exponential backoff retry logic (5 attempts by default)
   - `TimeoutManager` - hard timeout protection (10 seconds by default)
   - `CircuitBreaker` - fail-fast protection during sustained failures
   - All layers are configurable and can be disabled

3. Plugin runs in degraded mode if Discord connection fails - Minecraft commands continue working

### Core Services

**DiscordManager** (`discord/DiscordManager.kt`)
- JDA lifecycle management (build - awaitReady - shutdown)
- Slash command registration (guild-specific or global)
- Connection status tracking

**McmmoApiWrapper** (`mcmmo/McmmoApiWrapper.kt`)
- Direct DatabaseManager access for flatfile storage (NOT UserManager - see note below)
- Leaderboard caching with configurable TTL (default 60 seconds)
- Query limiting to prevent timeouts on large servers
- Power level calculation (sum of all non-child skills)

**UserMappingService** (`linking/UserMappingService.kt`)
- Bidirectional mapping: Discord ID - Minecraft username
- Config persistence (saved to `config.yml` under `user-mappings`)
- Validation: Discord IDs must be 17-19 digit snowflakes
- Automatic replacement: prevents duplicate mappings

**TimeoutManager** (`discord/TimeoutManager.kt`)
- Protects against hanging operations using ScheduledExecutorService
- Warning threshold (logs slow operations) + hard timeout (cancels operation)
- Returns sealed class `TimeoutResult` (Success, Timeout, Failure)

**CircuitBreaker** (`discord/CircuitBreaker.kt`)
- States: CLOSED (normal) - OPEN (failing fast) - HALF_OPEN (testing recovery)
- Configurable failure threshold in time window
- Cooldown period before attempting recovery

**RetryManager** (`discord/RetryManager.kt`)
- Exponential backoff with configurable multiplier
- Max delay cap to prevent excessive wait times
- Returns sealed class `RetryResult` (Success, Failure)

### Command Handling

**Discord Commands** (`discord/SlashCommandListener.kt`)
- Routes slash commands to handlers: `McStatsCommand`, `McTopCommand`, `DiscordLinkCommand`
- All commands check `CircuitBreaker` state before executing
- Uses ephemeral responses for errors

**Minecraft Commands** (`commands/AMSSyncCommand.kt`)
- Session-based number mapping system (5-minute auto-expiration)
- Quick linking workflow: `/amssync quick` shows lists, `/amssync quick 1 5` links
- Per-sender session isolation using ConcurrentHashMap
- Background cleanup task runs every minute

**LinkingSession** (`commands/LinkingSession.kt`)
- Stores numbered mappings for players and Discord members
- Timestamp tracking for expiration logic
- Thread-safe with immutable data classes

## Critical Implementation Details

### MCMMO Data Access Pattern

**IMPORTANT:** Always use `mcMMO.getDatabaseManager().loadPlayerProfile(uuid)` for offline players, NOT `UserManager.getOfflinePlayer(uuid)`.

- `UserManager` only returns loaded (online) profiles
- `DatabaseManager` reads directly from flatfile storage for offline players
- Check `profile.isLoaded` before accessing skill data
- See: https://github.com/mcMMO-Dev/mcMMO/issues/5163

### Power Level Calculation

```kotlin
PrimarySkillType.values()
    .filter { !it.isChildSkill }
    .sumOf { profile.getSkillLevel(it) }
```

Always filter out child skills (e.g., Salvage, Smelting) to avoid double-counting.

### Offline Player Lookup

`Bukkit.getOfflinePlayer(name)` doesn't search by name - it creates a new OfflinePlayer with an offline-mode UUID. Instead:

1. Search online players first (most efficient)
2. Iterate through `Bukkit.getOfflinePlayers()` for name match
3. Check `hasPlayedBefore()` to verify player has joined

### Discord ID Validation

Discord snowflakes are 17-19 digit unsigned integers. Always validate with:
```kotlin
discordId.matches(Regex("^\\d{17,19}$"))
```

### Dependency Relocation

The shadowJar task relocates dependencies to prevent conflicts:
- `net.dv8tion` - `io.github.darinc.amssync.libs.jda`
- `kotlin` - `io.github.darinc.amssync.libs.kotlin`
- `kotlinx` - `io.github.darinc.amssync.libs.kotlinx`

**DO NOT** relocate SLF4J - JDA needs to find it in the original package.

## Configuration Structure

All configuration lives in `src/main/resources/config.yml`:

- `discord.token` - Bot token (REQUIRED, never commit actual token)
- `discord.guild-id` - Server ID for instant command registration (optional but recommended)
- `discord.retry.*` - Retry logic configuration (enabled by default)
- `discord.timeout.*` - Timeout protection settings
- `discord.circuit-breaker.*` - Circuit breaker thresholds
- `mcmmo.leaderboard.max-players-to-scan` - Query limit to prevent timeouts (default: 1000)
- `mcmmo.leaderboard.cache-ttl-seconds` - Leaderboard cache duration (default: 60)
- `user-mappings` - Discord ID - Minecraft username mappings (managed by `/amssync`)

## Common Patterns

### Adding New Discord Slash Commands

1. Create command handler in `discord/commands/` implementing command logic
2. Register in `DiscordManager.registerSlashCommands()` using JDA's Commands builder
3. Route in `SlashCommandListener.onSlashCommandInteraction()`
4. Use `plugin.discordApiWrapper?.executeWithCircuitBreaker()` for MCMMO queries

### Error Handling

- Use custom exceptions in `exceptions/AMSSyncExceptions.kt`
- Discord errors use ephemeral responses for privacy
- Log levels: INFO (lifecycle), FINE (debug/cache), WARNING (recoverable), SEVERE (critical)
- Retry/timeout/circuit breaker all have distinct error messages for debugging

### Thread Safety

- `UserMappingService` - not thread-safe, accessed only from Bukkit main thread
- `McmmoApiWrapper.leaderboardCache` - uses ConcurrentHashMap
- `LinkingSessionManager` - uses ConcurrentHashMap for session storage
- Discord operations run on JDA thread pool, use Bukkit scheduler for server operations

## Testing Locally

1. Build: `./gradlew shadowJar`
2. Copy `build/libs/ams-sync-*.jar` to test server's `plugins/`
3. Start server to generate default config
4. Stop server and edit `plugins/AMSSync/config.yml` with real bot token and guild ID
5. Restart server and verify Discord connection in console logs

## Code Style

- Kotlin 1.9.21 with Java 21 toolchain
- Detekt for static analysis (config: `detekt-config.yml`)
- Use `lateinit` for plugin services initialized in `onEnable()`
- Nullable types for optional services (e.g., `timeoutManager`, `circuitBreaker`)
- Prefer sealed classes for result types (e.g., `RetryResult`, `TimeoutResult`)
