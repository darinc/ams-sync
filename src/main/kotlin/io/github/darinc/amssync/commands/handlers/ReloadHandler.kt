package io.github.darinc.amssync.commands.handlers

import io.github.darinc.amssync.commands.CommandContext
import io.github.darinc.amssync.commands.SubcommandHandler

/**
 * Handles the /amssync reload command.
 *
 * Performs a full reload of all plugin services:
 * - Reloads configuration from disk
 * - Reconnects to Discord
 * - Reinitializes all features and listeners
 *
 * This is useful for applying config changes without restarting the server.
 * Note: This is a slow operation (~5-10 seconds) due to Discord reconnection.
 */
class ReloadHandler : SubcommandHandler {
    override val name = "reload"
    override val usage = "/amssync reload"

    override fun execute(context: CommandContext, args: List<String>) {
        val (sender, plugin) = context

        sender.sendMessage("§eReloading AMSSync plugin...")
        sender.sendMessage("§7This may take a few seconds (Discord reconnection)...")

        try {
            val startTime = System.currentTimeMillis()
            val success = plugin.reloadAllServices()
            val duration = System.currentTimeMillis() - startTime

            if (success) {
                sender.sendMessage("§a✓ AMSSync reloaded successfully in ${duration}ms")
                if (plugin.discord.manager.isConnected()) {
                    sender.sendMessage("§a  Discord: Connected")
                } else {
                    sender.sendMessage("§c  Discord: Disconnected (degraded mode)")
                }
            } else {
                sender.sendMessage("§c✗ Reload completed with errors. Check console for details.")
            }
        } catch (e: Exception) {
            sender.sendMessage("§c✗ Reload failed: ${e.message}")
            plugin.logger.severe("Reload command failed: ${e.message}")
            plugin.logger.severe(e.stackTraceToString())
        }
    }
}
