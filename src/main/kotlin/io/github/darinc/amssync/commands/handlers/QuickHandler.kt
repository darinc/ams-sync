package io.github.darinc.amssync.commands.handlers

import io.github.darinc.amssync.commands.CommandContext
import io.github.darinc.amssync.commands.DiscordData
import io.github.darinc.amssync.commands.SubcommandHandler
import org.bukkit.Bukkit

/**
 * Handles the /amssync quick command for streamlined linking workflow.
 */
class QuickHandler(private val linkHandler: LinkHandler) : SubcommandHandler {
    override val name = "quick"
    override val usage = "/amssync quick [player#] [discord#]"

    override fun execute(context: CommandContext, args: List<String>) {
        // If arguments provided, try to link immediately
        if (args.size >= 2) {
            linkHandler.execute(context, args)
            return
        }

        val (sender, plugin, sessionManager) = context

        // No arguments - show both lists in compact format
        sender.sendMessage("§6§l=== Quick Link ===")
        sender.sendMessage("§7Loading players and Discord members...")
        sender.sendMessage("")

        // Get players
        val onlinePlayers = Bukkit.getOnlinePlayers().sortedBy { it.name }
        val whitelistedPlayers = Bukkit.getWhitelistedPlayers()
            .filter { it.name != null && !onlinePlayers.any { p -> p.name == it.name } }
            .sortedBy { it.name }

        val allPlayers = (onlinePlayers.map { it.name } + whitelistedPlayers.mapNotNull { it.name }).take(20)
        val playerNumbers = mutableMapOf<Int, String>()

        // Display players
        sender.sendMessage("§e§lMinecraft Players:")
        allPlayers.forEachIndexed { index, name ->
            val num = index + 1
            playerNumbers[num] = name
            val linkedStatus = if (plugin.services.userMappingService.isMinecraftLinked(name)) "§a✓" else "§8✗"
            sender.sendMessage("  §7[$num] $linkedStatus §f$name")
        }

        if (onlinePlayers.size + whitelistedPlayers.size > 20) {
            sender.sendMessage("  §7... and ${onlinePlayers.size + whitelistedPlayers.size - 20} more")
        }

        sender.sendMessage("")
        sender.sendMessage("§7Loading Discord members...")

        // Get Discord members
        val jda = plugin.services.discord.manager.getJda()
        if (jda == null) {
            sender.sendMessage("§cDiscord bot is not connected!")
            // Store player session anyway
            val session = sessionManager.getOrCreateSession(sender)
            session.setPlayerMappings(playerNumbers)
            return
        }

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

        // Load Discord members asynchronously
        guild.loadMembers().onSuccess { members ->
            Bukkit.getScheduler().runTask(plugin, Runnable {
                val sortedMembers = members
                    .filter { !it.user.isBot }
                    .sortedBy { it.effectiveName }

                val discordNumbers = mutableMapOf<Int, DiscordData>()

                sender.sendMessage("§b§lDiscord Members:")
                sortedMembers.take(20).forEachIndexed { index, member ->
                    val num = index + 1
                    discordNumbers[num] = DiscordData(member.id, member.effectiveName)

                    val linkedStatus = if (plugin.services.userMappingService.isDiscordLinked(member.id)) "§a✓" else "§8✗"
                    sender.sendMessage("  §7[$num] $linkedStatus §f${member.effectiveName} §8(${member.user.name})")
                }

                if (sortedMembers.size > 20) {
                    sender.sendMessage("  §7... and ${sortedMembers.size - 20} more")
                }

                // Store session
                val session = sessionManager.getOrCreateSession(sender)
                session.setPlayerMappings(playerNumbers)
                session.setDiscordMappings(discordNumbers)

                sender.sendMessage("")
                sender.sendMessage("§a§l✓ Quick link ready!")
                sender.sendMessage("§eType: §f/amssync quick <player#> <discord#>")
                sender.sendMessage("§7Example: §f/amssync quick 1 1 §7to link player #1 to Discord member #1")
                sender.sendMessage("")
                sender.sendMessage("§7Session expires in ${session.getTimeRemaining()} seconds")
            })
        }.onError { error ->
            Bukkit.getScheduler().runTask(plugin, Runnable {
                sender.sendMessage("§cFailed to load Discord members: ${error.message}")
                plugin.logger.warning("Failed to load Discord members: ${error.message}")
            })
        }
    }
}
