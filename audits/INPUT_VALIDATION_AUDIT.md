# Input Validation Security Audit Report

**Project:** AMSSync Minecraft Plugin
**Date:** 2025-12-11
**Auditor:** Claude Code Security Analysis
**Risk Score:** 2/10 (Low Risk)

---

## Executive Summary

The AMSSync plugin demonstrates **strong security practices** across all input validation categories. No critical or high-severity vulnerabilities were identified. The codebase shows consistent use of validation patterns, proper input sanitization, and defense-in-depth strategies.

### Key Findings
- **0 Critical vulnerabilities**
- **0 High-severity vulnerabilities**
- **2 Low-severity observations** (informational disclosures)
- **Strong validation patterns** consistently applied across all entry points

---

## Detailed Findings

### Finding #1: Information Disclosure in Error Messages

| Attribute | Value |
|-----------|-------|
| **Title** | Player Name Exposure in Error Responses |
| **Severity** | Low |
| **CWE** | CWE-209 (Information Exposure Through an Error Message) |
| **Evidence** | `McStatsCommand.kt:56-62`, `AmsStatsCommand.kt:85` |

**Description:**
Exception messages expose player names in Discord responses when players have no MCMMO data.

**Code Sample:**
```kotlin
// McStatsCommand.kt:56-62
catch (e: PlayerDataNotFoundException) {
    plugin.logger.fine("Player data not found: ${e.message}")
    CommandUtils.sendEphemeralMessage(
        event.hook,
        "Player **${e.playerName}** has no MCMMO data.\n" +
        "They may have never joined or haven't gained any XP yet.",
        ...
    )
}
```

**Why It Matters:**
While Minecraft usernames are generally public information, revealing whether a player has joined or has data could be useful for social engineering in some contexts.

**Exploitability:** Very Low - Player names are public, and the error message uses ephemeral responses.

**Remediation:**
No action required. This is expected behavior that aids usability. The use of ephemeral responses limits exposure.

---

### Finding #2: Discord ID Visibility in Admin Commands

| Attribute | Value |
|-----------|-------|
| **Title** | Discord IDs Displayed in Minecraft Chat |
| **Severity** | Low |
| **CWE** | CWE-200 (Information Exposure) |
| **Evidence** | `PlayersHandler.kt:31-32, 56-57` |

**Description:**
Admin listing commands show Discord IDs in Minecraft chat output.

**Code Sample:**
```kotlin
// Displays: "PlayerName §7(Discord: 123456789012345678)"
```

**Why It Matters:**
Discord IDs are semi-sensitive; knowing a user's ID enables direct message targeting.

**Exploitability:** Very Low - Only visible to server administrators with `amssync.admin` permission.

**Remediation:**
No action required. This is intentional admin-only functionality protected by permission checks.

---

## Validation Matrix

### Entry Points and Validation Status

| Entry Point | Input Type | Validation | Status |
|-------------|-----------|------------|--------|
| `/amssync add <discordId> <username>` | Discord ID | Regex `^\d{17,19}$` | **PASS** |
| `/amssync add <discordId> <username>` | MC Username | Regex `^[a-zA-Z0-9_]{3,16}$` | **PASS** |
| `/amssync remove <discordId>` | Discord ID | Regex validation | **PASS** |
| `/amssync link <num> <num>` | Session numbers | `toIntOrNull()` safe parsing | **PASS** |
| `/amssync quick <num> <num>` | Session numbers | `toIntOrNull()` safe parsing | **PASS** |
| Discord `/amssync add` | User mention | JDA User object (type-safe) | **PASS** |
| Discord `/amssync add` | MC Username | Regex `^[a-zA-Z0-9_]{3,16}$` | **PASS** |
| Discord `/amswhitelist add` | MC Username | Regex validation | **PASS** |
| Discord `/mcstats` | Username (optional) | Safe null handling + validation | **PASS** |
| Discord `/mcstats` | Skill name | Enum parsing with fallback | **PASS** |
| Discord `/mctop` | Skill name | Enum parsing with fallback | **PASS** |
| Config `discord.token` | Bot token | Regex `^[A-Za-z0-9_-]{18,}\.[A-Za-z0-9_-]{6}\.[A-Za-z0-9_-]{27,}$` | **PASS** |
| Config `discord.guild-id` | Guild ID | Regex `^\d{17,19}$` | **PASS** |
| Chat Bridge (MC→Discord) | Message content | @everyone/@here sanitization | **PASS** |
| Chat Bridge (Discord→MC) | Message content | Color code stripping | **PASS** |

