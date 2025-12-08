# AMSSync

A Paper/Spigot Minecraft plugin that bridges Discord and MCMMO for the Amazing Minecraft Server (AMS). This plugin embeds a Discord bot directly into your Minecraft server, allowing Discord users to check MCMMO stats, view leaderboards, and interact with server data through slash commands.

## Overview

AMSSync eliminates the need for separate bot hosting by integrating Discord functionality directly into your Minecraft server plugin. It provides a seamless experience for players to track their MCMMO progression from Discord, with intelligent user linking for easy administration.

## Key Features

### Discord Integration
- **Embedded Discord Bot**: JDA-powered bot runs within the plugin (no separate process needed)
- **Slash Commands**: Modern Discord slash command interface
- **Real-time Stats**: Live MCMMO data from both online and offline players
- **Automatic Reconnection**: Built-in retry logic with exponential backoff
- **Event Announcements**: Server start/stop, player deaths, and achievements
- **Webhook Support**: Optional webhooks for custom avatars and usernames

### MCMMO Integration
- **Full Stats Access**: Query individual skill levels, power levels, and leaderboards
- **Flatfile Support**: Direct DatabaseManager integration for reading offline player data
- **Cached Leaderboards**: 60-second cache to prevent server strain (configurable)
- **Timeout Protection**: Configurable timeout manager prevents long-running queries from blocking

### User Linking System
- **Quick Linking**: Fast 2-command workflow with `/amssync quick`
- **Number-Based Selection**: Session-based numbered lists (no copying/pasting Discord IDs)
- **Session Management**: 5-minute auto-expiring sessions per admin
- **Discord Name Display**: Shows Discord display names instead of IDs for better UX
- **Discord Admin Commands**: Server admins can manage links directly from Discord using `/amssync`

## Commands

### Discord Slash Commands

#### Player Commands

**`/mcstats [skill]`** - View your MCMMO stats
- **Without skill**: Shows all skill levels and total power level
- **With skill**: Shows specific skill level (e.g., `/mcstats mining`)
- Requires your Discord account to be linked to a Minecraft username
- Works for both online and offline players

**`/mctop [skill]`** - View MCMMO leaderboards
- **Without skill**: Shows top 10 players by power level (sum of all skills)
- **With skill**: Shows top 10 players for specific skill (e.g., `/mctop mining`)
- Displays leaderboard with medal emojis for top 3 places
- Cached for 60 seconds to prevent server strain

#### Admin Commands

**`/amssync`** - Manage Discord-to-Minecraft user links (requires Manage Server permission)
- **`/amssync add <user> <minecraft_username>`** - Link a Discord user to Minecraft player
- **`/amssync remove <user>`** - Remove a user's link
- **`/amssync list`** - Show all current links
- **`/amssync check <user>`** - Check if a user is linked

### Minecraft Console/In-Game Commands

**`/amssync players`** - List online/whitelisted players with link status
- Shows numbered list of players for easy reference
- Displays which players are already linked to Discord
- Session data expires after 5 minutes

**`/amssync discord`** - List Discord server members with link status
- Shows numbered list of Discord members
- Displays which members are already linked to Minecraft
- Session data expires after 5 minutes

**`/amssync list`** - View all current Discord - Minecraft mappings
- Shows Discord display names (not IDs) for better readability
- Indicates if Discord members are no longer in the server

**`/amssync quick [player#] [discord#]`** - Fast linking workflow (recommended)
- **Without numbers**: Shows both player and Discord lists side-by-side
- **With numbers**: Immediately links the selected player and Discord member
- Easy 2-command workflow for quick linking

**`/amssync link <player#> <discord#>`** - Direct number-based linking
- Uses session data from previous `/amssync players` or `/amssync discord` commands
- Numbers reference the lists shown in those commands
- Session expires after 5 minutes of inactivity

**`/amssync add <discordId> <mcUsername>`** - Traditional linking (for advanced users)
- Directly link using Discord ID (18-digit number)
- Requires copying Discord ID with Developer Mode enabled
- Most users should use quick or link commands instead

**`/amssync remove <discordId|number>`** - Remove a user link
- Use Discord ID or number from session list

**`/amssync metrics`** - Show plugin health metrics
- Discord API success/failure rates
- Circuit breaker state
- Connection statistics

## Requirements

- **Server**: Paper 1.21.4 or compatible Spigot fork
- **MCMMO**: Version 2.2.044-SNAPSHOT or compatible (with flatfile or SQL storage)
- **Java**: Java 21 or higher
- **Discord Bot**: Discord application with bot token and Server Members Intent enabled

## Installation

### 1. Build the Plugin

