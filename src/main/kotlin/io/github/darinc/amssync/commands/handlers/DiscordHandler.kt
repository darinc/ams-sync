package io.github.darinc.amssync.commands.handlers

import io.github.darinc.amssync.commands.CommandContext
import io.github.darinc.amssync.commands.DiscordData
import io.github.darinc.amssync.commands.SubcommandHandler
import org.bukkit.Bukkit

/**
 * Handles the /amssync discord command.
 */
class DiscordHandler : SubcommandHandler {
    override val name = "discord"
    override val usage = "/amssync discord"

    override fun execute(context: CommandContext, args: List<String>) {
        val (sender, plugin, sessionManager) = context

        val jda = plugin.discord.manager.getJda()
        if (jda == null) {
            sender.sendMessage("§cDiscord bot is not connected!")
            return
        }

        // Get the guild
        val guildId = plugin.config.getString("discord.guild-id")
        if (guildId.isNullOrBlank() || guildId == "YOUR_GUILD_ID_HERE") {
            sender.sendMessage("§cGuild ID not configured in config.yml")
            return
        }

        val guild = jda.getGuildById(guildId)
        if (guild == null) {
            sender.sendMessage("§cCould not find Discord guild with ID: $guildId")
            return
        }

        sender.sendMessage("§6§l=== Discord Members (${guild.name}) ===")
        sender.sendMessage("§7Loading members...")

        // Load members asynchronously
        guild.loadMembers().onSuccess { members ->
            Bukkit.getScheduler().runTask(plugin, Runnable {
                sender.sendMessage("")
                val sortedMembers = members
                    .filter { !it.user.isBot } // Exclude bots
                    .sortedBy { it.effectiveName }

                // Build Discord number mappings
                val discordNumbers = mutableMapOf<Int, DiscordData>()

                sender.sendMessage("§b§lDiscord Members (${sortedMembers.size} users):")
                sortedMembers.take(20).forEachIndexed { index, member ->
                    val num = index + 1
                    discordNumbers[num] = DiscordData(member.id, member.effectiveName)

                    val linkedStatus = if (plugin.userMappingService.isDiscordLinked(member.id)) {
                        val mcName = plugin.userMappingService.getMinecraftUsername(member.id)
                        "§a✓ -> $mcName"
                    } else {
                        "§8✗ Not Linked"
                    }
                    val displayName = member.effectiveName
                    val username = member.user.name
                    val discordId = member.id

                    sender.sendMessage("  §7[${num}] $linkedStatus §f$displayName")
                    sender.sendMessage("       §7Discord: §f$username §8(ID: $discordId)")
                }

                if (sortedMembers.size > 20) {
                    sender.sendMessage("  §7... and ${sortedMembers.size - 20} more")
                }

                // Store Discord mappings in session
                val session = sessionManager.getOrCreateSession(sender)
                session.setDiscordMappings(discordNumbers)

                sender.sendMessage("")
                sender.sendMessage("§e§l--- Link a Member ---")
                sender.sendMessage("§7/amssync add <fullDiscordId> <mcUsername>")
                sender.sendMessage("§7/amssync link <player#> <discord#> §f- Link by number")
                sender.sendMessage("§7/amssync quick [player#] [discord#] §f- Quick linking workflow")
                sender.sendMessage("")
                sender.sendMessage("§aSession stored for ${session.getTimeRemaining()} seconds")
            })
        }.onError { error ->
            Bukkit.getScheduler().runTask(plugin, Runnable {
                sender.sendMessage("§cFailed to load Discord members: ${error.message}")
                plugin.logger.warning("Failed to load Discord members: ${error.message}")
            })
        }
    }
}
