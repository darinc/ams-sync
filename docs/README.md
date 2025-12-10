# AMSSync Documentation

A Paper/Spigot Minecraft plugin that bridges Discord and MCMMO for the Amazing Minecraft Server (AMS). This plugin embeds a Discord bot directly into your server, providing MCMMO stats integration, visual player cards, bidirectional chat, and milestone announcements.

## Visual Player Cards

Generate stunning player stats cards and Olympic podium leaderboards directly in Discord!

<p align="center">
  <img src="images/stats-card-legendary.png" alt="Legendary Stats Card" width="400"/>
  <img src="images/leaderboard-power-example.png" alt="Power Leaderboard" width="400"/>
</p>

- **`/amsstats`** - Beautiful player cards with full body skin render, all skills with progress bars, and rarity classification
- **`/amstop`** - Olympic-style podium leaderboards with player avatars and crown for 1st place
- **Rarity System** - Cards display Common, Rare, Epic, or Legendary based on power level
- **Mastery Stars** - Skills at level 2000+ display golden mastery stars

<p align="center">
  <img src="images/stats-card-common.png" alt="Common" width="200"/>
  <img src="images/stats-card-rare.png" alt="Rare" width="200"/>
  <img src="images/stats-card-epic.png" alt="Epic" width="200"/>
  <img src="images/stats-card-legendary.png" alt="Legendary" width="200"/>
</p>

## Key Features

### Bidirectional Chat Bridge
Seamlessly connect your Minecraft server chat with Discord - messages flow both ways in real-time with optional webhook support for player avatars.

### MCMMO Milestone Announcements
Celebrate player achievements with automatic Discord announcements when players hit skill milestones with visual celebration cards.

### Discord Integration
Embedded JDA-powered Discord bot with modern slash commands, automatic reconnection with exponential backoff, and event announcements.

### Whitelist Management
Manage your server whitelist directly from Discord with `/amswhitelist` - add, remove, list, and check players without touching the console.

## Quick Start

- [Getting Started](getting-started.md) - Installation and basic setup
- [Configuration Reference](configuration.md) - Complete config.yml documentation

## Documentation

### Features

| Document | Description |
|----------|-------------|
| [Image Cards](features/image-cards.md) | Visual stats cards and leaderboard generation |
| [Chat Bridge](features/chat-bridge.md) | Two-way Minecraft ↔ Discord messaging |
| [Milestone Announcements](features/milestone-announcements.md) | MCMMO level-up notifications |
| [Discord Commands](features/discord-commands.md) | Slash command registration and handling |
| [Presence & Status](features/presence-status.md) | Bot activity and player count display |

### Architecture

| Document | Description |
|----------|-------------|
| [Overview](architecture/overview.md) | High-level architecture and component relationships |
| [Initialization Flow](architecture/initialization.md) | Plugin startup sequence and service initialization |
| [Threading & Concurrency](architecture/threading.md) | Thread safety patterns and async operations |

### Design Patterns

Production-grade patterns implemented in the plugin:

| Document | Description |
|----------|-------------|
| [Circuit Breaker](patterns/circuit-breaker.md) | Fail-fast protection during Discord outages |
| [Retry with Backoff](patterns/retry-backoff.md) | Automatic retry with exponential delays |
| [Timeout Protection](patterns/timeout-protection.md) | Preventing hanging operations |
| [Rate Limiting](patterns/rate-limiting.md) | Per-user request throttling |
| [Sealed Results](patterns/sealed-results.md) | Type-safe error handling with sealed classes |

### Integrations

| Document | Description |
|----------|-------------|
| [MCMMO API](integrations/mcmmo-api.md) | Critical patterns for MCMMO data access |
| [JDA (Discord)](integrations/jda-discord.md) | JDA lifecycle and event handling |
| [Webhooks](integrations/webhooks.md) | Discord webhook integration |

### Observability

| Document | Description |
|----------|-------------|
| [Metrics](observability/metrics.md) | Built-in performance tracking |
| [Audit Logging](observability/audit-logging.md) | Administrative action tracking |
| [Error Handling](observability/error-handling.md) | Exception hierarchy and handling |

### Development

| Document | Description |
|----------|-------------|
| [Building](development/building.md) | Build commands and shadow JAR |
| [Testing](development/testing.md) | Kotest patterns and MockK |
| [Contributing](development/contributing.md) | Code style and contribution guidelines |

## Technology Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin 1.9.21 with Java 21 |
| Server | Paper 1.21.4 (Spigot compatible) |
| Discord | JDA 5.0.0-beta.18 |
| Build | Gradle with Shadow plugin |
| Testing | Kotest with MockK |
| Analysis | Detekt |

## Source Code Reference

Key files for understanding the architecture:

| File | Purpose |
|------|---------|
| `AMSSyncPlugin.kt` | Plugin lifecycle and initialization |
| `image/PlayerCardRenderer.kt` | Stats and leaderboard card generation |
| `discord/ChatBridge.kt` | Bidirectional chat relay |
| `discord/CircuitBreaker.kt` | Circuit breaker implementation |
| `discord/RetryManager.kt` | Exponential backoff retry logic |
| `mcmmo/McmmoApiWrapper.kt` | MCMMO data access layer |
| `mcmmo/McMMOEventListener.kt` | Milestone tracking |

## License

MIT License - see [LICENSE](../LICENSE) for details.