First, ensure MCMMO is installed to your local Maven repository:

```bash
cd /path/to/mcMMO
mvn install -DskipTests
```

Then build AMSSync:

```bash
./gradlew shadowJar
```

The plugin JAR will be in `build/libs/ams-sync-*.jar`

### 2. Install on Server

Copy the JAR to your server's `plugins/` directory:

```bash
cp build/libs/ams-sync-*.jar /path/to/server/plugins/
```

### 3. Configure the Plugin

Start the server once to generate the default config, then edit `plugins/AMSSync/config.yml`:

```yaml
discord:
  token: "YOUR_BOT_TOKEN_HERE"
  guild-id: "YOUR_GUILD_ID_HERE"
  connection:
    max-retry-attempts: 5
    initial-backoff-ms: 1000
    max-backoff-ms: 30000

mcmmo:
  leaderboard:
    max-players-to-scan: 1000
    cache-ttl-ms: 60000

timeout:
  enabled: true
  default-timeout-ms: 5000

user-mappings:
  # Managed via /amssync command - no need to edit manually
```

**Important**: The config file in `plugins/AMSSync/` contains secrets. Never commit it to git!

### 4. Discord Bot Setup

1. Create a Discord application at https://discord.com/developers/applications
2. Create a bot user and copy the token
3. Enable "Server Members Intent" in Bot settings
4. Invite the bot to your server with these permissions:
   - Scopes: `bot`, `applications.commands`
   - Bot Permissions: Send Messages, Embed Links

### 5. Link Discord Users to Minecraft Players

#### Recommended: Quick Linking

The easiest way to link users is with the quick command:

```
/amssync quick              # Shows both lists
/amssync quick 1 5          # Immediately links player #1 to Discord member #5
```

This provides a fast 2-command workflow:
1. Run `/amssync quick` to see numbered lists of players and Discord members
2. Run `/amssync quick <player#> <discord#>` to create the link

#### Alternative: Manual Linking (Advanced)

If you prefer the traditional method:

1. Enable Developer Mode in Discord (User Settings - Advanced - Developer Mode)
2. Right-click the user in Discord - Copy ID
3. Run `/amssync add <pastedId> <MinecraftUsername>`

All mappings are automatically saved to the config file.

## Configuration

### discord.token

Your Discord bot token from the Discord Developer Portal.

**Required**: Yes

### discord.guild-id

Your Discord server (guild) ID. This enables instant slash command registration.
- Right-click your server icon - Copy ID (requires Developer Mode enabled in Discord settings)
- If not provided, commands register globally (may take up to 1 hour to appear)

**Required**: Recommended (for instant command registration)

### discord.connection

Discord connection retry settings:
- **max-retry-attempts**: Number of connection attempts before giving up (default: 5)
- **initial-backoff-ms**: Initial retry delay in milliseconds (default: 1000)
- **max-backoff-ms**: Maximum retry delay with exponential backoff (default: 30000)

### mcmmo.leaderboard

MCMMO leaderboard performance settings:
- **max-players-to-scan**: Maximum number of offline players to scan for leaderboards (default: 1000)
  - Prevents timeouts on servers with many players
  - Increase if you want more complete leaderboards on large servers
- **cache-ttl-ms**: Cache duration for leaderboard results in milliseconds (default: 60000 = 60 seconds)
  - Reduces database load by caching results
  - Lower values = more up-to-date but more server load

### timeout

Query timeout protection:
- **enabled**: Enable/disable timeout manager (default: true)
- **default-timeout-ms**: Maximum time for MCMMO queries before cancellation (default: 5000 = 5 seconds)

### discord.events

Event announcements to Discord:
- **enabled**: Enable/disable event announcements (default: false)
- **text-channel-id**: Channel ID for event messages
- **webhook-url**: Optional webhook URL for custom avatars (leave empty for bot messages)
- **use-embeds**: Use rich embeds or plain text (default: true)
- **show-avatars**: Include player Minecraft avatars in messages (default: true)
- **avatar-provider**: Avatar service - "mc-heads" or "crafatar" (default: mc-heads)
- **server-start.enabled**: Announce server start (default: true)
- **server-start.message**: Custom start message
- **server-stop.enabled**: Announce server stop (default: true)
- **server-stop.message**: Custom stop message
- **player-deaths.enabled**: Announce player deaths (default: true)
- **achievements.enabled**: Announce advancements (default: true)
- **achievements.exclude-recipes**: Skip recipe unlock advancements (default: true)

#### Webhook vs Bot Messages

When `webhook-url` is configured:
- Messages show the player's Minecraft head as the sender avatar
- Username appears as the player's name instead of the bot
- Creates a more immersive experience

