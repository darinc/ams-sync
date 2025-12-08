package io.github.darinc.amssync.mcmmo

import com.gmail.nossr50.datatypes.skills.PrimarySkillType
import com.gmail.nossr50.events.experience.McMMOPlayerLevelUpEvent
import com.gmail.nossr50.mcMMO
import io.github.darinc.amssync.AMSSyncPlugin
import io.github.darinc.amssync.discord.CircuitBreaker
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import java.awt.Color
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Listens for mcMMO level-up events and announces milestones to Discord.
 *
 * Announces skill milestones (e.g., level 100, 200, 300) and power level
 * milestones to a configured Discord text channel.
 *
 * @property plugin The parent plugin instance
 * @property config Announcement configuration
 */
class McMMOEventListener(
    private val plugin: AMSSyncPlugin,
    private val config: AnnouncementConfig
) : Listener {

    // Track last known power level for each player to detect milestones
    private val lastKnownPowerLevel = ConcurrentHashMap<String, Int>()

    /**
     * Handle mcMMO player level up event.
     * Checks if the new level is a milestone and announces it.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerLevelUp(event: McMMOPlayerLevelUpEvent) {
        val player = event.player
        val skill = event.skill
        val newLevel = event.skillLevel

        // Check for skill milestone
        if (config.skillMilestoneInterval > 0 && newLevel % config.skillMilestoneInterval == 0) {
            sendSkillMilestone(player.name, skill, newLevel)
        }

        // Check for power level milestone
        if (config.powerMilestoneInterval > 0) {
            checkPowerLevelMilestone(player.name, player.uniqueId.toString())
        }
    }

    /**
     * Check if player crossed a power level milestone and announce it.
     */
    private fun checkPowerLevelMilestone(playerName: String, uuid: String) {
        try {
            // Calculate current power level
            val profile = mcMMO.getDatabaseManager().loadPlayerProfile(
                java.util.UUID.fromString(uuid)
            )
            if (!profile.isLoaded) return

            val currentPowerLevel = PrimarySkillType.values()
                .filter { !it.isChildSkill }
                .sumOf { profile.getSkillLevel(it) }

            val lastPowerLevel = lastKnownPowerLevel.getOrDefault(playerName, 0)

            // Check if we crossed a milestone
            val lastMilestone = (lastPowerLevel / config.powerMilestoneInterval) * config.powerMilestoneInterval
            val currentMilestone = (currentPowerLevel / config.powerMilestoneInterval) * config.powerMilestoneInterval

            if (currentMilestone > lastMilestone && currentMilestone > 0) {
                sendPowerLevelMilestone(playerName, currentMilestone)
            }

            // Update tracked power level
            lastKnownPowerLevel[playerName] = currentPowerLevel
        } catch (e: Exception) {
            plugin.logger.warning("Error checking power level milestone: ${e.message}")
        }
    }

    /**
     * Send skill milestone announcement to Discord.
     */
    private fun sendSkillMilestone(playerName: String, skill: PrimarySkillType, level: Int) {
        val channel = getAnnouncementChannel() ?: return

        plugin.logger.info("Announcing skill milestone: $playerName reached level $level in ${skill.name}")

        try {
            val circuitBreaker = plugin.circuitBreaker

            val sendAction = {
                if (config.useEmbeds) {
                    val embed = EmbedBuilder()
                        .setColor(getSkillColor(skill))
                        .setTitle("Skill Milestone!")
                        .setDescription("**$playerName** reached level **$level** in **${formatSkillName(skill)}**!")
                        .setTimestamp(Instant.now())
                        .build()
                    channel.sendMessageEmbeds(embed).queue(
                        { plugin.logger.fine("Sent skill milestone embed") },
                        { e -> plugin.logger.warning("Failed to send skill milestone: ${e.message}") }
                    )
                } else {
                    channel.sendMessage("$playerName reached level $level in ${formatSkillName(skill)}!").queue(
                        { plugin.logger.fine("Sent skill milestone message") },
                        { e -> plugin.logger.warning("Failed to send skill milestone: ${e.message}") }
                    )
                }
            }

            if (circuitBreaker != null) {
                val result = circuitBreaker.execute("Send skill milestone", sendAction)
                if (result is CircuitBreaker.CircuitResult.Rejected) {
                    plugin.logger.fine("Skill milestone announcement rejected by circuit breaker")
                }
            } else {
                sendAction()
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error sending skill milestone announcement: ${e.message}")
        }
    }

    /**
     * Send power level milestone announcement to Discord.
     */
    private fun sendPowerLevelMilestone(playerName: String, powerLevel: Int) {
        val channel = getAnnouncementChannel() ?: return

        plugin.logger.info("Announcing power level milestone: $playerName reached power level $powerLevel")

        try {
            val circuitBreaker = plugin.circuitBreaker

            val sendAction = {
                if (config.useEmbeds) {
                    val embed = EmbedBuilder()
                        .setColor(Color(255, 215, 0)) // Gold color for power level
                        .setTitle("Power Level Milestone!")
                        .setDescription("**$playerName** reached power level **$powerLevel**!")
                        .setTimestamp(Instant.now())
                        .build()
                    channel.sendMessageEmbeds(embed).queue(
                        { plugin.logger.fine("Sent power level milestone embed") },
                        { e -> plugin.logger.warning("Failed to send power level milestone: ${e.message}") }
                    )
                } else {
                    channel.sendMessage("$playerName reached power level $powerLevel!").queue(
                        { plugin.logger.fine("Sent power level milestone message") },
                        { e -> plugin.logger.warning("Failed to send power level milestone: ${e.message}") }
                    )
                }
            }

            if (circuitBreaker != null) {
                val result = circuitBreaker.execute("Send power level milestone", sendAction)
                if (result is CircuitBreaker.CircuitResult.Rejected) {
                    plugin.logger.fine("Power level milestone announcement rejected by circuit breaker")
                }
            } else {
                sendAction()
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error sending power level milestone announcement: ${e.message}")
        }
    }

    /**
     * Get the configured announcement channel.
     */
    private fun getAnnouncementChannel(): TextChannel? {
        val jda = plugin.discordManager.getJda()
        if (jda == null || !plugin.discordManager.isConnected()) {
            plugin.logger.fine("Skipping announcement - Discord not connected")
            return null
        }

        val channel = jda.getTextChannelById(config.channelId)
        if (channel == null) {
            plugin.logger.warning("Announcement channel not found: ${config.channelId}")
            return null
        }

        return channel
    }

    /**
     * Format skill name for display (title case).
     */
    private fun formatSkillName(skill: PrimarySkillType): String {
        return skill.name.lowercase().replaceFirstChar { it.uppercase() }
    }

    /**
     * Get a color associated with a skill type.
     */
    private fun getSkillColor(skill: PrimarySkillType): Color {
        return when (skill) {
            // Combat skills - red tones
            PrimarySkillType.SWORDS -> Color(220, 20, 60)
            PrimarySkillType.AXES -> Color(178, 34, 34)
            PrimarySkillType.ARCHERY -> Color(255, 99, 71)
            PrimarySkillType.UNARMED -> Color(255, 69, 0)
            PrimarySkillType.TAMING -> Color(210, 105, 30)

            // Gathering skills - green/brown tones
            PrimarySkillType.MINING -> Color(128, 128, 128)
            PrimarySkillType.WOODCUTTING -> Color(139, 90, 43)
            PrimarySkillType.HERBALISM -> Color(34, 139, 34)
            PrimarySkillType.EXCAVATION -> Color(160, 82, 45)
            PrimarySkillType.FISHING -> Color(70, 130, 180)

            // Misc skills - various
            PrimarySkillType.ACROBATICS -> Color(255, 215, 0)
            PrimarySkillType.ALCHEMY -> Color(138, 43, 226)
            PrimarySkillType.REPAIR -> Color(192, 192, 192)
            PrimarySkillType.CROSSBOWS -> Color(139, 69, 19)
            PrimarySkillType.TRIDENTS -> Color(0, 191, 255)
            PrimarySkillType.MACES -> Color(105, 105, 105)

            else -> Color(100, 149, 237) // Default cornflower blue
        }
    }
}

/**
 * Configuration for mcMMO milestone announcements.
 *
 * @property enabled Enable/disable milestone announcements
 * @property channelId Text channel ID for announcements
 * @property skillMilestoneInterval Announce every N skill levels (0 = disabled)
 * @property powerMilestoneInterval Announce every N power levels (0 = disabled)
 * @property useEmbeds Use rich embeds (true) or plain text (false)
 */
data class AnnouncementConfig(
    val enabled: Boolean,
    val channelId: String,
    val skillMilestoneInterval: Int,
    val powerMilestoneInterval: Int,
    val useEmbeds: Boolean
) {
    companion object {
        /**
         * Load announcement configuration from Bukkit config file.
         *
         * @param config The Bukkit FileConfiguration to load from
         * @return AnnouncementConfig with loaded or default values
         */
        fun fromConfig(config: FileConfiguration): AnnouncementConfig {
            return AnnouncementConfig(
                enabled = config.getBoolean("discord.announcements.enabled", false),
                channelId = config.getString("discord.announcements.text-channel-id", "") ?: "",
                skillMilestoneInterval = config.getInt("discord.announcements.skill-milestone-interval", 100),
                powerMilestoneInterval = config.getInt("discord.announcements.power-milestone-interval", 500),
                useEmbeds = config.getBoolean("discord.announcements.use-embeds", true)
            )
        }
    }
}
