package io.github.darinc.amssync.events

import io.github.darinc.amssync.AMSSyncPlugin
import io.github.darinc.amssync.discord.WebhookManager
import io.github.darinc.amssync.exceptions.PlayerDataNotFoundException
import net.dv8tion.jda.api.EmbedBuilder
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import java.awt.Color
import java.time.Instant

/**
 * Listens for player death events and announces them to Discord.
 *
 * @property plugin The parent plugin instance
 * @property config Event announcement configuration
 * @property webhookManager Manager for sending messages (webhook or bot)
 */
class PlayerDeathListener(
    private val plugin: AMSSyncPlugin,
    private val config: EventAnnouncementConfig,
    private val webhookManager: WebhookManager
) : Listener {

    /**
     * Handle player death event and announce to Discord.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        if (!config.playerDeaths.enabled) return

        val player = event.entity
        val deathMessage = event.deathMessage() ?: return

        // Convert adventure Component to plain text
        val plainMessage = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
            .plainText()
            .serialize(deathMessage)

        plugin.logger.fine("Announcing player death: $plainMessage")

        // Generate commentary if enabled
        val commentary = if (config.playerDeaths.commentaryEnabled) {
            generateCommentary(event)
        } else null

        val avatarUrl = if (config.showAvatars) {
            EventAnnouncementConfig.getAvatarUrl(
                player.name,
                player.uniqueId,
                config.avatarProvider
            )
        } else null

        if (config.useEmbeds) {
            sendEmbedWithCommentary(plainMessage, commentary, player.name, avatarUrl)
        } else {
            val fullMessage = if (commentary != null) {
                "$plainMessage\n*$commentary*"
            } else {
                plainMessage
            }
            webhookManager.sendMessage(fullMessage, player.name, avatarUrl)
        }
    }

    /**
     * Generate commentary for the death event.
     * Tries MCMMO roast first (if enabled and applicable), then falls back to standard commentary.
     */
    private fun generateCommentary(event: PlayerDeathEvent): String {
        val category = DeathCauseClassifier.classify(event)

        // Try MCMMO roast first if enabled
        if (config.playerDeaths.mcmmoRoastsEnabled) {
            val roast = tryGetMcmmoRoast(event.entity.name, category)
            if (roast != null) return roast
        }

        // Fall back to standard commentary
        return DeathCommentaryRepository.getCommentary(category)
    }

    /**
     * Try to get an MCMMO elite roast for a high-level player.
     * Returns null if player isn't high enough level, death isn't embarrassing, or error occurs.
     */
    private fun tryGetMcmmoRoast(playerName: String, category: DeathCategory): String? {
        if (!category.isEmbarrassing()) return null

        return try {
            val powerLevel = plugin.mcmmoApi.getPowerLevel(playerName)
            DeathCommentaryRepository.getEliteRoast(
                category,
                powerLevel,
                config.playerDeaths.mcmmoRoastThreshold
            )
        } catch (e: PlayerDataNotFoundException) {
            plugin.logger.fine("No MCMMO data for $playerName, skipping roast")
            null
        } catch (e: Exception) {
            plugin.logger.warning("Error getting MCMMO power level for roast: ${e.message}")
            null
        }
    }

    /**
     * Send embed with vanilla message and optional commentary.
     */
    private fun sendEmbedWithCommentary(
        deathMessage: String,
        commentary: String?,
        playerName: String,
        avatarUrl: String?
    ) {
        val description = if (commentary != null) {
            "$deathMessage\n\n*$commentary*"
        } else {
            deathMessage
        }

        val embed = EmbedBuilder()
            .setColor(Color(237, 66, 69)) // Discord red
            .setDescription(description)
            .apply {
                if (avatarUrl != null) {
                    setThumbnail(avatarUrl)
                }
            }
            .setTimestamp(Instant.now())
            .build()

        webhookManager.sendEmbed(embed, playerName, avatarUrl)
    }
}
