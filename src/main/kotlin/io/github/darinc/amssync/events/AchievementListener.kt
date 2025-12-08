package io.github.darinc.amssync.events

import io.github.darinc.amssync.AMSSyncPlugin
import io.github.darinc.amssync.discord.WebhookManager
import net.dv8tion.jda.api.EmbedBuilder
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerAdvancementDoneEvent
import java.awt.Color
import java.time.Instant

/**
 * Listens for player advancement (achievement) events and announces them to Discord.
 *
 * @property plugin The parent plugin instance
 * @property config Event announcement configuration
 * @property webhookManager Manager for sending messages (webhook or bot)
 */
class AchievementListener(
    private val plugin: AMSSyncPlugin,
    private val config: EventAnnouncementConfig,
    private val webhookManager: WebhookManager
) : Listener {

    companion object {
        private val TASK_COLOR = Color(67, 181, 129) // Green
        private val GOAL_COLOR = Color(250, 166, 26) // Gold
        private val CHALLENGE_COLOR = Color(170, 0, 170) // Purple
    }

    /**
     * Handle player advancement event and announce to Discord.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onAdvancement(event: PlayerAdvancementDoneEvent) {
        if (!config.achievements.enabled) return

        val display = getAnnouncableDisplay(event) ?: return
        val player = event.player

        val serializer = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
        val titleText = serializer.serialize(display.title())
        val descriptionText = serializer.serialize(display.description())

        plugin.logger.fine("Announcing advancement: ${player.name} earned '$titleText'")

        val avatarUrl = if (config.showAvatars) {
            EventAnnouncementConfig.getAvatarUrl(player.name, player.uniqueId, config.avatarProvider)
        } else null

        if (config.useEmbeds) {
            sendEmbedAnnouncement(player.name, titleText, descriptionText, display.frame(), avatarUrl)
        } else {
            val message = "${player.name} has made the advancement [$titleText] - $descriptionText"
            webhookManager.sendMessage(message, player.name, avatarUrl)
        }
    }

    private fun getAnnouncableDisplay(event: PlayerAdvancementDoneEvent): io.papermc.paper.advancement.AdvancementDisplay? {
        val advancement = event.advancement
        val key = advancement.key.key

        // Filter out recipes if configured
        if (config.achievements.excludeRecipes && key.startsWith("recipes/")) {
            return null
        }

        // Only announce advancements with display data (real achievements)
        val display = advancement.display ?: return null

        // Skip hidden advancements
        if (!display.doesAnnounceToChat()) {
            return null
        }

        return display
    }

    private fun sendEmbedAnnouncement(
        playerName: String,
        titleText: String,
        descriptionText: String,
        frame: io.papermc.paper.advancement.AdvancementDisplay.Frame?,
        avatarUrl: String?
    ) {
        val frameColor = getFrameColor(frame)
        val frameEmoji = getFrameEmoji(frame)

        val embed = EmbedBuilder()
            .setColor(frameColor)
            .setTitle("$frameEmoji Advancement Made!")
            .setDescription("**$playerName** has made the advancement **$titleText**\n\n*$descriptionText*")
            .apply { avatarUrl?.let { setThumbnail(it) } }
            .setTimestamp(Instant.now())
            .build()

        webhookManager.sendEmbed(embed, playerName, avatarUrl)
    }

    private fun getFrameColor(frame: io.papermc.paper.advancement.AdvancementDisplay.Frame?): Color {
        return when (frame) {
            io.papermc.paper.advancement.AdvancementDisplay.Frame.TASK -> TASK_COLOR
            io.papermc.paper.advancement.AdvancementDisplay.Frame.GOAL -> GOAL_COLOR
            io.papermc.paper.advancement.AdvancementDisplay.Frame.CHALLENGE -> CHALLENGE_COLOR
            else -> TASK_COLOR
        }
    }

    private fun getFrameEmoji(frame: io.papermc.paper.advancement.AdvancementDisplay.Frame?): String {
        return when (frame) {
            io.papermc.paper.advancement.AdvancementDisplay.Frame.TASK -> ""
            io.papermc.paper.advancement.AdvancementDisplay.Frame.GOAL -> ""
            io.papermc.paper.advancement.AdvancementDisplay.Frame.CHALLENGE -> ""
            else -> ""
        }
    }
}
