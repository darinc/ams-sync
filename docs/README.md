# AMSSync Documentation

AMSSync is a Paper/Spigot Minecraft plugin that embeds a Discord bot directly into your server, providing MCMMO stats integration through Discord slash commands. Beyond basic functionality, this plugin demonstrates **production-grade patterns** for resilience, observability, and clean architecture.

> **Learning Focus**: This documentation emphasizes the *why* and *how* behind implementation choices, making it a reference for best practices in Minecraft plugin development.

## Quick Links

- [Getting Started](getting-started.md) - Installation and basic setup
- [Configuration Reference](configuration.md) - Complete config.yml documentation
- [Architecture Overview](architecture/overview.md) - How the plugin is structured

## Documentation Index

### Core Documentation

| Document | Description |
|----------|-------------|
| [Getting Started](getting-started.md) | Installation, prerequisites, and first run |
| [Configuration](configuration.md) | Complete reference for all config options |

### Architecture

| Document | Complexity | Description |
|----------|------------|-------------|
| [Overview](architecture/overview.md) | Beginner | High-level architecture and component relationships |
| [Initialization Flow](architecture/initialization.md) | Intermediate | Plugin startup sequence and service initialization |
| [Threading & Concurrency](architecture/threading.md) | Advanced | Thread safety patterns and async operations |

### Design Patterns

These documents dive deep into production-grade patterns implemented in the plugin:

| Document | Complexity | Description |
|----------|------------|-------------|
| [Circuit Breaker](patterns/circuit-breaker.md) | Advanced | Fail-fast protection during Discord outages |
| [Retry with Backoff](patterns/retry-backoff.md) | Intermediate | Automatic retry with exponential delays |
| [Timeout Protection](patterns/timeout-protection.md) | Advanced | Preventing hanging operations |
| [Rate Limiting](patterns/rate-limiting.md) | Intermediate | Per-user request throttling |
| [Sealed Results](patterns/sealed-results.md) | Intermediate | Type-safe error handling with sealed classes |

### Features

| Document | Complexity | Description |
|----------|------------|-------------|
| [Discord Commands](features/discord-commands.md) | Intermediate | Slash command registration and handling |
| [Chat Bridge](features/chat-bridge.md) | Intermediate | Two-way Minecraft ↔ Discord messaging |
| [Presence & Status](features/presence-status.md) | Intermediate | Bot activity and player count display |
| [Milestone Announcements](features/milestone-announcements.md) | Intermediate | MCMMO level-up notifications |
| [Image Cards](features/image-cards.md) | Advanced | Visual stats cards with Graphics2D |

### Integrations

| Document | Complexity | Description |
|----------|------------|-------------|
| [MCMMO API](integrations/mcmmo-api.md) | Intermediate | Critical patterns for MCMMO data access |
| [JDA (Discord)](integrations/jda-discord.md) | Intermediate | JDA lifecycle and event handling |
| [Webhooks](integrations/webhooks.md) | Beginner | Discord webhook integration |

### Observability

| Document | Complexity | Description |
|----------|------------|-------------|
| [Metrics](observability/metrics.md) | Intermediate | Built-in performance tracking |
| [Audit Logging](observability/audit-logging.md) | Beginner | Administrative action tracking |
| [Error Handling](observability/error-handling.md) | Intermediate | Exception hierarchy and handling |

### Development

| Document | Complexity | Description |
|----------|------------|-------------|
| [Building](development/building.md) | Beginner | Build commands and shadow JAR |
| [Testing](development/testing.md) | Intermediate | Kotest patterns and MockK |
| [Contributing](development/contributing.md) | Beginner | Code style and contribution guidelines |

## Key Patterns Demonstrated

This plugin showcases several production-grade patterns:

### Resilience Patterns
- **Circuit Breaker**: Prevents cascading failures during Discord outages
- **Exponential Backoff**: Graceful retry with increasing delays
- **Timeout Protection**: Hard limits on operation duration
- **Rate Limiting**: Per-user request throttling

### Architecture Patterns
- **Sealed Class Results**: Type-safe error handling without exceptions
- **Factory Pattern**: Configuration loading and object creation
- **Observer Pattern**: Event-driven Discord and Bukkit listeners
- **Graceful Degradation**: Plugin continues if Discord fails

### Concurrency Patterns
- **Atomic Operations**: Lock-free thread safety
- **ConcurrentHashMap**: Thread-safe caching
- **Debouncing**: Batch rapid events before processing

## Source Code Reference

Key files for understanding the architecture:

| File | Purpose |
|------|---------|
| `AMSSyncPlugin.kt` | Plugin lifecycle and initialization |
| `discord/CircuitBreaker.kt` | Circuit breaker implementation |
| `discord/RetryManager.kt` | Exponential backoff retry logic |
| `discord/TimeoutManager.kt` | Timeout protection |
| `discord/RateLimiter.kt` | Request rate limiting |
| `mcmmo/McmmoApiWrapper.kt` | MCMMO data access layer |
| `exceptions/AMSSyncExceptions.kt` | Sealed exception hierarchy |
| `metrics/ErrorMetrics.kt` | Built-in observability |

## Technology Stack

- **Language**: Kotlin 1.9.21 with Java 21 toolchain
- **Server**: Paper 1.21.4 (compatible with Spigot)
- **Discord**: JDA 5.0.0-beta.18
- **Build**: Gradle with Shadow plugin for dependency relocation
- **Testing**: Kotest with MockK
- **Analysis**: Detekt for static analysis
