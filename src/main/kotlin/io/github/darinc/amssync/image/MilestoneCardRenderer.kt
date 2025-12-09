package io.github.darinc.amssync.image

import com.gmail.nossr50.datatypes.skills.PrimarySkillType
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage

/**
 * Renders milestone announcement cards as images.
 *
 * Generates visually appealing celebration cards for:
 * - Skill milestones (reaching level 100, 200, etc.)
 * - Power level milestones (reaching 500, 1000, etc.)
 *
 * Cards feature:
 * - Skill-themed badge icons
 * - Player head avatars
 * - Progress bars toward mastery
 * - Tier-based color theming
 *
 * @property serverName Server name shown in footer
 */
class MilestoneCardRenderer(
    private val serverName: String
) {

    /**
     * Render a skill milestone announcement card.
     *
     * @param playerName Player's Minecraft username
     * @param skill The MCMMO skill type
     * @param level The new skill level
     * @param headImage Player's head avatar (64x64)
     * @return BufferedImage of the rendered card
     */
    fun renderSkillMilestoneCard(
        playerName: String,
        skill: PrimarySkillType,
        level: Int,
        headImage: BufferedImage
    ): BufferedImage {
        val width = MilestoneStyles.SKILL_CARD_WIDTH
        val height = MilestoneStyles.SKILL_CARD_HEIGHT
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g2d = image.createGraphics()

        enableAntialiasing(g2d)

        val tier = MilestoneStyles.SkillTier.fromLevel(level)

        // Draw background
        drawBackground(g2d, width, height, tier.glowColor)

        // Draw border
        drawBorder(g2d, width, height, tier.color)

        // Draw header section with badge and title
        drawSkillHeader(g2d, skill, tier)

        // Draw player info section
        drawPlayerSection(g2d, playerName, headImage, skill, level)

        // Draw progress bar
        drawProgressBar(g2d, level)

        // Draw footer
        drawFooter(g2d, width, height, level)

        g2d.dispose()
        return image
    }

    /**
     * Render a power level milestone announcement card.
     *
     * @param playerName Player's Minecraft username
     * @param powerLevel The new power level
     * @param headImage Player's head avatar (64x64)
     * @return BufferedImage of the rendered card
     */
    fun renderPowerMilestoneCard(
        playerName: String,
        powerLevel: Int,
        headImage: BufferedImage
    ): BufferedImage {
        val width = MilestoneStyles.POWER_CARD_WIDTH
        val height = MilestoneStyles.POWER_CARD_HEIGHT
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g2d = image.createGraphics()

        enableAntialiasing(g2d)

        val tier = MilestoneStyles.PowerTier.fromPowerLevel(powerLevel)

        // Draw background
        drawBackground(g2d, width, height, tier.glowColor)

        // Draw border
        drawBorder(g2d, width, height, tier.color)

        // Draw header
        drawPowerHeader(g2d, tier)

        // Draw player info section
        drawPowerPlayerSection(g2d, playerName, headImage, powerLevel, tier)

        // Draw footer with server name
        drawPowerFooter(g2d, width, height)

        g2d.dispose()
        return image
    }

    // ========== DRAWING HELPERS ==========

    private fun enableAntialiasing(g2d: Graphics2D) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    }

    private fun drawBackground(g2d: Graphics2D, width: Int, height: Int, glowColor: java.awt.Color) {
        // Gradient background
        val gradient = GradientPaint(
            0f, 0f, MilestoneStyles.BG_GRADIENT_START,
            0f, height.toFloat(), MilestoneStyles.BG_GRADIENT_END
        )
        g2d.paint = gradient
        g2d.fillRoundRect(0, 0, width, height, MilestoneStyles.BORDER_RADIUS, MilestoneStyles.BORDER_RADIUS)

        // Subtle glow effect at top
        val glowGradient = GradientPaint(
            0f, 0f, glowColor,
            0f, (height / 3).toFloat(), java.awt.Color(0, 0, 0, 0)
        )
        g2d.paint = glowGradient
        g2d.fillRoundRect(0, 0, width, height / 3, MilestoneStyles.BORDER_RADIUS, MilestoneStyles.BORDER_RADIUS)
    }

    private fun drawBorder(g2d: Graphics2D, width: Int, height: Int, borderColor: java.awt.Color) {
        g2d.color = borderColor
        g2d.stroke = java.awt.BasicStroke(3f)
        g2d.drawRoundRect(1, 1, width - 3, height - 3, MilestoneStyles.BORDER_RADIUS, MilestoneStyles.BORDER_RADIUS)
    }

    private fun drawSkillHeader(g2d: Graphics2D, skill: PrimarySkillType, tier: MilestoneStyles.SkillTier) {
        val padding = MilestoneStyles.PADDING

        // Draw skill badge
        SkillBadges.drawSkillBadge(g2d, skill, padding, padding, MilestoneStyles.BADGE_SIZE)

        // Draw title
        g2d.font = MilestoneStyles.FONT_TITLE
        g2d.color = tier.color
        val titleX = padding + MilestoneStyles.BADGE_SIZE + 12
        val titleY = padding + 20
        g2d.drawString("SKILL MILESTONE!", titleX, titleY)

        // Draw tier label
        g2d.font = MilestoneStyles.FONT_SMALL
        g2d.color = MilestoneStyles.TEXT_SECONDARY
        g2d.drawString(tier.label, titleX, titleY + 18)
    }

    private fun drawPlayerSection(
        g2d: Graphics2D,
        playerName: String,
        headImage: BufferedImage,
        skill: PrimarySkillType,
        level: Int
    ) {
        val padding = MilestoneStyles.PADDING
        val sectionY = MilestoneStyles.HEADER_HEIGHT + padding

        // Draw player head
        g2d.drawImage(headImage, padding, sectionY, MilestoneStyles.HEAD_SIZE, MilestoneStyles.HEAD_SIZE, null)

        // Draw player name
        val textX = padding + MilestoneStyles.HEAD_SIZE + 16
        g2d.font = MilestoneStyles.FONT_PLAYER_NAME
        g2d.color = MilestoneStyles.TEXT_PRIMARY
        g2d.drawString(playerName, textX, sectionY + 20)

        // Draw skill achievement text
        g2d.font = MilestoneStyles.FONT_BODY
        g2d.color = MilestoneStyles.TEXT_SECONDARY
        val skillName = formatSkillName(skill)
        g2d.drawString("reached level ${MilestoneStyles.formatLevel(level)}", textX, sectionY + 40)
        g2d.color = MilestoneStyles.TEXT_ACCENT
        g2d.drawString("in $skillName", textX, sectionY + 58)
    }

    private fun drawProgressBar(g2d: Graphics2D, level: Int) {
        val padding = MilestoneStyles.PADDING
        val barY = MilestoneStyles.SKILL_CARD_HEIGHT - 50
        val barWidth = MilestoneStyles.SKILL_CARD_WIDTH - (padding * 2) - 80
        val barHeight = 16
        val barX = padding + MilestoneStyles.HEAD_SIZE + 16

        // Background
        g2d.color = MilestoneStyles.PROGRESS_BG
        g2d.fillRoundRect(barX, barY, barWidth, barHeight, 8, 8)

        // Fill
        val progress = MilestoneStyles.getMasteryProgress(level)
        val fillWidth = (barWidth * progress).toInt()
        if (fillWidth > 0) {
            val fillGradient = GradientPaint(
                barX.toFloat(), 0f, MilestoneStyles.PROGRESS_FILL_START,
                (barX + barWidth).toFloat(), 0f, MilestoneStyles.PROGRESS_FILL_END
            )
            g2d.paint = fillGradient
            g2d.fillRoundRect(barX, barY, fillWidth, barHeight, 8, 8)
        }

        // Level text
        g2d.color = MilestoneStyles.TEXT_SECONDARY
        g2d.font = MilestoneStyles.FONT_SMALL
        val levelText = "${level}/${MilestoneStyles.MAX_SKILL_LEVEL}"
        g2d.drawString(levelText, barX + barWidth + 8, barY + 12)
    }

    private fun drawFooter(g2d: Graphics2D, width: Int, height: Int, level: Int) {
        val padding = MilestoneStyles.PADDING
        val footerY = height - padding

        // Mastery message (left)
        g2d.font = MilestoneStyles.FONT_SMALL
        g2d.color = MilestoneStyles.TEXT_ACCENT
        val masteryMsg = MilestoneStyles.getMasteryMessage(level)
        g2d.drawString(masteryMsg, padding, footerY)

        // Server name (right)
        g2d.font = MilestoneStyles.FONT_FOOTER
        g2d.color = MilestoneStyles.TEXT_SECONDARY
        val serverWidth = g2d.fontMetrics.stringWidth(serverName)
        g2d.drawString(serverName, width - padding - serverWidth, footerY)
    }

    private fun drawPowerHeader(g2d: Graphics2D, tier: MilestoneStyles.PowerTier) {
        val padding = MilestoneStyles.PADDING
        val width = MilestoneStyles.POWER_CARD_WIDTH

        // Draw power icon (lightning bolt)
        g2d.color = tier.color
        drawLightningBolt(g2d, padding, padding - 4, 32)

        // Draw title (centered)
        g2d.font = MilestoneStyles.FONT_TITLE
        val title = "POWER LEVEL MILESTONE!"
        val titleWidth = g2d.fontMetrics.stringWidth(title)
        val titleX = (width - titleWidth) / 2
        g2d.drawString(title, titleX, padding + 20)

        // Draw power icon on right side too
        drawLightningBolt(g2d, width - padding - 32, padding - 4, 32)
    }

    private fun drawPowerPlayerSection(
        g2d: Graphics2D,
        playerName: String,
        headImage: BufferedImage,
        powerLevel: Int,
        tier: MilestoneStyles.PowerTier
    ) {
        val padding = MilestoneStyles.PADDING
        val sectionY = MilestoneStyles.HEADER_HEIGHT + 8

        // Draw player head
        g2d.drawImage(headImage, padding, sectionY, MilestoneStyles.HEAD_SIZE, MilestoneStyles.HEAD_SIZE, null)

        // Draw player name
        val textX = padding + MilestoneStyles.HEAD_SIZE + 16
        g2d.font = MilestoneStyles.FONT_PLAYER_NAME
        g2d.color = MilestoneStyles.TEXT_PRIMARY
        g2d.drawString(playerName, textX, sectionY + 24)

        // Draw power level achievement
        g2d.font = MilestoneStyles.FONT_BODY
        g2d.color = MilestoneStyles.TEXT_SECONDARY
        g2d.drawString("reached power level", textX, sectionY + 46)

        g2d.font = MilestoneStyles.FONT_TIER
        g2d.color = MilestoneStyles.TEXT_ACCENT
        g2d.drawString(MilestoneStyles.formatLevel(powerLevel), textX, sectionY + 66)

        // Draw tier badge on the right
        val tierBadgeX = MilestoneStyles.POWER_CARD_WIDTH - padding - 100
        val tierBadgeY = sectionY + 20
        g2d.color = tier.color
        g2d.fillRoundRect(tierBadgeX, tierBadgeY, 90, 28, 14, 14)

        g2d.font = MilestoneStyles.FONT_SMALL
        g2d.color = MilestoneStyles.TEXT_PRIMARY
        val labelWidth = g2d.fontMetrics.stringWidth(tier.label)
        g2d.drawString(tier.label, tierBadgeX + (90 - labelWidth) / 2, tierBadgeY + 18)
    }

    private fun drawPowerFooter(g2d: Graphics2D, width: Int, height: Int) {
        val padding = MilestoneStyles.PADDING

        // Server name (centered)
        g2d.font = MilestoneStyles.FONT_FOOTER
        g2d.color = MilestoneStyles.TEXT_SECONDARY
        val serverWidth = g2d.fontMetrics.stringWidth(serverName)
        g2d.drawString(serverName, (width - serverWidth) / 2, height - padding)
    }

    private fun drawLightningBolt(g2d: Graphics2D, x: Int, y: Int, size: Int) {
        val path = java.awt.geom.GeneralPath()
        path.moveTo((x + size * 0.6).toDouble(), y.toDouble())
        path.lineTo((x + size * 0.25).toDouble(), (y + size * 0.45).toDouble())
        path.lineTo((x + size * 0.45).toDouble(), (y + size * 0.45).toDouble())
        path.lineTo((x + size * 0.35).toDouble(), (y + size).toDouble())
        path.lineTo((x + size * 0.75).toDouble(), (y + size * 0.5).toDouble())
        path.lineTo((x + size * 0.55).toDouble(), (y + size * 0.5).toDouble())
        path.closePath()
        g2d.fill(path)
    }

    private fun formatSkillName(skill: PrimarySkillType): String {
        return skill.name.lowercase().replaceFirstChar { it.uppercase() }
    }
}
