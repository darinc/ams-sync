package io.github.darinc.amssync.commands.handlers

import io.github.darinc.amssync.commands.CommandContext
import io.github.darinc.amssync.commands.SubcommandHandler

/**
 * Handles the /amssync link <player#> <discord#> command.
 */
class LinkHandler : SubcommandHandler {
    override val name = "link"
    override val usage = "/amssync link <player#> <discord#>"

    override fun execute(context: CommandContext, args: List<String>) {
        val (sender, plugin, sessionManager) = context

        if (args.size < 2) {
            sender.sendMessage("§cUsage: $usage")
            sender.sendMessage("§7Run §f/amssync players §7and §f/amssync discord §7first to get numbers")
            return
        }

        // Get the session
        val session = sessionManager.getSession(sender)
        if (session == null) {
            sender.sendMessage("§cNo active session found!")
            sender.sendMessage("§7Run §f/amssync players §7and §f/amssync discord §7first")
            return
        }

        // Parse player number
        val playerNum = args[0].toIntOrNull()
        if (playerNum == null) {
            sender.sendMessage("§cInvalid player number: ${args[0]}")
            return
        }

        // Parse discord number
        val discordNum = args[1].toIntOrNull()
        if (discordNum == null) {
            sender.sendMessage("§cInvalid discord number: ${args[1]}")
            return
        }

        // Look up player name
        val playerName = session.getPlayerName(playerNum)
        if (playerName == null) {
            sender.sendMessage("§cPlayer number $playerNum not found in session")
            sender.sendMessage("§7Run §f/amssync players §7to refresh the list")
            return
        }

        // Look up discord data
        val discordData = session.getDiscordData(discordNum)
        if (discordData == null) {
            sender.sendMessage("§cDiscord number $discordNum not found in session")
            sender.sendMessage("§7Run §f/amssync discord §7to refresh the list")
            return
        }

        // Create the link
        sender.sendMessage("§aLinking §f$playerName §a(#$playerNum) to §f${discordData.displayName} §a(#$discordNum)...")
        plugin.userMappingService.addMapping(discordData.id, playerName)
        plugin.userMappingService.saveMappings()

        sender.sendMessage("§aSuccessfully linked §f$playerName §a-> §f${discordData.displayName}§a!")
    }
}
