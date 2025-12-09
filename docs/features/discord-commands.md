# Discord Slash Commands

**Complexity**: Intermediate
**Key Files**:
- [`discord/SlashCommandListener.kt`](../../src/main/kotlin/io/github/darinc/amssync/discord/SlashCommandListener.kt)
- [`discord/DiscordManager.kt`](../../src/main/kotlin/io/github/darinc/amssync/discord/DiscordManager.kt)
- [`discord/commands/*.kt`](../../src/main/kotlin/io/github/darinc/amssync/discord/commands/)

## Overview

AMSSync provides five Discord slash commands:

| Command | Type | Description |
|---------|------|-------------|
| `/mcstats [player]` | Text | View MCMMO stats as embed |
| `/mctop [skill]` | Text | View leaderboards as embed |
| `/amsstats [player]` | Image | View stats as visual card |
| `/amstop [skill]` | Image | View leaderboard as visual podium |
| `/amssync` | Admin | Link Discord users to Minecraft |

## Command Registration

### Guild vs Global Commands

```kotlin
// In DiscordManager.kt
fun registerSlashCommands(guildId: String?) {
    val jda = jda ?: return

    val commands = listOf(
        Commands.slash("mcstats", "View MCMMO stats for a player")
            .addOption(OptionType.STRING, "player", "Player name", false),
        Commands.slash("mctop", "View MCMMO leaderboard")
            .addOption(OptionType.STRING, "skill", "Skill name (or 'power')", false),
        // ... more commands
    )

    if (guildId != null) {
        // Guild commands: instant registration
        val guild = jda.getGuildById(guildId)
        guild?.updateCommands()?.addCommands(commands)?.queue()
    } else {
        // Global commands: up to 1 hour delay
        jda.updateCommands().addCommands(commands).queue()
    }
}
```

**Guild commands** (recommended):
- Register instantly
- Only visible in that server
- Require `guild-id` in config

**Global commands**:
- Up to 1 hour to propagate
- Visible in all servers with the bot
- Used when no `guild-id` configured

## Command Routing

### SlashCommandListener

```kotlin
class SlashCommandListener(
    private val plugin: AMSSyncPlugin,
    private val amsStatsCommand: AmsStatsCommand?,  // Optional - requires image cards
    private val amsTopCommand: AmsTopCommand?
) : ListenerAdapter() {

    private val mcStatsCommand = McStatsCommand(plugin)
    private val mcTopCommand = McTopCommand(plugin)
    private val discordLinkCommand = DiscordLinkCommand(plugin)

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        // 1. Check rate limit
        // 2. Route to handler
        // 3. Execute command
    }
}
```

### Rate Limiting Integration

```kotlin
override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
    val userId = event.user.id

    // Check rate limit before processing
    val rateLimiter = plugin.rateLimiter
    if (rateLimiter != null) {
        when (val result = rateLimiter.checkRateLimit(userId)) {
            is RateLimitResult.Cooldown -> {
                // Log security event
                plugin.auditLogger.logSecurityEvent(
                    event = SecurityEvent.RATE_LIMITED,
                    actor = "${event.user.name} ($userId)",
                    actorType = ActorType.DISCORD_USER,
                    details = mapOf("command" to event.name, "reason" to "cooldown")
                )

                // Ephemeral response (only visible to user)
                event.reply("Please wait ${result.remainingSeconds}s...")
                    .setEphemeral(true)
                    .queue()
                return
            }
            is RateLimitResult.BurstLimited -> {
                // Similar handling...
            }
            is RateLimitResult.Allowed -> {
                // Continue
            }
        }
    }

    // Route command
    when (event.name) {
        "mcstats" -> mcStatsCommand.handle(event)
        "mctop" -> mcTopCommand.handle(event)
        "amsstats" -> amsStatsCommand?.handle(event)
            ?: event.reply("Image cards not enabled").setEphemeral(true).queue()
        // ...
    }
}
```

## Command Handler Pattern

### Text Command Example (McStatsCommand)

```kotlin
class McStatsCommand(private val plugin: AMSSyncPlugin) {

    fun handle(event: SlashCommandInteractionEvent) {
        val startTime = System.currentTimeMillis()

        // Get player name from option or user mapping
        val playerOption = event.getOption("player")?.asString
        val playerName = playerOption ?: resolvePlayerFromUser(event.user.id)

        if (playerName == null) {
            event.reply("Please specify a player or link your account with `/amssync`")
                .setEphemeral(true)
                .queue()
            return
        }

        // Defer reply for potentially slow operation
        event.deferReply().queue()

        try {
            // Execute with circuit breaker
            val result = plugin.discordApiWrapper?.executeWithCircuitBreaker("mcstats query") {
                plugin.mcmmoApiWrapper.getPlayerStats(playerName)
            }

            if (result == null) {
                event.hook.editOriginal("Discord service temporarily unavailable").queue()
                return
            }

            // Build embed
            val embed = buildStatsEmbed(playerName, result)
            event.hook.editOriginalEmbeds(embed).queue()

            // Record metrics
            val duration = System.currentTimeMillis() - startTime
            plugin.errorMetrics.recordCommandSuccess("mcstats", duration)

        } catch (e: PlayerDataNotFoundException) {
            event.hook.editOriginal("Player not found: $playerName").queue()
        } catch (e: Exception) {
            plugin.errorMetrics.recordCommandFailure("mcstats")
            event.hook.editOriginal("Error: ${e.message}").queue()
        }
    }
}
```

### Image Command Example (AmsStatsCommand)

