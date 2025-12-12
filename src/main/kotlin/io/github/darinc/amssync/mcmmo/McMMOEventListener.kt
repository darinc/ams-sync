package io.github.darinc.amssync.mcmmo

import club.minnced.discord.webhook.WebhookClient
import club.minnced.discord.webhook.WebhookClientBuilder
import club.minnced.discord.webhook.send.WebhookMessageBuilder
import com.gmail.nossr50.datatypes.skills.PrimarySkillType
import com.gmail.nossr50.events.experience.McMMOPlayerLevelUpEvent
import com.gmail.nossr50.mcMMO
import io.github.darinc.amssync.AMSSyncPlugin
import io.github.darinc.amssync.discord.CircuitBreaker
import io.github.darinc.amssync.image.AvatarFetcher
import io.github.darinc.amssync.image.MilestoneCardRenderer
import io.github.darinc.amssync.image.MilestoneStyles
import io.github.darinc.amssync.validation.Validators
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.utils.FileUpload
import org.bukkit.Bukkit
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

/**
 * Listens for mcMMO level-up events and announces milestones to Discord.
 *
 * Announces skill milestones (e.g., level 100, 200, 300) and power level
 * milestones to a configured Discord text channel.
 *
 * Supports three announcement modes:
 * 1. Image cards with webhooks (richest experience)
 * 2. Image cards with bot messages (file attachments)
 * 3. Traditional embeds or plain text (fallback)
 *
 * @property plugin The parent plugin instance
 * @property config Announcement configuration
 * @property avatarFetcher Avatar fetching service (optional, for image cards)
 * @property cardRenderer Image card renderer (optional, for image cards)
 */
