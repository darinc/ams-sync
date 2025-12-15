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

**Run tests:**
```bash
./gradlew test
```
Uses Kotest with JUnit5 platform.

**Run a single test class:**
```bash
./gradlew test --tests "io.github.darinc.amssync.discord.CircuitBreakerTest"
```

**Run a single test method:**
```bash
./gradlew test --tests "io.github.darinc.amssync.discord.CircuitBreakerTest.CircuitBreaker -- CLOSED state -- starts in CLOSED state"
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

**Environment Variables:** Discord secrets can be set via environment variables (override config values):
- `AMS_DISCORD_TOKEN` - Bot token
- `AMS_GUILD_ID` - Guild ID

## Architecture

> **Detailed Diagrams**: See [docs/architecture/initialization.md](docs/architecture/initialization.md) for complete initialization diagrams showing all 6 phases, service groupings, and file locations.

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

### Service Architecture

Services are exposed as direct properties on `AMSSyncPlugin`:

**Core Services (always initialized):**
- `plugin.userMappingService` - Discord-Minecraft user mappings
- `plugin.mcmmoApi` - MCMMO data access wrapper
- `plugin.errorMetrics` - Error tracking and metrics
- `plugin.auditLogger` - Administrative action logging
- `plugin.rateLimiter` - Command rate limiting (nullable, config-dependent)

**Grouped Services:**
- `plugin.resilience` - `ResilienceServices` (timeoutManager, circuitBreaker)
- `plugin.discord` - `DiscordServices` (manager, apiWrapper, webhookManager)

**Feature Coordinators** (`features/`):
Features manage lifecycle (initialize/shutdown) of related services. Each implements the `Feature` interface with `isEnabled`, `initialize()`, and `shutdown()` methods.

- `plugin.imageFeature` - `ImageCardFeature` (avatarFetcher, cardRenderer, statsCommand, topCommand)
- `plugin.eventsFeature` - `EventAnnouncementFeature` (mcmmoListener, serverListener, deathListener, achievementListener)
- `plugin.chatBridgeFeature` - `ChatBridgeFeature` (chatBridge, chatWebhookManager)
- `plugin.playerCountFeature` - `PlayerCountDisplayFeature` (presence, statusChannel)
- `plugin.progressionFeature` - `ProgressionTrackingFeature` (database, snapshotTask, retentionTask)

### Core Services

**DiscordManager** (`discord/DiscordManager.kt`)
- JDA lifecycle management (build → awaitReady → shutdown)
- Slash command registration (guild-specific or global)
- Connection status tracking

**McmmoApiWrapper** (`mcmmo/McmmoApiWrapper.kt`)
- Direct DatabaseManager access for flatfile storage (NOT UserManager - see note below)
- Leaderboard caching with configurable TTL (default 60 seconds)
- Query limiting to prevent timeouts on large servers
- Power level calculation (sum of all non-child skills)

**UserMappingService** (`linking/UserMappingService.kt`)
- Bidirectional mapping: Discord ID ↔ Minecraft username
- Config persistence (saved to `config.yml` under `user-mappings`)
- Validation: Discord IDs must be 17-19 digit snowflakes
- Automatic replacement: prevents duplicate mappings

**TimeoutManager** (`discord/TimeoutManager.kt`)
- Protects against hanging operations using ScheduledExecutorService
- Warning threshold (logs slow operations) + hard timeout (cancels operation)
- Returns sealed class `TimeoutResult` (Success, Timeout, Failure)

**CircuitBreaker** (`discord/CircuitBreaker.kt`)
- States: CLOSED (normal) → OPEN (failing fast) → HALF_OPEN (testing recovery)
- Configurable failure threshold in time window
- Cooldown period before attempting recovery

**RetryManager** (`discord/RetryManager.kt`)
- Exponential backoff with configurable multiplier
- Max delay cap to prevent excessive wait times
- Returns sealed class `RetryResult` (Success, Failure)

**RateLimiter** (`discord/RateLimiter.kt`)
- Per-user rate limiting for Discord commands
- Configurable penalty cooldown after exceeding limit
- Prevents spam and abuse

**ErrorMetrics** (`metrics/ErrorMetrics.kt`)
- Tracks connection attempts, API call success/failure rates
- Exposed via `/amssync metrics` command

**AuditLogger** (`audit/AuditLogger.kt`)
- Logs administrative actions (link/unlink operations)
- File-based audit trail

**ConfigMigrator** (`config/ConfigMigrator.kt`)
- Handles config.yml version migrations between plugin updates
- Creates backups before migration
- Returns sealed class `MigrationResult` (FreshInstall, UpToDate, Migrated, Failed)

**ConfigValidator** (`config/ConfigValidator.kt`)
- Pre-validates Discord configuration before connection attempts
- Bot token format validation
- Guild ID validation

### Image Card System

**PlayerCardRenderer** (`image/PlayerCardRenderer.kt`)
- Generates visual stats cards for `/amsstats`
- Creates podium leaderboard images for `/amstop`
- Uses Graphics2D for image generation

**MilestoneCardRenderer** (`image/MilestoneCardRenderer.kt`)
- Generates celebration cards for MCMMO milestone achievements
- Includes skill badges and player avatars

**AvatarFetcher** (`image/AvatarFetcher.kt`)
- Downloads and caches player Minecraft avatars
- Supports mc-heads and crafatar providers
- LRU cache with configurable TTL

### Event and Communication Services

**ChatBridge** (`discord/ChatBridge.kt`)
- Two-way chat relay between Minecraft and Discord
- Configurable message formats with placeholders
- Optional webhook support for rich messages
- @mention resolution: converts `@Username` to Discord pings for linked users
- Message sanitization prevents @everyone/@here exploits

**PlayerCountPresence** (`discord/PlayerCountPresence.kt`)
- Updates bot activity/status with player count
- Optional nickname updates showing player count
- Rate-limited to respect Discord API limits

**StatusChannelManager** (`discord/StatusChannelManager.kt`)
- Updates voice channel name with player count
- Respects Discord's 2 per 10 minute rename limit

**McMMOEventListener** (`mcmmo/McMMOEventListener.kt`)
- Listens for MCMMO level-up events
- Posts milestone announcements to Discord
- Supports both embeds and image cards

**WebhookManager** (`discord/WebhookManager.kt`)
- Sends messages via Discord webhooks with custom avatars
- Fallback to bot messages when webhook unavailable

**ChatWebhookManager** (`discord/ChatWebhookManager.kt`)
- Specialized webhook manager for chat bridge messages
- Sends player chat with Minecraft head avatars

### Command Handling

**Discord Commands** (`discord/SlashCommandListener.kt`)
- Routes slash commands to handlers
- Text commands: `McStatsCommand`, `McTopCommand` (embed-based)
- Image commands: `AmsStatsCommand`, `AmsTopCommand` (visual cards)
- Admin: `DiscordLinkCommand`, `DiscordWhitelistCommand`
- All commands check `CircuitBreaker` and `RateLimiter` state before executing
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
- `net.dv8tion` → `io.github.darinc.amssync.libs.jda`
- `club.minnced` → `io.github.darinc.amssync.libs.webhook`
- `kotlin` → `io.github.darinc.amssync.libs.kotlin`
- `kotlinx` → `io.github.darinc.amssync.libs.kotlinx`

**DO NOT** relocate SLF4J - JDA needs to find it in the original package.

## Configuration Structure

All configuration lives in `src/main/resources/config.yml`:

**Core Settings:**
- `discord.token` - Bot token (REQUIRED, or use `AMS_DISCORD_TOKEN` env var)
- `discord.guild-id` - Server ID for instant command registration (optional but recommended)

**Resilience Settings:**
- `discord.retry.*` - Retry logic configuration (enabled by default)
- `discord.timeout.*` - Timeout protection settings
- `discord.circuit-breaker.*` - Circuit breaker thresholds

**Discord Features:**
- `discord.presence.*` - Bot activity/status and nickname player count display
- `discord.status-channel.*` - Voice channel name showing player count
- `discord.announcements.*` - MCMMO milestone announcements
- `discord.events.*` - Server start/stop, deaths, achievements announcements
- `discord.chat-bridge.*` - Two-way Minecraft↔Discord chat relay
  - `resolve-mentions` - Convert `@Username` to Discord pings (default: true)

**MCMMO Settings:**
- `mcmmo.leaderboard.max-players-to-scan` - Query limit to prevent timeouts (default: 1000)
- `mcmmo.leaderboard.cache-ttl-seconds` - Leaderboard cache duration (default: 60)

**Other Settings:**
- `image-cards.*` - Visual card generation for `/amsstats` and `/amstop`
- `rate-limiting.*` - Command spam protection
- `user-mappings` - Discord ID → Minecraft username mappings (managed by `/amssync`)

## Common Patterns

### Adding New Discord Slash Commands

1. Create command handler in `discord/commands/` implementing `SlashCommandHandler` interface
2. Add handler to `AMSSyncPlugin.buildSlashCommandHandlers()` map
3. Register slash command definition in `DiscordManager.registerSlashCommands()` using JDA's Commands builder
4. Access services via direct plugin properties:
   - `plugin.mcmmoApi` for MCMMO data
   - `plugin.userMappingService` for Discord-Minecraft mappings
   - `plugin.discord.apiWrapper` for circuit-breaker-protected Discord API calls
   - `plugin.resilience.circuitBreaker` for manual circuit breaker checks
   - `plugin.imageFeature?.avatarFetcher` for avatar fetching
   - `plugin.progressionFeature?.database` for progression data

### Error Handling

- Use custom exceptions in `exceptions/AMSSyncExceptions.kt`
- Discord errors use ephemeral responses for privacy
- Log levels: INFO (lifecycle), FINE (debug/cache), WARNING (recoverable), SEVERE (critical)
- Retry/timeout/circuit breaker all have distinct error messages for debugging

### Thread Safety

- `UserMappingService` - not thread-safe, accessed only from Bukkit main thread
- `McmmoApiWrapper.leaderboardCache` - uses ConcurrentHashMap
- `LinkingSessionManager` - uses ConcurrentHashMap for session storage
- `RateLimiter` - uses ConcurrentHashMap for per-user tracking
- `AvatarFetcher` - uses synchronized LinkedHashMap for LRU cache
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
- Use `lateinit` for required plugin services initialized in `onEnable()`
- Nullable types for optional services and features (e.g., `rateLimiter`, `imageFeature`)
- Services exposed as direct properties on `AMSSyncPlugin` (access via `plugin.serviceName`)
- Feature coordinators implement `Feature` interface for lifecycle management
- Grouped services use wrapper data classes (e.g., `DiscordServices`, `ResilienceServices`)
- Prefer sealed classes for result types (e.g., `RetryResult`, `TimeoutResult`)
- Config data classes with companion `fromConfig()` factory methods (e.g., `ImageConfig`, `PresenceConfig`)
- Testing with Kotest (spec style) and MockK for mocking
