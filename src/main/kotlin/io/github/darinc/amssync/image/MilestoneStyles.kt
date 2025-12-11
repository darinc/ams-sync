package io.github.darinc.amssync.image

import java.awt.Color
import java.awt.Font

/**
 * Visual styling constants for milestone announcement cards.
 *
 * Provides colors, fonts, and dimensions for:
 * - Skill milestone cards
 * - Power level milestone cards
 * - Tier-based visual theming
 */
object MilestoneStyles {

    // ========== CARD DIMENSIONS ==========

    /** Skill milestone card width */
    const val SKILL_CARD_WIDTH = 400

    /** Skill milestone card height */
    const val SKILL_CARD_HEIGHT = 220

    /** Power milestone card width */
    const val POWER_CARD_WIDTH = 400

    /** Power milestone card height */
    const val POWER_CARD_HEIGHT = 180

    /** Skill badge size in pixels */
    const val BADGE_SIZE = 48

    /** Player head avatar size */
    const val HEAD_SIZE = 64

    /** Maximum skill level for mastery */
    const val MAX_SKILL_LEVEL = 1000

    // ========== BACKGROUND COLORS ==========

    /** Dark gradient start color */
    val BG_GRADIENT_START = Color(26, 26, 46)

    /** Dark gradient end color */
    val BG_GRADIENT_END = Color(22, 33, 62)

    // ========== SKILL MILESTONE TIER COLORS ==========

    /** Tier colors based on skill level milestone */
    enum class SkillTier(val color: Color, val glowColor: Color, val label: String) {
        BEGINNER(Color(76, 175, 80), Color(76, 175, 80, 80), "Beginner"),        // Green @ 100
        SKILLED(Color(33, 150, 243), Color(33, 150, 243, 80), "Skilled"),         // Blue @ 250
        EXPERT(Color(156, 39, 176), Color(156, 39, 176, 80), "Expert"),           // Purple @ 500
        MASTER(Color(255, 152, 0), Color(255, 152, 0, 80), "Master"),             // Orange @ 750
        LEGENDARY(Color(255, 215, 0), Color(255, 215, 0, 80), "Legendary");       // Gold @ 1000

        companion object {
            /**
             * Get the tier for a skill level.
             */
            fun fromLevel(level: Int): SkillTier {
                return when {
                    level >= 1000 -> LEGENDARY
                    level >= 750 -> MASTER
                    level >= 500 -> EXPERT
                    level >= 250 -> SKILLED
                    else -> BEGINNER
                }
            }
        }
    }

    // ========== POWER LEVEL TIER COLORS ==========

    /** Tier colors based on total power level */
    enum class PowerTier(val color: Color, val glowColor: Color, val label: String) {
        NOVICE(Color(158, 158, 158), Color(158, 158, 158, 80), "Novice"),          // Gray < 1000
        WARRIOR(Color(76, 175, 80), Color(76, 175, 80, 80), "Warrior"),            // Green 1000-4999
        CHAMPION(Color(33, 150, 243), Color(33, 150, 243, 80), "Champion"),        // Blue 5000-9999
        LEGEND(Color(156, 39, 176), Color(156, 39, 176, 80), "Legend"),            // Purple 10000-19999
        MYTHIC(Color(255, 215, 0), Color(255, 215, 0, 80), "Mythic");              // Gold 20000+

        companion object {
            /**
             * Get the tier for a power level.
             */
            fun fromPowerLevel(power: Int): PowerTier {
                return when {
                    power >= 20000 -> MYTHIC
                    power >= 10000 -> LEGEND
                    power >= 5000 -> CHAMPION
                    power >= 1000 -> WARRIOR
                    else -> NOVICE
                }
            }
        }
    }

    // ========== TEXT COLORS ==========

    /** Primary text color (white) */
    val TEXT_PRIMARY = Color(255, 255, 255)

    /** Secondary text color (light gray) */
    val TEXT_SECONDARY = Color(200, 200, 200)

    /** Accent text color (gold) */
    val TEXT_ACCENT = Color(255, 215, 0)

    // ========== PROGRESS BAR COLORS ==========

    /** Progress bar background */
    val PROGRESS_BG = Color(60, 60, 60)

    /** Progress bar fill start (gradient) */
    val PROGRESS_FILL_START = Color(76, 175, 80)

    /** Progress bar fill end (gradient) */
    val PROGRESS_FILL_END = Color(139, 195, 74)

    // ========== FONTS ==========

    /** Title font (large, bold) */
    val FONT_TITLE: Font = Font("SansSerif", Font.BOLD, 20)

    /** Player name font */
    val FONT_PLAYER_NAME: Font = Font("SansSerif", Font.BOLD, 18)

    /** Body text font */
    val FONT_BODY: Font = Font("SansSerif", Font.PLAIN, 14)

    /** Small text font */
    val FONT_SMALL: Font = Font("SansSerif", Font.PLAIN, 12)

    /** Footer text font */
    val FONT_FOOTER: Font = Font("SansSerif", Font.ITALIC, 10)

    /** Tier label font */
    val FONT_TIER: Font = Font("SansSerif", Font.BOLD, 16)

    // ========== BORDER & DECORATION ==========

    /** Card border radius */
    const val BORDER_RADIUS = 16

    /** Card padding */
    const val PADDING = 16

    /** Header section height */
    const val HEADER_HEIGHT = 50

    // ========== UTILITY METHODS ==========

    /**
     * Get progress percentage toward mastery.
     */
    fun getMasteryProgress(level: Int): Float {
        return (level.coerceAtMost(MAX_SKILL_LEVEL).toFloat() / MAX_SKILL_LEVEL)
    }

    /**
     * Format level with commas for display.
     */
    fun formatLevel(level: Int): String {
        return "%,d".format(level)
    }

    /**
     * Get mastery message based on progress.
     */
    fun getMasteryMessage(level: Int): String {
        val progress = (level * 100 / MAX_SKILL_LEVEL).coerceAtMost(100)
        return when {
            level >= MAX_SKILL_LEVEL -> "MASTERED!"
            progress >= 75 -> "$progress% to Mastery!"
            progress >= 50 -> "$progress% to Mastery!"
            progress >= 25 -> "$progress% to Mastery!"
            else -> "$progress% to Mastery"
        }
    }
}
