# Discord Webhooks

**Complexity**: Beginner
**Key Files**:
- [`discord/WebhookSender.kt`](../../src/main/kotlin/io/github/darinc/amssync/discord/WebhookSender.kt)
- [`discord/WebhookManager.kt`](../../src/main/kotlin/io/github/darinc/amssync/discord/WebhookManager.kt)

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

## Architecture

Webhooks are sent with JDA's built-in webhook client (`WebhookClient.createClient`), so no
separate webhook library is bundled. `WebhookSender` wraps it and is shared by:

- `WebhookManager` - server events, deaths, achievements (falls back to bot messages)
- `ChatWebhookManager` - chat bridge messages with player name and head
- `McMMOEventListener` - milestone image cards

```kotlin
class WebhookSender(
    private val webhookUrl: String,                 // must match Webhook.WEBHOOK_URL
    private val jdaProvider: () -> JDA?,            // current bot connection
    private val circuitBreakerProvider: () -> CircuitBreaker?,
    private val logger: Logger
) {
    fun sendMessage(content: String, username: String? = null, avatarUrl: String? = null): Boolean
    fun sendEmbed(embed: MessageEmbed, username: String? = null, avatarUrl: String? = null): Boolean
    fun sendFile(bytes: ByteArray, filename: String, username: String? = null, avatarUrl: String? = null): Boolean
}
```

Each send:

1. Skips (returns `false`) if the bot is not connected
2. Creates a webhook client from the current JDA instance
3. Applies the username/avatar overrides and queues the request through JDA
4. Is gated by the circuit breaker when one is configured

Because requests go through JDA, webhook sends share JDA's rate-limit handling and
**require the bot to be connected**. The URL is validated when the manager is created;
an invalid URL logs a warning and the manager falls back to bot messages.

## Usage Patterns

### Send Embed

```kotlin
fun sendEmbed(
    embed: MessageEmbed,
    username: String? = null,
    avatarUrl: String? = null
) {
    val sender = webhookSender
    if (sender != null) {
        sender.sendEmbed(embed, username, avatarUrl)
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
    val sender = webhookSender
    if (sender != null) {
        sender.sendMessage(content, username, avatarUrl)
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
    avatarUrl = "https://mc-heads.net/avatar/$playerName/64"
)

// Chat bridge with player's identity
webhookManager.sendMessage(
    content = message,
    username = playerName,
    avatarUrl = "https://mc-heads.net/avatar/$playerName/64"
)
```

## Embeds

JDA `MessageEmbed` objects are passed straight to the webhook client; no conversion is needed.

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

`shutdown()` stops new webhook sends. Requests already queued are completed by JDA's
graceful shutdown in `DiscordManager.shutdown()`.

## Check Webhook Availability

```kotlin
fun isWebhookAvailable(): Boolean = webhookSender != null
```

Use this to determine messaging mode:

```kotlin
if (webhookManager.isWebhookAvailable()) {
    logger.info("Using webhooks for rich messages")
} else {
    logger.info("Falling back to bot messages")
}
```

## Common Issues

### Webhook Not Working

1. **Invalid URL**: Verify webhook URL is complete and valid (startup logs "Invalid webhook URL")
2. **Bot offline**: Webhook sends need the bot connected; check the Discord connection in logs
3. **Deleted webhook**: Check webhook still exists in Discord
4. **Wrong channel**: Webhook is channel-specific

### Rate Limited

Discord limits webhook requests to ~30 per minute. JDA queues requests and waits out rate limits automatically.

### Missing Permissions

The webhook doesn't need additional permissions - it inherits from the channel where it was created.

## Related Documentation

- [JDA Discord](jda-discord.md) - Bot message sending
- [Chat Bridge](../features/chat-bridge.md) - Webhook usage for chat relay
- [Circuit Breaker](../patterns/circuit-breaker.md) - Failure protection