---

## Checklist Assessment

### 1. SQL Injection Vulnerabilities

| Check | Status | Evidence |
|-------|--------|----------|
| Raw SQL queries without parameterization | **N/A** | No SQL queries in codebase |
| Dynamic query building | **N/A** | Uses MCMMO API, not direct SQL |
| Stored procedure calls | **N/A** | No stored procedures |

**Assessment:** No SQL is used. MCMMO data access uses UUID-based API calls:
```kotlin
// McmmoApiWrapper.kt:57
val profile = mcMMO.getDatabaseManager().loadPlayerProfile(offlinePlayer.uniqueId)
```

### 2. NoSQL Injection (MongoDB)

| Check | Status | Evidence |
|-------|--------|----------|
| Unvalidated query operators | **N/A** | No MongoDB/NoSQL usage |
| JavaScript execution in queries | **N/A** | No NoSQL databases |

**Assessment:** Not applicable - plugin uses MCMMO's flatfile storage and Bukkit's YAML config.

### 3. Command Injection

| Check | Status | Evidence |
|-------|--------|----------|
| Child process spawning | **PASS** | No `Runtime.exec()` or `ProcessBuilder` found |
| System command execution | **PASS** | Grep for `Runtime\|ProcessBuilder\|exec\(` returned no matches |

**Assessment:** No command execution functionality exists in the codebase.

### 4. XSS Prevention

| Check | Status | Evidence |
|-------|--------|----------|
| Input sanitization | **PASS** | Discord mention sanitization implemented |
| Output encoding | **PASS** | Minecraft color codes stripped from Discord input |
| Content-Type headers | **N/A** | No HTTP server functionality |

**Evidence:**
```kotlin
// ChatBridge.kt:160-167 - Discord output sanitization
private fun sanitizeDiscordMessage(message: String): String {
    return message
        .replace("@everyone", "@\u200Beveryone")
        .replace("@here", "@\u200Bhere")
        .replace(Regex("@(&|!)?(\\d+)")) { match -> "@\u200B${match.groupValues[1]}${match.groupValues[2]}" }
}

// ChatBridge.kt:172-180 - Minecraft output sanitization
private fun sanitizeMinecraftMessage(message: String): String {
    return message
        .replace("&", "&\u200B")  // Prevent color code injection
        .replace("\uFE0E", "")     // Strip variation selectors
        .replace("\uFE0F", "")
}
```

### 5. XXE (XML External Entity) Attacks

| Check | Status | Evidence |
|-------|--------|----------|
| XML parsing configuration | **N/A** | No XML parsing in codebase |
| File upload handling | **N/A** | No file upload functionality |

