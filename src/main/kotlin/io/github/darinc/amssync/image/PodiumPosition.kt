package io.github.darinc.amssync.image

import java.awt.Color

/**
 * Layout context for rendering the podium area.
 */
data class PodiumLayout(
    val centerX: Int,
    val startY: Int,
    val podiumY: Int,
    val podiumWidth: Int,
    val firstHeight: Int
)

/**
 * Represents a position on the leaderboard podium with all rendering parameters.
 */
data class PodiumPosition(
    val rank: Int,
    val color: Color,
    val height: Int,
    val xOffset: Int,          // offset from centerX for podium block
    val avatarYOffset: Int,    // Y offset from startY for avatar
    val nameYOffset: Int,      // Y offset from startY for name
    val scoreYOffset: Int,     // Y offset from startY for score
    val rankTextYOffset: Int,  // Y offset from podium top for rank number
    val showCrown: Boolean
) {
    companion object {
        private const val PODIUM_WIDTH = 70
        private const val FIRST_HEIGHT = 80
        private const val SECOND_HEIGHT = 60
        private const val THIRD_HEIGHT = 50

        val FIRST = PodiumPosition(
            rank = 1,
            color = CardStyles.PODIUM_GOLD,
            height = FIRST_HEIGHT,
            xOffset = -PODIUM_WIDTH / 2,
            avatarYOffset = 55,
            nameYOffset = 95,
            scoreYOffset = 110,
            rankTextYOffset = 40,
            showCrown = true
        )

        val SECOND = PodiumPosition(
            rank = 2,
            color = CardStyles.PODIUM_SILVER,
            height = SECOND_HEIGHT,
            xOffset = -PODIUM_WIDTH - 40,
            avatarYOffset = 70,
            nameYOffset = 105,
            scoreYOffset = 120,
            rankTextYOffset = 35,
            showCrown = false
        )

        val THIRD = PodiumPosition(
            rank = 3,
            color = CardStyles.PODIUM_BRONZE,
            height = THIRD_HEIGHT,
            xOffset = 40,
            avatarYOffset = 75,
            nameYOffset = 110,
            scoreYOffset = 125,
            rankTextYOffset = 30,
            showCrown = false
        )

        val ALL = listOf(FIRST, SECOND, THIRD)

        /**
         * Get the podium width.
         */
        fun getPodiumWidth(): Int = PODIUM_WIDTH

        /**
         * Get the first place height (used for Y offset calculations).
         */
        fun getFirstHeight(): Int = FIRST_HEIGHT
    }
}