class McMMOEventListener(
    private val plugin: AMSSyncPlugin,
    private val config: AnnouncementConfig,
    private val avatarFetcher: AvatarFetcher? = null,
    private val cardRenderer: MilestoneCardRenderer? = null
) : Listener {

    // Track last known power level for each player to detect milestones
    private val lastKnownPowerLevel = ConcurrentHashMap<String, Int>()

    // Webhook client for richer messages
    private var webhookClient: WebhookClient? = null

    init {
        // Initialize webhook client if URL is configured
        if (!config.webhookUrl.isNullOrBlank()) {
            try {
                webhookClient = WebhookClientBuilder(config.webhookUrl)
                    .setThreadFactory { r ->
                        val thread = Thread(r, "AMSSync-Milestone-Webhook")
                        thread.isDaemon = true
                        thread
                    }
                    .setWait(false)
                    .build()
                plugin.logger.info("Milestone webhook client initialized")
            } catch (e: Exception) {
                plugin.logger.warning("Failed to initialize milestone webhook: ${e.message}")
            }
        }
    }

    /**
     * Handle mcMMO player level up event.
     * Checks if the new level is a milestone and announces it.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerLevelUp(event: McMMOPlayerLevelUpEvent) {
        val player = event.player
        val skill = event.skill
        val newLevel = event.skillLevel
        val uuid = player.uniqueId

        // Check for skill milestone
        if (config.skillMilestoneInterval > 0 && newLevel % config.skillMilestoneInterval == 0) {
            sendSkillMilestone(player.name, uuid, skill, newLevel)
        }

        // Check for power level milestone
        if (config.powerMilestoneInterval > 0) {
            checkPowerLevelMilestone(player.name, uuid)
        }
    }

    /**
     * Check if player crossed a power level milestone and announce it.
     */
    private fun checkPowerLevelMilestone(playerName: String, uuid: UUID) {
        try {
            // Calculate current power level
            val profile = mcMMO.getDatabaseManager().loadPlayerProfile(uuid)
            if (!profile.isLoaded) return

            val currentPowerLevel = PrimarySkillType.values()
                .filter { !it.isChildSkill }
                .sumOf { profile.getSkillLevel(it) }

            val lastPowerLevel = lastKnownPowerLevel.getOrDefault(playerName, 0)

            // Check if we crossed a milestone
            val lastMilestone = (lastPowerLevel / config.powerMilestoneInterval) * config.powerMilestoneInterval
            val currentMilestone = (currentPowerLevel / config.powerMilestoneInterval) * config.powerMilestoneInterval

            if (currentMilestone > lastMilestone && currentMilestone > 0) {
                sendPowerLevelMilestone(playerName, uuid, currentMilestone)
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
    private fun sendSkillMilestone(playerName: String, uuid: UUID, skill: PrimarySkillType, level: Int) {
        plugin.logger.info("Announcing skill milestone: $playerName reached level $level in ${skill.name}")

        // Try image card first
        if (config.useImageCards && cardRenderer != null) {
            sendSkillMilestoneImage(playerName, uuid, skill, level)
        } else {
            sendSkillMilestoneEmbed(playerName, skill, level)
        }
    }

    /**
     * Send skill milestone as an image card.
     */
    private fun sendSkillMilestoneImage(playerName: String, uuid: UUID, skill: PrimarySkillType, level: Int) {
        // Fetch avatar asynchronously then render and send
        CompletableFuture.supplyAsync {
            val headImage = if (config.showAvatars && avatarFetcher != null) {
                avatarFetcher.fetchHeadAvatar(playerName, uuid, config.avatarProvider, MilestoneStyles.HEAD_SIZE)
            } else {
                createPlaceholderHead()
            }

            cardRenderer!!.renderSkillMilestoneCard(playerName, skill, level, headImage)
        }.thenAccept { cardImage ->
            sendImageToDiscord(cardImage, "${playerName}_skill_milestone.png", "Skill Milestone")
        }.exceptionally { e ->
            plugin.logger.warning("Failed to generate skill milestone card: ${e.message}")
            // Fallback to embed
            sendSkillMilestoneEmbed(playerName, skill, level)
            null
        }
    }

    /**
     * Send skill milestone as a traditional embed.
     */
    private fun sendSkillMilestoneEmbed(playerName: String, skill: PrimarySkillType, level: Int) {
        val channel = getAnnouncementChannel() ?: return

        try {
            val circuitBreaker = plugin.circuitBreaker

            val sendAction = {
                if (config.useEmbeds) {
                    val embed = EmbedBuilder()
                        .setColor(getSkillColor(skill))
                        .setTitle("Skill Milestone!")
                        .setDescription("**$playerName** reached level **$level** in **${Validators.formatSkillName(skill)}**!")
                        .setTimestamp(Instant.now())
                        .build()
                    channel.sendMessageEmbeds(embed).queue(
                        { plugin.logger.fine("Sent skill milestone embed") },
                        { e -> plugin.logger.warning("Failed to send skill milestone: ${e.message}") }
                    )
                } else {
                    channel.sendMessage("$playerName reached level $level in ${Validators.formatSkillName(skill)}!").queue(
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
    private fun sendPowerLevelMilestone(playerName: String, uuid: UUID, powerLevel: Int) {
        plugin.logger.info("Announcing power level milestone: $playerName reached power level $powerLevel")

        // Try image card first
        if (config.useImageCards && cardRenderer != null) {
            sendPowerLevelMilestoneImage(playerName, uuid, powerLevel)
        } else {
            sendPowerLevelMilestoneEmbed(playerName, powerLevel)
        }
    }

    /**
     * Send power level milestone as an image card.
     */
    private fun sendPowerLevelMilestoneImage(playerName: String, uuid: UUID, powerLevel: Int) {
        // Fetch avatar asynchronously then render and send
        CompletableFuture.supplyAsync {
            val headImage = if (config.showAvatars && avatarFetcher != null) {
                avatarFetcher.fetchHeadAvatar(playerName, uuid, config.avatarProvider, MilestoneStyles.HEAD_SIZE)
            } else {
                createPlaceholderHead()
            }

            cardRenderer!!.renderPowerMilestoneCard(playerName, powerLevel, headImage)
        }.thenAccept { cardImage ->
            sendImageToDiscord(cardImage, "${playerName}_power_milestone.png", "Power Milestone")
        }.exceptionally { e ->
            plugin.logger.warning("Failed to generate power milestone card: ${e.message}")
            // Fallback to embed
            sendPowerLevelMilestoneEmbed(playerName, powerLevel)
            null
        }
    }

    /**
     * Send power level milestone as a traditional embed.
     */
    private fun sendPowerLevelMilestoneEmbed(playerName: String, powerLevel: Int) {
        val channel = getAnnouncementChannel() ?: return

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
     * Send an image to Discord via webhook or bot message.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun sendImageToDiscord(image: BufferedImage, filename: String, milestoneType: String) {
        try {
            // Convert image to byte array
            val baos = ByteArrayOutputStream()
            ImageIO.write(image, "PNG", baos)
            val imageBytes = baos.toByteArray()

            val client = webhookClient
            if (client != null) {
                // Send via webhook
                sendImageViaWebhook(imageBytes, filename, client)
            } else {
                // Send via bot message
                sendImageViaBot(imageBytes, filename)
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error sending milestone image: ${e.message}")
        }
    }

    /**
     * Send image via webhook.
     */
    private fun sendImageViaWebhook(imageBytes: ByteArray, filename: String, client: WebhookClient) {
        try {
            val circuitBreaker = plugin.circuitBreaker

            val sendAction = {
                val message = WebhookMessageBuilder()
                    .setUsername("MCMMO Milestones")
                    .addFile(filename, imageBytes)
                    .build()

                client.send(message)
                    .thenAccept { plugin.logger.fine("Sent milestone image via webhook") }
                    .exceptionally { e ->
                        plugin.logger.warning("Failed to send milestone via webhook: ${e.message}")
                        null
                    }
                Unit
            }

            if (circuitBreaker != null) {
                val result = circuitBreaker.execute("Send milestone webhook", sendAction)
                if (result is CircuitBreaker.CircuitResult.Rejected) {
                    plugin.logger.fine("Milestone webhook rejected by circuit breaker")
                }
            } else {
                sendAction()
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error sending milestone webhook: ${e.message}")
        }
    }

    /**
     * Send image via bot message with file attachment.
     */
    private fun sendImageViaBot(imageBytes: ByteArray, filename: String) {
        val channel = getAnnouncementChannel() ?: return

        try {
            val circuitBreaker = plugin.circuitBreaker

            val sendAction = {
                channel.sendFiles(FileUpload.fromData(imageBytes, filename)).queue(
                    { plugin.logger.fine("Sent milestone image via bot") },
                    { e -> plugin.logger.warning("Failed to send milestone image: ${e.message}") }
                )
            }

            if (circuitBreaker != null) {
                val result = circuitBreaker.execute("Send milestone image", sendAction)
                if (result is CircuitBreaker.CircuitResult.Rejected) {
                    plugin.logger.fine("Milestone image rejected by circuit breaker")
                }
            } else {
                sendAction()
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error sending milestone image via bot: ${e.message}")
        }
    }

    /**
     * Create a placeholder head image when avatar fetching is disabled.
     */
    private fun createPlaceholderHead(): BufferedImage {
        val size = MilestoneStyles.HEAD_SIZE
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g2d = image.createGraphics()

        // Gray background
        g2d.color = Color(80, 80, 80)
        g2d.fillRect(0, 0, size, size)

        // Question mark
        g2d.color = Color(150, 150, 150)
        g2d.font = java.awt.Font("SansSerif", java.awt.Font.BOLD, 32)
        val metrics = g2d.fontMetrics
        val text = "?"
        val x = (size - metrics.stringWidth(text)) / 2
        val y = (size - metrics.height) / 2 + metrics.ascent
        g2d.drawString(text, x, y)

        g2d.dispose()
        return image
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

    /**
     * Shutdown the webhook client.
     */
    fun shutdown() {
        webhookClient?.close()
        webhookClient = null
    }
}

/**
 * Configuration for mcMMO milestone announcements.
 *
 * @property enabled Enable/disable milestone announcements
 * @property channelId Text channel ID for announcements
 * @property webhookUrl Optional webhook URL for richer messages with images
 * @property skillMilestoneInterval Announce every N skill levels (0 = disabled)
 * @property powerMilestoneInterval Announce every N power levels (0 = disabled)
 * @property useEmbeds Use rich embeds (true) or plain text (false)
 * @property useImageCards Generate visual image cards for milestones
 * @property showAvatars Include player avatars in announcements
 * @property avatarProvider Avatar service: "mc-heads" or "crafatar"
 */
data class AnnouncementConfig(
    val enabled: Boolean,
    val channelId: String,
    val webhookUrl: String?,
    val skillMilestoneInterval: Int,
    val powerMilestoneInterval: Int,
    val useEmbeds: Boolean,
    val useImageCards: Boolean,
    val showAvatars: Boolean,
    val avatarProvider: String
) {
    companion object {
        /**
         * Load announcement configuration from Bukkit config file.
         *
         * @param config The Bukkit FileConfiguration to load from
         * @return AnnouncementConfig with loaded or default values
         */
        fun fromConfig(config: FileConfiguration): AnnouncementConfig {
            val webhookUrl = config.getString("discord.announcements.webhook-url", "") ?: ""

            return AnnouncementConfig(
                enabled = config.getBoolean("discord.announcements.enabled", false),
                channelId = config.getString("discord.announcements.text-channel-id", "") ?: "",
                webhookUrl = webhookUrl.ifBlank { null },
                skillMilestoneInterval = config.getInt("discord.announcements.skill-milestone-interval", 100),
                powerMilestoneInterval = config.getInt("discord.announcements.power-milestone-interval", 500),
                useEmbeds = config.getBoolean("discord.announcements.use-embeds", true),
                useImageCards = config.getBoolean("discord.announcements.use-image-cards", true),
                showAvatars = config.getBoolean("discord.announcements.show-avatars", true),
                avatarProvider = config.getString("discord.announcements.avatar-provider", "mc-heads") ?: "mc-heads"
            )
        }
    }
}