**Assessment:** Plugin uses YAML configuration (via Bukkit's safe YamlConfiguration), no XML parsing.

### 6. Path Traversal

| Check | Status | Evidence |
|-------|--------|----------|
| File system operations | **PASS** | All file paths use `plugin.dataFolder` base |
| Directory listing prevention | **PASS** | No directory traversal from user input |

**Evidence:**
```kotlin
// ConfigMigrator.kt:65 - Hardcoded filename within plugin folder
val configFile = File(plugin.dataFolder, "config.yml")

// ConfigMigrator.kt:156 - Timestamp-based backup naming (no user input)
val backupFile = File(configFile.parent, "config-backup-$timestamp.yml")

// AuditLogger.kt:16 - Fixed audit log path
File(plugin.dataFolder, "audit.log")
```

User input never flows into file path construction.

### 7. Request Validation

| Check | Status | Evidence |
|-------|--------|----------|
| Body size limits | **N/A** | No HTTP endpoints |
| Parameter pollution | **PASS** | JDA handles Discord parameter parsing |
| Type checking | **PASS** | Kotlin type system + explicit validation |
| Required field validation | **PASS** | Null checks before processing |

**Evidence - Type Checking:**
```kotlin
// Validators.kt:34,40 - Strict regex patterns
private val MC_USERNAME_REGEX = Regex("^[a-zA-Z0-9_]{3,16}$")
private val DISCORD_ID_REGEX = Regex("^\\d{17,19}$")
```

**Evidence - Required Field Validation:**
```kotlin
// AddHandler.kt:23-26
if (args.size < 2) {
    sender.sendMessage("§cUsage: $usage")
    return
}

// DiscordLinkCommand.kt:124-127
if (targetUser == null || minecraftName == null) {
    sendEphemeralMessage(event.hook, "❌ Missing required parameters.")
    return
}
```

---

## Defense-in-Depth Features

The codebase implements multiple security layers:

### 1. Rate Limiting
```kotlin
// RateLimiter.kt - Per-user rate limiting
when (val result = rateLimiter.checkRateLimit(sender.uniqueId.toString())) {
    is RateLimitResult.Cooldown -> { /* Block request */ }
    is RateLimitResult.BurstLimited -> { /* Block request */ }
    is RateLimitResult.Allowed -> { /* Proceed */ }
}
```

### 2. Permission Checks
```kotlin
// AMSSyncCommand.kt:75 - Minecraft permissions
if (sender.hasPermission("amssync.admin")) { ... }

// DiscordLinkCommand.kt:34 - Discord permissions
if (event.member?.hasPermission(Permission.MANAGE_SERVER) != true) { ... }
```

### 3. Audit Logging
```kotlin
// AuditLogger.kt - Security event tracking
fun logSecurityEvent(
    event: SecurityEvent,  // PERMISSION_DENIED, RATE_LIMITED, INVALID_INPUT
    actor: String,
    actorType: ActorType,
    details: Map<String, Any>
)
```

### 4. Circuit Breaker Pattern
```kotlin
// CircuitBreaker.kt - Prevents cascading failures
sealed class CircuitResult<T> {
    data class Success<T>(val result: T) : CircuitResult<T>()
    class Rejected<T> : CircuitResult<T>()
    data class Failed<T>(val exception: Exception) : CircuitResult<T>()
}
```

### 5. Token Masking
```kotlin
// ConfigValidator.kt:72-78 - Prevents token leakage in logs
fun maskToken(token: String): String {
    return if (token.length > 10) {
        token.take(10) + "..."
    } else {
        "[invalid token]"
    }
}
```

### 6. JSON Escaping in Audit Logs
```kotlin
// AuditLogger.kt:131-138 - Prevents log injection
private fun escapeJson(value: String): String {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
```

---

## Top 5 Prioritized Recommendations

Since no significant vulnerabilities were found, these are hardening suggestions:

| Priority | Recommendation | Effort | Impact |
|----------|---------------|--------|--------|
| 1 | Continue using current validation patterns | - | Maintain security |
| 2 | Consider adding input length limits to chat bridge messages | Low | Prevent potential DoS via very long messages |
| 3 | Add structured logging for security events | Medium | Improve incident response capability |
| 4 | Document security architecture for future maintainers | Low | Knowledge preservation |
| 5 | Consider adding HTTP timeouts to avatar fetching | Low | Already implemented (3s/5s) - no action needed |

---

## Conclusion

The AMSSync plugin demonstrates **exemplary security practices** for a Minecraft plugin:

1. **Strong input validation** with consistent use of regex patterns
2. **No injection vulnerabilities** - no SQL, command execution, or path traversal risks
3. **Proper sanitization** of cross-platform messages (Discord ↔ Minecraft)
4. **Defense-in-depth** with rate limiting, permissions, audit logging, and circuit breakers
5. **Type-safe data access** using UUIDs rather than string-based lookups

The overall risk score of **2/10** reflects a secure, well-designed system with only minor informational observations.

---

## Appendix: Files Reviewed

| File | Lines | Purpose |
|------|-------|---------|
| `validation/Validators.kt` | 93 | Core input validation |
| `config/ConfigValidator.kt` | 167 | Config validation |
| `config/ConfigMigrator.kt` | 384 | Config file handling |
| `audit/AuditLogger.kt` | 181 | Security event logging |
| `linking/UserMappingService.kt` | 266 | User mapping storage |
| `mcmmo/McmmoApiWrapper.kt` | 318 | MCMMO data access |
| `commands/AMSSyncCommand.kt` | 170 | MC command router |
| `commands/handlers/AddHandler.kt` | 84 | Add mapping handler |
| `discord/SlashCommandListener.kt` | 52 | Discord command router |
| `discord/commands/DiscordLinkCommand.kt` | 446 | Discord link command |
| `discord/commands/DiscordWhitelistCommand.kt` | 395 | Discord whitelist command |
| `discord/commands/McStatsCommand.kt` | 127 | Stats command |
| `discord/ChatBridge.kt` | 256 | Chat relay |
| `image/AvatarFetcher.kt` | 288 | External API calls |

**Total lines reviewed:** ~3,200+ lines of Kotlin
