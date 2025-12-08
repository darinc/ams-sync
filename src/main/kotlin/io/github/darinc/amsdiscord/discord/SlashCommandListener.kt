package io.github.darinc.amsdiscord.discord

import io.github.darinc.amsdiscord.AmsDiscordPlugin
import io.github.darinc.amsdiscord.discord.commands.DiscordLinkCommand
import io.github.darinc.amsdiscord.discord.commands.McStatsCommand
import io.github.darinc.amsdiscord.discord.commands.McTopCommand
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

/**
 * Listens for Discord slash command interactions
 */
class SlashCommandListener(private val plugin: AmsDiscordPlugin) : ListenerAdapter() {

    private val mcStatsCommand = McStatsCommand(plugin)
    private val mcTopCommand = McTopCommand(plugin)
    private val discordLinkCommand = DiscordLinkCommand(plugin)

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        plugin.logger.info("Received slash command: ${event.name} from ${event.user.name}")

        when (event.name) {
            "mcstats" -> mcStatsCommand.handle(event)
            "mctop" -> mcTopCommand.handle(event)
            "amslink" -> discordLinkCommand.handle(event)
            else -> {
                event.reply("Unknown command!").setEphemeral(true).queue()
            }
        }
    }
}
