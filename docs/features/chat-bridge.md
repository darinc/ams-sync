# Chat Bridge

**Complexity**: Intermediate
**Key Files**:
- [`discord/ChatBridge.kt`](../../src/main/kotlin/io/github/darinc/amssync/discord/ChatBridge.kt)
- [`discord/ChatWebhookManager.kt`](../../src/main/kotlin/io/github/darinc/amssync/discord/ChatWebhookManager.kt)

## Overview

The chat bridge provides two-way message relay between Minecraft and Discord:

```
Minecraft                           Discord
   │                                   │
   │ Player types in chat              │
   │ ─────────────────────────────────►│ Message appears in channel
   │                                   │
   │ Message appears in-game           │ User types in channel
   │ ◄─────────────────────────────────│
   │                                   │
```

## Configuration

```yaml
discord:
  chat-bridge:
    enabled: true
    channel-id: "1234567890123456789"  # Discord channel ID

    # Direction toggles
    minecraft-to-discord: true
    discord-to-minecraft: true

    # Message formats
    mc-format: "&7[Discord] &b{author}&7: {message}"
    discord-format: "**{player}**: {message}"

    # Ignore messages starting with these prefixes
    ignore-prefixes:
      - "/"

    # Suppress Discord push notifications
    suppress-notifications: true

    # Webhook mode for player avatars
    use-webhook: false
    webhook-url: ""
    avatar-provider: "mc-heads"
```

## Minecraft → Discord

### Event Handling

```kotlin
class ChatBridge(...) : Listener, ListenerAdapter() {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onAsyncChat(event: AsyncChatEvent) {
        if (!config.minecraftToDiscord) return

        // Serialize Paper's Adventure component to plain text
        val message = PlainTextComponentSerializer.plainText().serialize(event.message())

        // Skip ignored prefixes (commands)
        if (config.ignorePrefixes.any { message.startsWith(it) }) {
            return
        }

        val player = event.player
        val playerName = player.name

        // Choose delivery method
        if (config.useWebhook && chatWebhookManager?.isWebhookAvailable() == true) {
            val avatarUrl = ChatBridgeConfig.getAvatarUrl(
                playerName, player.uniqueId, config.avatarProvider
            )
            sendViaWebhook(message, playerName, avatarUrl)
        } else {
            val formatted = config.discordFormat
                .replace("{player}", playerName)
                .replace("{message}", message)
            sendToDiscord(formatted)
        }
    }
}
```

### Bot Message Mode

Standard bot message with formatted text:

```kotlin
private fun sendToDiscord(message: String) {
    val jda = plugin.discordManager.getJda() ?: return
    val channel = jda.getTextChannelById(config.channelId) ?: return

    // Sanitize to prevent @everyone/@here
    val sanitized = sanitizeDiscordMessage(message)

    // Send with circuit breaker protection
    val circuitBreaker = plugin.circuitBreaker
    val sendAction = {
        channel.sendMessage(sanitized)
            .setSuppressedNotifications(config.suppressNotifications)
            .queue()
    }

    if (circuitBreaker != null) {
        circuitBreaker.execute("Relay chat to Discord", sendAction)
    } else {
        sendAction()
    }
}
```

**Output**: `**Steve**: Hello everyone!`

### Webhook Mode

Rich messages with player avatars:

```kotlin
private fun sendViaWebhook(message: String, playerName: String, avatarUrl: String) {
    val sanitized = sanitizeDiscordMessage(message)
    chatWebhookManager?.sendMessage(sanitized, playerName, avatarUrl)
}

// In ChatWebhookManager
fun sendMessage(content: String, username: String, avatarUrl: String) {
    val message = WebhookMessageBuilder()
        .setContent(content)
        .setUsername(username)      // Appears as sender name
        .setAvatarUrl(avatarUrl)    // Player's Minecraft head
        .build()

    webhookClient.send(message).queue()
}
```

**Output**: Message appears to come from player with their Minecraft head as avatar.

### Avatar URL Generation

```kotlin
companion object {
    fun getAvatarUrl(playerName: String, uuid: UUID, provider: String): String {
        return when (provider.lowercase()) {
            "crafatar" -> {
                // Crafatar requires UUID without dashes
                val id = uuid.toString().replace("-", "")
                "https://crafatar.com/avatars/$id?size=64&overlay"
            }
            else -> "https://mc-heads.net/avatar/$playerName/64"
        }
    }
}
```

## Discord → Minecraft

### Event Handling

```kotlin
override fun onMessageReceived(event: MessageReceivedEvent) {
    if (!config.discordToMinecraft) return

    // Ignore bot messages (including our own)
    if (event.author.isBot) return

    // Only process messages from configured channel
    if (event.channel.id != config.channelId) return

    val author = event.member?.effectiveName ?: event.author.name
    val message = event.message.contentDisplay

    // Skip empty messages (image-only, etc.)
    if (message.isBlank()) return

    // Format for Minecraft with color codes
    @Suppress("DEPRECATION")
    val formatted = ChatColor.translateAlternateColorCodes(
        '&',
        config.mcFormat
            .replace("{author}", author)
            .replace("{message}", sanitizeMinecraftMessage(message))
    )

    // Broadcast on main thread (required for Bukkit API)
    Bukkit.getScheduler().runTask(plugin, Runnable {
        Bukkit.broadcastMessage(formatted)
    })
}
```

