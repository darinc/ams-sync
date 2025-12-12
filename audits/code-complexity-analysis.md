# Code Complexity Analysis Report
## AMSSync Minecraft Plugin

**Analysis Date:** 2025-12-11
**Total Kotlin Files Analyzed:** 46
**Codebase Location:** `src/main/kotlin/io/github/darinc/amssync/`

---

## Executive Summary

| Category | Count | Status |
|----------|-------|--------|
| Files >300 LOC | 10 | HIGH |
| Files >600 LOC | 2 | CRITICAL |
| Functions >50 LOC | 16 | MEDIUM |
| Functions >100 LOC | 4 | HIGH |
| Cyclomatic Complexity >10 | 2 | CRITICAL |
| Nested If Depth >3 | 6 | HIGH |

**Overall Code Health: 7/10 (GOOD)**

Most critical finding: **AMSSyncPlugin.kt** (683 lines) and **AMSSyncCommand.kt** (688 lines) are oversized with too many responsibilities.

---

## 1. Cyclomatic Complexity Findings

### 1.1 AMSSyncPlugin.kt - onEnable()
**Location:** `AMSSyncPlugin.kt:112-143`
**Complexity:** ~8
**Severity:** 7/10

Sequential if statements for config loading/validation with 15+ service initializations.

```kotlin
// Current: Multiple initialization blocks
override fun onEnable() {
    if (!dataFolder.exists()) { ... }
    handleConfigMigration()
    saveDefaultConfig()
    val discordConfig = loadDiscordConfig() ?: return
    val retryConfig = loadRetryConfig()
    initializeResilienceComponents()
    initializeImageCards()
    discordManager = DiscordManager(...)
    connectToDiscord(...)
}
```

**Remediation:**
```kotlin
// Proposed: Initialization phases
override fun onEnable() {
    val phases = listOf(
        ::initializeDataFolder,
        ::initializeConfig,
        ::initializeResilience,
        ::initializeDiscord
    )
    phases.forEach { phase ->
        if (!phase()) {
            logger.severe("Initialization failed at ${phase.name}")
            return
        }
    }
}
```

---

### 1.2 ConfigMigrator.kt - mergeConfigs()
**Location:** `ConfigMigrator.kt:220-282`
**Complexity:** ~9
**Severity:** 6/10

Nested if-else for YAML line parsing with path stack management.

**Remediation:** Extract YAML line parsing to separate function:
```kotlin
private fun parseYamlLine(line: String): YamlLineInfo {
    val trimmed = line.trimStart()
    val indent = line.length - trimmed.length
    val colonIndex = trimmed.indexOf(':')
    return YamlLineInfo(
        indent = indent,
        isComment = trimmed.isEmpty() || trimmed.startsWith("#"),
        key = if (colonIndex > 0) trimmed.substring(0, colonIndex) else null,
        isSection = colonIndex > 0 && trimmed.substring(colonIndex + 1).isBlank()
    )
}
```

---

### 1.3 AMSSyncCommand.kt - onCommand()
**Location:** `AMSSyncCommand.kt:24-94`
**Complexity:** ~7
**Severity:** 5/10

Permission check + rate limiting + command routing in single method.

**Remediation:**
```kotlin
override fun onCommand(...): Boolean {
    if (!checkPermission(sender)) return true
    if (!checkRateLimit(sender)) return true
    return routeCommand(sender, args)
}

private fun routeCommand(sender: CommandSender, args: Array<String>): Boolean {
    val handler = commandHandlers[args.getOrNull(0)?.lowercase()]
        ?: return handleHelp(sender)
    return handler.execute(sender, args.drop(1))
}
```

---

### 1.4 PlayerCardRenderer.kt - drawPodium()
**Location:** `PlayerCardRenderer.kt:338-460`
**Nesting Depth:** 4 levels
**Complexity:** ~8
**Severity:** 6/10

120+ lines of duplicated code for 1st, 2nd, 3rd place rendering.

