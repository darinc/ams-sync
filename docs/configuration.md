# Configuration Reference

Complete reference for `plugins/AMSSync/config.yml`. All settings with their defaults, descriptions, and recommendations.

## Security Note

**Never commit your bot token to version control!**

Recommended approaches:
1. Add `plugins/AMSSync/config.yml` to `.gitignore`
2. Use environment variables (see below)

## Environment Variables

These override config file values:

| Variable | Config Path | Description |
|----------|-------------|-------------|
| `AMS_DISCORD_TOKEN` | `discord.token` | Bot token |
| `AMS_GUILD_ID` | `discord.guild-id` | Discord server ID |

```bash
export AMS_DISCORD_TOKEN="your-bot-token"
export AMS_GUILD_ID="123456789012345678"
```

---

## Discord Core Settings

### Token and Guild

```yaml
discord:
  # REQUIRED: Your Discord bot token
  # Get from: https://discord.com/developers/applications -> Bot -> Token
  token: "YOUR_BOT_TOKEN_HERE"

  # RECOMMENDED: Your Discord server ID for instant command registration
  # Without this, commands register globally (can take up to 1 hour)
  # Get by: Right-click server -> Copy ID (Developer Mode required)
  guild-id: "YOUR_GUILD_ID_HERE"
```

---

## Resilience Settings

These settings control how the plugin handles failures and protects against cascading issues.

### Retry Configuration

Implements exponential backoff for Discord connection failures.

```yaml
discord:
  retry:
    # Enable/disable retry logic
    # true = retry on failure, false = fail immediately
    enabled: true

    # Maximum connection attempts (including initial)
    # Recommended: 3-5 for production
    max-attempts: 5

    # Initial delay before first retry (seconds)
    initial-delay-seconds: 5

    # Maximum delay between retries (seconds)
    # Caps the exponential growth
    max-delay-seconds: 300

    # Backoff multiplier
    # 2.0 = delays double: 5s, 10s, 20s, 40s, 80s...
    backoff-multiplier: 2.0
```

**How it works**: If connection fails, waits `initial-delay`, then retries. Each subsequent failure doubles the wait time until `max-delay` is reached.

### Timeout Protection

Prevents operations from hanging indefinitely.

```yaml
discord:
  timeout:
    # Enable/disable timeout protection
    enabled: true

    # Warning threshold (seconds)
    # Logs warning if operation takes longer
    # Helps identify slow operations
    warning-threshold-seconds: 3

    # Hard timeout (seconds)
    # Operation is cancelled after this duration
    hard-timeout-seconds: 10
```

**Use case**: Discord API can occasionally hang. This ensures your server thread isn't blocked forever.

### Circuit Breaker

Prevents cascading failures by failing fast when Discord is down.

```yaml
discord:
  circuit-breaker:
    # Enable/disable circuit breaker
    enabled: true

    # Failures before circuit opens
    failure-threshold: 5

    # Time window for counting failures (seconds)
    time-window-seconds: 60

    # Cooldown before testing recovery (seconds)
    cooldown-seconds: 30

    # Successes needed to close circuit
    success-threshold: 2
```

**States**:
- **CLOSED**: Normal operation
- **OPEN**: Failing fast (all requests rejected immediately)
- **HALF_OPEN**: Testing if service recovered

See [Circuit Breaker Pattern](patterns/circuit-breaker.md) for detailed documentation.

---

## Player Count Presence

Shows player count in bot status and/or nickname.

```yaml
discord:
  presence:
    # Master toggle for all presence features
    enabled: true

    # Minimum seconds between Discord updates
    # Discord limits to ~5/minute, recommend 30-60
    min-update-interval-seconds: 30

    # Debounce delay (seconds)
    # Batches rapid player join/quit events
    debounce-seconds: 5

    # Bot activity/status display
    activity:
      enabled: true
      # Options: playing, watching, listening, competing
      type: "playing"
      # Placeholders: {count}, {max}
      template: "{count} players online"

    # Bot nickname (per-guild)
    # REQUIRES: CHANGE_NICKNAME permission
    nickname:
      enabled: false
      # Placeholders: {count}, {max}, {name}
      template: "[{count}] {name}"
      # Continue if nickname change fails
      graceful-fallback: true
```

---

## Status Channel

Updates a voice channel name with player count.

```yaml
discord:
  status-channel:
    enabled: false
    # Voice channel ID to update
    voice-channel-id: ""
    # Placeholders: {count}, {max}
    template: "{count} AMS Online"
    # Update interval (seconds)
    # Discord limits: 2 renames per 10 minutes
    # Recommend: 300+ seconds
    update-interval-seconds: 300
```

**Note**: Discord heavily rate-limits channel renames. Updates are automatically batched.

---

## MCMMO Announcements

Posts milestone achievements to Discord.

