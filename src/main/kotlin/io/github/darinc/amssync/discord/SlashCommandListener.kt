package io.github.darinc.amssync.discord

import io.github.darinc.amssync.AMSSyncPlugin
import io.github.darinc.amssync.discord.commands.DiscordLinkCommand
import io.github.darinc.amssync.discord.commands.McStatsCommand
import io.github.darinc.amssync.discord.commands.McTopCommand
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

/**
 * Listens for Discord slash command interactions
 */
class SlashCommandListener(private val plugin: AMSSyncPlugin) : ListenerAdapter() {

    private val mcStatsCommand = McStatsCommand(plugin)
    private val mcTopCommand = McTopCommand(plugin)
    private val discordLinkCommand = DiscordLinkCommand(plugin)

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        plugin.logger.info("Received slash command: ${event.name} from ${event.user.name}")

        when (event.name) {
            "mcstats" -> mcStatsCommand.handle(event)
            "mctop" -> mcTopCommand.handle(event)
            "amssync" -> discordLinkCommand.handle(event)
            else -> {
                event.reply("Unknown command!").setEphemeral(true).queue(
                    null,
                    { error -> plugin.logger.warning("Failed to reply to unknown command: ${error.message}") }
                )
            }
        }
    }
}
