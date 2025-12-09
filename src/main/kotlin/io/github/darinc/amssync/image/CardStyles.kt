package io.github.darinc.amssync.image

import java.awt.Color
import java.awt.Font

/**
 * Visual styles and constants for player card image generation.
 */
object CardStyles {

    // === Card Dimensions ===
    const val STATS_CARD_WIDTH = 450
    const val STATS_CARD_HEIGHT = 600
    const val LEADERBOARD_CARD_WIDTH = 600
    const val LEADERBOARD_CARD_HEIGHT = 450

    // === Border and Padding ===
    const val CARD_BORDER_WIDTH = 3
    const val CARD_CORNER_RADIUS = 15
    const val CARD_PADDING = 15

    // === Avatar Sizes ===
    const val BODY_RENDER_HEIGHT = 128
    const val HEAD_AVATAR_SIZE = 64
    const val PODIUM_HEAD_SIZE = 48

    // === Skill Bar Dimensions ===
    const val SKILL_BAR_WIDTH = 100
    const val SKILL_BAR_HEIGHT = 8
    const val SKILL_BAR_RADIUS = 4

    // === Background Colors ===
    val BACKGROUND_GRADIENT_START = Color(26, 26, 46)      // #1a1a2e
    val BACKGROUND_GRADIENT_END = Color(22, 33, 62)        // #16213e
    val CARD_INNER_BG = Color(37, 37, 80, 230)             // Semi-transparent

    // === Border Colors (Rarity Tiers) ===
    val BORDER_BRONZE = Color(205, 127, 50)                // < 1000 power
    val BORDER_SILVER = Color(192, 192, 192)               // 1000-5000 power
    val BORDER_GOLD = Color(255, 215, 0)                   // 5000-10000 power
    val BORDER_DIAMOND = Color(0, 255, 255)                // 10000+ power

    // === Text Colors ===
    val TEXT_WHITE = Color(255, 255, 255)
    val TEXT_LIGHT_GRAY = Color(224, 224, 224)
    val TEXT_GRAY = Color(160, 160, 160)
    val TEXT_GOLD = Color(255, 215, 0)
    val TEXT_CYAN = Color(0, 255, 255)

    // === Skill Category Colors (Gradient Start, End) ===
    val COMBAT_BAR_START = Color(230, 57, 70)              // Red
    val COMBAT_BAR_END = Color(244, 162, 97)               // Orange
    val GATHERING_BAR_START = Color(42, 157, 143)          // Teal
    val GATHERING_BAR_END = Color(233, 196, 106)           // Yellow
    val MISC_BAR_START = Color(69, 123, 157)               // Blue
    val MISC_BAR_END = Color(168, 218, 220)                // Cyan

    // === Skill Bar Background ===
    val SKILL_BAR_BG = Color(50, 50, 80)

    // === Category Header Colors ===
    val HEADER_COMBAT = Color(255, 100, 100)
    val HEADER_GATHERING = Color(100, 255, 150)
    val HEADER_MISC = Color(100, 180, 255)

    // === Podium Colors ===
    val PODIUM_GOLD = Color(255, 215, 0)
    val PODIUM_SILVER = Color(192, 192, 192)
    val PODIUM_BRONZE = Color(205, 127, 50)
    val PODIUM_DARK = Color(40, 40, 70)

    // === Leaderboard Row Colors ===
    val ROW_EVEN = Color(42, 42, 74)
    val ROW_ODD = Color(31, 31, 58)

    // === Font Definitions ===
    val FONT_TITLE = Font("SansSerif", Font.BOLD, 24)
    val FONT_PLAYER_NAME = Font("SansSerif", Font.BOLD, 20)
    val FONT_POWER_LEVEL = Font("SansSerif", Font.BOLD, 16)
    val FONT_CATEGORY = Font("SansSerif", Font.BOLD, 14)
    val FONT_SKILL = Font("SansSerif", Font.PLAIN, 12)
    val FONT_SKILL_VALUE = Font("SansSerif", Font.BOLD, 12)
    val FONT_FOOTER = Font("SansSerif", Font.ITALIC, 10)
    val FONT_LEADERBOARD_RANK = Font("SansSerif", Font.BOLD, 14)
    val FONT_LEADERBOARD_NAME = Font("SansSerif", Font.PLAIN, 13)

    /**
     * Get border color based on power level (rarity tier).
     */
    fun getBorderColor(powerLevel: Int): Color {
        return when {
            powerLevel >= 10000 -> BORDER_DIAMOND
            powerLevel >= 5000 -> BORDER_GOLD
            powerLevel >= 1000 -> BORDER_SILVER
            else -> BORDER_BRONZE
        }
    }

    /**
     * Get rarity tier name for display.
     */
    fun getRarityName(powerLevel: Int): String {
        return when {
            powerLevel >= 10000 -> "LEGENDARY"
            powerLevel >= 5000 -> "EPIC"
            powerLevel >= 1000 -> "RARE"
            else -> "COMMON"
        }
    }

    /**
     * Get skill bar colors for a category.
     */
    fun getSkillBarColors(category: SkillCategory): Pair<Color, Color> {
        return when (category) {
            SkillCategory.COMBAT -> COMBAT_BAR_START to COMBAT_BAR_END
            SkillCategory.GATHERING -> GATHERING_BAR_START to GATHERING_BAR_END
            SkillCategory.MISC -> MISC_BAR_START to MISC_BAR_END
        }
    }

    /**
     * Get category header color.
     */
    fun getCategoryHeaderColor(category: SkillCategory): Color {
        return when (category) {
            SkillCategory.COMBAT -> HEADER_COMBAT
            SkillCategory.GATHERING -> HEADER_GATHERING
            SkillCategory.MISC -> HEADER_MISC
        }
    }
}

/**
 * Skill categories for grouping MCMMO skills.
 */
enum class SkillCategory {
    COMBAT,
    GATHERING,
    MISC
}
