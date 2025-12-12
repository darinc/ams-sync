package io.github.darinc.amssync.discord.commands

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent

/**
 * Interface for Discord slash command handlers.
 *
 * Enables dependency injection and registry-based routing for slash commands.
 * Modeled after [io.github.darinc.amssync.commands.SubcommandHandler] for consistency.
 */
interface SlashCommandHandler {
    /**
     * The command name as registered with Discord (e.g., "mcstats", "amstop").
     */
    val commandName: String

    /**
     * Handle the slash command interaction.
     *
     * @param event The Discord slash command event
     */
    fun handle(event: SlashCommandInteractionEvent)
}
