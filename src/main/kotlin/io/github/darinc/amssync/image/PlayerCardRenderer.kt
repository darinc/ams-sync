package io.github.darinc.amssync.image

import java.awt.Color
import java.awt.image.BufferedImage
import java.text.NumberFormat
import java.util.Locale

/**
 * Renders visual player stat cards and leaderboard images.
 *
 * @property serverName Server name to display in footer
 */
class PlayerCardRenderer(
    private val serverName: String
) {
    private val numberFormat = NumberFormat.getNumberInstance(Locale.US)

    /**
     * Render a player stats card with full body skin and all skills.
     *
     * @param playerName Minecraft username
     * @param stats Map of skill name to level
     * @param powerLevel Total power level
     * @param bodyImage Player's full body skin render
     * @return BufferedImage of the rendered card
     */
    fun renderStatsCard(
        playerName: String,
        stats: Map<String, Int>,
        powerLevel: Int,
        bodyImage: BufferedImage
    ): BufferedImage {
        val width = CardStyles.STATS_CARD_WIDTH
        val height = CardStyles.STATS_CARD_HEIGHT
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g2d = image.createGraphics()
        GraphicsUtils.enableAntialiasing(g2d)

        // Draw background gradient
        GraphicsUtils.fillGradientBackground(
            g2d, width, height,
            CardStyles.BACKGROUND_GRADIENT_START,
            CardStyles.BACKGROUND_GRADIENT_END
        )

        val padding = CardStyles.CARD_PADDING
        val borderColor = CardStyles.getBorderColor(powerLevel)

        // Draw card border
        GraphicsUtils.drawRoundedRect(
            g2d,
            padding / 2, padding / 2,
            width - padding, height - padding,
            CardStyles.CARD_CORNER_RADIUS,
            fill = null,
            stroke = borderColor,
            strokeWidth = CardStyles.CARD_BORDER_WIDTH
        )

        // Draw inner card background
        GraphicsUtils.drawRoundedRect(
            g2d,
            padding, padding,
            width - padding * 2, height - padding * 2,
            CardStyles.CARD_CORNER_RADIUS - 2,
            fill = CardStyles.CARD_INNER_BG,
            stroke = null
        )

        // === Header Section ===
        val headerY = padding + 10

        // Draw body render (left side)
        val bodyX = padding + 15
        val bodyY = headerY
        g2d.drawImage(bodyImage, bodyX, bodyY, null)

        // Draw player name (right of body)
        val nameX = bodyX + bodyImage.width + 20
        val nameY = headerY + 30
        GraphicsUtils.drawString(
            g2d, playerName, nameX, nameY,
            CardStyles.FONT_PLAYER_NAME, CardStyles.TEXT_WHITE
        )

        // Draw rarity label
        val rarityY = nameY + 20
        val rarityText = CardStyles.getRarityName(powerLevel)
        GraphicsUtils.drawString(
            g2d, rarityText, nameX, rarityY,
            CardStyles.FONT_SKILL, borderColor
        )

        // Draw power level with diamond icon
        val powerY = rarityY + 30
        GraphicsUtils.drawString(
            g2d, "\u2666 Power: ${numberFormat.format(powerLevel)}", nameX, powerY,
            CardStyles.FONT_POWER_LEVEL, CardStyles.TEXT_GOLD
        )

        // === Skills Section ===
        val skillsStartY = headerY + bodyImage.height + 25
        val (combat, gathering, misc) = SkillCategories.categorize(stats)

        // Calculate column widths
        val columnWidth = (width - padding * 2 - 20) / 3
        val column1X = padding + 10
        val column2X = column1X + columnWidth
        val column3X = column2X + columnWidth

        // Draw category headers
        var currentY = skillsStartY
        drawCategoryHeader(g2d, "COMBAT", column1X, currentY, SkillCategory.COMBAT)
        drawCategoryHeader(g2d, "GATHER", column2X, currentY, SkillCategory.GATHERING)
        drawCategoryHeader(g2d, "MISC", column3X, currentY, SkillCategory.MISC)

        currentY += 25

        // Draw skills in each column
        val maxRows = maxOf(combat.size, gathering.size, misc.size)
        val rowHeight = 35

        for (row in 0 until maxRows) {
            val rowY = currentY + row * rowHeight

            // Combat column
            combat.entries.elementAtOrNull(row)?.let { (skill, level) ->
                drawSkillRow(g2d, skill, level, column1X, rowY, columnWidth - 10, SkillCategory.COMBAT)
            }

            // Gathering column
            gathering.entries.elementAtOrNull(row)?.let { (skill, level) ->
                drawSkillRow(g2d, skill, level, column2X, rowY, columnWidth - 10, SkillCategory.GATHERING)
            }

            // Misc column
            misc.entries.elementAtOrNull(row)?.let { (skill, level) ->
                drawSkillRow(g2d, skill, level, column3X, rowY, columnWidth - 10, SkillCategory.MISC)
            }
        }

        // === Footer Section ===
        val footerY = height - padding - 15
        GraphicsUtils.drawCenteredString(
            g2d, serverName, width / 2, footerY,
            CardStyles.FONT_FOOTER, CardStyles.TEXT_GRAY
        )

        g2d.dispose()
        return image
    }

    /**
     * Render a leaderboard podium card.
     *
     * @param title Leaderboard title (skill name or "Power Level")
     * @param leaderboard List of (playerName, score) pairs, sorted descending
     * @param avatarImages Map of playerName to head avatar image
     * @return BufferedImage of the rendered card
     */
    fun renderLeaderboardCard(
        title: String,
        leaderboard: List<Pair<String, Int>>,
        avatarImages: Map<String, BufferedImage>
    ): BufferedImage {
        val width = CardStyles.LEADERBOARD_CARD_WIDTH
        val height = CardStyles.LEADERBOARD_CARD_HEIGHT
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g2d = image.createGraphics()
        GraphicsUtils.enableAntialiasing(g2d)

        // Draw background gradient
        GraphicsUtils.fillGradientBackground(
            g2d, width, height,
            CardStyles.BACKGROUND_GRADIENT_START,
            CardStyles.BACKGROUND_GRADIENT_END
        )

        val padding = CardStyles.CARD_PADDING

        // Draw card border
        GraphicsUtils.drawRoundedRect(
            g2d,
            padding / 2, padding / 2,
            width - padding, height - padding,
            CardStyles.CARD_CORNER_RADIUS,
            fill = null,
            stroke = CardStyles.BORDER_GOLD,
            strokeWidth = CardStyles.CARD_BORDER_WIDTH
        )

        // === Title Section ===
        val titleY = padding + 30
        GraphicsUtils.drawCenteredString(
            g2d, "\u2694 $title \u2694", width / 2, titleY,
            CardStyles.FONT_TITLE, CardStyles.TEXT_WHITE
        )

        // === Podium Section ===
        if (leaderboard.isNotEmpty()) {
            drawPodium(g2d, leaderboard.take(3), avatarImages, width, padding + 50)
        }

        // === List Section (positions 4-10) ===
        val listStartY = padding + 250
        val rowHeight = 22

        leaderboard.drop(3).take(7).forEachIndexed { index, (name, score) ->
            val rank = index + 4
            val rowY = listStartY + index * rowHeight

            // Alternating row background
            val rowColor = if (index % 2 == 0) CardStyles.ROW_EVEN else CardStyles.ROW_ODD
            g2d.color = rowColor
            g2d.fillRect(padding + 10, rowY - 15, width - padding * 2 - 20, rowHeight)

            // Rank number
            GraphicsUtils.drawString(
                g2d, "$rank.", padding + 20, rowY,
                CardStyles.FONT_LEADERBOARD_RANK, CardStyles.TEXT_GRAY
            )

            // Player name
            GraphicsUtils.drawString(
                g2d, name, padding + 50, rowY,
                CardStyles.FONT_LEADERBOARD_NAME, CardStyles.TEXT_WHITE
            )

            // Dotted leader
            val nameWidth = GraphicsUtils.getStringWidth(g2d, name, CardStyles.FONT_LEADERBOARD_NAME)
            val scoreText = numberFormat.format(score)
            val scoreWidth = GraphicsUtils.getStringWidth(g2d, scoreText, CardStyles.FONT_LEADERBOARD_NAME)
            val dotsStartX = padding + 60 + nameWidth
            val dotsEndX = width - padding - 30 - scoreWidth

            GraphicsUtils.drawDottedLine(g2d, dotsStartX, dotsEndX, rowY - 4, CardStyles.TEXT_GRAY)

            // Score
            GraphicsUtils.drawRightAlignedString(
                g2d, scoreText, width - padding - 20, rowY,
                CardStyles.FONT_LEADERBOARD_NAME, CardStyles.TEXT_CYAN
            )
        }

        // === Footer ===
        val footerY = height - padding - 10
        GraphicsUtils.drawCenteredString(
            g2d, serverName, width / 2, footerY,
            CardStyles.FONT_FOOTER, CardStyles.TEXT_GRAY
        )

        g2d.dispose()
        return image
    }

    /**
     * Draw a category header.
     */
    private fun drawCategoryHeader(
        g2d: java.awt.Graphics2D,
        text: String,
        x: Int,
        y: Int,
        category: SkillCategory
    ) {
        val color = CardStyles.getCategoryHeaderColor(category)
        GraphicsUtils.drawString(g2d, text, x, y, CardStyles.FONT_CATEGORY, color)
    }

    /**
     * Draw a skill row with name, level, and progress bar.
     */
    private fun drawSkillRow(
        g2d: java.awt.Graphics2D,
        skill: String,
        level: Int,
        x: Int,
        y: Int,
        maxWidth: Int,
        category: SkillCategory
    ) {
        val displayName = SkillCategories.getDisplayName(skill)
        val (barStart, barEnd) = CardStyles.getSkillBarColors(category)

        // Skill name with mastery star if applicable
        val nameText = if (SkillCategories.isMastered(level)) {
            "\u2605 $displayName"  // Star prefix for mastery
        } else {
            displayName
        }
        val nameColor = if (SkillCategories.isMastered(level)) CardStyles.TEXT_GOLD else CardStyles.TEXT_LIGHT_GRAY
        GraphicsUtils.drawString(g2d, nameText, x, y, CardStyles.FONT_SKILL, nameColor)

        // Level value (right-aligned)
        GraphicsUtils.drawRightAlignedString(
            g2d, level.toString(), x + maxWidth, y,
            CardStyles.FONT_SKILL_VALUE, CardStyles.TEXT_CYAN
        )

        // Progress bar
        val barY = y + 5
        val barWidth = maxWidth - 10
        val fillPercent = (level.toFloat() / SkillCategories.MAX_SKILL_LEVEL).coerceAtMost(1f)

        GraphicsUtils.drawProgressBar(
            g2d, x, barY, barWidth, CardStyles.SKILL_BAR_HEIGHT,
            fillPercent, barStart, barEnd
        )
    }

    /**
     * Draw the podium with top 3 players.
     */
    private fun drawPodium(
        g2d: java.awt.Graphics2D,
        top3: List<Pair<String, Int>>,
        avatarImages: Map<String, BufferedImage>,
        cardWidth: Int,
        startY: Int
    ) {
        val centerX = cardWidth / 2

        // Podium dimensions
        val podiumWidth = 70
        val firstHeight = 80
        val secondHeight = 60
        val thirdHeight = 50
        val podiumY = startY + 120

        // Draw 1st place (center, highest)
        if (top3.isNotEmpty()) {
            val (name, score) = top3[0]
            val firstX = centerX - podiumWidth / 2

            // Draw crown above
            GraphicsUtils.drawCrown(g2d, centerX, startY, 30, 20, CardStyles.PODIUM_GOLD)

            // Draw head avatar
            val avatar = avatarImages[name]
            if (avatar != null) {
                GraphicsUtils.drawCenteredImage(g2d, avatar, centerX, startY + 55)
            }

            // Draw name and score
            GraphicsUtils.drawCenteredString(
                g2d, name, centerX, startY + 95,
                CardStyles.FONT_SKILL_VALUE, CardStyles.TEXT_WHITE
            )
            GraphicsUtils.drawCenteredString(
                g2d, numberFormat.format(score), centerX, startY + 110,
                CardStyles.FONT_SKILL, CardStyles.TEXT_GOLD
            )

            // Draw podium block
            g2d.color = CardStyles.PODIUM_GOLD
            g2d.fillRect(firstX, podiumY, podiumWidth, firstHeight)
            g2d.color = CardStyles.PODIUM_DARK
            g2d.drawRect(firstX, podiumY, podiumWidth, firstHeight)

            // "1" on podium
            GraphicsUtils.drawCenteredString(
                g2d, "1", centerX, podiumY + 40,
                CardStyles.FONT_TITLE, CardStyles.PODIUM_DARK
            )
        }

        // Draw 2nd place (left)
        if (top3.size >= 2) {
            val (name, score) = top3[1]
            val secondX = centerX - podiumWidth - 40
            val secondCenterX = secondX + podiumWidth / 2

            // Draw head avatar
            val avatar = avatarImages[name]
            if (avatar != null) {
                GraphicsUtils.drawCenteredImage(g2d, avatar, secondCenterX, startY + 70)
            }

            // Draw name and score
            GraphicsUtils.drawCenteredString(
                g2d, name, secondCenterX, startY + 105,
                CardStyles.FONT_SKILL_VALUE, CardStyles.TEXT_WHITE
            )
            GraphicsUtils.drawCenteredString(
                g2d, numberFormat.format(score), secondCenterX, startY + 120,
                CardStyles.FONT_SKILL, CardStyles.PODIUM_SILVER
            )

            // Draw podium block
            g2d.color = CardStyles.PODIUM_SILVER
            g2d.fillRect(secondX, podiumY + (firstHeight - secondHeight), podiumWidth, secondHeight)
            g2d.color = CardStyles.PODIUM_DARK
            g2d.drawRect(secondX, podiumY + (firstHeight - secondHeight), podiumWidth, secondHeight)

            // "2" on podium
            GraphicsUtils.drawCenteredString(
                g2d, "2", secondCenterX, podiumY + (firstHeight - secondHeight) + 35,
                CardStyles.FONT_TITLE, CardStyles.PODIUM_DARK
            )
        }

        // Draw 3rd place (right)
        if (top3.size >= 3) {
            val (name, score) = top3[2]
            val thirdX = centerX + 40
            val thirdCenterX = thirdX + podiumWidth / 2

            // Draw head avatar
            val avatar = avatarImages[name]
            if (avatar != null) {
                GraphicsUtils.drawCenteredImage(g2d, avatar, thirdCenterX, startY + 75)
            }

            // Draw name and score
            GraphicsUtils.drawCenteredString(
                g2d, name, thirdCenterX, startY + 110,
                CardStyles.FONT_SKILL_VALUE, CardStyles.TEXT_WHITE
            )
            GraphicsUtils.drawCenteredString(
                g2d, numberFormat.format(score), thirdCenterX, startY + 125,
                CardStyles.FONT_SKILL, CardStyles.PODIUM_BRONZE
            )

            // Draw podium block
            g2d.color = CardStyles.PODIUM_BRONZE
            g2d.fillRect(thirdX, podiumY + (firstHeight - thirdHeight), podiumWidth, thirdHeight)
            g2d.color = CardStyles.PODIUM_DARK
            g2d.drawRect(thirdX, podiumY + (firstHeight - thirdHeight), podiumWidth, thirdHeight)

            // "3" on podium
            GraphicsUtils.drawCenteredString(
                g2d, "3", thirdCenterX, podiumY + (firstHeight - thirdHeight) + 30,
                CardStyles.FONT_TITLE, CardStyles.PODIUM_DARK
            )
        }
    }
}
