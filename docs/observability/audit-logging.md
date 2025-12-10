# Audit Logging

**Complexity**: Beginner
**Key File**: [`audit/AuditLogger.kt`](../../src/main/kotlin/io/github/darinc/amssync/audit/AuditLogger.kt)

## Overview

AuditLogger tracks administrative actions for security and compliance. It provides:

- **Console logging** - Immediate visibility in server console
- **JSON audit file** - Structured, parseable audit trail

## Dual Output

```
Admin Action
     │
     ├─► Console (INFO level)
     │   "[AUDIT] [+] Link User: 123456789 (DISCORD_USER) -> player_name"
     │
     └─► audit.log (JSON Lines)
         {"timestamp":"2024-01-15T10:30:00.000-05:00","action":"LINK_USER",...}
```

## Action Types

```kotlin
enum class AuditAction(val displayName: String) {
    LINK_USER("Link User"),           // Discord-MC link created
    UNLINK_USER("Unlink User"),       // Discord-MC link removed
    LIST_MAPPINGS("List Mappings"),   // Viewed all links
    CHECK_USER("Check User"),         // Checked specific user
    PERMISSION_DENIED("Permission Denied"),  // Access denied
    RATE_LIMITED("Rate Limited"),     // Rate limit triggered
    INVALID_INPUT("Invalid Input")    // Bad input received
}
```

## Actor Types

```kotlin
enum class ActorType {
    DISCORD_USER,      // Action from Discord command
    MINECRAFT_PLAYER,  // Action from Minecraft command
    CONSOLE           // Action from server console
}
```

## Usage Examples

### Log Admin Action

```kotlin
auditLogger.logAdminAction(
    action = AuditAction.LINK_USER,
    actor = discordId,
    actorType = ActorType.DISCORD_USER,
    target = minecraftUsername,
    success = true,
    details = mapOf("method" to "discord_command")
)
```

**Console output:**
```
[INFO] [AUDIT] [+] Link User: 123456789012345678 (DISCORD_USER) -> steve method=discord_command
```

**JSON output:**
```json
{"timestamp":"2024-01-15T10:30:00.000-05:00","action":"LINK_USER","actor":"123456789012345678","actorType":"DISCORD_USER","success":true,"target":"steve","details":{"method":"discord_command"}}
```

### Log Security Event

```kotlin
auditLogger.logSecurityEvent(
    event = SecurityEvent.RATE_LIMITED,
    actor = userId,
    actorType = ActorType.DISCORD_USER,
    details = mapOf(
        "command" to "mcstats",
        "requestCount" to 61
    )
)
```

**Console output:**
```
[INFO] [AUDIT] [-] Rate Limited: 123456789012345678 (DISCORD_USER) command=mcstats, requestCount=61
```

## Security Events

```kotlin
enum class SecurityEvent {
    PERMISSION_DENIED,  // User lacks required permission
    RATE_LIMITED,       // User exceeded rate limit
    INVALID_INPUT       // Invalid or malicious input detected
}
```

## Implementation Details

### Console Format

```kotlin
val statusIcon = if (success) "+" else "-"
val targetStr = target?.let { " -> $it" } ?: ""
val detailsStr = if (details.isNotEmpty()) {
    " ${details.entries.joinToString(", ") { "${it.key}=${it.value}" }}"
} else ""

val message = "[AUDIT] [$statusIcon] ${action.displayName}: $actor ($actorType)$targetStr$detailsStr"
plugin.logger.info(message)
```

### JSON Format

```kotlin
private fun buildJsonLine(
    timestamp: String,
    action: String,
    actor: String,
    actorType: String,
    target: String?,
    success: Boolean,
    details: Map<String, Any>
): String {
    val parts = mutableListOf(
        """"timestamp":"$timestamp"""",
        """"action":"$action"""",
        """"actor":"${escapeJson(actor)}"""",
        """"actorType":"$actorType"""",
        """"success":$success"""
    )

    target?.let {
        parts.add(""""target":"${escapeJson(it)}"""")
    }

    if (details.isNotEmpty()) {
        val detailsJson = details.entries.joinToString(",") { (key, value) ->
            val jsonValue = when (value) {
                is String -> "\"${escapeJson(value)}\""
                is Number -> value.toString()
                is Boolean -> value.toString()
                else -> "\"${escapeJson(value.toString())}\""
            }
            """"$key":$jsonValue"""
        }
        parts.add(""""details":{$detailsJson}""")
    }

    return "{${parts.joinToString(",")}}"
}
```

### JSON Escaping

