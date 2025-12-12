package io.github.darinc.amssync.discord.commands

import io.github.darinc.amssync.discord.DiscordApiWrapper
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.utils.FileUpload
import java.util.logging.Logger

/**
 * Shared utility functions for Discord slash commands.
 *
 * Provides common operations like deferred replies, ephemeral messages,
 * embed sending, and file uploads with consistent error handling.
 */
object CommandUtils {

    /**
     * Defer the reply to a slash command interaction.
     *
     * Uses the DiscordApiWrapper if available (for circuit breaker protection),
     * otherwise falls back to direct JDA call.
     *
     * @param event The slash command event
     * @param commandName Command name for logging
     * @param discordApi Optional DiscordApiWrapper
     * @param logger Logger for error messages
     */
    fun deferReply(
        event: SlashCommandInteractionEvent,
        commandName: String,
        discordApi: DiscordApiWrapper?,
        logger: Logger
    ) {
        discordApi?.deferReply(event)?.exceptionally { error ->
            logger.warning("Failed to defer reply for $commandName: ${error.message}")
            null
        } ?: event.deferReply().queue(
            null,
            { error -> logger.warning("Failed to defer reply for $commandName: ${error.message}") }
        )
    }

    /**
     * Send an ephemeral message via interaction hook.
     *
     * @param hook The interaction hook
     * @param message Message content
     * @param discordApi Optional DiscordApiWrapper
     * @param logger Logger for error messages
     */
    fun sendEphemeralMessage(
        hook: InteractionHook,
        message: String,
        discordApi: DiscordApiWrapper?,
        logger: Logger
    ) {
        discordApi?.sendMessage(hook, message, ephemeral = true)?.exceptionally { error ->
            logger.warning("Failed to send message: ${error.message}")
            null
        } ?: hook.sendMessage(message).setEphemeral(true).queue(
            null,
            { error -> logger.warning("Failed to send message: ${error.message}") }
        )
    }

    /**
     * Send an embed via interaction hook.
     *
     * @param hook The interaction hook
     * @param embed The embed to send
     * @param discordApi Optional DiscordApiWrapper
     * @param logger Logger for error messages
     */
    fun sendEmbed(
        hook: InteractionHook,
        embed: MessageEmbed,
        discordApi: DiscordApiWrapper?,
        logger: Logger
    ) {
        discordApi?.sendMessageEmbed(hook, embed)?.exceptionally { error ->
            logger.warning("Failed to send embed: ${error.message}")
            null
        } ?: hook.sendMessageEmbeds(embed).queue(
            null,
            { error -> logger.warning("Failed to send embed: ${error.message}") }
        )
    }

    /**
     * Send file attachments via interaction hook.
     *
     * @param hook The interaction hook
     * @param discordApi Optional DiscordApiWrapper
     * @param logger Logger for error messages
     * @param files The files to upload
     */
    fun sendFiles(
        hook: InteractionHook,
        discordApi: DiscordApiWrapper?,
        logger: Logger,
        vararg files: FileUpload
    ) {
        discordApi?.sendFiles(hook, *files)?.exceptionally { error ->
            logger.warning("Failed to send files: ${error.message}")
            null
        } ?: hook.sendFiles(*files).queue(
            null,
            { error -> logger.warning("Failed to send files: ${error.message}") }
        )
    }
}
