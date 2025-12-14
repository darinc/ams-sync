package io.github.darinc.amssync.discord

import io.github.darinc.amssync.audit.AuditLogger
import io.github.darinc.amssync.discord.commands.CommandUtils
import io.github.darinc.amssync.discord.commands.SlashCommandHandler
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import java.util.logging.Logger

/**
 * Listens for Discord slash command interactions and routes them to appropriate handlers.
 *
 * Uses a registry pattern for extensible command routing. Commands are injected via constructor
 * rather than instantiated directly, enabling better testability and following DIP.
 *
 * @param discordApiWrapper Discord API wrapper for protected API calls
 * @param rateLimiter Rate limiter for command throttling
 * @param auditLogger Audit logger for security events
 * @param logger Logger for command events
 * @param handlers Map of command names to their handlers
 */
class SlashCommandListener(
    private val discordApiWrapper: DiscordApiWrapper?,
    private val rateLimiter: RateLimiter?,
    private val auditLogger: AuditLogger,
    private val logger: Logger,
    private val handlers: Map<String, SlashCommandHandler>
) : ListenerAdapter() {

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        val userName = event.user.name

        logger.info("Received slash command: ${event.name} from $userName")

        // Check rate limit (extracted to CommandUtils)
        if (!CommandUtils.checkRateLimitAndRespond(
                event,
                rateLimiter,
                auditLogger,
                discordApiWrapper,
                logger
            )) {
            return
        }

        // Route to handler using registry
        val handler = handlers[event.name]
        if (handler == null) {
            CommandUtils.replyEphemeral(event, "Unknown command!", discordApiWrapper, logger)
            return
        }

        handler.handle(event)
    }
}
