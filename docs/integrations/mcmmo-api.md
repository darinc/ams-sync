# MCMMO API Integration

**Complexity**: Intermediate
**Key File**: [`mcmmo/McmmoApiWrapper.kt`](../../src/main/kotlin/io/github/darinc/amssync/mcmmo/McmmoApiWrapper.kt)

## Critical Knowledge

> **IMPORTANT**: This document contains critical information about accessing MCMMO player data. Getting this wrong means your plugin won't find any player stats!

### The #1 Mistake

```kotlin
// ❌ WRONG - Returns null for offline players!
val profile = UserManager.getOfflinePlayer(uuid)

// ✅ CORRECT - Loads from flatfile storage
val profile = mcMMO.getDatabaseManager().loadPlayerProfile(uuid)
```

**Why?** `UserManager` only returns profiles that are **currently loaded in memory**. For offline players using flatfile storage, their profile isn't loaded. You must use `DatabaseManager` to read from disk.

See: [mcMMO Issue #5163](https://github.com/mcMMO-Dev/mcMMO/issues/5163)

## McmmoApiWrapper

### Initialization

```kotlin
class McmmoApiWrapper(
    private val plugin: AMSSyncPlugin,
    private val maxPlayersToScan: Int = 1000,   // Query limit
    private val cacheTtlMs: Long = 60000L       // 60 second cache
) {
    private val leaderboardCache = ConcurrentHashMap<String, CachedLeaderboard>()
}
```

### Getting Player Stats

```kotlin
fun getPlayerStats(playerName: String): Map<String, Int> {
    // Step 1: Find the player's OfflinePlayer object
    val offlinePlayer = getOfflinePlayer(playerName)
        ?: throw PlayerDataNotFoundException(playerName)

    // Step 2: Load profile from MCMMO database (not UserManager!)
    val profile = mcMMO.getDatabaseManager().loadPlayerProfile(offlinePlayer.uniqueId)

    // Step 3: Check if profile loaded successfully
    if (!profile.isLoaded) {
        throw PlayerDataNotFoundException(playerName)
    }

    // Step 4: Extract skill levels (excluding child skills)
    return PrimarySkillType.values()
        .filter { !it.isChildSkill }
        .associate { skill ->
            skill.name to profile.getSkillLevel(skill)
        }
}
```

### Power Level Calculation

```kotlin
fun getPowerLevel(playerName: String): Int {
    val offlinePlayer = getOfflinePlayer(playerName)
        ?: throw PlayerDataNotFoundException(playerName)

    val profile = mcMMO.getDatabaseManager().loadPlayerProfile(offlinePlayer.uniqueId)
    if (!profile.isLoaded) {
        throw PlayerDataNotFoundException(playerName)
    }

    // Sum all non-child skills
    return PrimarySkillType.values()
        .filter { !it.isChildSkill }  // Important! Exclude Salvage, Smelting, etc.
        .sumOf { profile.getSkillLevel(it) }
}
```

**Why filter child skills?** Child skills (Salvage, Smelting) derive their levels from parent skills. Including them would double-count.

## Offline Player Lookup

### The Problem

```kotlin
// ❌ WRONG - Creates new player with offline-mode UUID!
val player = Bukkit.getOfflinePlayer("Steve")
```

`Bukkit.getOfflinePlayer(name)` doesn't search by name - it creates a new `OfflinePlayer` with an offline-mode UUID. This won't match the player's real UUID.

### The Solution

```kotlin
fun getOfflinePlayer(playerName: String): OfflinePlayer? {
    // 1. Check online players first (most efficient)
    val onlinePlayer = Bukkit.getOnlinePlayers().find {
        it.name.equals(playerName, ignoreCase = true)
    }
    if (onlinePlayer != null) return onlinePlayer

    // 2. Search through all offline players
    val offlinePlayer = Bukkit.getOfflinePlayers().find {
        it.name?.equals(playerName, ignoreCase = true) == true
    }

    if (offlinePlayer == null) return null

    // 3. Verify player has actually played
    if (!offlinePlayer.hasPlayedBefore() && !offlinePlayer.isOnline) {
        return null
    }

    return offlinePlayer
}
```

## Leaderboard Generation

### With Caching

```kotlin
fun getLeaderboard(skillName: String, limit: Int = 10): List<Pair<String, Int>> {
    val skill = parseSkillType(skillName)  // Validates skill name
    val cacheKey = "skill:${skill.name}:$limit"

    // Check cache first
    val cached = leaderboardCache[cacheKey]
    if (cached != null && System.currentTimeMillis() - cached.timestamp < cacheTtlMs) {
        return cached.data  // Cache hit!
    }

    // Generate leaderboard
    val allPlayers = Bukkit.getOfflinePlayers()
    val playersToScan = allPlayers.take(maxPlayersToScan)  // Query limit

    val leaderboard = playersToScan
        .mapNotNull { offlinePlayer ->
            val name = offlinePlayer.name ?: return@mapNotNull null

            val profile = mcMMO.getDatabaseManager().loadPlayerProfile(offlinePlayer.uniqueId)
            if (!profile.isLoaded) return@mapNotNull null

            val level = profile.getSkillLevel(skill)
            if (level > 0) name to level else null
        }
        .sortedByDescending { it.second }
        .take(limit)

    // Cache result
    leaderboardCache[cacheKey] = CachedLeaderboard(leaderboard, System.currentTimeMillis())

    return leaderboard
}
```

### Query Limiting

Large servers can have thousands of players. Loading all profiles is slow:

```yaml
mcmmo:
  leaderboard:
    max-players-to-scan: 1000   # Limit queries
    cache-ttl-seconds: 60       # Cache results
```

```kotlin
val allPlayers = Bukkit.getOfflinePlayers()  // Could be 10,000+
val playersToScan = allPlayers.take(maxPlayersToScan)  // Only scan 1,000

if (allPlayers.size > maxPlayersToScan) {
    plugin.logger.warning(
        "Server has ${allPlayers.size} players, only scanning first $maxPlayersToScan"
    )
}
```

## Skill Parsing

```kotlin
fun parseSkillType(skillName: String): PrimarySkillType {
    // Try exact match
    val skill = try {
        PrimarySkillType.valueOf(skillName.uppercase())
    } catch (e: IllegalArgumentException) {
        // Try case-insensitive search
        PrimarySkillType.values().find {
            it.name.equals(skillName, ignoreCase = true)
        }
    }

    return skill ?: throw InvalidSkillException(
        skillName = skillName,
        validSkills = getAllSkillNames()
    )
}

fun getAllSkillNames(): List<String> {
    return PrimarySkillType.values()
        .filter { !it.isChildSkill }
        .map { it.name }
}
```

## Caching Strategy

### Cache Structure

```kotlin
private data class CachedLeaderboard(
    val data: List<Pair<String, Int>>,
    val timestamp: Long
)

private val leaderboardCache = ConcurrentHashMap<String, CachedLeaderboard>()
```

### Cache Keys

```
skill:MINING:10     - Mining leaderboard, top 10
skill:WOODCUTTING:5 - Woodcutting leaderboard, top 5
power:10            - Power level leaderboard, top 10
```

### TTL Checking

```kotlin
val cached = leaderboardCache[cacheKey]
if (cached != null && System.currentTimeMillis() - cached.timestamp < cacheTtlMs) {
    return cached.data  // Fresh enough
}
// Otherwise regenerate
```

### Why Cache?

Without caching, every `/mctop` command:
1. Loads 1000+ player profiles from disk
2. Calculates skill levels
3. Sorts and filters

With 60-second cache:
- First request: ~500ms (generates leaderboard)
- Subsequent requests: ~1ms (returns cached)

## Exception Hierarchy

```kotlin
sealed class McmmoQueryException : AMSSyncException()

class PlayerDataNotFoundException(
    val playerName: String
) : McmmoQueryException()

class InvalidSkillException(
    val skillName: String,
    val validSkills: List<String>
) : McmmoQueryException()
```

Usage:
```kotlin
try {
    val stats = mcmmoApiWrapper.getPlayerStats(playerName)
} catch (e: PlayerDataNotFoundException) {
    reply("Player ${e.playerName} not found")
} catch (e: InvalidSkillException) {
    reply("Invalid skill. Try: ${e.validSkills.joinToString()}")
}
```

## Thread Safety

### Safe: ConcurrentHashMap Cache

```kotlin
private val leaderboardCache = ConcurrentHashMap<String, CachedLeaderboard>()

// Safe concurrent access
leaderboardCache[key] = value
val cached = leaderboardCache[key]
```

### Safe: MCMMO DatabaseManager

`mcMMO.getDatabaseManager().loadPlayerProfile()` is thread-safe and can be called from any thread.

### Unsafe: Bukkit API

Some Bukkit API calls must be on main thread:
```kotlin
// May need main thread depending on implementation
val offlinePlayers = Bukkit.getOfflinePlayers()
```

In practice, `getOfflinePlayers()` works from async threads, but be cautious with other Bukkit API calls.

## Configuration

```yaml
mcmmo:
  leaderboard:
    # Performance tuning
    max-players-to-scan: 1000   # Reduce for faster queries
    cache-ttl-seconds: 60       # Increase for less DB load
```

### Recommendations

| Server Size | max-players-to-scan | cache-ttl-seconds |
|-------------|---------------------|-------------------|
| Small (<100) | 500 | 30 |
| Medium (<1000) | 1000 | 60 |
| Large (1000+) | 2000 | 120 |

## Build Dependency

MCMMO must be installed locally:

```bash
# Clone and build MCMMO
git clone https://github.com/mcMMO-Dev/mcMMO.git
cd mcMMO
mvn install -DskipTests
```

In `build.gradle.kts`:
```kotlin
repositories {
    mavenLocal()  // For locally-built MCMMO
}

dependencies {
    compileOnly("com.gmail.nossr50.mcMMO:mcMMO:2.1.230")
}
```

## Testing

### Verify Player Lookup

```kotlin
// Check console output
plugin.logger.info("Searching for player: '$playerName'")
plugin.logger.info("Found ${allOfflinePlayers.size} offline players")
plugin.logger.info("Available names: ${allOfflinePlayers.mapNotNull { it.name }.take(10)}")
```

### Verify Profile Loading

```kotlin
val profile = mcMMO.getDatabaseManager().loadPlayerProfile(uuid)
plugin.logger.info("Profile loaded: ${profile.isLoaded}")
plugin.logger.info("Power level: ${calculatePowerLevel(profile)}")
```

## Common Issues

**"Player not found" for known player**:
- Player hasn't joined since UUID migration
- Player name case mismatch (use case-insensitive search)
- MCMMO flatfile corrupted

**Leaderboard missing players**:
- `max-players-to-scan` too low
- Players have 0 in that skill
- Profile not loaded (check `profile.isLoaded`)

**Slow leaderboard queries**:
- Increase `cache-ttl-seconds`
- Decrease `max-players-to-scan`
- Check disk I/O on server

## Related Documentation

- [Discord Commands](../features/discord-commands.md) - Uses MCMMO data
- [Image Cards](../features/image-cards.md) - Visualizes MCMMO data
- [Error Handling](../observability/error-handling.md) - Exception types
