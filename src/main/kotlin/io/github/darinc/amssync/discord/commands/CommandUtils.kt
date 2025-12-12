package io.github.darinc.amssync.discord.commands

import io.github.darinc.amssync.audit.ActorType
import io.github.darinc.amssync.audit.AuditLogger
import io.github.darinc.amssync.audit.SecurityEvent
import io.github.darinc.amssync.discord.DiscordApiWrapper
import io.github.darinc.amssync.discord.RateLimitResult
import io.github.darinc.amssync.discord.RateLimiter
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

    /**
     * Check rate limit for a Discord user and handle the response if rate limited.
     *
     * @param event The slash command event
     * @param rateLimiter Optional rate limiter (if null, always returns true)
     * @param auditLogger Audit logger for security events
     * @param discordApi Optional DiscordApiWrapper for circuit breaker protection
     * @param logger Logger for error messages
     * @return true if request is allowed, false if rate limited (response already sent)
     */
    fun checkRateLimitAndRespond(
        event: SlashCommandInteractionEvent,
        rateLimiter: RateLimiter?,
        auditLogger: AuditLogger,
        discordApi: DiscordApiWrapper?,
        logger: Logger
    ): Boolean {
        if (rateLimiter == null) return true

        val userId = event.user.id
        val userName = event.user.name

        when (val result = rateLimiter.checkRateLimit(userId)) {
            is RateLimitResult.Cooldown -> {
                auditLogger.logSecurityEvent(
                    event = SecurityEvent.RATE_LIMITED,
                    actor = "$userName ($userId)",
                    actorType = ActorType.DISCORD_USER,
                    details = mapOf(
                        "command" to event.name,
                        "reason" to "cooldown",
                        "remainingMs" to result.remainingMs
                    )
                )
                val message = "Please wait ${String.format("%.1f", result.remainingSeconds)} seconds before using another command."
                replyEphemeral(event, message, discordApi, logger)
                return false
            }
            is RateLimitResult.BurstLimited -> {
                auditLogger.logSecurityEvent(
                    event = SecurityEvent.RATE_LIMITED,
                    actor = "$userName ($userId)",
                    actorType = ActorType.DISCORD_USER,
                    details = mapOf(
                        "command" to event.name,
                        "reason" to "burst",
                        "retryAfterMs" to result.retryAfterMs
                    )
                )
                val seconds = String.format("%.0f", result.retryAfterSeconds)
                val message = "You've made too many requests. Please try again in $seconds seconds."
                replyEphemeral(event, message, discordApi, logger)
                return false
            }
            is RateLimitResult.Allowed -> return true
        }
    }

    /**
     * Reply to a slash command event with an ephemeral message.
     *
     * @param event The slash command event
     * @param message Message content
     * @param discordApi Optional DiscordApiWrapper
     * @param logger Logger for error messages
     */
    fun replyEphemeral(
        event: SlashCommandInteractionEvent,
        message: String,
        discordApi: DiscordApiWrapper?,
        logger: Logger
    ) {
        discordApi?.reply(event, message, ephemeral = true)?.exceptionally { error ->
            logger.warning("Failed to send reply: ${error.message}")
            null
        } ?: event.reply(message).setEphemeral(true).queue(
            null,
            { error -> logger.warning("Failed to send reply: ${error.message}") }
        )
    }

    /**
     * Reply to a slash command event.
     *
     * @param event The slash command event
     * @param message Message content
     * @param ephemeral Whether the message should be ephemeral
     * @param discordApi Optional DiscordApiWrapper
     * @param logger Logger for error messages
     */
    fun reply(
        event: SlashCommandInteractionEvent,
        message: String,
        ephemeral: Boolean,
        discordApi: DiscordApiWrapper?,
        logger: Logger
    ) {
        discordApi?.reply(event, message, ephemeral)?.exceptionally { error ->
            logger.warning("Failed to send reply: ${error.message}")
            null
        } ?: event.reply(message).setEphemeral(ephemeral).queue(
            null,
            { error -> logger.warning("Failed to send reply: ${error.message}") }
        )
    }
}