```kotlin
private fun escapeJson(value: String): String {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
```

## Audit File Location

```
plugins/AMSSync/audit.log
```

File is created lazily on first log entry:

```kotlin
private val auditFile: File by lazy {
    File(plugin.dataFolder, "audit.log").also {
        if (!it.exists()) {
            it.createNewFile()
        }
    }
}
```

## Integration with Commands

### Discord Link Command

```kotlin
// In DiscordLinkCommand
when (subcommand) {
    "add" -> {
        userMappingService.addMapping(discordId, minecraftName)
        auditLogger.logAdminAction(
            action = AuditAction.LINK_USER,
            actor = event.user.id,
            actorType = ActorType.DISCORD_USER,
            target = "$discordId -> $minecraftName",
            success = true
        )
    }
    "remove" -> {
        userMappingService.removeMapping(discordId)
        auditLogger.logAdminAction(
            action = AuditAction.UNLINK_USER,
            actor = event.user.id,
            actorType = ActorType.DISCORD_USER,
            target = discordId,
            success = true
        )
    }
}
```

### Minecraft Command

```kotlin
// In AMSSyncCommand
auditLogger.logAdminAction(
    action = AuditAction.LINK_USER,
    actor = sender.name,
    actorType = if (sender is Player) ActorType.MINECRAFT_PLAYER else ActorType.CONSOLE,
    target = "$discordId -> $minecraftName",
    success = true
)
```

### Rate Limiter Integration

```kotlin
when (val result = rateLimiter.checkRateLimit(userId)) {
    is RateLimitResult.BurstLimited -> {
        auditLogger.logSecurityEvent(
            event = SecurityEvent.RATE_LIMITED,
            actor = userId,
            actorType = ActorType.DISCORD_USER,
            details = mapOf("command" to commandName)
        )
    }
}
```

## Parsing Audit Logs

Audit logs use JSON Lines format (one JSON object per line):

```bash
# View all link events
grep '"action":"LINK_USER"' plugins/AMSSync/audit.log | jq .

# Count rate limit events
grep '"action":"RATE_LIMITED"' plugins/AMSSync/audit.log | wc -l

# Find failed actions
grep '"success":false' plugins/AMSSync/audit.log | jq .

# Actions by specific user
grep '123456789012345678' plugins/AMSSync/audit.log | jq .
```

## JSON Schema

```json
{
  "timestamp": "ISO-8601 datetime",
  "action": "LINK_USER | UNLINK_USER | LIST_MAPPINGS | CHECK_USER | PERMISSION_DENIED | RATE_LIMITED | INVALID_INPUT",
  "actor": "Discord ID, Minecraft username, or 'CONSOLE'",
  "actorType": "DISCORD_USER | MINECRAFT_PLAYER | CONSOLE",
  "success": true,
  "target": "Optional target of action",
  "details": {
    "key": "Additional context"
  }
}
```

## Example Audit Trail

```json
{"timestamp":"2024-01-15T10:30:00.000-05:00","action":"LINK_USER","actor":"123456789012345678","actorType":"DISCORD_USER","success":true,"target":"steve"}
{"timestamp":"2024-01-15T10:30:05.000-05:00","action":"CHECK_USER","actor":"123456789012345678","actorType":"DISCORD_USER","success":true,"target":"987654321098765432"}
{"timestamp":"2024-01-15T10:30:10.000-05:00","action":"RATE_LIMITED","actor":"111222333444555666","actorType":"DISCORD_USER","success":false,"details":{"command":"mcstats","requestCount":61}}
{"timestamp":"2024-01-15T10:31:00.000-05:00","action":"UNLINK_USER","actor":"Admin","actorType":"CONSOLE","success":true,"target":"123456789012345678"}
```

## Initialization

```kotlin
// In AMSSyncPlugin.onEnable()
auditLogger = AuditLogger(this)
logger.info("Audit logger initialized")
```

## Common Use Cases

### Security Monitoring

Monitor for abuse patterns:
- Excessive `RATE_LIMITED` events from same user
- `PERMISSION_DENIED` attempts
- `INVALID_INPUT` injection attempts

### Compliance

Track all administrative changes:
- Who linked which users
- When links were removed
- Who viewed sensitive data

### Debugging

Trace issues by following audit trail:
- Verify actions were logged
- Check success/failure status
- Review timing of events

## Related Documentation

- [Rate Limiting](../patterns/rate-limiting.md) - Rate limit events
- [Error Handling](error-handling.md) - Error tracking
- [Metrics](metrics.md) - Performance monitoring
