package io.github.darinc.amssync.image

import com.gmail.nossr50.datatypes.skills.PrimarySkillType
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.geom.GeneralPath

/**
 * Procedurally draws skill-themed badge icons for milestone announcements.
 *
 * Each skill has a unique icon drawn using Graphics2D shapes:
 * - Combat skills: weapons (swords, axes, bow)
 * - Gathering skills: tools (pickaxe, axe, shovel)
 * - Misc skills: themed icons (anvil, potion, etc.)
 */
object SkillBadges {

    // Badge background colors per skill category
    private val COMBAT_BG = Color(139, 0, 0)      // Dark red
    private val GATHERING_BG = Color(0, 100, 0)   // Dark green
    private val MISC_BG = Color(75, 0, 130)       // Indigo

    // Icon foreground color
    private val ICON_COLOR = Color(255, 255, 255, 230)
    private val ICON_STROKE = BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)

    /**
     * Draw a skill badge at the specified position.
     *
     * @param g2d Graphics context
     * @param skill The MCMMO skill type
     * @param x X position (top-left)
     * @param y Y position (top-left)
     * @param size Badge size in pixels (square)
     */
    fun drawSkillBadge(g2d: Graphics2D, skill: PrimarySkillType, x: Int, y: Int, size: Int) {
        val oldStroke = g2d.stroke
        val oldColor = g2d.color

        // Draw circular background
        g2d.color = getBackgroundColor(skill)
        g2d.fillOval(x, y, size, size)

        // Draw border
        g2d.color = Color(255, 255, 255, 100)
        g2d.stroke = BasicStroke(2f)
        g2d.drawOval(x, y, size, size)

        // Draw skill icon
        g2d.color = ICON_COLOR
        g2d.stroke = ICON_STROKE

        val iconPadding = size / 5
        val iconX = x + iconPadding
        val iconY = y + iconPadding
        val iconSize = size - (iconPadding * 2)

        drawSkillIcon(g2d, skill, iconX, iconY, iconSize)

        g2d.stroke = oldStroke
        g2d.color = oldColor
    }

    /**
     * Get the background color for a skill's badge.
     */
    private fun getBackgroundColor(skill: PrimarySkillType): Color {
        return when (skill) {
            // Combat skills - red
            PrimarySkillType.SWORDS,
            PrimarySkillType.AXES,
            PrimarySkillType.ARCHERY,
            PrimarySkillType.UNARMED,
            PrimarySkillType.TAMING,
            PrimarySkillType.CROSSBOWS,
            PrimarySkillType.TRIDENTS,
            PrimarySkillType.MACES -> COMBAT_BG

            // Gathering skills - green
            PrimarySkillType.MINING,
            PrimarySkillType.WOODCUTTING,
            PrimarySkillType.HERBALISM,
            PrimarySkillType.EXCAVATION,
            PrimarySkillType.FISHING -> GATHERING_BG

            // Misc skills - indigo
            else -> MISC_BG
        }
    }

    /**
     * Draw the appropriate icon for a skill.
     */
    private fun drawSkillIcon(g2d: Graphics2D, skill: PrimarySkillType, x: Int, y: Int, size: Int) {
        when (skill) {
            PrimarySkillType.MINING -> drawPickaxe(g2d, x, y, size)
            PrimarySkillType.WOODCUTTING -> drawWoodAxe(g2d, x, y, size)
            PrimarySkillType.FISHING -> drawFishHook(g2d, x, y, size)
            PrimarySkillType.HERBALISM -> drawPlant(g2d, x, y, size)
            PrimarySkillType.EXCAVATION -> drawShovel(g2d, x, y, size)
            PrimarySkillType.SWORDS -> drawSword(g2d, x, y, size)
            PrimarySkillType.AXES -> drawBattleAxe(g2d, x, y, size)
            PrimarySkillType.ARCHERY -> drawBow(g2d, x, y, size)
            PrimarySkillType.UNARMED -> drawFist(g2d, x, y, size)
            PrimarySkillType.TAMING -> drawPaw(g2d, x, y, size)
            PrimarySkillType.REPAIR -> drawAnvil(g2d, x, y, size)
            PrimarySkillType.ACROBATICS -> drawRunner(g2d, x, y, size)
            PrimarySkillType.ALCHEMY -> drawPotion(g2d, x, y, size)
            PrimarySkillType.CROSSBOWS -> drawCrossbow(g2d, x, y, size)
            PrimarySkillType.TRIDENTS -> drawTrident(g2d, x, y, size)
            PrimarySkillType.MACES -> drawMace(g2d, x, y, size)
            else -> drawStar(g2d, x, y, size) // Default fallback
        }
    }

    // ========== GATHERING SKILLS ==========

    private fun drawPickaxe(g2d: Graphics2D, x: Int, y: Int, size: Int) {
        // Handle (diagonal)
        g2d.drawLine(x + size / 4, y + size * 3 / 4, x + size * 3 / 4, y + size / 4)

        // Head (horizontal bar at top)
        val headY = y + size / 4
        g2d.drawLine(x + size / 3, headY, x + size * 2 / 3, headY)

        // Pick points
        g2d.drawLine(x + size / 3, headY, x + size / 5, headY + size / 6)
        g2d.drawLine(x + size * 2 / 3, headY, x + size * 4 / 5, headY + size / 6)
    }

    private fun drawWoodAxe(g2d: Graphics2D, x: Int, y: Int, size: Int) {
        // Handle
        g2d.drawLine(x + size / 3, y + size * 2 / 3, x + size * 2 / 3, y + size / 3)

        // Axe head (curved shape)
        val path = GeneralPath()
        path.moveTo((x + size / 2).toDouble(), (y + size / 4).toDouble())
        path.curveTo(
            (x + size * 3 / 4).toDouble(), (y + size / 6).toDouble(),
            (x + size).toDouble(), (y + size / 3).toDouble(),
            (x + size * 3 / 4).toDouble(), (y + size / 2).toDouble()
        )
        path.lineTo((x + size / 2).toDouble(), (y + size / 3).toDouble())
        g2d.draw(path)
    }

    private fun drawFishHook(g2d: Graphics2D, x: Int, y: Int, size: Int) {
        // Line
        g2d.drawLine(x + size / 2, y, x + size / 2, y + size / 2)

        // Hook curve
        val path = GeneralPath()
        path.moveTo((x + size / 2).toDouble(), (y + size / 2).toDouble())
        path.curveTo(
            (x + size / 2).toDouble(), (y + size * 3 / 4).toDouble(),
            (x + size / 4).toDouble(), (y + size * 3 / 4).toDouble(),
            (x + size / 4).toDouble(), (y + size / 2).toDouble()
        )
        g2d.draw(path)

        // Hook point
        g2d.drawLine(x + size / 4, y + size / 2, x + size / 3, y + size * 2 / 5)
    }

    private fun drawPlant(g2d: Graphics2D, x: Int, y: Int, size: Int) {
        // Stem
        g2d.drawLine(x + size / 2, y + size, x + size / 2, y + size / 3)

        // Left leaf
        val leftLeaf = GeneralPath()
        leftLeaf.moveTo((x + size / 2).toDouble(), (y + size * 2 / 3).toDouble())
        leftLeaf.curveTo(
            (x + size / 4).toDouble(), (y + size / 2).toDouble(),
            (x + size / 6).toDouble(), (y + size / 3).toDouble(),
            (x + size / 3).toDouble(), (y + size / 4).toDouble()
        )
        g2d.draw(leftLeaf)

        // Right leaf
        val rightLeaf = GeneralPath()
        rightLeaf.moveTo((x + size / 2).toDouble(), (y + size / 2).toDouble())
        rightLeaf.curveTo(
            (x + size * 3 / 4).toDouble(), (y + size / 3).toDouble(),
            (x + size * 5 / 6).toDouble(), (y + size / 4).toDouble(),
            (x + size * 2 / 3).toDouble(), (y + size / 6).toDouble()
        )
        g2d.draw(rightLeaf)
    }

    private fun drawShovel(g2d: Graphics2D, x: Int, y: Int, size: Int) {
        // Handle
        g2d.drawLine(x + size / 2, y, x + size / 2, y + size / 2)

        // Blade (rounded rectangle)
        val bladeWidth = size / 2
        val bladeHeight = size / 2
        val bladeX = x + size / 4
        val bladeY = y + size / 2
        g2d.drawRoundRect(bladeX, bladeY, bladeWidth, bladeHeight, bladeWidth / 3, bladeWidth / 3)
    }

    // ========== COMBAT SKILLS ==========

    private fun drawSword(g2d: Graphics2D, x: Int, y: Int, size: Int) {
        // Blade (diagonal)
        g2d.drawLine(x + size / 4, y + size * 3 / 4, x + size * 3 / 4, y + size / 6)

        // Crossguard
        val guardY = y + size * 2 / 3
        g2d.drawLine(x + size / 6, guardY, x + size / 2, guardY)

        // Handle
        g2d.drawLine(x + size / 4, y + size * 3 / 4, x + size / 6, y + size * 5 / 6)
    }

    private fun drawBattleAxe(g2d: Graphics2D, x: Int, y: Int, size: Int) {
        // Handle (vertical)
        g2d.drawLine(x + size / 2, y + size / 6, x + size / 2, y + size * 5 / 6)

        // Double-sided axe head
        val path = GeneralPath()
        // Left blade
        path.moveTo((x + size / 2).toDouble(), (y + size / 4).toDouble())
        path.curveTo(
            (x + size / 6).toDouble(), (y + size / 4).toDouble(),
            (x + size / 6).toDouble(), (y + size / 2).toDouble(),
            (x + size / 2).toDouble(), (y + size / 2).toDouble()
        )
        // Right blade
        path.moveTo((x + size / 2).toDouble(), (y + size / 4).toDouble())
        path.curveTo(
            (x + size * 5 / 6).toDouble(), (y + size / 4).toDouble(),
            (x + size * 5 / 6).toDouble(), (y + size / 2).toDouble(),
            (x + size / 2).toDouble(), (y + size / 2).toDouble()
        )
        g2d.draw(path)
    }

    private fun drawBow(g2d: Graphics2D, x: Int, y: Int, size: Int) {
        // Bow arc
        val bowPath = GeneralPath()
        bowPath.moveTo((x + size / 6).toDouble(), (y + size / 6).toDouble())
        bowPath.curveTo(
            (x + size * 2 / 3).toDouble(), (y + size / 4).toDouble(),
            (x + size * 2 / 3).toDouble(), (y + size * 3 / 4).toDouble(),
            (x + size / 6).toDouble(), (y + size * 5 / 6).toDouble()
        )
        g2d.draw(bowPath)

        // String
        g2d.drawLine(x + size / 6, y + size / 6, x + size / 6, y + size * 5 / 6)

        // Arrow
        g2d.drawLine(x + size / 6, y + size / 2, x + size * 5 / 6, y + size / 2)
        g2d.drawLine(x + size * 5 / 6, y + size / 2, x + size * 2 / 3, y + size / 3)
        g2d.drawLine(x + size * 5 / 6, y + size / 2, x + size * 2 / 3, y + size * 2 / 3)
    }

    private fun drawFist(g2d: Graphics2D, x: Int, y: Int, size: Int) {
        // Main fist shape (rounded rectangle)
        g2d.drawRoundRect(x + size / 4, y + size / 4, size / 2, size / 2, size / 4, size / 4)

        // Knuckle lines
        val knuckleY = y + size / 3
        g2d.drawLine(x + size / 3, knuckleY, x + size / 3, y + size / 2)
        g2d.drawLine(x + size / 2, knuckleY, x + size / 2, y + size / 2)
        g2d.drawLine(x + size * 2 / 3, knuckleY, x + size * 2 / 3, y + size / 2)

        // Thumb
        g2d.drawLine(x + size / 4, y + size / 2, x + size / 6, y + size * 2 / 3)
    }

    private fun drawPaw(g2d: Graphics2D, x: Int, y: Int, size: Int) {
        // Main pad
        g2d.fillOval(x + size / 4, y + size / 2, size / 2, size / 3)

        // Toe pads
        val toeSize = size / 5
        g2d.fillOval(x + size / 6, y + size / 4, toeSize, toeSize)
        g2d.fillOval(x + size / 3, y + size / 6, toeSize, toeSize)
        g2d.fillOval(x + size / 2, y + size / 6, toeSize, toeSize)
        g2d.fillOval(x + size * 2 / 3, y + size / 4, toeSize, toeSize)
    }

    private fun drawCrossbow(g2d: Graphics2D, x: Int, y: Int, size: Int) {
        // Stock
        g2d.drawLine(x + size / 6, y + size / 2, x + size * 5 / 6, y + size / 2)

        // Bow (horizontal)
        g2d.drawLine(x + size / 3, y + size / 4, x + size / 3, y + size * 3 / 4)

        // String
        g2d.drawLine(x + size / 3, y + size / 4, x + size / 2, y + size / 2)
        g2d.drawLine(x + size / 3, y + size * 3 / 4, x + size / 2, y + size / 2)
    }

    private fun drawTrident(g2d: Graphics2D, x: Int, y: Int, size: Int) {
        // Handle
        g2d.drawLine(x + size / 2, y + size * 5 / 6, x + size / 2, y + size / 3)

        // Three prongs
        g2d.drawLine(x + size / 4, y + size / 6, x + size / 4, y + size / 3)
        g2d.drawLine(x + size / 2, y, x + size / 2, y + size / 3)
        g2d.drawLine(x + size * 3 / 4, y + size / 6, x + size * 3 / 4, y + size / 3)

        // Cross bar
        g2d.drawLine(x + size / 4, y + size / 3, x + size * 3 / 4, y + size / 3)
    }

    private fun drawMace(g2d: Graphics2D, x: Int, y: Int, size: Int) {
        // Handle
        g2d.drawLine(x + size / 2, y + size * 5 / 6, x + size / 2, y + size / 2)

        // Mace head (circle with spikes)
        val headSize = size / 3
        val headX = x + size / 3
        val headY = y + size / 6
        g2d.drawOval(headX, headY, headSize, headSize)

        // Spikes
        val centerX = x + size / 2
        val centerY = y + size / 3
        g2d.drawLine(centerX, headY, centerX, headY - size / 8)
        g2d.drawLine(headX, centerY, headX - size / 8, centerY)
        g2d.drawLine(headX + headSize, centerY, headX + headSize + size / 8, centerY)
    }

    // ========== MISC SKILLS ==========

    private fun drawAnvil(g2d: Graphics2D, x: Int, y: Int, size: Int) {
        // Top surface
        g2d.drawLine(x + size / 6, y + size / 3, x + size * 5 / 6, y + size / 3)

        // Horn (left extension)
        g2d.drawLine(x + size / 6, y + size / 3, x, y + size / 2)

        // Body
        g2d.drawLine(x + size / 4, y + size / 3, x + size / 4, y + size * 2 / 3)
        g2d.drawLine(x + size * 3 / 4, y + size / 3, x + size * 3 / 4, y + size * 2 / 3)

        // Base
        g2d.drawLine(x + size / 6, y + size * 2 / 3, x + size * 5 / 6, y + size * 2 / 3)
        g2d.drawLine(x + size / 6, y + size * 2 / 3, x + size / 6, y + size * 5 / 6)
        g2d.drawLine(x + size * 5 / 6, y + size * 2 / 3, x + size * 5 / 6, y + size * 5 / 6)
        g2d.drawLine(x + size / 6, y + size * 5 / 6, x + size * 5 / 6, y + size * 5 / 6)
    }

    private fun drawRunner(g2d: Graphics2D, x: Int, y: Int, size: Int) {
        // Head
        g2d.drawOval(x + size / 2 - size / 8, y + size / 8, size / 4, size / 4)

        // Body (leaning forward)
        g2d.drawLine(x + size / 2, y + size / 3, x + size / 3, y + size * 2 / 3)

        // Front leg (extended)
        g2d.drawLine(x + size / 3, y + size * 2 / 3, x + size * 2 / 3, y + size * 5 / 6)

        // Back leg (bent)
        g2d.drawLine(x + size / 3, y + size * 2 / 3, x + size / 6, y + size / 2)
        g2d.drawLine(x + size / 6, y + size / 2, x + size / 4, y + size * 5 / 6)

        // Arms (running motion)
        g2d.drawLine(x + size / 2, y + size / 2, x + size * 2 / 3, y + size / 3)
        g2d.drawLine(x + size / 2, y + size / 2, x + size / 4, y + size * 2 / 3)
    }

    private fun drawPotion(g2d: Graphics2D, x: Int, y: Int, size: Int) {
        // Bottle neck
        g2d.drawRect(x + size * 2 / 5, y + size / 8, size / 5, size / 4)

        // Bottle body (rounded)
        val bodyPath = GeneralPath()
        bodyPath.moveTo((x + size * 2 / 5).toDouble(), (y + size * 3 / 8).toDouble())
        bodyPath.lineTo((x + size / 5).toDouble(), (y + size / 2).toDouble())
        bodyPath.curveTo(
            (x + size / 8).toDouble(), (y + size * 2 / 3).toDouble(),
            (x + size / 8).toDouble(), (y + size * 5 / 6).toDouble(),
            (x + size / 3).toDouble(), (y + size * 5 / 6).toDouble()
        )
        bodyPath.lineTo((x + size * 2 / 3).toDouble(), (y + size * 5 / 6).toDouble())
        bodyPath.curveTo(
            (x + size * 7 / 8).toDouble(), (y + size * 5 / 6).toDouble(),
            (x + size * 7 / 8).toDouble(), (y + size * 2 / 3).toDouble(),
            (x + size * 4 / 5).toDouble(), (y + size / 2).toDouble()
        )
        bodyPath.lineTo((x + size * 3 / 5).toDouble(), (y + size * 3 / 8).toDouble())
        g2d.draw(bodyPath)

        // Bubbles inside
        g2d.drawOval(x + size / 3, y + size * 2 / 3, size / 8, size / 8)
        g2d.drawOval(x + size / 2, y + size * 3 / 4, size / 10, size / 10)
    }

    private fun drawStar(g2d: Graphics2D, x: Int, y: Int, size: Int) {
        // Default star shape for unknown skills
        val centerX = x + size / 2
        val centerY = y + size / 2
        val outerRadius = size / 2
        val innerRadius = size / 4

        val path = GeneralPath()
        for (i in 0 until 5) {
            val outerAngle = Math.toRadians((i * 72 - 90).toDouble())
            val innerAngle = Math.toRadians((i * 72 + 36 - 90).toDouble())

            val outerX = centerX + (outerRadius * Math.cos(outerAngle)).toInt()
            val outerY = centerY + (outerRadius * Math.sin(outerAngle)).toInt()
            val innerX = centerX + (innerRadius * Math.cos(innerAngle)).toInt()
            val innerY = centerY + (innerRadius * Math.sin(innerAngle)).toInt()

            if (i == 0) {
                path.moveTo(outerX.toDouble(), outerY.toDouble())
            } else {
                path.lineTo(outerX.toDouble(), outerY.toDouble())
            }
            path.lineTo(innerX.toDouble(), innerY.toDouble())
        }
        path.closePath()
        g2d.draw(path)
    }
}