### Thread Safety Note

JDA events fire on JDA's thread pool, not the Bukkit main thread. The `Bukkit.broadcastMessage()` call must be scheduled on the main thread:

```kotlin
// BAD: Direct call from JDA thread
Bukkit.broadcastMessage(formatted)  // May crash!

// GOOD: Schedule on main thread
Bukkit.getScheduler().runTask(plugin, Runnable {
    Bukkit.broadcastMessage(formatted)
})
```

## Message Sanitization

### Discord Message Sanitization

Prevent mention exploits:

```kotlin
private fun sanitizeDiscordMessage(message: String): String {
    return message
        // Prevent @everyone and @here
        .replace("@everyone", "@\u200Beveryone")  // Zero-width space
        .replace("@here", "@\u200Bhere")
        // Prevent role mentions
        .replace(Regex("@(&|!)?(\\d+)")) { match ->
            "@\u200B${match.groupValues[1]}${match.groupValues[2]}"
        }
}
```

### Minecraft Message Sanitization

Prevent format exploits and rendering issues:

```kotlin
private fun sanitizeMinecraftMessage(message: String): String {
    return message
        // Prevent color code injection from Discord
        .replace("&", "&\u200B")
        // Strip Unicode variation selectors (cause boxes in MC)
        .replace("\uFE0E", "")  // VS15 text-style
        .replace("\uFE0F", "")  // VS16 emoji-style
}
```

## Webhook Setup

### Creating a Discord Webhook

1. Open Discord channel settings
2. Go to Integrations → Webhooks
3. Click "New Webhook"
4. Copy the webhook URL
5. Add to config:

```yaml
discord:
  chat-bridge:
    use-webhook: true
    webhook-url: "https://discord.com/api/webhooks/123456/abcdef..."
```

### Webhook Manager

```kotlin
class ChatWebhookManager(
    private val plugin: AMSSyncPlugin,
    private val webhookUrl: String?
) {
    private var webhookClient: WebhookClient? = null

    init {
        if (!webhookUrl.isNullOrBlank()) {
            webhookClient = WebhookClientBuilder(webhookUrl)
                .setThreadFactory { r ->
                    Thread(r, "AMSSync-ChatWebhook").apply { isDaemon = true }
                }
                .setWait(false)  // Don't wait for response
                .build()
        }
    }

    fun isWebhookAvailable(): Boolean = webhookClient != null

    fun shutdown() {
        webhookClient?.close()
    }
}
```

## Configuration Options

### Message Format Placeholders

**Minecraft format** (`mc-format`):
- `{author}` - Discord username or nickname
- `{message}` - Message content

**Discord format** (`discord-format`):
- `{player}` - Minecraft username
- `{message}` - Message content

### Color Codes

Minecraft format supports `&` color codes:

```yaml
mc-format: "&7[&9Discord&7] &b{author}&7: &f{message}"
#          gray [blue Discord gray] aqua name gray : white message
```

### Notification Suppression

```yaml
suppress-notifications: true
```

When true, messages from Minecraft don't trigger Discord push notifications. Users still see messages but don't get pinged.

**Note**: Only works with bot messages, not webhooks.

## Dual Listener Registration

ChatBridge implements both Bukkit and JDA listeners:

```kotlin
class ChatBridge(...) : Listener,      // Bukkit events
                        ListenerAdapter() {  // JDA events

// Registration in AMSSyncPlugin:
fun initializeChatBridge() {
    chatBridge = ChatBridge(this, chatConfig, chatWebhookManager)

    // Register as Bukkit listener (MC → Discord)
    server.pluginManager.registerEvents(chatBridge!!, this)

    // Register as JDA listener (Discord → MC)
    discordManager.getJda()?.addEventListener(chatBridge)
}
```

## Error Handling

### Channel Not Found

```kotlin
val channel = jda.getTextChannelById(config.channelId)
if (channel == null) {
    plugin.logger.warning("Chat bridge channel not found: ${config.channelId}")
    return
}
```

### Circuit Breaker Integration

```kotlin
val circuitBreaker = plugin.circuitBreaker

if (circuitBreaker != null) {
    val result = circuitBreaker.execute("Relay chat to Discord", sendAction)
    if (result is CircuitBreaker.CircuitResult.Rejected) {
        plugin.logger.fine("Chat relay rejected by circuit breaker")
    }
} else {
    sendAction()
}
```

## Testing

1. Enable chat bridge in config
2. Set `channel-id` to a test channel
3. Send message in Minecraft → appears in Discord
4. Send message in Discord → appears in Minecraft
5. Test with webhook enabled for avatar display

### Common Issues

**Messages not appearing**:
- Check `channel-id` is correct
- Verify bot has Send Messages permission
- Check direction toggles are enabled

**Webhook avatar not showing**:
- Verify webhook URL is correct
- Check `avatar-provider` setting
- Ensure player UUID format is correct for crafatar

## Related Documentation

- [Architecture Overview](../architecture/overview.md) - Component relationships
- [Threading](../architecture/threading.md) - Cross-thread communication
- [Webhooks](../integrations/webhooks.md) - Webhook integration details
