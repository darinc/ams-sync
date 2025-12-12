# Code Duplication Analysis Report

**Project:** AMSSync
**Date:** 2025-12-11
**Analyzed Path:** `src/main/kotlin/io/github/darinc/amssync/`
**Last Updated:** 2025-12-11

---

## Executive Summary

| Category | Count | Lines Affected | Estimated Savings |
|----------|-------|----------------|-------------------|
| Structural Duplicates | 4 | ~195 | ~140 lines |
| Data Duplicates | 1 | ~4 | ~2 lines |
| **Total** | **5** | **~199** | **~142 lines** |

---

## Finding 1: Webhook Client Initialization (STRUCTURAL DUPLICATE)

**Importance: 7/10**

### Locations

| File | Lines |
|------|-------|
| `discord/WebhookManager.kt` | 33-48 |
| `discord/ChatWebhookManager.kt` | 23-39 |
| `mcmmo/McMMOEventListener.kt` | 61-76 |

### Duplicated Pattern

```kotlin
if (!webhookUrl.isNullOrBlank()) {
    try {
        webhookClient = WebhookClientBuilder(webhookUrl)
            .setThreadFactory { r ->
                val thread = Thread(r, "AMSSync-[NAME]")
                thread.isDaemon = true
                thread
            }
            .setWait(false)
            .build()
        plugin.logger.info("[NAME] webhook client initialized")
    } catch (e: Exception) {
        plugin.logger.warning("Failed to initialize [NAME] webhook: ${e.message}")
    }
}
```

**Duplication:** ~80% similar across 3 files (~45 lines total)

### Remediation

Create `discord/WebhookClientFactory.kt`:

```kotlin
package io.github.darinc.amssync.discord

import club.minnced.discord.webhook.WebhookClient
import club.minnced.discord.webhook.WebhookClientBuilder
import java.util.logging.Logger

object WebhookClientFactory {
    /**
     * Create a webhook client with standard configuration.
     *
     * @param webhookUrl The webhook URL
     * @param threadName Thread name for the webhook executor
     * @param logger Logger for status messages
     * @return WebhookClient if successful, null otherwise
     */
    fun create(
        webhookUrl: String?,
        threadName: String,
        logger: Logger
    ): WebhookClient? {
        if (webhookUrl.isNullOrBlank()) return null

        return try {
            val client = WebhookClientBuilder(webhookUrl)
                .setThreadFactory { r ->
                    Thread(r, threadName).apply { isDaemon = true }
                }
                .setWait(false)
                .build()
            logger.info("$threadName webhook client initialized")
            client
        } catch (e: Exception) {
            logger.warning("Failed to initialize $threadName webhook: ${e.message}")
            null
        }
    }
}
```

Usage:
```kotlin
// In WebhookManager init
webhookClient = WebhookClientFactory.create(webhookUrl, "AMSSync-Webhook", plugin.logger)

// In ChatWebhookManager init
webhookClient = WebhookClientFactory.create(webhookUrl, "AMSSync-ChatWebhook", plugin.logger)
```

---

## Finding 2: Circuit Breaker Execution Pattern (STRUCTURAL DUPLICATE)

**Importance: 8/10**

### Locations

| File | Occurrences |
|------|-------------|
| `discord/WebhookManager.kt` | 4 (lines 103-131, 144-171, 181-197, 207-223) |
| `discord/ChatWebhookManager.kt` | 1 (lines 62-87) |
| `mcmmo/McMMOEventListener.kt` | 5 (lines 175-207, 253-286, 316-345, 351-375, 354-371) |

### Duplicated Pattern

```kotlin
try {
    val circuitBreaker = plugin.circuitBreaker
    val sendAction = { /* operation */ }

    if (circuitBreaker != null) {
        val result = circuitBreaker.execute("Operation name", sendAction)
        if (result is CircuitBreaker.CircuitResult.Rejected) {
            plugin.logger.fine("Operation rejected by circuit breaker")
        }
    } else {
        sendAction()
    }
} catch (e: Exception) {
    plugin.logger.warning("Error: ${e.message}")
}
```

**Duplication:** ~75% similar across 10+ occurrences (~150 lines total)

### Remediation

Add extension function to `CircuitBreaker.kt`:

```kotlin
/**
 * Execute an action with circuit breaker protection if available, otherwise execute directly.
 */
inline fun CircuitBreaker?.executeOrDirect(
    operationName: String,
    logger: java.util.logging.Logger,
    crossinline action: () -> Unit
) {
    try {
        if (this != null) {
            val result = execute(operationName) { action() }
            if (result is CircuitResult.Rejected) {
                logger.fine("$operationName rejected by circuit breaker")
            }
        } else {
            action()
        }
    } catch (e: Exception) {
        logger.warning("Error in $operationName: ${e.message}")
    }
}
```

Usage:
```kotlin
plugin.circuitBreaker.executeOrDirect("Send webhook embed", plugin.logger) {
    client.send(message)
        .thenAccept { plugin.logger.fine("Sent webhook embed") }
        .exceptionally { e ->
            plugin.logger.warning("Failed to send webhook embed: ${e.message}")
            null
        }
}
```

---

## Finding 3: Discord Snowflake Regex (DATA DUPLICATE)

**Importance: 4/10**

### Locations

| File | Line | Identifier |
|------|------|------------|
| `config/ConfigValidator.kt` | 19 | `SNOWFLAKE_PATTERN` |
| `validation/Validators.kt` | 18 | `DISCORD_ID_REGEX` |

