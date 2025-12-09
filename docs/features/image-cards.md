# Image Cards

**Complexity**: Advanced
**Key Files**:
- [`image/PlayerCardRenderer.kt`](../../src/main/kotlin/io/github/darinc/amssync/image/PlayerCardRenderer.kt)
- [`image/MilestoneCardRenderer.kt`](../../src/main/kotlin/io/github/darinc/amssync/image/MilestoneCardRenderer.kt)
- [`image/AvatarFetcher.kt`](../../src/main/kotlin/io/github/darinc/amssync/image/AvatarFetcher.kt)
- [`image/CardStyles.kt`](../../src/main/kotlin/io/github/darinc/amssync/image/CardStyles.kt)
- [`image/GraphicsUtils.kt`](../../src/main/kotlin/io/github/darinc/amssync/image/GraphicsUtils.kt)

## Overview

AMSSync generates Pokemon-style visual cards for player stats and leaderboards using Java's Graphics2D API. These cards are sent as image attachments to Discord.

## Card Types

### Stats Card (`/amsstats`)

Visual representation of a player's MCMMO skills:

```
┌──────────────────────────────────────────┐
│  ┌────┐                                  │
│  │Body│  PlayerName                      │
│  │Skin│  Legendary                       │
│  │    │  ♦ Power: 5,432                  │
│  └────┘                                  │
│────────────────────────────────────────────│
│ COMBAT      │ GATHER       │ MISC        │
│ ▪ Swords 500│ ▪ Mining 750 │ ▪ Repair 300│
│ ▪ Axes   400│ ▪ Woodcut 600│ ▪ Alchemy 200│
│ ...         │ ...          │ ...         │
│────────────────────────────────────────────│
│            Server Name                    │
└──────────────────────────────────────────┘
```

### Leaderboard Card (`/amstop`)

Podium-style top 3 display:

```
┌──────────────────────────────────────────┐
│           ⭐ MINING TOP 3 ⭐              │
│                                          │
│           ┌────┐                         │
│     ┌────┐│ #1 │┌────┐                   │
│     │ #2 ││    ││ #3 │                   │
│     │    ││    ││    │                   │
│     └────┘└────┘└────┘                   │
│      750   1000   500                    │
│     Alex  Steve  Bob                     │
└──────────────────────────────────────────┘
```

### Milestone Card (Announcements)

Celebration card for level achievements:

```
┌──────────────────────────────────────────┐
│        🎉 SKILL MILESTONE! 🎉            │
│                                          │
│  ┌────┐   Steve reached                  │
│  │Head│   MINING LEVEL 500!              │
│  └────┘                                  │
│         [Mining Badge]                   │
└──────────────────────────────────────────┘
```

## Graphics2D Fundamentals

### Creating an Image

```kotlin
fun renderStatsCard(playerName: String, stats: Map<String, Int>, ...): BufferedImage {
    // Create image canvas
    val width = CardStyles.STATS_CARD_WIDTH   // e.g., 500
    val height = CardStyles.STATS_CARD_HEIGHT // e.g., 600
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

    // Get graphics context
    val g2d = image.createGraphics()

    // Enable anti-aliasing for smooth edges
    GraphicsUtils.enableAntialiasing(g2d)

    // ... draw operations ...

    // Clean up
    g2d.dispose()
    return image
}
```

### Anti-aliasing Setup

```kotlin
object GraphicsUtils {
    fun enableAntialiasing(g2d: Graphics2D) {
        g2d.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON
        )
        g2d.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB
        )
        g2d.setRenderingHint(
            RenderingHints.KEY_RENDERING,
            RenderingHints.VALUE_RENDER_QUALITY
        )
    }
}
```

### Drawing Primitives

```kotlin
// Gradient background
fun fillGradientBackground(g2d: Graphics2D, width: Int, height: Int, startColor: Color, endColor: Color) {
    val gradient = GradientPaint(0f, 0f, startColor, 0f, height.toFloat(), endColor)
    g2d.paint = gradient
    g2d.fillRect(0, 0, width, height)
}

// Rounded rectangle
fun drawRoundedRect(g2d: Graphics2D, x: Int, y: Int, width: Int, height: Int,
                    cornerRadius: Int, fill: Color?, stroke: Color?, strokeWidth: Int = 2) {
    val rect = RoundRectangle2D.Float(
        x.toFloat(), y.toFloat(),
        width.toFloat(), height.toFloat(),
        cornerRadius.toFloat(), cornerRadius.toFloat()
    )

    if (fill != null) {
        g2d.color = fill
        g2d.fill(rect)
    }

    if (stroke != null) {
        g2d.color = stroke
        g2d.stroke = BasicStroke(strokeWidth.toFloat())
        g2d.draw(rect)
    }
}

// Text drawing
fun drawString(g2d: Graphics2D, text: String, x: Int, y: Int, font: Font, color: Color) {
    g2d.font = font
    g2d.color = color
    g2d.drawString(text, x, y)
}

// Centered text
fun drawCenteredString(g2d: Graphics2D, text: String, centerX: Int, y: Int, font: Font, color: Color) {
    g2d.font = font
    val metrics = g2d.fontMetrics
    val x = centerX - metrics.stringWidth(text) / 2
    g2d.color = color
    g2d.drawString(text, x, y)
}
```

