# JDA Discord Integration

**Complexity**: Intermediate
**Key File**: [`discord/DiscordManager.kt`](../../src/main/kotlin/io/github/darinc/amssync/discord/DiscordManager.kt)

## Overview

AMSSync uses [JDA (Java Discord API)](https://github.com/DV8FromTheWorld/JDA) for Discord integration. This document covers JDA lifecycle management, event handling, and slash command registration.

## JDA Lifecycle

```
┌─────────────────────────────────────────────────────────────┐
│                    JDA Lifecycle                             │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌─────────┐                                                 │
│  │ Create  │  JDABuilder.createDefault(token)                │
│  └────┬────┘                                                 │
│       │                                                      │
│       ▼                                                      │
│  ┌─────────┐                                                 │
│  │Configure│  .enableIntents(...) .addEventListeners(...)    │
│  └────┬────┘                                                 │
│       │                                                      │
│       ▼                                                      │
│  ┌─────────┐                                                 │
│  │ Build   │  .build()                                       │
│  └────┬────┘                                                 │
│       │                                                      │
│       ▼                                                      │
│  ┌─────────┐                                                 │
│  │  Wait   │  .awaitReady() - blocks until connected         │
│  └────┬────┘                                                 │
│       │                                                      │
│       ▼                                                      │
│  ┌─────────┐                                                 │
│  │ Ready!  │  Bot is online, register commands               │
│  └────┬────┘                                                 │
│       │                                                      │
│       ▼                                                      │
│  ┌─────────┐                                                 │
│  │Shutdown │  .shutdownNow() on plugin disable               │
│  └─────────┘                                                 │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

## Gateway Intents

JDA requires declaring which events you need access to:

```kotlin
jda = JDABuilder.createDefault(token)
    .enableIntents(
        GatewayIntent.GUILD_MEMBERS,   // User lookups for linking
        GatewayIntent.GUILD_MESSAGES,  // Chat bridge - receive messages
        GatewayIntent.MESSAGE_CONTENT  // Chat bridge - read message content
    )
    .addEventListeners(SlashCommandListener(plugin, amsStatsCommand, amsTopCommand))
    .build()
    .awaitReady()
```

> **IMPORTANT**: `MESSAGE_CONTENT` is a privileged intent. Enable it in Discord Developer Portal under Bot → Privileged Gateway Intents.

## Slash Command Registration

### Guild vs Global Commands

```kotlin
private fun registerSlashCommands(guildId: String) {
    val commands = listOf(
        Commands.slash("mcstats", "View MCMMO stats")
            .addOption(OptionType.STRING, "username", "Minecraft username", false),
        // ... more commands
    )

    if (guildId.isNotBlank() && guildId != "YOUR_GUILD_ID_HERE") {
        // Guild-specific: instant registration
        val guild = jda?.getGuildById(guildId)
        guild?.updateCommands()?.addCommands(commands)?.queue(
            { logger.info("Commands registered to guild") },
            { error -> logger.warning("Registration failed: ${error.message}") }
        )
    } else {
        // Global: up to 1 hour propagation
        jda?.updateCommands()?.addCommands(commands)?.queue(
            { logger.info("Commands registered globally (may take up to 1 hour)") },
            { error -> logger.warning("Registration failed: ${error.message}") }
        )
    }
}
```

**Recommendation**: Always configure `guild-id` for faster development iteration.

### Command Definitions

```kotlin
// Player commands
Commands.slash("mcstats", "View MCMMO stats for yourself or another player")
    .addOption(OptionType.STRING, "username", "Minecraft or Discord username", false)
    .addOption(OptionType.STRING, "skill", "View specific skill stats", false)

Commands.slash("mctop", "View MCMMO leaderboard")
    .addOption(OptionType.STRING, "skill", "Skill for leaderboard", false)

// Visual card commands
Commands.slash("amsstats", "View MCMMO stats as a visual player card")
    .addOption(OptionType.STRING, "username", "Player to look up", false)

Commands.slash("amstop", "View MCMMO leaderboard as a visual podium card")
    .addOption(OptionType.STRING, "skill", "Skill for leaderboard", false)

// Admin command with subcommands
Commands.slash("amssync", "Admin: Link Discord users to Minecraft players")
    .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))
    .setGuildOnly(true)
    .addSubcommands(
        SubcommandData("add", "Link a Discord user")
            .addOption(OptionType.USER, "user", "Discord user", true)
            .addOption(OptionType.STRING, "minecraft_username", "MC username", true),
        SubcommandData("remove", "Unlink a Discord user")
            .addOption(OptionType.USER, "user", "Discord user", true),
        SubcommandData("list", "Show all links"),
        SubcommandData("check", "Check if user is linked")
            .addOption(OptionType.USER, "user", "Discord user", true)
    )