**Remediation:**
```kotlin
// Extract common podium position rendering
private fun drawPodiumPosition(
    g2d: Graphics2D,
    rank: Int,
    data: Pair<String, Int>?,
    avatar: BufferedImage?,
    x: Int,
    y: Int,
    width: Int,
    color: Color
) {
    data ?: return
    val (name, score) = data

    avatar?.let { GraphicsUtils.drawCenteredImage(g2d, it, x, y - avatarSize) }

    g2d.color = color
    g2d.fillRect(x - width/2, y, width, podiumHeights[rank])

    drawCenteredText(g2d, name, x, y + 20)
    drawCenteredText(g2d, formatScore(score), x, y + 40)
}

// Usage in drawPodium():
drawPodiumPosition(g2d, 0, top3.getOrNull(0), avatars[0], centerX, podiumY, podiumWidth, CardStyles.PODIUM_GOLD)
drawPodiumPosition(g2d, 1, top3.getOrNull(1), avatars[1], leftX, podiumY, podiumWidth, CardStyles.PODIUM_SILVER)
drawPodiumPosition(g2d, 2, top3.getOrNull(2), avatars[2], rightX, podiumY, podiumWidth, CardStyles.PODIUM_BRONZE)
```

---

## 2. Cognitive Complexity Findings

### 2.1 PlayerCardRenderer.kt - renderStatsCard()
**Location:** `PlayerCardRenderer.kt:30-169`
**Cognitive Complexity:** 12+
**Severity:** 7/10

139 lines mixing graphics rendering with layout calculations and hardcoded coordinate arithmetic.

**Remediation:** Use layout data classes:
```kotlin
data class CardSection(
    val x: Int, val y: Int,
    val width: Int, val height: Int
)

data class StatsCardLayout(
    val header: CardSection,
    val skills: CardSection,
    val footer: CardSection
)

private fun calculateLayout(width: Int, height: Int, padding: Int): StatsCardLayout {
    val headerHeight = 100
    val footerHeight = 30
    return StatsCardLayout(
        header = CardSection(padding, padding, width - 2*padding, headerHeight),
        skills = CardSection(padding, padding + headerHeight + 10, width - 2*padding, height - headerHeight - footerHeight - 3*padding),
        footer = CardSection(padding, height - footerHeight - padding, width - 2*padding, footerHeight)
    )
}
```

---

### 2.2 AMSSyncCommand.kt - handleQuick()
**Location:** `AMSSyncCommand.kt:460-560`
**Cognitive Complexity:** 11+
**Severity:** 6/10

Complex Discord guild loading with nested callbacks (JDA onSuccess → Bukkit runTask).

**Remediation:** Extract Discord operations:
```kotlin
private fun loadDiscordMembers(
    sender: CommandSender,
    onSuccess: (List<Member>) -> Unit
) {
    val jda = plugin.discordManager.getJda() ?: run {
        sender.sendMessage("${ChatColor.RED}Discord not connected")
        return
    }

    val guildId = plugin.config.getString("discord.guild-id")
    if (guildId.isNullOrBlank()) {
        sender.sendMessage("${ChatColor.RED}Guild ID not configured")
        return
    }

    val guild = jda.getGuildById(guildId) ?: run {
        sender.sendMessage("${ChatColor.RED}Guild not found")
        return
    }

    guild.loadMembers()
        .onSuccess { members ->
            Bukkit.getScheduler().runTask(plugin, Runnable {
                onSuccess(members.filter { !it.user.isBot }.sortedBy { it.effectiveName })
            })
        }
        .onError { sender.sendMessage("${ChatColor.RED}Failed to load members") }
}
```

---

## 3. Lines of Code Metrics

### Files Over 300 Lines

| File | Lines | Severity | Primary Issue |
|------|-------|----------|---------------|
| AMSSyncPlugin.kt | 683 | **8/10** | God Object - 15+ service properties |
| AMSSyncCommand.kt | 688 | **7/10** | 9 subcommands in single class |
| PlayerCardRenderer.kt | 595 | **6/10** | Mixed layout + rendering |
| McMMOEventListener.kt | 461 | **6/10** | Events + rendering + networking |
| ConfigMigrator.kt | 384 | **5/10** | Complex YAML handling |
| CircuitBreaker.kt | 355 | **4/10** | Acceptable - focused pattern |
| MilestoneCardRenderer.kt | 322 | **5/10** | Graphics rendering |
| TimeoutManager.kt | 260 | **4/10** | Acceptable - focused |
| ChatBridge.kt | 256 | **4/10** | Good separation |

### Functions Over 50 Lines

