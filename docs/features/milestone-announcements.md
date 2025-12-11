# Milestone Announcements

**Complexity**: Intermediate
**Key Files**:
- [`mcmmo/McMMOEventListener.kt`](../../src/main/kotlin/io/github/darinc/amssync/mcmmo/McMMOEventListener.kt)
- [`image/MilestoneCardRenderer.kt`](../../src/main/kotlin/io/github/darinc/amssync/image/MilestoneCardRenderer.kt)

## Overview

McMMOEventListener announces skill and power level milestones to Discord. Supports three announcement modes:

1. **Image cards** with webhooks (richest experience)
2. **Image cards** with bot messages (file attachments)
3. **Traditional embeds** or plain text (fallback)

## Milestone Types

### Skill Milestones

Announced when a player reaches level intervals (e.g., 100, 200, 300):

```
Player reaches level 100 in Mining
        │
        ▼
Is 100 % skillMilestoneInterval (100)?
        │
        ▼ Yes
sendSkillMilestone(player, MINING, 100)
```

### Power Level Milestones

Announced when total power level crosses thresholds:

```
Player levels up any skill
        │
        ▼
Calculate current power level (sum of all non-child skills)
        │
        ▼
Check if crossed milestone threshold (e.g., 1000, 1500, 2000)
        │
        ▼ Yes
sendPowerLevelMilestone(player, 1000)
```

## Event Handling

```kotlin
@EventHandler(priority = EventPriority.MONITOR)
fun onPlayerLevelUp(event: McMMOPlayerLevelUpEvent) {
    val player = event.player
    val skill = event.skill
    val newLevel = event.skillLevel

    // Check for skill milestone
    if (config.skillMilestoneInterval > 0 && newLevel % config.skillMilestoneInterval == 0) {
        sendSkillMilestone(player.name, player.uniqueId, skill, newLevel)
    }

    // Check for power level milestone
    if (config.powerMilestoneInterval > 0) {
        checkPowerLevelMilestone(player.name, player.uniqueId)
    }
}
```

### Power Level Tracking

```kotlin
private val lastKnownPowerLevel = ConcurrentHashMap<String, Int>()

private fun checkPowerLevelMilestone(playerName: String, uuid: UUID) {
    val profile = mcMMO.getDatabaseManager().loadPlayerProfile(uuid)
    if (!profile.isLoaded) return

    // Calculate power level (exclude child skills)
    val currentPowerLevel = PrimarySkillType.values()
        .filter { !it.isChildSkill }
        .sumOf { profile.getSkillLevel(it) }

    val lastPowerLevel = lastKnownPowerLevel.getOrDefault(playerName, 0)

    // Check if we crossed a milestone
    val lastMilestone = (lastPowerLevel / config.powerMilestoneInterval) * config.powerMilestoneInterval
    val currentMilestone = (currentPowerLevel / config.powerMilestoneInterval) * config.powerMilestoneInterval

    if (currentMilestone > lastMilestone && currentMilestone > 0) {
        sendPowerLevelMilestone(playerName, uuid, currentMilestone)
    }

    lastKnownPowerLevel[playerName] = currentPowerLevel
}
```

## Announcement Modes

### Image Cards with Webhooks

Richest experience - custom avatar and username:

```kotlin
private fun sendImageViaWebhook(imageBytes: ByteArray, filename: String, client: WebhookClient) {
    val message = WebhookMessageBuilder()
        .setUsername("MCMMO Milestones")
        .addFile(filename, imageBytes)
        .build()

    client.send(message)
        .thenAccept { logger.fine("Milestone sent via webhook") }
}
```

### Image Cards with Bot

File attachment via bot message:

```kotlin
private fun sendImageViaBot(imageBytes: ByteArray, filename: String) {
    val channel = getAnnouncementChannel() ?: return

    channel.sendFiles(FileUpload.fromData(imageBytes, filename)).queue(
        { logger.fine("Milestone image sent") },
        { e -> logger.warning("Failed: ${e.message}") }
    )
}
```