```kotlin
class AmsStatsCommand(
    private val plugin: AMSSyncPlugin,
    private val cardRenderer: PlayerCardRenderer,
    private val avatarFetcher: AvatarFetcher,
    private val avatarProvider: String
) {

    fun handle(event: SlashCommandInteractionEvent) {
        val playerName = resolvePlayer(event) ?: return

        // Defer - image generation takes time
        event.deferReply().queue()

        // Generate card asynchronously
        CompletableFuture.supplyAsync {
            val stats = plugin.mcmmoApiWrapper.getPlayerStats(playerName)
            val powerLevel = stats.values.sum()
            val uuid = plugin.mcmmoApiWrapper.getOfflinePlayer(playerName)?.uniqueId

            // Fetch avatar
            val bodyImage = avatarFetcher.fetchBodyRender(playerName, uuid, avatarProvider)

            // Render card
            cardRenderer.renderStatsCard(playerName, stats, powerLevel, bodyImage)

        }.thenAccept { cardImage ->
            // Convert to bytes and send
            val bytes = imageToBytes(cardImage)
            event.hook.editOriginal(MessageEditData.fromFiles(
                FileUpload.fromData(bytes, "${playerName}_stats.png")
            )).queue()

        }.exceptionally { e ->
            event.hook.editOriginal("Error generating card: ${e.message}").queue()
            null
        }
    }
}
```

## Key Patterns

### 1. Deferred Replies

Discord requires response within 3 seconds. For slow operations:

```kotlin
// Acknowledge immediately
event.deferReply().queue()

// ... do slow work ...

// Edit the deferred response
event.hook.editOriginal("Result here").queue()
```

### 2. Ephemeral Responses

Error messages visible only to the user:

```kotlin
event.reply("Error message")
    .setEphemeral(true)  // Only user sees this
    .queue()
```

### 3. Player Resolution

Commands resolve player names in order:

1. Explicit `player` option from command
2. Linked Minecraft username from user mapping
3. Error asking user to specify or link

```kotlin
private fun resolvePlayer(event: SlashCommandInteractionEvent): String? {
    // 1. Check explicit option
    event.getOption("player")?.asString?.let { return it }

    // 2. Check user mapping
    val userId = event.user.id
    plugin.userMappingService.getMinecraftName(userId)?.let { return it }

    // 3. No player found
    event.reply("Specify a player or link your account")
        .setEphemeral(true)
        .queue()
    return null
}
```

### 4. Option Choices for Skills

Pre-defined choices for skill selection:

```kotlin
Commands.slash("mctop", "View leaderboard")
    .addOption(
        OptionType.STRING, "skill", "Skill name",
        false  // Not required - defaults to power level
    )
    .addChoices(
        Command.Choice("Power Level", "power"),
        Command.Choice("Mining", "mining"),
        Command.Choice("Woodcutting", "woodcutting"),
        // ... more skills
    )
```

## Admin Command: /amssync

### Subcommands

| Subcommand | Permission | Description |
|------------|------------|-------------|
| `link <discord-id> <username>` | MANAGE_SERVER | Create mapping |
| `unlink <discord-id>` | MANAGE_SERVER | Remove mapping |
| `list` | MANAGE_SERVER | Show all mappings |
| `check [user]` | None | Check own/other's link |

### Permission Checking

```kotlin
fun handle(event: SlashCommandInteractionEvent) {
    val subcommand = event.subcommandName

    // Check permission for admin subcommands
    if (subcommand in listOf("link", "unlink", "list")) {
        val member = event.member
        if (member == null || !member.hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("You need Manage Server permission")
                .setEphemeral(true)
                .queue()
            return
        }
    }

    when (subcommand) {
        "link" -> handleLink(event)
        "unlink" -> handleUnlink(event)
        // ...
    }
}
```

## Error Handling

### Exception Types

```kotlin
try {
    val stats = mcmmoApiWrapper.getPlayerStats(playerName)
} catch (e: PlayerDataNotFoundException) {
    // Player doesn't exist or has no MCMMO data
    event.reply("Player '$playerName' not found or has no MCMMO data")
        .setEphemeral(true).queue()
} catch (e: InvalidSkillException) {
    // Invalid skill name
    event.reply("Invalid skill. Valid skills: ${e.validSkills.joinToString()}")
        .setEphemeral(true).queue()
} catch (e: Exception) {
    // Unexpected error
    plugin.logger.warning("Command error: ${e.message}")
    event.reply("An error occurred").setEphemeral(true).queue()
}
```

### Metrics Recording

```kotlin
val startTime = System.currentTimeMillis()

try {
    // ... command logic ...

    val duration = System.currentTimeMillis() - startTime
    plugin.errorMetrics.recordCommandSuccess("mcstats", duration)

} catch (e: Exception) {
    plugin.errorMetrics.recordCommandFailure("mcstats")
    throw e
}
```

## Testing Commands

### In Discord

1. Type `/` to see available commands
2. Select command and fill options
3. Check response type (embed, image, ephemeral)

### Debugging

```yaml
# In config.yml, enable fine logging
# Then check server console for:
# - "Received slash command: mcstats from Username"
# - "Searching for player: 'PlayerName'"
# - Success/failure messages
```

## Related Documentation

- [Rate Limiting](../patterns/rate-limiting.md) - Command throttling
- [Circuit Breaker](../patterns/circuit-breaker.md) - Failure protection
- [Image Cards](image-cards.md) - Visual command output
- [MCMMO API](../integrations/mcmmo-api.md) - Data source for stats