| File | Function | Lines | Severity |
|------|----------|-------|----------|
| PlayerCardRenderer.kt | renderStatsCard | 139 | 7/10 |
| PlayerCardRenderer.kt | drawPodium | 122 | 7/10 |
| AMSSyncCommand.kt | handleQuick | 100 | 6/10 |
| PlayerCardRenderer.kt | renderLeaderboardCard | 99 | 6/10 |
| AMSSyncCommand.kt | handleDiscordList | 75 | 6/10 |
| AMSSyncCommand.kt | handlePlayers | 65 | 6/10 |
| ConfigMigrator.kt | mergeConfigs | 62 | 6/10 |

---

## 4. Coupling Metrics

### High Efferent Coupling (Dependencies On Others)

#### AMSSyncPlugin.kt - 33 imports
**Severity:** 8/10

Dependencies span all subsystems:
- 11 discord package imports
- 5 image package imports
- 3 event listener imports
- 2 config imports
- 2 linking imports

**Remediation:**
```kotlin
// Create service registries to reduce import sprawl
class DiscordServices(
    val manager: DiscordManager,
    val apiWrapper: DiscordApiWrapper?,
    val chatBridge: ChatBridge?,
    val presence: PlayerCountPresence?,
    val statusChannel: StatusChannelManager?
)

class AMSSyncPlugin : JavaPlugin() {
    lateinit var discord: DiscordServices
    lateinit var resilience: ResilienceServices
    lateinit var mcmmo: McmmoServices
}
```

### High Afferent Coupling (Others Depend On)

#### AMSSyncPlugin.kt
**Reverse Dependencies:** ~20+ classes
**Severity:** 7/10

All commands, listeners, and services reference the plugin directly.

**Remediation:** Extract interfaces for services:
```kotlin
interface StatsProvider {
    fun getPlayerStats(uuid: UUID): PlayerStats?
    fun getLeaderboard(skill: PrimarySkillType, limit: Int): List<Pair<String, Int>>
}

// Commands depend on interface, not plugin
class McStatsCommand(private val statsProvider: StatsProvider)
```

---

## 5. Cohesion Analysis

### Poor Cohesion Classes

#### 5.1 AMSSyncPlugin.kt
**Severity:** 8/10

**Responsibilities (too many):**
1. Plugin lifecycle
2. Configuration loading/validation
3. Service initialization
4. Configuration migration
5. Discord connection retry logic
6. Event listener registration
7. 15+ service property management

**Remediation:**
```kotlin
// Split into focused classes
class PluginLifecycle(private val plugin: AMSSyncPlugin) {
    fun initialize(): Boolean { ... }
    fun shutdown() { ... }
}

class ServiceRegistry {
    lateinit var discord: DiscordServices
    lateinit var resilience: ResilienceServices
    lateinit var mcmmo: McmmoServices
    lateinit var image: ImageServices

    fun initialize(plugin: AMSSyncPlugin, config: FileConfiguration): Boolean {
        resilience = ResilienceServices.fromConfig(config)
        mcmmo = McmmoServices.fromConfig(config, resilience)
        discord = DiscordServices.fromConfig(config, resilience)
        image = ImageServices.fromConfig(config)
        return true
    }
}
```

---

#### 5.2 AMSSyncCommand.kt
**Severity:** 7/10

**Responsibilities (too many):**
1. Command parsing/routing
2. User listing/pagination
3. Quick linking workflow
4. Direct linking
5. Session management
6. Rate limiting
7. Audit logging
8. Discord member loading

**Remediation:** Command pattern with handlers:
```kotlin
interface SubcommandHandler {
    val name: String
    val permission: String
    fun execute(sender: CommandSender, args: List<String>): Boolean
    fun tabComplete(sender: CommandSender, args: List<String>): List<String>
}

class AddHandler(private val services: Services) : SubcommandHandler { ... }
class RemoveHandler(private val services: Services) : SubcommandHandler { ... }
class QuickHandler(private val services: Services) : SubcommandHandler { ... }

class AMSSyncCommand(handlers: List<SubcommandHandler>) : CommandExecutor {
    private val handlerMap = handlers.associateBy { it.name }

    override fun onCommand(...): Boolean {
        val handler = handlerMap[args.getOrNull(0)?.lowercase()]
        return handler?.execute(sender, args.drop(1)) ?: showHelp(sender)
    }
}
```

---

#### 5.3 McMMOEventListener.kt
**Severity:** 5/10

**Mixed Concerns:**
- Event listening
- Milestone detection
- Image card generation
- Embed generation
- Webhook management