### Drawing Images

```kotlin
// Draw player body/head
g2d.drawImage(
    bodyImage,           // BufferedImage source
    x, y,                // Position
    targetWidth, targetHeight,  // Size (can scale)
    null                 // ImageObserver (null for BufferedImage)
)
```

## Style Constants

### CardStyles.kt

```kotlin
object CardStyles {
    // Dimensions
    const val STATS_CARD_WIDTH = 500
    const val STATS_CARD_HEIGHT = 600
    const val CARD_PADDING = 20
    const val CARD_CORNER_RADIUS = 15
    const val CARD_BORDER_WIDTH = 3

    // Colors
    val BACKGROUND_GRADIENT_START = Color(30, 30, 40)
    val BACKGROUND_GRADIENT_END = Color(20, 20, 30)
    val CARD_INNER_BG = Color(40, 40, 50, 200)
    val TEXT_WHITE = Color(255, 255, 255)
    val TEXT_GRAY = Color(150, 150, 150)
    val TEXT_GOLD = Color(255, 215, 0)

    // Fonts
    val FONT_PLAYER_NAME = Font("SansSerif", Font.BOLD, 24)
    val FONT_SKILL = Font("SansSerif", Font.PLAIN, 14)
    val FONT_POWER_LEVEL = Font("SansSerif", Font.BOLD, 18)
    val FONT_FOOTER = Font("SansSerif", Font.ITALIC, 12)

    // Power level thresholds for border colors
    fun getBorderColor(powerLevel: Int): Color = when {
        powerLevel >= 10000 -> Color(255, 0, 255)    // Mythic (purple)
        powerLevel >= 5000 -> Color(255, 165, 0)    // Legendary (orange)
        powerLevel >= 2500 -> Color(128, 0, 128)    // Epic (purple)
        powerLevel >= 1000 -> Color(0, 112, 221)    // Rare (blue)
        powerLevel >= 500 -> Color(0, 200, 0)       // Uncommon (green)
        else -> Color(150, 150, 150)                // Common (gray)
    }

    fun getRarityName(powerLevel: Int): String = when {
        powerLevel >= 10000 -> "MYTHIC"
        powerLevel >= 5000 -> "LEGENDARY"
        powerLevel >= 2500 -> "EPIC"
        powerLevel >= 1000 -> "RARE"
        powerLevel >= 500 -> "UNCOMMON"
        else -> "COMMON"
    }
}
```

## Skill Categories

Organizing skills into columns:

```kotlin
object SkillCategories {
    val COMBAT = setOf("SWORDS", "AXES", "ARCHERY", "UNARMED", "TAMING")
    val GATHERING = setOf("MINING", "WOODCUTTING", "HERBALISM", "EXCAVATION", "FISHING")
    val MISC = setOf("REPAIR", "ACROBATICS", "ALCHEMY")

    fun categorize(stats: Map<String, Int>): Triple<Map<String, Int>, Map<String, Int>, Map<String, Int>> {
        val combat = stats.filterKeys { it in COMBAT }
        val gathering = stats.filterKeys { it in GATHERING }
        val misc = stats.filterKeys { it in MISC }
        return Triple(combat, gathering, misc)
    }
}
```

## Avatar Fetching

### AvatarFetcher

```kotlin
class AvatarFetcher(
    private val logger: Logger,
    private val cacheMaxSize: Int = 100,
    private val cacheTtlMs: Long = 300_000L  // 5 minutes
) {
    private val avatarCache = ConcurrentHashMap<String, CachedAvatar>()

    companion object {
        private const val CONNECT_TIMEOUT_MS = 3000
        private const val READ_TIMEOUT_MS = 5000
        const val BODY_SIZE = 128
        const val HEAD_SIZE = 64
    }
}
```

### Fetching with Cache

