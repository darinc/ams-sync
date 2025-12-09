# Getting Started

This guide walks you through installing AMSSync and getting your first Discord commands working.

## Prerequisites

### Required
- **Minecraft Server**: Paper 1.21.x (or compatible Spigot fork)
- **MCMMO Plugin**: Must be installed and working on your server
- **Java**: Version 21 or higher
- **Discord Bot**: A Discord application with bot token

### Creating a Discord Bot

1. Go to [Discord Developer Portal](https://discord.com/developers/applications)
2. Click "New Application" and give it a name
3. Go to "Bot" section and click "Add Bot"
4. Copy the **Bot Token** (you'll need this for config)
5. Under "Privileged Gateway Intents", enable:
   - Server Members Intent (for user lookups)
   - Message Content Intent (for chat bridge)
6. Go to "OAuth2" → "URL Generator"
7. Select scopes: `bot`, `applications.commands`
8. Select permissions: `Send Messages`, `Use Slash Commands`, `Change Nickname` (optional)
9. Copy the generated URL and open it to invite the bot to your server

### Getting Your Guild ID

1. In Discord, go to User Settings → Advanced → Enable "Developer Mode"
2. Right-click your server icon → "Copy ID"
3. Save this ID for the config file

## Installation

### Step 1: Build MCMMO Locally

AMSSync requires MCMMO to be installed in your local Maven repository:

```bash
# Clone and build MCMMO
git clone https://github.com/mcMMO-Dev/mcMMO.git
cd mcMMO
mvn install -DskipTests
```

### Step 2: Build AMSSync

```bash
# Clone the repository
git clone https://github.com/darinc/ams-sync.git
cd ams-sync

# Build the plugin
./gradlew shadowJar

# Output will be in: build/libs/ams-sync-*.jar
```

### Step 3: Install the Plugin

1. Copy `build/libs/ams-sync-*.jar` to your server's `plugins/` folder
2. Start the server to generate default config
3. Stop the server

### Step 4: Configure the Plugin

Edit `plugins/AMSSync/config.yml`:

```yaml
discord:
  # Your bot token from Discord Developer Portal
  token: "YOUR_BOT_TOKEN_HERE"

  # Your Discord server ID
  guild-id: "YOUR_GUILD_ID_HERE"
```

**Security Tip**: Instead of putting secrets in config, use environment variables:

```bash
export AMS_DISCORD_TOKEN="your-bot-token"
export AMS_GUILD_ID="your-guild-id"
```

### Step 5: Start and Verify

1. Start your server
2. Check console for: `AMSSync enabled successfully - Connected to Discord!`
3. In Discord, type `/mcstats` to see available commands

## Available Commands

### Discord Slash Commands

| Command | Description |
|---------|-------------|
| `/mcstats [player]` | View MCMMO stats (text embed) |
| `/mctop [skill]` | View leaderboards (text embed) |
| `/amsstats [player]` | View stats as visual card |
| `/amstop [skill]` | View leaderboard as visual podium |
| `/amssync` | Admin: Link Discord users to Minecraft |

### Minecraft Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/amssync add <discord-id> <username>` | `amssync.admin` | Link Discord to Minecraft |
| `/amssync remove <discord-id>` | `amssync.admin` | Remove a link |
| `/amssync list` | `amssync.admin` | List all mappings |
| `/amssync quick` | `amssync.admin` | Interactive quick-link mode |
| `/amssync metrics` | `amssync.admin` | View plugin health metrics |

## Verifying Installation

### Check Discord Connection

Look for these console messages:
```
[AMSSync] Connecting to Discord...
[AMSSync] Discord connected successfully as BotName#1234
[AMSSync] Registered 5 slash commands
[AMSSync] AMSSync enabled successfully - Connected to Discord!
```

### Test Commands

1. In Discord, type `/mcstats` - should show your stats or prompt to link
2. In-game, run `/amssync metrics` - should show plugin health

### Common Issues

**Bot not responding to commands:**
- Verify guild-id is correct (enable Developer Mode, right-click server)
- Check bot has `applications.commands` scope
- Wait 1 minute for guild commands to register

**"Invalid token" error:**
- Double-check token has no extra spaces
- Regenerate token in Discord Developer Portal
- Try using environment variable instead

**MCMMO data not loading:**
- Ensure MCMMO is installed and players have joined
- Check that MCMMO's `flatfile/` directory has data

## Next Steps

- [Configuration Reference](configuration.md) - Enable more features
- [Architecture Overview](architecture/overview.md) - Understand how it works
- [Design Patterns](patterns/circuit-breaker.md) - Learn the resilience patterns

## Quick Feature Enable

### Enable Chat Bridge

```yaml
discord:
  chat-bridge:
    enabled: true
    channel-id: "YOUR_CHANNEL_ID"
    use-webhook: true
    webhook-url: "YOUR_WEBHOOK_URL"
```

### Enable Player Count Status

```yaml
discord:
  presence:
    enabled: true
    activity:
      enabled: true
      template: "{count}/{max} players online"
```

### Enable Milestone Announcements

```yaml
discord:
  announcements:
    enabled: true
    text-channel-id: "YOUR_CHANNEL_ID"
    skill-milestone-interval: 100
    use-image-cards: true
```

See [Configuration Reference](configuration.md) for complete options.
