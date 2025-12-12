package io.github.darinc.amssync.commands.handlers

import io.github.darinc.amssync.commands.CommandContext
import io.github.darinc.amssync.commands.SubcommandHandler
import org.bukkit.Bukkit

/**
 * Handles the /amssync players command.
 */
class PlayersHandler : SubcommandHandler {
    override val name = "players"
    override val usage = "/amssync players"

    override fun execute(context: CommandContext, args: List<String>) {
        val (sender, plugin, sessionManager) = context

        sender.sendMessage("§6§l=== Minecraft Players ===")
        sender.sendMessage("")

        var index = 1
        val playerNumbers = mutableMapOf<Int, String>()

        // Show online players
        val onlinePlayers = Bukkit.getOnlinePlayers().sortedBy { it.name }
        if (onlinePlayers.isNotEmpty()) {
            sender.sendMessage("§a§lOnline (${onlinePlayers.size}):")
            onlinePlayers.forEach { player ->
                val linkedStatus = if (plugin.services.userMappingService.isMinecraftLinked(player.name)) "§a✓ Linked" else "§8✗ Not Linked"
                val discordInfo = if (plugin.services.userMappingService.isMinecraftLinked(player.name)) {
                    val discordId = plugin.services.userMappingService.getDiscordId(player.name)
                    " §7(Discord: $discordId)"
                } else ""
                sender.sendMessage("  §7[$index] $linkedStatus §f${player.name}$discordInfo")
                playerNumbers[index] = player.name
                index++
            }
        }

        // Show whitelisted players
        val whitelistedPlayers = Bukkit.getWhitelistedPlayers()
            .filter { it.name != null && !onlinePlayers.any { p -> p.name == it.name } }
            .sortedBy { it.name }

        if (whitelistedPlayers.isNotEmpty()) {
            sender.sendMessage("")
            val countMessage = if (whitelistedPlayers.size <= 15) {
                "§b§lWhitelisted (${whitelistedPlayers.size} players):"
            } else {
                "§b§lWhitelisted (showing first 15 of ${whitelistedPlayers.size}):"
            }
            sender.sendMessage(countMessage)
            whitelistedPlayers.take(15).forEach { offlinePlayer ->
                val name = offlinePlayer.name ?: "Unknown"
                val linkedStatus = if (plugin.services.userMappingService.isMinecraftLinked(name)) "§a✓ Linked" else "§8✗ Not Linked"
                val discordInfo = if (plugin.services.userMappingService.isMinecraftLinked(name)) {
                    val discordId = plugin.services.userMappingService.getDiscordId(name)
                    " §7(Discord: $discordId)"
                } else ""
                sender.sendMessage("  §7[$index] $linkedStatus §f$name$discordInfo")
                playerNumbers[index] = name
                index++
            }
            if (whitelistedPlayers.size > 15) {
                sender.sendMessage("  §7... and ${whitelistedPlayers.size - 15} more")
            }
        }

        // Store player mappings in session
        val session = sessionManager.getOrCreateSession(sender)
        session.setPlayerMappings(playerNumbers)

        sender.sendMessage("")
        sender.sendMessage("§e§l--- Commands ---")
        sender.sendMessage("§7/amssync add <discordId> <mcUsername> §f- Link by Discord ID")
        sender.sendMessage("§7/amssync discord §f- Show Discord members with numbers")
        sender.sendMessage("§7/amssync link <player#> <discord#> §f- Link by number")
        sender.sendMessage("§7/amssync quick [player#] [discord#] §f- Quick linking workflow")
        sender.sendMessage("")
        sender.sendMessage("§aSession stored for ${session.getTimeRemaining()} seconds")
    }
}
