package io.github.darinc.amssync.mcmmo

import com.gmail.nossr50.datatypes.skills.PrimarySkillType
import com.gmail.nossr50.mcMMO
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

/**
 * Represents a detected milestone event.
 */
sealed class MilestoneEvent {
    abstract val playerName: String
    abstract val uuid: UUID
    abstract val level: Int

    data class SkillMilestone(
        override val playerName: String,
        override val uuid: UUID,
        override val level: Int,
        val skill: PrimarySkillType
    ) : MilestoneEvent()

    data class PowerLevelMilestone(
        override val playerName: String,
        override val uuid: UUID,
        override val level: Int
    ) : MilestoneEvent()
}

/**
 * Detects MCMMO skill and power level milestones.
 * Tracks player power levels to detect when milestones are crossed.
 */
class MilestoneDetector(
    private val config: AnnouncementConfig,
    private val logger: Logger
) {
    // Track last known power level for each player to detect milestones
    private val lastKnownPowerLevel = ConcurrentHashMap<String, Int>()

    /**
     * Check if a skill level is a milestone.
     *
     * @param skill The skill that leveled up (reserved for future skill-specific intervals)
     * @param level The new skill level
     * @return true if this is a milestone level
     */
    @Suppress("UNUSED_PARAMETER")
    fun isSkillMilestone(skill: PrimarySkillType, level: Int): Boolean {
        return config.skillMilestoneInterval > 0 && level % config.skillMilestoneInterval == 0
    }

    /**
     * Check if the player crossed a power level milestone.
     *
     * @param playerName The player's name
     * @param uuid The player's UUID
     * @return The milestone level if a milestone was crossed, null otherwise
     */
    fun checkPowerLevelMilestone(playerName: String, uuid: UUID): Int? {
        if (config.powerMilestoneInterval <= 0) {
            return null
        }

        try {
            // Calculate current power level
            val profile = mcMMO.getDatabaseManager().loadPlayerProfile(uuid)
            if (!profile.isLoaded) return null

            @Suppress("DEPRECATION")
            val currentPowerLevel = PrimarySkillType.values()
                .filter { !it.isChildSkill }
                .sumOf { profile.getSkillLevel(it) }

            val lastPowerLevel = lastKnownPowerLevel.getOrDefault(playerName, 0)

            // Check if we crossed a milestone
            val lastMilestone = (lastPowerLevel / config.powerMilestoneInterval) * config.powerMilestoneInterval
            val currentMilestone = (currentPowerLevel / config.powerMilestoneInterval) * config.powerMilestoneInterval

            // Update tracked power level
            lastKnownPowerLevel[playerName] = currentPowerLevel

            return if (currentMilestone > lastMilestone && currentMilestone > 0) {
                currentMilestone
            } else {
                null
            }
        } catch (e: Exception) {
            logger.warning("Error checking power level milestone: ${e.message}")
            return null
        }
    }

    /**
     * Create a skill milestone event.
     */
    fun createSkillMilestone(
        playerName: String,
        uuid: UUID,
        skill: PrimarySkillType,
        level: Int
    ): MilestoneEvent.SkillMilestone {
        return MilestoneEvent.SkillMilestone(playerName, uuid, level, skill)
    }

    /**
     * Create a power level milestone event.
     */
    fun createPowerLevelMilestone(
        playerName: String,
        uuid: UUID,
        powerLevel: Int
    ): MilestoneEvent.PowerLevelMilestone {
        return MilestoneEvent.PowerLevelMilestone(playerName, uuid, powerLevel)
    }
}
