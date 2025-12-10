# Discord Webhooks

**Complexity**: Beginner
**Key File**: [`discord/WebhookManager.kt`](../../src/main/kotlin/io/github/darinc/amssync/discord/WebhookManager.kt)

## Overview

Webhooks provide richer Discord messages than bot messages. They allow:

- Custom username per message
- Custom avatar per message
- Appear as different "users" for different event types

AMSSync uses webhooks for event announcements, chat bridge, and milestone celebrations.

## Webhooks vs Bot Messages

| Feature | Bot Messages | Webhooks |
|---------|--------------|----------|
| Custom username | No (uses bot name) | Yes |
| Custom avatar | No (uses bot avatar) | Yes |
| Setup required | None | Create webhook in Discord |
| Fallback | N/A | Automatic to bot |

```
Webhook Message                    Bot Message
┌─────────────────────┐           ┌─────────────────────┐
│ [Custom Avatar]     │           │ [Bot Avatar]        │
│ Custom Username     │           │ AMSSync Bot         │
│ ─────────────────── │           │ ─────────────────── │
│ Message content     │           │ Message content     │
└─────────────────────┘           └─────────────────────┘
```

## Creating a Discord Webhook

1. Open Discord channel settings
2. Go to **Integrations** → **Webhooks**
3. Click **New Webhook**
4. Copy the webhook URL
5. Add to `config.yml`

## WebhookManager Architecture

```kotlin
class WebhookManager(
    private val plugin: AMSSyncPlugin,
    private val webhookUrl: String?,
    private val channelId: String
) {
    private var webhookClient: WebhookClient? = null

    init {
        if (!webhookUrl.isNullOrBlank()) {
            webhookClient = WebhookClientBuilder(webhookUrl)
                .setThreadFactory { Thread(it, "AMSSync-Webhook").apply { isDaemon = true } }
                .setWait(false)  // Don't wait for response
                .build()
        }
    }
}
```

## Usage Patterns

### Send Embed

```kotlin
fun sendEmbed(
    embed: MessageEmbed,
    username: String? = null,
    avatarUrl: String? = null
) {
    val client = webhookClient
    if (client != null) {
        sendViaWebhook(embed, username, avatarUrl, client)
    } else {
        sendViaBot(embed)  // Fallback
    }
}
```

### Send Plain Message

```kotlin
fun sendMessage(
    content: String,
    username: String? = null,
    avatarUrl: String? = null
) {
    val client = webhookClient
    if (client != null) {
        sendMessageViaWebhook(content, username, avatarUrl, client)
    } else {
        sendMessageViaBot(content)  // Fallback
    }
}
```

### With Custom Identity

```kotlin
// Event announcements with custom appearance
webhookManager.sendEmbed(
    embed = deathEmbed,
    username = "Death Tracker",
    avatarUrl = "https://mc-heads.net/avatar/$playerName"
)

// Chat bridge with player's identity
webhookManager.sendMessage(
    content = message,
    username = playerName,
    avatarUrl = "https://mc-heads.net/avatar/$playerName"
)
```

## Embed Conversion

JDA embeds must be converted to webhook library format:

```kotlin
private fun convertToWebhookEmbed(embed: MessageEmbed): WebhookEmbed {
    val builder = WebhookEmbedBuilder()

    embed.title?.let { builder.setTitle(WebhookEmbed.EmbedTitle(it, embed.url)) }
    embed.description?.let { builder.setDescription(it) }
    embed.colorRaw.let { builder.setColor(it) }
    embed.timestamp?.let { builder.setTimestamp(it) }
    embed.thumbnail?.url?.let { builder.setThumbnailUrl(it) }
    embed.image?.url?.let { builder.setImageUrl(it) }
    embed.footer?.let { footer ->
        builder.setFooter(WebhookEmbed.EmbedFooter(footer.text ?: "", footer.iconUrl))
    }
    embed.author?.let { author ->
        builder.setAuthor(WebhookEmbed.EmbedAuthor(author.name ?: "", author.iconUrl, author.url))
    }

    embed.fields.forEach { field ->
        builder.addField(WebhookEmbed.EmbedField(field.isInline, field.name ?: "", field.value ?: ""))
    }

    return builder.build()
}
```

