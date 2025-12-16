package io.github.darinc.amssync.image

import io.github.darinc.amssync.events.DeathCategory
import org.bukkit.plugin.java.JavaPlugin
import java.awt.Color
import java.awt.GradientPaint
import java.awt.image.BufferedImage
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger
import javax.imageio.ImageIO

/**
 * Manages loading and caching of bundled death card assets.
 *
 * Assets are loaded from the plugin JAR's resources and cached in memory.
 * Provides fallback procedurally-generated graphics when assets are missing.
 *
 * @property plugin The parent plugin for accessing resources
 * @property logger Logger for debugging and warnings
 */
class DeathAssetManager(
    private val plugin: JavaPlugin,
    private val logger: Logger
) {
    private val assetCache = ConcurrentHashMap<String, BufferedImage>()

    companion object {
        private const val ASSET_BASE_PATH = "assets/death"

        // Fallback dimensions
        private const val FALLBACK_BG_WIDTH = DeathCardStyles.CARD_WIDTH
        private const val FALLBACK_BG_HEIGHT = DeathCardStyles.CARD_HEIGHT
        private const val FALLBACK_ICON_SIZE = 40
        private const val FALLBACK_COFFIN_WIDTH = DeathCardStyles.COFFIN_WIDTH
        private const val FALLBACK_COFFIN_HEIGHT = DeathCardStyles.COFFIN_HEIGHT
    }

    /**
     * Load a background image for a theme.
     * Falls back to overworld if the specific theme isn't found,
     * or generates a procedural background if no assets exist.
     *
     * @param theme The visual theme for the death card
     * @return BufferedImage of the background
     */
    fun getBackground(theme: DeathThemes.Theme): BufferedImage {
        return loadAsset("backgrounds/${theme.backgroundAsset}")
            ?: loadAsset("backgrounds/overworld.png")
            ?: createFallbackBackground(theme)
    }

    /**
     * Load an icon for a death category.
     * Returns the appropriate mob or environmental icon.
     *
     * @param category The death category
     * @return BufferedImage of the icon, or fallback skull
     */
    fun getDeathIcon(category: DeathCategory): BufferedImage {
        val directory = DeathThemes.getIconDirectory(category)
        val filename = DeathThemes.getIconAsset(category)
        return loadAsset("$directory/$filename")
            ?: loadAsset("environmental/skull.png")
            ?: createFallbackSkull()
    }

    /**
     * Load a specific mob icon by name.
     *
     * @param mobName The mob icon filename (without .png)
     * @return BufferedImage of the icon, or null if not found
     */
    fun getMobIcon(mobName: String): BufferedImage? {
        return loadAsset("mobs/$mobName.png")
    }

    /**
     * Load a specific environmental icon by name.
     *
     * @param iconName The icon filename (without .png)
     * @return BufferedImage of the icon, or null if not found
     */
    fun getEnvironmentalIcon(iconName: String): BufferedImage? {
        return loadAsset("environmental/$iconName.png")
    }

    /**
     * Load the coffin/casket sprite.
     *
     * @return BufferedImage of the coffin, or fallback if not found
     */
    fun getCoffin(): BufferedImage {
        return loadAsset("coffin/coffin_base.png")
            ?: createFallbackCoffin()
    }

    /**
     * Load the skull icon.
     *
     * @return BufferedImage of the skull, or fallback if not found
     */
    fun getSkullIcon(): BufferedImage {
        return loadAsset("environmental/skull.png")
            ?: createFallbackSkull()
    }

    /**
     * Load an asset from the plugin resources.
     * Caches loaded assets for performance.
     *
     * @param relativePath Path relative to assets/death/
     * @return BufferedImage if found, null otherwise
     */
    private fun loadAsset(relativePath: String): BufferedImage? {
        // Check cache first
        assetCache[relativePath]?.let { return it }

        return try {
            val fullPath = "$ASSET_BASE_PATH/$relativePath"
            plugin.getResource(fullPath)?.use { stream ->
                val image = ImageIO.read(stream)
                if (image != null) {
                    assetCache[relativePath] = image
                    logger.fine("Loaded death asset: $relativePath")
                }
                image
            }
        } catch (e: Exception) {
            logger.fine("Asset not found or failed to load: $relativePath (${e.message})")
            null
        }
    }

    // ========== FALLBACK GRAPHICS GENERATION ==========

    /**
     * Create a fallback background with gradient based on theme.
     */
    private fun createFallbackBackground(theme: DeathThemes.Theme): BufferedImage {
        val image = BufferedImage(FALLBACK_BG_WIDTH, FALLBACK_BG_HEIGHT, BufferedImage.TYPE_INT_ARGB)
        val g2d = image.createGraphics()
        GraphicsUtils.enableAntialiasing(g2d)

        // Create gradient using theme tint
        val darker = Color(
            (theme.tintColor.red * 0.3).toInt().coerceIn(0, 255),
            (theme.tintColor.green * 0.3).toInt().coerceIn(0, 255),
            (theme.tintColor.blue * 0.3).toInt().coerceIn(0, 255)
        )

        val gradient = GradientPaint(
            0f, 0f, darker,
            FALLBACK_BG_WIDTH.toFloat(), FALLBACK_BG_HEIGHT.toFloat(), theme.tintColor
        )
        g2d.paint = gradient
        g2d.fillRect(0, 0, FALLBACK_BG_WIDTH, FALLBACK_BG_HEIGHT)

        // Add some texture with semi-transparent darker squares
        g2d.color = Color(0, 0, 0, 30)
        for (y in 0 until FALLBACK_BG_HEIGHT step 20) {
            for (x in 0 until FALLBACK_BG_WIDTH step 20) {
                if ((x + y) % 40 == 0) {
                    g2d.fillRect(x, y, 20, 20)
                }
            }
        }

        g2d.dispose()
        return image
    }

    /**
     * Create a fallback coffin shape.
     */
    private fun createFallbackCoffin(): BufferedImage {
        val image = BufferedImage(FALLBACK_COFFIN_WIDTH, FALLBACK_COFFIN_HEIGHT, BufferedImage.TYPE_INT_ARGB)
        val g2d = image.createGraphics()
        GraphicsUtils.enableAntialiasing(g2d)

        val centerX = FALLBACK_COFFIN_WIDTH / 2
        val topWidth = 60
        val midWidth = 80
        val bottomWidth = 50
        val height = FALLBACK_COFFIN_HEIGHT - 10

        // Coffin shape (hexagonal)
        val xPoints = intArrayOf(
            centerX - topWidth / 2,      // top-left
            centerX + topWidth / 2,      // top-right
            centerX + midWidth / 2,      // shoulder-right
            centerX + bottomWidth / 2,   // hip-right
            centerX - bottomWidth / 2,   // hip-left
            centerX - midWidth / 2       // shoulder-left
        )
        val yPoints = intArrayOf(
            5,           // top
            5,           // top
            25,          // shoulders
            height,      // bottom
            height,      // bottom
            25           // shoulders
        )

        // Dark wood color
        g2d.color = Color(60, 40, 30)
        g2d.fillPolygon(xPoints, yPoints, 6)

        // Lighter wood grain
        g2d.color = Color(80, 55, 40)
        g2d.stroke = java.awt.BasicStroke(2f)
        for (offset in listOf(-15, 0, 15)) {
            g2d.drawLine(centerX + offset, 15, centerX + offset, height - 10)
        }

        // Border
        g2d.color = Color(40, 25, 20)
        g2d.stroke = java.awt.BasicStroke(3f)
        g2d.drawPolygon(xPoints, yPoints, 6)

        g2d.dispose()
        return image
    }

    /**
     * Create a fallback skull icon.
     */
    private fun createFallbackSkull(): BufferedImage {
        val size = FALLBACK_ICON_SIZE
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g2d = image.createGraphics()
        GraphicsUtils.enableAntialiasing(g2d)

        val centerX = size / 2
        val centerY = size / 2

        // Skull shape (oval head)
        g2d.color = Color(220, 220, 210)
        g2d.fillOval(centerX - 14, centerY - 16, 28, 28)

        // Jaw
        g2d.fillRoundRect(centerX - 10, centerY + 6, 20, 10, 4, 4)

        // Eye sockets
        g2d.color = Color(30, 30, 30)
        g2d.fillOval(centerX - 10, centerY - 8, 8, 10)
        g2d.fillOval(centerX + 2, centerY - 8, 8, 10)

        // Nose hole
        g2d.fillOval(centerX - 2, centerY + 2, 4, 5)

        // Teeth line
        g2d.color = Color(60, 60, 60)
        g2d.drawLine(centerX - 8, centerY + 10, centerX + 8, centerY + 10)

        g2d.dispose()
        return image
    }

    /**
     * Create a generic fallback icon (simple colored circle with symbol).
     */
    fun createFallbackIcon(color: Color, symbol: Char): BufferedImage {
        val size = FALLBACK_ICON_SIZE
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g2d = image.createGraphics()
        GraphicsUtils.enableAntialiasing(g2d)

        // Colored circle background
        g2d.color = color
        g2d.fillOval(2, 2, size - 4, size - 4)

        // Border
        g2d.color = color.darker()
        g2d.stroke = java.awt.BasicStroke(2f)
        g2d.drawOval(2, 2, size - 4, size - 4)

        // Symbol
        g2d.color = Color.WHITE
        g2d.font = DeathCardStyles.FONT_DEATH_MESSAGE
        val metrics = g2d.fontMetrics
        val text = symbol.toString()
        val x = (size - metrics.stringWidth(text)) / 2
        val y = (size - metrics.height) / 2 + metrics.ascent
        g2d.drawString(text, x, y)

        g2d.dispose()
        return image
    }

    /**
     * Clear the asset cache.
     */
    fun clearCache() {
        assetCache.clear()
        logger.info("Death asset cache cleared")
    }

    /**
     * Get current cache size.
     */
    fun getCacheSize(): Int = assetCache.size
}