### Duplicated Code

```kotlin
// ConfigValidator.kt
private val SNOWFLAKE_PATTERN = Regex("^\\d{17,19}$")

// Validators.kt
private val DISCORD_ID_REGEX = Regex("^\\d{17,19}$")
```

### Remediation

Consolidate in `Validators.kt` and reference from `ConfigValidator`:

```kotlin
// In Validators.kt - make public
val DISCORD_ID_REGEX = Regex("^\\d{17,19}$")

// In ConfigValidator.kt - replace line 19 with:
private val SNOWFLAKE_PATTERN = Validators.DISCORD_ID_REGEX
```

Or create shared constants object:

```kotlin
// validation/ValidationPatterns.kt
package io.github.darinc.amssync.validation

object ValidationPatterns {
    val DISCORD_SNOWFLAKE = Regex("^\\d{17,19}$")
    val MINECRAFT_USERNAME = Regex("^[a-zA-Z0-9_]{3,16}$")
    val BOT_TOKEN = Regex("^[A-Za-z0-9_-]{18,}\\.[A-Za-z0-9_-]{6}\\.[A-Za-z0-9_-]{27,}$")
}
```

---

## Finding 4: Event Embed/Message Pattern (STRUCTURAL DUPLICATE)

**Importance: 5/10**

### Locations

| File | Lines | Pattern |
|------|-------|---------|
| `events/ServerEventListener.kt` | 36-47, 62-73 | embed vs message branching |
| `events/PlayerDeathListener.kt` | 51-66 | embed vs message branching |
| `events/AchievementListener.kt` | 52-58 | embed vs message branching |

### Duplicated Pattern

```kotlin
if (config.useEmbeds) {
    val embed = EmbedBuilder()
        .setColor(Color(...))
        .setTitle("...")
        .setDescription(message)
        .setTimestamp(Instant.now())
        .build()
    webhookManager.sendEmbed(embed, username, avatarUrl)
} else {
    webhookManager.sendMessage(message, username, avatarUrl)
}
```

### Remediation

Add helper to `WebhookManager`:

```kotlin
/**
 * Send a message with optional embed formatting.
 */
fun sendAnnouncement(
    title: String?,
    description: String,
    color: Color,
    useEmbed: Boolean,
    username: String? = null,
    avatarUrl: String? = null,
    thumbnailUrl: String? = null
) {
    if (useEmbed) {
        val embed = EmbedBuilder()
            .setColor(color)
            .apply { title?.let { setTitle(it) } }
            .setDescription(description)
            .apply { thumbnailUrl?.let { setThumbnail(it) } }
            .setTimestamp(Instant.now())
            .build()
        sendEmbed(embed, username, avatarUrl)
    } else {
        sendMessage(description, username, avatarUrl)
    }
}
```

---

## Finding 5: Timeout Execution Pattern (STRUCTURAL DUPLICATE)

**Importance: 6/10**

### Locations

| File | Lines |
|------|-------|
| `discord/commands/McTopCommand.kt` | 40-127 |
| `discord/commands/AmsTopCommand.kt` | 52-89 |

### Duplicated Pattern

```kotlin
if (timeoutManager != null) {
    val result = timeoutManager.executeOnBukkitWithTimeout(plugin, "Operation") { /* task */ }
    when (result) {
        is TimeoutManager.TimeoutResult.Success -> { /* success */ }
        is TimeoutManager.TimeoutResult.Timeout -> { /* timeout handling */ }
        is TimeoutManager.TimeoutResult.Failure -> { /* failure handling */ }
    }
} else {
    Bukkit.getScheduler().runTask(plugin, Runnable { /* task */ })
}
```

### Remediation

Add extension to `TimeoutManager.kt`:

```kotlin
/**
 * Execute a task with timeout protection if manager is available,
 * otherwise run on Bukkit scheduler directly.
 */
inline fun TimeoutManager?.executeOnBukkitOrDirect(
    plugin: AMSSyncPlugin,
    operationName: String,
    crossinline onTimeout: (Long) -> Unit,
    crossinline onFailure: (Exception) -> Unit,
    crossinline task: () -> Unit
) {
    if (this != null) {
        val result = executeOnBukkitWithTimeout(plugin, operationName) { task() }
        when (result) {
            is TimeoutResult.Success -> { /* no-op */ }
            is TimeoutResult.Timeout -> onTimeout(result.timeoutMs)
            is TimeoutResult.Failure -> onFailure(result.exception)
        }
    } else {
        Bukkit.getScheduler().runTask(plugin, Runnable {
            try {
                task()
            } catch (e: Exception) {
                onFailure(e)
            }
        })
    }
}
```

---

## Summary: Recommended New Utility Files

| New File | Purpose | Eliminates Lines |
|----------|---------|------------------|
| `discord/WebhookClientFactory.kt` | Webhook client creation | ~45 |
| `validation/ValidationPatterns.kt` | Shared regex patterns | ~4 |

**Total estimated line reduction:** ~142 lines (after adding ~50 lines of utility code)

---

## Refactoring Priority

| Priority | Finding | Effort | Impact |
|----------|---------|--------|--------|
| **P2** | #1: Webhook initialization | Low | Medium |
| **P2** | #2: Circuit breaker pattern | Medium | High |
| **P3** | #3: Snowflake regex | Low | Low |
| **P3** | #4: Event embed pattern | Medium | Medium |
| **P3** | #5: Timeout pattern | Medium | Medium |
