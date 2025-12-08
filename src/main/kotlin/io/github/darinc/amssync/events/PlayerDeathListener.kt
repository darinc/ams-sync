package io.github.darinc.amssync.events

import io.github.darinc.amssync.AMSSyncPlugin
import io.github.darinc.amssync.discord.WebhookManager
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

        val avatarUrl = if (config.showAvatars) {
            EventAnnouncementConfig.getAvatarUrl(
                player.name,
                player.uniqueId,
                config.avatarProvider
            )
        } else null

        if (config.useEmbeds) {
            val embed = EmbedBuilder()
                .setColor(Color(237, 66, 69)) // Discord red
                .setDescription(plainMessage)
                .apply {
                    if (avatarUrl != null) {
                        setThumbnail(avatarUrl)
                    }
                }
                .setTimestamp(Instant.now())
                .build()

            webhookManager.sendEmbed(embed, player.name, avatarUrl)
        } else {
            webhookManager.sendMessage(plainMessage, player.name, avatarUrl)
        }
    }
}