When `webhook-url` is empty (default):
- Messages are sent as the bot
- Player avatars appear as embed thumbnails
- Works out of the box with no extra setup

To create a webhook: Channel Settings → Integrations → Webhooks → New Webhook

### user-mappings

Discord ID to Minecraft username mappings.

**Management**: Automatically managed via the `/amssync` command - no manual editing needed!

## Architecture

```
AMSSyncPlugin (Main)
├── DiscordManager - JDA lifecycle management & slash command registration
│   ├── Retry logic with exponential backoff
│   └── Connection status monitoring
│
├── UserMappingService - Discord - Minecraft linking with config persistence
│
├── McmmoApiWrapper - MCMMO API integration
│   ├── Direct DatabaseManager flatfile access
│   ├── Leaderboard caching (60s TTL)
│   ├── Query limiting (max players to scan)
│   └── Power level calculation
│
├── TimeoutManager - Query timeout protection
│   ├── Configurable timeout thresholds
│   └── Async task cancellation
│
├── LinkingSessionManager - Session-based number mapping
│   ├── Per-user session isolation
│   ├── 5-minute auto-expiration
│   ├── ConcurrentHashMap for thread safety
│   └── Automatic cleanup task
│
├── WebhookManager - Discord webhook/bot message handling
│   ├── Optional webhook support with custom avatars
│   ├── Fallback to standard bot messages
│   └── Circuit breaker integration
│
├── Event Listeners
│   ├── ServerEventListener - Server start/stop announcements
│   ├── PlayerDeathListener - Death announcements with avatars
│   └── AchievementListener - Advancement announcements
│
├── SlashCommandListener (Discord)
│   ├── McStatsCommand - /mcstats handler
│   ├── McTopCommand - /mctop handler
│   └── DiscordLinkCommand - /amssync admin commands
│
└── AMSSyncCommand (Minecraft) - In-game linking commands
    ├── Quick linking (2-command workflow)
    ├── Number-based linking (session references)
    └── Traditional ID-based linking
```

## Development

### Project Structure

```
ams-sync/
├── build.gradle.kts
├── src/main/
│   ├── kotlin/io/github/darinc/amssync/
│   │   ├── AMSSyncPlugin.kt
│   │   │
│   │   ├── commands/
│   │   │   ├── AMSSyncCommand.kt          # Minecraft in-game linking
│   │   │   └── LinkingSession.kt          # Session management
│   │   │
│   │   ├── discord/
│   │   │   ├── DiscordManager.kt          # JDA lifecycle
│   │   │   ├── SlashCommandListener.kt    # Command routing
│   │   │   ├── TimeoutManager.kt          # Query timeout protection
│   │   │   ├── WebhookManager.kt          # Webhook/bot message handling
│   │   │   └── commands/
│   │   │       ├── DiscordLinkCommand.kt  # Discord /amssync admin
│   │   │       ├── McStatsCommand.kt      # /mcstats handler
│   │   │       └── McTopCommand.kt        # /mctop handler
│   │   │
│   │   ├── events/
│   │   │   ├── EventAnnouncementConfig.kt # Event configuration
│   │   │   ├── ServerEventListener.kt     # Start/stop announcements
│   │   │   ├── PlayerDeathListener.kt     # Death announcements
│   │   │   └── AchievementListener.kt     # Advancement announcements
│   │   │
│   │   ├── exceptions/
│   │   │   └── AMSSyncExceptions.kt       # Custom exception hierarchy
│   │   │
│   │   ├── linking/
│   │   │   └── UserMappingService.kt      # Mapping persistence
│   │   │
│   │   └── mcmmo/
│   │       └── McmmoApiWrapper.kt         # MCMMO API wrapper
│   │
│   └── resources/
│       ├── plugin.yml
│       └── config.yml
│
└── README.md
```

### Tech Stack

- **Language**: Kotlin 1.9.21
- **Discord Library**: JDA 5.0.0-beta.18
- **Async**: Kotlin Coroutines
- **Build**: Gradle with Shadow plugin (for fat JAR)

### Building

```bash
./gradlew build
```

The shaded JAR with all dependencies will be in `build/libs/`.

## Error Handling

The plugin provides robust error handling for common scenarios:

### Connection Errors
- **Discord connection failures**: Automatic retry with exponential backoff (up to 5 attempts)
- **Missing MCMMO plugin**: Startup dependency check with clear error message
- **Discord bot not in guild**: Warning logged when guild ID is invalid

### User Errors
- **Unlinked Discord accounts**: User-friendly ephemeral error message with instructions
- **Invalid skill names**: Error message includes list of valid skills
- **Player not found**: Clear error distinguishing between "never joined" and "no MCMMO data"
- **Session expired**: Helpful message indicating 5-minute timeout with remaining time shown