### Traditional Embeds

Fallback when image cards disabled:

```kotlin
private fun sendSkillMilestoneEmbed(playerName: String, skill: PrimarySkillType, level: Int) {
    val embed = EmbedBuilder()
        .setColor(getSkillColor(skill))
        .setTitle("Skill Milestone!")
        .setDescription("**$playerName** reached level **$level** in **${skill.name}**!")
        .setTimestamp(Instant.now())
        .build()

    channel.sendMessageEmbeds(embed).queue()
}
```

## Skill Colors

Each skill has a themed color for embeds:

```kotlin
private fun getSkillColor(skill: PrimarySkillType): Color {
    return when (skill) {
        // Combat - red tones
        PrimarySkillType.SWORDS -> Color(220, 20, 60)
        PrimarySkillType.AXES -> Color(178, 34, 34)
        PrimarySkillType.ARCHERY -> Color(255, 99, 71)
        PrimarySkillType.UNARMED -> Color(255, 69, 0)
        PrimarySkillType.TAMING -> Color(210, 105, 30)

        // Gathering - green/brown tones
        PrimarySkillType.MINING -> Color(128, 128, 128)
        PrimarySkillType.WOODCUTTING -> Color(139, 90, 43)
        PrimarySkillType.HERBALISM -> Color(34, 139, 34)
        PrimarySkillType.EXCAVATION -> Color(160, 82, 45)
        PrimarySkillType.FISHING -> Color(70, 130, 180)

        // Misc - various
        PrimarySkillType.ACROBATICS -> Color(255, 215, 0)
        PrimarySkillType.ALCHEMY -> Color(138, 43, 226)
        PrimarySkillType.REPAIR -> Color(192, 192, 192)

        else -> Color(100, 149, 237)  // Default cornflower blue
    }
}
```

## Avatar Fetching

When `show-avatars` is enabled, player avatars are fetched asynchronously:

```kotlin
CompletableFuture.supplyAsync {
    val headImage = if (config.showAvatars && avatarFetcher != null) {
        avatarFetcher.fetchHeadAvatar(playerName, uuid, config.avatarProvider, HEAD_SIZE)
    } else {
        createPlaceholderHead()  // Gray box with "?"
    }

    cardRenderer.renderSkillMilestoneCard(playerName, skill, level, headImage)
}.thenAccept { cardImage ->
    sendImageToDiscord(cardImage, "${playerName}_skill_milestone.png", "Skill Milestone")
}.exceptionally { e ->
    // Fallback to embed on failure
    sendSkillMilestoneEmbed(playerName, skill, level)
    null
}
```

### Avatar Providers

| Provider | URL Pattern |
|----------|-------------|
| `mc-heads` | `https://mc-heads.net/avatar/{playerName}/{size}` |
| `crafatar` | `https://crafatar.com/avatars/{uuid-no-dashes}?size={size}&overlay` |

## Circuit Breaker Integration

All Discord sends are protected:

```kotlin
if (circuitBreaker != null) {
    val result = circuitBreaker.execute("Send skill milestone", sendAction)
    if (result is CircuitResult.Rejected) {
        logger.fine("Milestone rejected by circuit breaker")
    }
} else {
    sendAction()
}
```

## Configuration

```yaml
discord:
  announcements:
    enabled: true
    text-channel-id: "123456789012345678"
    webhook-url: "https://discord.com/api/webhooks/..."  # Optional

    skill-milestone-interval: 100   # Every 100 levels (0 = disabled)
    power-milestone-interval: 500   # Every 500 power levels (0 = disabled)

    use-embeds: true                # Rich embeds vs plain text
    use-image-cards: true           # Visual cards vs embeds
    show-avatars: true              # Include player heads
    avatar-provider: "mc-heads"     # mc-heads or crafatar
```

### Configuration Data Class

