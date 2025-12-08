package io.github.darinc.amssync.discord

import io.github.darinc.amssync.AMSSyncPlugin
import io.github.darinc.amssync.audit.ActorType
import io.github.darinc.amssync.audit.SecurityEvent
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
        val userId = event.user.id
        val userName = event.user.name

        plugin.logger.info("Received slash command: ${event.name} from $userName")

        // Check rate limit
        val rateLimiter = plugin.rateLimiter
        if (rateLimiter != null) {
            when (val result = rateLimiter.checkRateLimit(userId)) {
                is RateLimitResult.Cooldown -> {
                    plugin.auditLogger.logSecurityEvent(
                        event = SecurityEvent.RATE_LIMITED,
                        actor = "$userName ($userId)",
                        actorType = ActorType.DISCORD_USER,
                        details = mapOf(
                            "command" to event.name,
                            "reason" to "cooldown",
                            "remainingMs" to result.remainingMs
                        )
                    )
                    event.reply("Please wait ${String.format("%.1f", result.remainingSeconds)} seconds before using another command.")
                        .setEphemeral(true)
                        .queue()
                    return
                }
                is RateLimitResult.BurstLimited -> {
                    plugin.auditLogger.logSecurityEvent(
                        event = SecurityEvent.RATE_LIMITED,
                        actor = "$userName ($userId)",
                        actorType = ActorType.DISCORD_USER,
                        details = mapOf(
                            "command" to event.name,
                            "reason" to "burst",
                            "retryAfterMs" to result.retryAfterMs
                        )
                    )
                    event.reply("You've made too many requests. Please try again in ${String.format("%.0f", result.retryAfterSeconds)} seconds.")
                        .setEphemeral(true)
                        .queue()
                    return
                }
                is RateLimitResult.Allowed -> {
                    // Continue processing
                }
            }
        }

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