```

## Event Listener Pattern

```kotlin
class SlashCommandListener(
    private val plugin: AMSSyncPlugin,
    private val amsStatsCommand: AmsStatsCommand?,
    private val amsTopCommand: AmsTopCommand?
) : ListenerAdapter() {

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        // Check rate limiter first
        val rateLimiter = plugin.rateLimiter
        if (rateLimiter != null) {
            when (val result = rateLimiter.checkRateLimit(event.user.id)) {
                is RateLimitResult.Cooldown -> {
                    event.reply("Please wait ${result.remainingSeconds.toInt()}s")
                        .setEphemeral(true)
                        .queue()
                    return
                }
                is RateLimitResult.BurstLimited -> {
                    event.reply("Rate limit exceeded")
                        .setEphemeral(true)
                        .queue()
                    return
                }
                else -> { /* continue */ }
            }
        }

        // Route to appropriate handler
        when (event.name) {
            "mcstats" -> McStatsCommand(plugin).execute(event)
            "mctop" -> McTopCommand(plugin).execute(event)
            "amsstats" -> amsStatsCommand?.execute(event)
            "amstop" -> amsTopCommand?.execute(event)
            "amssync" -> DiscordLinkCommand(plugin).execute(event)
        }
    }
}
```

## Connection Status

```kotlin
fun isConnected(): Boolean {
    return connected && jda != null && jda?.status == JDA.Status.CONNECTED
}
```

Use this to guard Discord-dependent operations:

```kotlin
if (!discordManager.isConnected()) {
    logger.fine("Skipping - Discord not connected")
    return
}
```

## Graceful Shutdown

```kotlin
fun shutdown() {
    logger.info("Disconnecting from Discord...")
    jda?.shutdownNow()  // Force immediate shutdown
    jda = null
    connected = false
}
```

> **Why `shutdownNow()`?** Regular `shutdown()` waits for pending requests. During plugin disable, we want immediate disconnection to avoid blocking server shutdown.

## Error Handling Pattern

```kotlin
fun initialize(token: String, guildId: String) {
    logger.info("Connecting to Discord...")

    try {
        jda = JDABuilder.createDefault(token)
            .enableIntents(...)
            .addEventListeners(...)
            .build()
            .awaitReady()

        connected = true
        logger.info("Discord bot ready: ${jda?.selfUser?.name}")
        registerSlashCommands(guildId)

    } catch (e: Exception) {
        // Clean up on failure
        connected = false
        jda?.shutdownNow()
        jda = null

        // Re-throw for retry logic to catch
        throw e
    }
}
```

## Thread Model

JDA uses its own thread pool for event handling:

```
┌─────────────────────────────────────────────────────────────┐
│                     Thread Model                             │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Bukkit Main Thread                 JDA Thread Pool          │
│  ─────────────────                  ───────────────          │
│  • Plugin lifecycle                 • Event listeners        │
│  • Bukkit API calls                 • Command handlers       │
│  • UserMappingService               • API responses          │
│                                                              │
│  ⚠️ Cross-thread caution:                                    │
│  • Don't access Bukkit API from JDA threads                  │
│  • Use Bukkit.getScheduler().runTask() to switch threads     │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### Switching to Bukkit Thread

```kotlin
override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
    // This runs on JDA thread

    // Need to access Bukkit API? Schedule on main thread:
    Bukkit.getScheduler().runTask(plugin, Runnable {
        // This runs on Bukkit main thread
        val player = Bukkit.getPlayer(username)
    })
}
```

## Configuration

```yaml
discord:
  token: "your-bot-token"          # Or use AMS_DISCORD_TOKEN env var
  guild-id: "123456789012345678"   # For instant command registration
```

## Common Issues

### Commands Not Appearing

1. **Guild commands**: Check `guild-id` is correct
2. **Global commands**: Wait up to 1 hour
3. **Permissions**: Check bot has `applications.commands` scope

### MESSAGE_CONTENT Intent Error

```
[ERROR] Missing required intent: MESSAGE_CONTENT
```

**Solution**: Enable in Discord Developer Portal → Bot → Privileged Gateway Intents

### Rate Limited by Discord

JDA handles Discord's rate limits automatically, but excessive requests can still cause issues. Use the plugin's rate limiter to prevent user abuse.

## Related Documentation

- [Webhooks](webhooks.md) - Rich message sending
- [Presence & Status](../features/presence-status.md) - Bot activity updates
- [Rate Limiting](../patterns/rate-limiting.md) - Command spam protection