```kotlin
fun fetchHeadAvatar(playerName: String, uuid: UUID?, provider: String, size: Int = HEAD_SIZE): BufferedImage {
    val cacheKey = "head:$playerName:$provider:$size"

    // Check cache
    avatarCache[cacheKey]?.let { cached ->
        if (!cached.isExpired(cacheTtlMs)) {
            logger.fine("Avatar cache hit: $playerName")
            return cached.image
        }
    }

    // Build URL
    val url = when (provider.lowercase()) {
        "crafatar" -> {
            val id = uuid?.toString()?.replace("-", "") ?: playerName
            "https://crafatar.com/avatars/$id?size=$size&overlay"
        }
        else -> "https://mc-heads.net/avatar/$playerName/$size"
    }

    return fetchAndCache(url, cacheKey) ?: createPlaceholderHead(size)
}
```

### HTTP Fetching

```kotlin
private fun fetchAndCache(urlString: String, cacheKey: String): BufferedImage? {
    return try {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "AMSSync-Minecraft-Plugin")

        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            logger.warning("Avatar fetch failed: ${connection.responseCode}")
            return null
        }

        val image = ImageIO.read(connection.inputStream)
        connection.disconnect()

        if (image != null) {
            cleanupCacheIfNeeded()
            avatarCache[cacheKey] = CachedAvatar(image, System.currentTimeMillis())
        }

        image
    } catch (e: Exception) {
        logger.warning("Failed to fetch avatar: ${e.message}")
        null
    }
}
```

### Placeholder Generation

```kotlin
private fun createPlaceholderHead(size: Int): BufferedImage {
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g2d = image.createGraphics()
    GraphicsUtils.enableAntialiasing(g2d)

    // Gray background
    g2d.color = Color(80, 80, 80)
    g2d.fillRect(0, 0, size, size)

    // Question mark
    g2d.color = Color(150, 150, 150)
    g2d.font = CardStyles.FONT_TITLE
    val metrics = g2d.fontMetrics
    val x = (size - metrics.stringWidth("?")) / 2
    val y = (size - metrics.height) / 2 + metrics.ascent
    g2d.drawString("?", x, y)

    g2d.dispose()
    return image
}
```

## Image to Discord

### Converting to Bytes

```kotlin
fun imageToBytes(image: BufferedImage, format: String = "PNG"): ByteArray {
    val outputStream = ByteArrayOutputStream()
    ImageIO.write(image, format, outputStream)
    return outputStream.toByteArray()
}
```

### Sending as Attachment

```kotlin
// In command handler
val cardImage = cardRenderer.renderStatsCard(playerName, stats, powerLevel, bodyImage)
val bytes = imageToBytes(cardImage)

event.hook.editOriginal(
    MessageEditData.fromFiles(
        FileUpload.fromData(bytes, "${playerName}_stats.png")
    )
).queue()
```

## Async Generation

Image generation is CPU-intensive. Use async:

```kotlin
fun handle(event: SlashCommandInteractionEvent) {
    event.deferReply().queue()  // Acknowledge immediately

    CompletableFuture.supplyAsync {
        // Fetch data
        val stats = mcmmoApiWrapper.getPlayerStats(playerName)
        val uuid = mcmmoApiWrapper.getOfflinePlayer(playerName)?.uniqueId

        // Fetch avatar (may involve HTTP)
        val bodyImage = avatarFetcher.fetchBodyRender(playerName, uuid, provider)

        // Render card (CPU intensive)
        cardRenderer.renderStatsCard(playerName, stats, powerLevel, bodyImage)

    }.thenAccept { cardImage ->
        // Send to Discord
        val bytes = imageToBytes(cardImage)
        event.hook.editOriginal(MessageEditData.fromFiles(
            FileUpload.fromData(bytes, "${playerName}_stats.png")
        )).queue()

    }.exceptionally { e ->
        event.hook.editOriginal("Error: ${e.message}").queue()
        null
    }
}
```

## Configuration

```yaml
image-cards:
  enabled: true
  avatar-provider: "mc-heads"    # or "crafatar"
  server-name: "My Server"
  avatar-cache-ttl-seconds: 300  # 5 minutes
  avatar-cache-max-size: 100
```

### Avatar Providers

| Provider | URL Pattern | Notes |
|----------|-------------|-------|
| mc-heads | `/avatar/{name}/{size}` | Uses player name |
| crafatar | `/avatars/{uuid}?size={size}` | Uses UUID (no dashes) |

## Performance Considerations

1. **Cache avatars**: Avoid repeated HTTP requests
2. **Async generation**: Don't block main thread or JDA threads
3. **Reuse Graphics2D hints**: Set once, not per-draw
4. **Dispose graphics**: Always call `g2d.dispose()`

## Related Documentation

- [Discord Commands](discord-commands.md) - Commands that use image cards
- [MCMMO API](../integrations/mcmmo-api.md) - Data source for stats
- [Threading](../architecture/threading.md) - Async patterns