### Performance Protection
- **Query timeouts**: Configurable 5-second timeout prevents blocking Discord responses
- **Large player counts**: Warning when scanning is limited (shows actual vs scanned player count)
- **Leaderboard caching**: Prevents rapid repeated queries from straining the server

### Data Integrity
- **MCMMO profile not loaded**: Distinguishes between missing player vs missing MCMMO data
- **Missing Discord members**: Handles cases where linked Discord users leave the server
- **Concurrent access**: Thread-safe session management with ConcurrentHashMap

## Permissions

- `amssync.admin` - Administrator permissions, required for in-game `/amssync` commands (default: op)
- `amssync.link` - Link Discord account (default: true, reserved for future self-service linking)

## Logging

The plugin provides detailed logging to the server console:
- **INFO**: Lifecycle events (Discord connection, command registration, successful operations)
- **FINE**: Debug information (cache hits, player lookups, session management)
- **WARNING**: Recoverable errors (player not found, invalid skills, retry attempts)
- **SEVERE**: Critical failures that prevent operation (missing dependencies, config errors)

### Example Log Output

```
[INFO] [AMSSync] Enabling AMSSync v0.5.0
[INFO] [AMSSync] Connecting to Discord...
[INFO] [AMSSync] Discord bot is ready! Connected as AMS Bot#1234
[INFO] [AMSSync] Slash commands registered to guild: Amazing Minecraft Server
[FINE] [AMSSync] Returning cached leaderboard for MINING
[INFO] [AMSSync] Successfully linked  Steve- darin.c#0000!
```

## Technical Details

### MCMMO Flatfile Integration

The plugin uses MCMMO's `DatabaseManager` API for direct flatfile access:

```kotlin
val profile = mcMMO.getDatabaseManager().loadPlayerProfile(offlinePlayer.uniqueId)
```

This approach correctly reads offline player data from MCMMO's flatfile storage, unlike the `UserManager` API which only works for currently loaded (online) players.

### Session Management

User sessions are managed per-admin with automatic cleanup:
- Sessions store numbered mappings for players and Discord members
- Each session is isolated to the command sender (UUID or "CONSOLE")
- Sessions auto-expire after 5 minutes of inactivity
- Background cleanup task runs every minute to remove expired sessions
- Thread-safe using `ConcurrentHashMap`

### Power Level Calculation

Power level is calculated by summing all non-child skill levels:

```kotlin
val powerLevel = PrimarySkillType.values()
    .filter { !it.isChildSkill }
    .sumOf { profile.getSkillLevel(it) }
```

## Future Enhancements

Potential features for future releases:
- [ ] Self-service `/link` command with verification codes
- [ ] Skill-specific role assignments (e.g., "Master Miner" role at Mining 1000)
- [ ] Database storage for mappings (MySQL/PostgreSQL support)
- [ ] Web dashboard for link management
- [ ] Player statistics dashboard in Discord
- [ ] Custom embed colors and formatting options

## Usage Examples

### Example 1: Quick Linking

In Minecraft console:
```
> /amssync quick
[Shows both player and Discord lists side-by-side]

> /amssync quick 1 5
Linking  Steve(#1) to  discord_user(#5)...
Successfully linked  Steve- darin.c!
```

### Example 2: Player Checking Stats in Discord

In Discord:
```
User: /mcstats
Bot: MCMMO Stats for CtrlAltDC
     Mining: 450
     Woodcutting: 320
     ... (all skills)
     Power Level: 2,450

User: /mcstats mining
Bot: Mining Level for CtrlAltDC
     Level: 450

User: /mctop
Bot: Top 10 - Power Level
     1.  Steve- 2,450
     2. NothingTV - 2,100
     3. PlayerX - 1,850
     ...

User: /mctop mining
Bot: Top 10 - Mining
     1. NothingTV - 500
     2.  Steve- 450
     3. PlayerX - 400
     ...
```

### Example 3: Admin Managing Links from Discord

Discord server admin (with Manage Server permission):
```
Admin: /amssync list
Bot: Current Discord-Minecraft Links:
      discord_user- CtrlAltDC
     otheruser - PlayerX
     newplayer - NothingTV

Admin: /amssync add @newuser MinecraftPlayer
Bot: Successfully linked newuser to MinecraftPlayer

Admin: /amssync check @darin.c
Bot:  discord_useris linked to: CtrlAltDC

Admin: /amssync remove @olduser
Bot: Removed link for olduser
```

## License

[Add your license here]

## Support

For issues and feature requests, please open an issue on GitHub.
