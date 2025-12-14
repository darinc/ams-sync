package io.github.darinc.amssync.commands.handlers

import io.github.darinc.amssync.commands.CommandContext
import io.github.darinc.amssync.commands.SubcommandHandler
import org.bukkit.Bukkit

/**
 * Handles the /amssync list command.
 */
class ListHandler : SubcommandHandler {
    override val name = "list"
    override val usage = "/amssync list"

    override fun execute(context: CommandContext, args: List<String>) {
        val (sender, plugin) = context
        val mappings = plugin.userMappingService.getAllMappings()

        if (mappings.isEmpty()) {
            sender.sendMessage("§eNo user mappings configured yet.")
            sender.sendMessage("§7Use §f/amssync add <discordId> <minecraftUsername> §7to add one.")
            return
        }

        sender.sendMessage("§6§l=== Discord-Minecraft User Mappings (${mappings.size}) ===")

        // Try to fetch Discord member names
        val jda = plugin.discord.manager.getJda()
        if (jda == null) {
            // Discord bot not connected - show IDs only
            sender.sendMessage("§7(Discord bot offline - showing IDs only)")
            mappings.forEach { (discordId, minecraftUsername) ->
                sender.sendMessage("§8$discordId §7-> §f$minecraftUsername")
            }
            return
        }

        val guildId = plugin.config.getString("discord.guild-id")
        if (guildId.isNullOrBlank() || guildId == "YOUR_GUILD_ID_HERE") {
            // Guild not configured - show IDs only
            mappings.forEach { (discordId, minecraftUsername) ->
                sender.sendMessage("§8$discordId §7-> §f$minecraftUsername")
            }
            return
        }

        val guild = jda.getGuildById(guildId)
        if (guild == null) {
            // Guild not found - show IDs only
            mappings.forEach { (discordId, minecraftUsername) ->
                sender.sendMessage("§8$discordId §7-> §f$minecraftUsername")
            }
            return
        }

        sender.sendMessage("§7Loading Discord member names...")

        // Load all guild members to get their display names
        guild.loadMembers().onSuccess { members ->
            Bukkit.getScheduler().runTask(plugin, Runnable {
                // Create a map of Discord ID -> Display Name
                val memberNames = members.associate { it.id to it.effectiveName }

                sender.sendMessage("")
                mappings.forEach { (discordId, minecraftUsername) ->
                    val discordName = memberNames[discordId]
                    if (discordName != null) {
                        sender.sendMessage("§b$discordName §7-> §f$minecraftUsername")
                    } else {
                        // Member not found (left server?) - show ID
                        sender.sendMessage("§8$discordId §7-> §f$minecraftUsername §8(member not found)")
                    }
                }
            })
        }.onError { _ ->
            Bukkit.getScheduler().runTask(plugin, Runnable {
                // Failed to load members - show IDs only
                sender.sendMessage("§7(Failed to load Discord members - showing IDs)")
                mappings.forEach { (discordId, minecraftUsername) ->
                    sender.sendMessage("§8$discordId §7-> §f$minecraftUsername")
                }
            })
        }
    }
}
