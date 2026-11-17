package io.github.darinc.amssync.image

import io.github.darinc.amssync.events.DeathCategory
import java.awt.Color
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.image.BufferedImage

/**
 * Renders visual death announcement cards as images.
 *
 * Generates eye-catching death cards featuring:
 * - Themed backgrounds based on death cause
 * - The player's skin laid face up in an open coffin
 * - MCMMO-integrated sarcastic commentary
 * - Skill/power level information
 *
 * @property serverName Server name shown in footer
 * @property assetManager Manager for loading death card assets
 */
class DeathCardRenderer(
    private val serverName: String,
    private val assetManager: DeathAssetManager
) {

    /**
     * Render a death card for a player.
     *
     * @param playerName Minecraft username
     * @param powerLevel MCMMO power level (nullable if not available)
     * @param deathCategory The classified death category
     * @param sarcasticMessage The main sarcastic death message
     * @param commentary Additional commentary (shown below coffin)
     * @param skin Player's skin texture, or null to show a shrouded body
     * @param slimSkin Whether the skin has slim arms; null detects it from the texture
     * @return BufferedImage of the rendered card
     */
    fun renderDeathCard(
        playerName: String,
        powerLevel: Int?,
        deathCategory: DeathCategory,
        sarcasticMessage: String,
        commentary: String,
        skin: BufferedImage?,
        slimSkin: Boolean? = null
    ): BufferedImage {
        val width = DeathCardStyles.CARD_WIDTH
        val height = DeathCardStyles.CARD_HEIGHT
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g2d = image.createGraphics()
        GraphicsUtils.enableAntialiasing(g2d)

        // Get theme for this death type
        val theme = DeathThemes.getTheme(deathCategory)

        // 1. Draw themed background
        drawBackground(g2d, width, height, theme)

        // 2. Draw header (player name, tier badge, power level, DECEASED badge)
        drawHeader(g2d, playerName, powerLevel)

        // 3. Draw death message box with icons
        drawDeathMessageBox(g2d, sarcasticMessage, deathCategory)

        // 4. Draw coffin with the player lying face up
        val body = skin?.let { SkinBodyRenderer.renderFront(it, slimSkin) } ?: SkinBodyRenderer.renderShroud()
        drawCoffinSection(g2d, body, width, height)

        // 5. Draw commentary section
        drawCommentarySection(g2d, commentary, width, height)

        // 6. Draw footer
        drawFooter(g2d, width, height)

        g2d.dispose()
        return image
    }

    // ========== DRAWING SECTIONS ==========

    private fun drawBackground(g2d: Graphics2D, width: Int, height: Int, theme: DeathThemes.Theme) {
        // Get themed background image
        val bgImage = assetManager.getBackground(theme)

        // Scale and draw background to fill card
        g2d.drawImage(bgImage, 0, 0, width, height, null)

        // Apply dark overlay for text readability
        g2d.color = DeathCardStyles.BG_OVERLAY
        g2d.fillRect(0, 0, width, height)

        // Apply theme tint
        val tint = Color(
            theme.tintColor.red,
            theme.tintColor.green,
            theme.tintColor.blue,
            40
        )
        g2d.color = tint
        g2d.fillRect(0, 0, width, height)

        // Add vignette effect (darker corners)
        val vignetteGradient = GradientPaint(
            (width / 2).toFloat(), (height / 2).toFloat(),
            Color(0, 0, 0, 0),
            0f, height.toFloat(),
            Color(0, 0, 0, 100)
        )
        g2d.paint = vignetteGradient
        g2d.fillRect(0, 0, width, height)
    }

    private fun drawHeader(g2d: Graphics2D, playerName: String, powerLevel: Int?) {
        val padding = DeathCardStyles.PADDING
        val headerY = padding

        // Semi-transparent header background
        GraphicsUtils.drawRoundedRect(
            g2d, padding, headerY - 5,
            DeathCardStyles.CARD_WIDTH - padding * 2,
            DeathCardStyles.HEADER_HEIGHT - 5,
            8,
            fill = DeathCardStyles.HEADER_BG
        )

        // Player name (left side)
        GraphicsUtils.drawString(
            g2d, playerName,
            padding + 10, headerY + 28,
            DeathCardStyles.FONT_PLAYER_NAME, DeathCardStyles.TEXT_PRIMARY
        )

        // Calculate element positions from right to left
        val rightEdge = DeathCardStyles.CARD_WIDTH - padding - 10
        var currentX = rightEdge

        // DECEASED badge (right side)
        currentX = drawDeceasedBadge(g2d, currentX, headerY + 8)

        // Power level and tier (if available)
        if (powerLevel != null) {
            currentX -= 15 // spacing

            // Power level text
            val powerText = "Power: ${DeathCardStyles.formatPowerLevel(powerLevel)}"
            g2d.font = DeathCardStyles.FONT_POWER_LEVEL
            val powerWidth = g2d.fontMetrics.stringWidth(powerText)
            currentX -= powerWidth
            GraphicsUtils.drawString(
                g2d, powerText,
                currentX, headerY + 38,
                DeathCardStyles.FONT_POWER_LEVEL, DeathCardStyles.TEXT_SECONDARY
            )

            // Tier badge
            val tierName = DeathCardStyles.getTierName(powerLevel)
            val tierColor = DeathCardStyles.getTierColor(powerLevel)
            currentX -= 10 // spacing
            drawTierBadge(g2d, tierName, tierColor, currentX, headerY + 8)
        }
    }

    private fun drawTierBadge(g2d: Graphics2D, tierName: String, tierColor: Color, rightX: Int, y: Int): Int {
        g2d.font = DeathCardStyles.FONT_TIER
        val textWidth = g2d.fontMetrics.stringWidth(tierName)
        val badgeWidth = textWidth + 16
        val badgeX = rightX - badgeWidth

        // Badge background
        GraphicsUtils.drawRoundedRect(
            g2d, badgeX, y,
            badgeWidth, DeathCardStyles.TIER_BADGE_HEIGHT,
            11,
            fill = tierColor
        )

        // Badge text
        GraphicsUtils.drawCenteredString(
            g2d, tierName,
            badgeX + badgeWidth / 2, y + 15,
            DeathCardStyles.FONT_TIER, Color.WHITE
        )

        return badgeX
    }

    private fun drawDeceasedBadge(g2d: Graphics2D, rightX: Int, y: Int): Int {
        val text = "DECEASED"
        g2d.font = DeathCardStyles.FONT_DECEASED
        val textWidth = g2d.fontMetrics.stringWidth(text)
        val badgeWidth = textWidth + 14
        val badgeX = rightX - badgeWidth

        // Red badge background
        GraphicsUtils.drawRoundedRect(
            g2d, badgeX, y,
            badgeWidth, DeathCardStyles.DECEASED_BADGE_HEIGHT,
            11,
            fill = DeathCardStyles.TEXT_DECEASED.darker()
        )

        // Badge text
        GraphicsUtils.drawCenteredString(
            g2d, text,
            badgeX + badgeWidth / 2, y + 15,
            DeathCardStyles.FONT_DECEASED, Color.WHITE
        )

        return badgeX
    }

    private fun drawDeathMessageBox(g2d: Graphics2D, message: String, category: DeathCategory) {
        val padding = DeathCardStyles.PADDING
        val boxY = DeathCardStyles.HEADER_HEIGHT + 5
        val boxWidth = DeathCardStyles.CARD_WIDTH - padding * 2
        val boxHeight = DeathCardStyles.DEATH_MESSAGE_BOX_HEIGHT

        // Box background with border
        GraphicsUtils.drawRoundedRect(
            g2d, padding, boxY,
            boxWidth, boxHeight,
            DeathCardStyles.MESSAGE_BOX_RADIUS,
            fill = DeathCardStyles.DEATH_MESSAGE_BOX_BG,
            stroke = DeathCardStyles.DEATH_MESSAGE_BOX_BORDER,
            strokeWidth = 2
        )

        // Skull icon (left side)
        val skullIcon = assetManager.getSkullIcon()
        val iconY = boxY + (boxHeight - DeathCardStyles.SKULL_ICON_SIZE) / 2
        g2d.drawImage(
            skullIcon,
            padding + 10, iconY,
            DeathCardStyles.SKULL_ICON_SIZE, DeathCardStyles.SKULL_ICON_SIZE,
            null
        )

        // Death cause icon (right side)
        val deathIcon = assetManager.getDeathIcon(category)
        g2d.drawImage(
            deathIcon,
            DeathCardStyles.CARD_WIDTH - padding - DeathCardStyles.MOB_ICON_SIZE - 10, iconY - 4,
            DeathCardStyles.MOB_ICON_SIZE, DeathCardStyles.MOB_ICON_SIZE,
            null
        )

        // Death message (centered)
        val messageX = padding + DeathCardStyles.SKULL_ICON_SIZE + 25
        val messageMaxWidth = boxWidth - DeathCardStyles.SKULL_ICON_SIZE - DeathCardStyles.MOB_ICON_SIZE - 50
        val messageY = boxY + boxHeight / 2 + 5

        // Shrink the font to fit; truncate only as a last resort
        var font = DeathCardStyles.FONT_DEATH_MESSAGE
        g2d.font = font
        while (g2d.fontMetrics.stringWidth(message) > messageMaxWidth && font.size > DeathCardStyles.DEATH_MESSAGE_MIN_SIZE) {
            font = font.deriveFont((font.size - 1).toFloat())
            g2d.font = font
        }
        var displayMessage = message
        while (g2d.fontMetrics.stringWidth(displayMessage) > messageMaxWidth && displayMessage.length > 10) {
            displayMessage = displayMessage.dropLast(4) + "..."
        }

        GraphicsUtils.drawString(
            g2d, displayMessage,
            messageX, messageY,
            font, DeathCardStyles.TEXT_PRIMARY
        )
    }

    private fun drawCoffinSection(g2d: Graphics2D, body: BufferedImage, width: Int, height: Int) {
        val sectionTop = DeathCardStyles.HEADER_HEIGHT + DeathCardStyles.DEATH_MESSAGE_BOX_HEIGHT + 10
        val sectionBottom = height - DeathCardStyles.FOOTER_HEIGHT - DeathCardStyles.COMMENTARY_HEIGHT
        CoffinPainter.paint(g2d, width / 2, (sectionTop + sectionBottom) / 2, body)
    }

    private fun drawCommentarySection(g2d: Graphics2D, commentary: String, width: Int, height: Int) {
        val sectionY = height - DeathCardStyles.FOOTER_HEIGHT - DeathCardStyles.COMMENTARY_HEIGHT
        val centerX = width / 2
        val padding = DeathCardStyles.PADDING

        // Semi-transparent background for commentary
        GraphicsUtils.drawRoundedRect(
            g2d, padding, sectionY,
            width - padding * 2, DeathCardStyles.COMMENTARY_HEIGHT - 5,
            8,
            fill = DeathCardStyles.COMMENTARY_BG
        )

        // Wrap and draw commentary text
        g2d.font = DeathCardStyles.FONT_COMMENTARY
        val maxWidth = width - padding * 4
        val wrappedCommentary = wrapText(g2d, "\"$commentary\"", maxWidth)

        // Draw each line of wrapped text
        val lineHeight = g2d.fontMetrics.height
        val startY = sectionY + 18
        for ((index, line) in wrappedCommentary.withIndex()) {
            GraphicsUtils.drawCenteredString(
                g2d, line,
                centerX, startY + index * lineHeight,
                DeathCardStyles.FONT_COMMENTARY, DeathCardStyles.TEXT_SARCASTIC
            )
        }

        // "Press 'F' to pay sarcastic respect." (below commentary)
        val pressFY = sectionY + DeathCardStyles.COMMENTARY_HEIGHT - 15
        GraphicsUtils.drawCenteredString(
            g2d, "Press 'F' to pay sarcastic respect.",
            centerX, pressFY,
            DeathCardStyles.FONT_PRESS_F, DeathCardStyles.TEXT_SECONDARY
        )
    }

    private fun drawFooter(g2d: Graphics2D, width: Int, height: Int) {
        val padding = DeathCardStyles.PADDING
        val footerY = height - 12

        // Server name (left)
        GraphicsUtils.drawString(
            g2d, serverName,
            padding, footerY,
            DeathCardStyles.FONT_FOOTER, DeathCardStyles.TEXT_SECONDARY
        )

        // AMS Sync branding (right)
        GraphicsUtils.drawRightAlignedString(
            g2d, CardStyles.BRANDING_TEXT,
            width - padding, footerY,
            DeathCardStyles.FONT_FOOTER, DeathCardStyles.TEXT_SECONDARY
        )
    }

    // ========== UTILITY METHODS ==========

    /**
     * Wrap text to fit within a maximum width.
     */
    private fun wrapText(g2d: Graphics2D, text: String, maxWidth: Int): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (g2d.fontMetrics.stringWidth(testLine) <= maxWidth) {
                currentLine = StringBuilder(testLine)
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                }
                currentLine = StringBuilder(word)
            }
        }

        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }

        // Limit to 2 lines max
        return lines.take(2)
    }
}