## Circuit Breaker Integration

All webhook sends are protected by the circuit breaker:

```kotlin
private fun sendViaWebhook(embed: MessageEmbed, ...) {
    val circuitBreaker = plugin.circuitBreaker

    val sendAction = {
        val webhookEmbed = convertToWebhookEmbed(embed)
        val message = WebhookMessageBuilder()
            .apply {
                username?.let { setUsername(it) }
                avatarUrl?.let { setAvatarUrl(it) }
            }
            .addEmbeds(webhookEmbed)
            .build()

        client.send(message)
            .thenAccept { logger.fine("Webhook sent") }
            .exceptionally { e ->
                logger.warning("Webhook failed: ${e.message}")
                null
            }
    }

    if (circuitBreaker != null) {
        val result = circuitBreaker.execute("Send webhook", sendAction)
        if (result is CircuitResult.Rejected) {
            logger.fine("Webhook rejected by circuit breaker")
        }
    } else {
        sendAction()
    }
}
```

## Fallback to Bot Messages

When webhook is unavailable, messages are sent via the bot:

```kotlin
private fun sendViaBot(embed: MessageEmbed) {
    val channel = getChannel() ?: return

    channel.sendMessageEmbeds(embed).queue(
        { logger.fine("Bot embed sent") },
        { e -> logger.warning("Bot embed failed: ${e.message}") }
    )
}

private fun getChannel(): TextChannel? {
    val jda = plugin.discordManager.getJda()
    if (jda == null || !plugin.discordManager.isConnected()) {
        return null
    }
    return jda.getTextChannelById(channelId)
}
```

## Configuration

### Event Announcements

```yaml
discord:
  events:
    enabled: true
    text-channel-id: "123456789012345678"
    webhook-url: "https://discord.com/api/webhooks/..."  # Optional
```

### Chat Bridge

```yaml
discord:
  chat-bridge:
    enabled: true
    channel-id: "123456789012345678"
    use-webhook: true
    webhook-url: "https://discord.com/api/webhooks/..."
```

### MCMMO Announcements

```yaml
discord:
  announcements:
    enabled: true
    text-channel-id: "123456789012345678"
    webhook-url: "https://discord.com/api/webhooks/..."  # Optional
```

## Graceful Shutdown

```kotlin
fun shutdown() {
    webhookClient?.close()
    webhookClient = null
}
```

## Check Webhook Availability

```kotlin
fun isWebhookAvailable(): Boolean = webhookClient != null
```

Use this to determine messaging mode:

```kotlin
if (webhookManager.isWebhookAvailable()) {
    logger.info("Using webhooks for rich messages")
} else {
    logger.info("Falling back to bot messages")
}
```

## Webhook Library

AMSSync uses [discord-webhooks](https://github.com/MinnDevelopment/discord-webhooks) library:

```kotlin
// build.gradle.kts
implementation("club.minnced:discord-webhooks:0.8.4")
```

The library is relocated to avoid conflicts:

```kotlin
relocate("club.minnced", "io.github.darinc.amssync.libs.webhook")
```

## Common Issues

### Webhook Not Working

1. **Invalid URL**: Verify webhook URL is complete and valid
2. **Deleted webhook**: Check webhook still exists in Discord
3. **Wrong channel**: Webhook is channel-specific

### Rate Limited

Discord limits webhook requests to ~30 per minute. The circuit breaker helps manage this.

### Missing Permissions

The webhook doesn't need additional permissions - it inherits from the channel where it was created.

## Related Documentation

- [JDA Discord](jda-discord.md) - Bot message sending
- [Chat Bridge](../features/chat-bridge.md) - Webhook usage for chat relay
- [Circuit Breaker](../patterns/circuit-breaker.md) - Failure protection