```yaml
discord:
  announcements:
    enabled: false
    # Text channel for announcements
    text-channel-id: ""
    # Optional webhook for custom avatars
    webhook-url: ""
    # Announce at skill level multiples (0 = disabled)
    # 100 = announce at 100, 200, 300...
    skill-milestone-interval: 100
    # Announce at power level multiples (0 = disabled)
    power-milestone-interval: 500
    # Use embeds (ignored if image cards enabled)
    use-embeds: true
    # Generate visual celebration cards
    use-image-cards: true
    # Show player Minecraft avatars
    show-avatars: true
    # Avatar provider: "mc-heads" or "crafatar"
    avatar-provider: "mc-heads"
```

---

## Event Announcements

Announces server events to Discord.

```yaml
discord:
  events:
    enabled: false
    text-channel-id: ""
    webhook-url: ""
    use-embeds: true
    show-avatars: true
    avatar-provider: "mc-heads"

    server-start:
      enabled: true
      message: "Server is now online!"

    server-stop:
      enabled: true
      message: "Server is shutting down..."

    player-deaths:
      enabled: true

    achievements:
      enabled: true
      # Skip recipe unlocks
      exclude-recipes: true
```

---

## Chat Bridge

Two-way messaging between Minecraft and Discord.

```yaml
discord:
  chat-bridge:
    enabled: false
    # Text channel for chat relay
    channel-id: ""
    # Direction toggles
    minecraft-to-discord: true
    discord-to-minecraft: true

    # Format for Discord -> Minecraft
    # Placeholders: {author}, {message}
    # Supports & color codes
    mc-format: "&7[Discord] &b{author}&7: {message}"

    # Format for Minecraft -> Discord
    # Placeholders: {player}, {message}
    # Supports Discord markdown
    # NOTE: Ignored when use-webhook is true
    discord-format: "**{player}**: {message}"

    # Prefixes to ignore (don't relay commands)
    ignore-prefixes:
      - "/"

    # Suppress Discord notifications
    # Only works when use-webhook is false
    suppress-notifications: true

    # Use webhook for player avatars
    use-webhook: false
    webhook-url: ""
    # Avatar provider: "mc-heads" or "crafatar"
    avatar-provider: "mc-heads"
```

**Webhook mode**: When `use-webhook: true`, messages appear with the player's Minecraft head as the avatar and their username as the sender.

---

## Image Cards

Visual stats cards for Discord commands.

```yaml
image-cards:
  # Enable image card generation
  # When false, /amsstats and /amstop show text fallback
  enabled: true

  # Avatar provider
  # mc-heads: Uses player name (faster)
  # crafatar: Uses player UUID (accurate for name changes)
  avatar-provider: "mc-heads"

  # Server name in card footer
  server-name: "Minecraft Server"

  # Avatar cache settings
  avatar-cache-ttl-seconds: 300
  avatar-cache-max-size: 100
```

---

## Rate Limiting

Prevents command spam and abuse.

```yaml
rate-limiting:
  enabled: true

  # Penalty after exceeding limit (milliseconds)
  penalty-cooldown-ms: 10000

  # Max commands per minute per user
  max-requests-per-minute: 60
```

---

## MCMMO Settings

```yaml
mcmmo:
  leaderboard:
    # Max players to scan for leaderboards
    # Higher = more complete but slower
    # Recommended: 1000 for servers with <5000 players
    max-players-to-scan: 1000

    # Cache duration (seconds)
    # 0 = no caching (not recommended)
    cache-ttl-seconds: 60
```

**Performance tip**: Caching provides ~60x performance improvement on repeat queries.

---

## User Mappings

Managed automatically by `/amssync` commands. Format reference:

```yaml
user-mappings:
  # Format: "discord_id": "MinecraftUsername"
  "123456789012345678": "Steve"
  "987654321098765432": "Alex"
```

**Important**: Values are Minecraft **usernames**, not UUIDs.

---

## Configuration Patterns

### Minimal Production Config

```yaml
discord:
  token: ""  # Use AMS_DISCORD_TOKEN env var
  guild-id: ""  # Use AMS_GUILD_ID env var
  retry:
    enabled: true
  timeout:
    enabled: true
  circuit-breaker:
    enabled: true

rate-limiting:
  enabled: true
```

### Full-Featured Config

```yaml
discord:
  token: ""
  guild-id: ""

  presence:
    enabled: true
    activity:
      enabled: true
      template: "{count}/{max} online"

  chat-bridge:
    enabled: true
    channel-id: "CHAT_CHANNEL_ID"
    use-webhook: true
    webhook-url: "WEBHOOK_URL"

  announcements:
    enabled: true
    text-channel-id: "ANNOUNCE_CHANNEL_ID"
    use-image-cards: true

  events:
    enabled: true
    text-channel-id: "EVENTS_CHANNEL_ID"

image-cards:
  enabled: true
  server-name: "My Awesome Server"
```