```kotlin
data class AnnouncementConfig(
    val enabled: Boolean,
    val channelId: String,
    val webhookUrl: String?,
    val skillMilestoneInterval: Int,
    val powerMilestoneInterval: Int,
    val useEmbeds: Boolean,
    val useImageCards: Boolean,
    val showAvatars: Boolean,
    val avatarProvider: String
) {
    companion object {
        fun fromConfig(config: FileConfiguration): AnnouncementConfig {
            return AnnouncementConfig(
                enabled = config.getBoolean("discord.announcements.enabled", false),
                channelId = config.getString("discord.announcements.text-channel-id", "") ?: "",
                webhookUrl = config.getString("discord.announcements.webhook-url", "")?.ifBlank { null },
                skillMilestoneInterval = config.getInt("discord.announcements.skill-milestone-interval", 100),
                powerMilestoneInterval = config.getInt("discord.announcements.power-milestone-interval", 500),
                useEmbeds = config.getBoolean("discord.announcements.use-embeds", true),
                useImageCards = config.getBoolean("discord.announcements.use-image-cards", true),
                showAvatars = config.getBoolean("discord.announcements.show-avatars", true),
                avatarProvider = config.getString("discord.announcements.avatar-provider", "mc-heads") ?: "mc-heads"
            )
        }
    }
}
```

## Initialization

```kotlin
private fun initializeMcMMOAnnouncements() {
    val announcementConfig = AnnouncementConfig.fromConfig(config)
    if (!announcementConfig.enabled) return

    if (announcementConfig.channelId.isBlank()) {
        logger.warning("MCMMO announcements enabled but no channel ID configured")
        return
    }

    // Initialize card renderer if using image cards
    val milestoneCardRenderer = if (announcementConfig.useImageCards) {
        MilestoneCardRenderer(imageConfig?.serverName ?: "Minecraft Server")
    } else {
        null
    }

    // Use existing or create avatar fetcher
    val milestoneAvatarFetcher = if (announcementConfig.useImageCards && announcementConfig.showAvatars) {
        avatarFetcher ?: AvatarFetcher(logger, 100, 300000L)
    } else {
        null
    }

    mcmmoEventListener = McMMOEventListener(
        this,
        announcementConfig,
        milestoneAvatarFetcher,
        milestoneCardRenderer
    )
    server.pluginManager.registerEvents(mcmmoEventListener!!, this)
}
```

## Shutdown

```kotlin
fun shutdown() {
    webhookClient?.close()
    webhookClient = null
}
```

## Placeholder Head

When avatars are disabled or unavailable:

```kotlin
private fun createPlaceholderHead(): BufferedImage {
    val size = MilestoneStyles.HEAD_SIZE
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g2d = image.createGraphics()

    g2d.color = Color(80, 80, 80)  // Gray background
    g2d.fillRect(0, 0, size, size)

    g2d.color = Color(150, 150, 150)
    g2d.font = Font("SansSerif", Font.BOLD, 32)
    g2d.drawString("?", centerX, centerY)

    g2d.dispose()
    return image
}
```

## Common Issues

### No Announcements

1. **Check enabled**: `discord.announcements.enabled: true`
2. **Check channel ID**: Valid text channel ID configured
3. **Check intervals**: `skill-milestone-interval` and `power-milestone-interval` > 0
4. **MCMMO loaded**: Ensure mcMMO plugin is installed and working

### Image Cards Not Working

1. **Check enabled**: `use-image-cards: true`
2. **Avatar fetch failing**: Check network connectivity
3. **Rendering error**: Check server logs for graphics errors

### Webhook vs Bot

- **Webhook configured**: Uses webhook with custom username/avatar
- **No webhook**: Falls back to bot messages with file attachments

## Related Documentation

- [Image Cards](image-cards.md) - Card rendering system
- [Webhooks](../integrations/webhooks.md) - Webhook sending
- [MCMMO API](../integrations/mcmmo-api.md) - Data access patterns
