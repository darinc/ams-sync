package io.github.darinc.amssync.image

import java.awt.Color
import java.awt.Font

/**
 * Visual styling constants for death announcement cards.
 *
 * Provides colors, fonts, and dimensions for:
 * - Death card layout and positioning
 * - Tier-based visual theming (based on power level)
 * - Text styling for sarcastic commentary
 */
object DeathCardStyles {

    // ========== CARD DIMENSIONS ==========

    /** Death card width */
    const val CARD_WIDTH = 560

    /** Death card height */
    const val CARD_HEIGHT = 440

    // ========== LAYOUT HEIGHTS ==========

    /** Header section height (name, tier, power, DECEASED) */
    const val HEADER_HEIGHT = 55

    /** Death message box height */
    const val DEATH_MESSAGE_BOX_HEIGHT = 50

    /** Coffin/body section height */
    const val COFFIN_SECTION_HEIGHT = 230

    /** Commentary section height */
    const val COMMENTARY_HEIGHT = 60

    /** Footer section height */
    const val FOOTER_HEIGHT = 30

    // ========== SPACING & PADDING ==========

    /** Standard padding */
    const val PADDING = 16

    /** Border radius for rounded elements */
    const val BORDER_RADIUS = 12

    /** Death message box border radius */
    const val MESSAGE_BOX_RADIUS = 8

    // ========== ICON SIZES ==========

    /** Skull icon size */
    const val SKULL_ICON_SIZE = 32

    /** Mob/cause icon size */
    const val MOB_ICON_SIZE = 40

    // ========== BADGE DIMENSIONS ==========

    /** Tier badge width */
    const val TIER_BADGE_WIDTH = 75

    /** Tier badge height */
    const val TIER_BADGE_HEIGHT = 22

    /** DECEASED badge width */
    const val DECEASED_BADGE_WIDTH = 85

    /** DECEASED badge height */
    const val DECEASED_BADGE_HEIGHT = 22

    // ========== BACKGROUND COLORS ==========

    /** Dark overlay for background readability */
    val BG_OVERLAY = Color(0, 0, 0, 180)

    /** Secondary overlay for text areas */
    val BG_OVERLAY_LIGHT = Color(0, 0, 0, 120)

    // ========== ELEMENT BACKGROUND COLORS ==========

    /** Death message box background */
    val DEATH_MESSAGE_BOX_BG = Color(60, 40, 60, 220)

    /** Death message box border */
    val DEATH_MESSAGE_BOX_BORDER = Color(120, 80, 120)

    /** Header background */
    val HEADER_BG = Color(20, 15, 25, 200)

    /** Commentary section background */
    val COMMENTARY_BG = Color(30, 25, 35, 180)

    // ========== TEXT COLORS ==========

    /** Primary text color (white) */
    val TEXT_PRIMARY = Color(255, 255, 255)

    /** Secondary text color (light gray) */
    val TEXT_SECONDARY = Color(180, 180, 180)

    /** Sarcastic commentary text color (amber/gold) */
    val TEXT_SARCASTIC = Color(255, 200, 100)

    /** DECEASED badge text color (red) */
    val TEXT_DECEASED = Color(220, 60, 60)

    /** Skill/stat highlight color (cyan) */
    val TEXT_HIGHLIGHT = Color(100, 255, 255)

    /** Mob name highlight color (varies by mob) */
    val TEXT_MOB_NAME = Color(255, 100, 100)

    // ========== TIER COLORS (matches CardStyles rarity) ==========

    /** Common tier (< 1000 power) - Gray */
    val TIER_COMMON = Color(158, 158, 158)

    /** Rare tier (1000-4999 power) - Blue */
    val TIER_RARE = Color(33, 150, 243)

    /** Epic tier (5000-9999 power) - Purple */
    val TIER_EPIC = Color(156, 39, 176)

    /** Legendary tier (10000+ power) - Gold */
    val TIER_LEGENDARY = Color(255, 215, 0)

    // ========== FONTS ==========

    /** Player name font (large, bold) */
    val FONT_PLAYER_NAME: Font = Font("SansSerif", Font.BOLD, 22)

    /** Tier badge font */
    val FONT_TIER: Font = Font("SansSerif", Font.BOLD, 11)

    /** Power level font */
    val FONT_POWER_LEVEL: Font = Font("SansSerif", Font.BOLD, 13)

    /** DECEASED badge font */
    val FONT_DECEASED: Font = Font("SansSerif", Font.BOLD, 10)

    /** Death message font (largest size; shrinks to fit long headlines) */
    val FONT_DEATH_MESSAGE: Font = Font("SansSerif", Font.BOLD, 16)

    /** Smallest size a headline shrinks to before it is truncated */
    const val DEATH_MESSAGE_MIN_SIZE = 11

    /** Sarcastic commentary font */
    val FONT_COMMENTARY: Font = Font("SansSerif", Font.ITALIC, 14)

    /** "Press F" text font */
    val FONT_PRESS_F: Font = Font("SansSerif", Font.ITALIC, 11)

    /** Footer font */
    val FONT_FOOTER: Font = Font("SansSerif", Font.ITALIC, 10)

    // ========== UTILITY METHODS ==========

    /**
     * Get the tier name for a power level.
     * Matches CardStyles.getRarityName().
     */
    fun getTierName(powerLevel: Int): String {
        return when {
            powerLevel >= 10000 -> "LEGENDARY"
            powerLevel >= 5000 -> "EPIC"
            powerLevel >= 1000 -> "RARE"
            else -> "COMMON"
        }
    }

    /**
     * Get the tier color for a power level.
     * Matches CardStyles.getBorderColor().
     */
    fun getTierColor(powerLevel: Int): Color {
        return when {
            powerLevel >= 10000 -> TIER_LEGENDARY
            powerLevel >= 5000 -> TIER_EPIC
            powerLevel >= 1000 -> TIER_RARE
            else -> TIER_COMMON
        }
    }

    /**
     * Format power level with commas for display.
     */
    fun formatPowerLevel(powerLevel: Int): String {
        return "%,d".format(powerLevel)
    }
}