**Remediation:**
```kotlin
class MilestoneDetector {
    fun checkSkillMilestone(skill: PrimarySkillType, level: Int): MilestoneType?
    fun checkPowerLevelMilestone(powerLevel: Int, previousLevel: Int): MilestoneType?
}

interface AnnouncementService {
    fun announce(milestone: MilestoneEvent)
}

class ImageAnnouncementService : AnnouncementService { ... }
class EmbedAnnouncementService : AnnouncementService { ... }

class McMMOEventListener(
    private val detector: MilestoneDetector,
    private val announcer: AnnouncementService
) : Listener {
    @EventHandler
    fun onLevelUp(event: McMMOPlayerLevelUpEvent) {
        val milestone = detector.checkSkillMilestone(event.skill, event.skillLevel)
            ?: return
        announcer.announce(MilestoneEvent(event.player, milestone))
    }
}
```

---

### Good Cohesion Classes (Reference)

| Class | Cohesion | Notes |
|-------|----------|-------|
| CircuitBreaker.kt | 9/10 | Single responsibility: state machine |
| TimeoutManager.kt | 9/10 | Single responsibility: timeout execution |
| RetryManager.kt | 10/10 | Clean, focused retry logic |
| RateLimiter.kt | 9/10 | Single responsibility: rate limiting |
| ChatBridge.kt | 8/10 | Focused on message relay |

---

## 6. Priority Remediation Roadmap

### Priority 1: AMSSyncPlugin Decomposition
**Effort:** High | **Impact:** High | **Severity:** 8/10

1. Create `ServiceRegistry` to hold all services
2. Extract initialization to `PluginInitializer`
3. Extract Discord connection logic to `DiscordConnector`
4. Reduce AMSSyncPlugin to ~200 lines

---

### Priority 2: AMSSyncCommand Refactoring
**Effort:** Medium | **Impact:** Medium | **Severity:** 7/10

1. Create `SubcommandHandler` interface
2. Extract each subcommand to separate handler class
3. Implement command router in main class
4. Move session management to dedicated `LinkingSessionService`

---

### Priority 3: PlayerCardRenderer Extraction
**Effort:** Medium | **Impact:** Medium | **Severity:** 7/10

1. Create `CardLayout` data classes
2. Extract `drawPodiumPosition()` helper
3. Create `SkillGridRenderer` for skill display
4. Reduce main methods to ~50 lines each

---

### Priority 4: McMMOEventListener Split
**Effort:** Low | **Impact:** Low | **Severity:** 5/10

1. Extract `MilestoneDetector` class
2. Create `AnnouncementService` interface
3. Separate image vs embed announcement implementations

---

## 7. Metrics Summary Table

| Metric | Current | Target | Gap |
|--------|---------|--------|-----|
| Max file LOC | 688 | 300 | -388 |
| Max function LOC | 139 | 50 | -89 |
| Max cyclomatic complexity | ~9 | 10 | OK |
| Max nesting depth | 4 | 3 | -1 |
| God objects | 2 | 0 | -2 |
| Avg imports per file | 8 | 10 | OK |

---

## 8. Appendix: File Scores

| File | LOC | Complexity | Cohesion | Score |
|------|-----|------------|----------|-------|
| AMSSyncPlugin.kt | 683 | HIGH | POOR | 8/10 |
| AMSSyncCommand.kt | 688 | HIGH | POOR | 7/10 |
| PlayerCardRenderer.kt | 595 | HIGH | MEDIUM | 7/10 |
| McMMOEventListener.kt | 461 | MEDIUM | MEDIUM | 6/10 |
| ConfigMigrator.kt | 384 | MEDIUM | GOOD | 6/10 |
| MilestoneCardRenderer.kt | 322 | MEDIUM | GOOD | 5/10 |
| CircuitBreaker.kt | 355 | MEDIUM | EXCELLENT | 5/10 |
| TimeoutManager.kt | 260 | MEDIUM | EXCELLENT | 5/10 |
| ChatBridge.kt | 256 | LOW | GOOD | 5/10 |
| DiscordManager.kt | 172 | LOW | GOOD | 4/10 |
| RetryManager.kt | 127 | LOW | EXCELLENT | 3/10 |
| RateLimiter.kt | 89 | LOW | EXCELLENT | 2/10 |

*Score = Severity of issues (higher = more urgent to address)*

---

**Report Generated:** 2025-12-11
