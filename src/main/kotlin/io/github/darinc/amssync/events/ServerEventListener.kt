package io.github.darinc.amssync.events

import io.github.darinc.amssync.AMSSyncPlugin
import io.github.darinc.amssync.discord.WebhookManager
import net.dv8tion.jda.api.EmbedBuilder
import java.awt.Color
import java.time.Instant

/**
 * Handles server start and stop announcements to Discord.
 *
 * Unlike other event listeners, this class does not implement Bukkit Listener
 * since server start/stop are handled directly from plugin lifecycle methods.
 *
 * @property plugin The parent plugin instance
 * @property config Event announcement configuration
 * @property webhookManager Manager for sending messages (webhook or bot)
 */
class ServerEventListener(
    private val plugin: AMSSyncPlugin,
    private val config: EventAnnouncementConfig,
    private val webhookManager: WebhookManager
) {
    /**
     * Announce that the server has started.
     * Call this from AMSSyncPlugin.onEnable() after Discord connects.
     */
    fun announceServerStart() {
        if (!config.serverStart.enabled) {
            plugin.logger.fine("Server start announcements disabled")
            return
        }

        plugin.logger.info("Announcing server start to Discord")

        if (config.useEmbeds) {
            val embed = EmbedBuilder()
                .setColor(Color(67, 181, 129)) // Discord green
                .setTitle("Server Online")
                .setDescription(config.serverStart.message)
                .setTimestamp(Instant.now())
                .build()

            webhookManager.sendEmbed(embed, "Server", null)
        } else {
            webhookManager.sendMessage(config.serverStart.message, "Server", null)
        }
    }

    /**
     * Announce that the server is stopping.
     * Call this from AMSSyncPlugin.onDisable() before Discord disconnects.
     */
    fun announceServerStop() {
        if (!config.serverStop.enabled) {
            plugin.logger.fine("Server stop announcements disabled")
            return
        }

        plugin.logger.info("Announcing server stop to Discord")

        if (config.useEmbeds) {
            val embed = EmbedBuilder()
                .setColor(Color(237, 66, 69)) // Discord red
                .setTitle("Server Offline")
                .setDescription(config.serverStop.message)
                .setTimestamp(Instant.now())
                .build()

            webhookManager.sendEmbed(embed, "Server", null)
        } else {
            webhookManager.sendMessage(config.serverStop.message, "Server", null)
        }
    }
}
