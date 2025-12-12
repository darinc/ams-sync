package io.github.darinc.amssync.discord

import io.github.darinc.amssync.AMSSyncPlugin
import io.github.darinc.amssync.discord.commands.CommandUtils
import io.github.darinc.amssync.discord.commands.SlashCommandHandler
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

/**
 * Listens for Discord slash command interactions and routes them to appropriate handlers.
 *
 * Uses a registry pattern for extensible command routing. Commands are injected via constructor
 * rather than instantiated directly, enabling better testability and following DIP.
 *
 * @param plugin The plugin instance for accessing services
 * @param handlers Map of command names to their handlers
 */
class SlashCommandListener(
    private val plugin: AMSSyncPlugin,
    private val handlers: Map<String, SlashCommandHandler>
) : ListenerAdapter() {

    private val discordApi: DiscordApiWrapper?
        get() = plugin.services.discord.apiWrapper

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        val userName = event.user.name

        plugin.logger.info("Received slash command: ${event.name} from $userName")

        // Check rate limit (extracted to CommandUtils)
        if (!CommandUtils.checkRateLimitAndRespond(
                event,
                plugin.services.rateLimiter,
                plugin.services.auditLogger,
                discordApi,
                plugin.logger
            )) {
            return
        }

        // Route to handler using registry
        val handler = handlers[event.name]
        if (handler == null) {
            CommandUtils.replyEphemeral(event, "Unknown command!", discordApi, plugin.logger)
            return
        }

        handler.handle(event)
    }
}
