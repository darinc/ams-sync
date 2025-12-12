package io.github.darinc.amssync.discord

import io.github.darinc.amssync.AMSSyncPlugin
import io.github.darinc.amssync.audit.ActorType
import io.github.darinc.amssync.audit.SecurityEvent
import io.github.darinc.amssync.discord.commands.AmsStatsCommand
import io.github.darinc.amssync.discord.commands.AmsTopCommand
import io.github.darinc.amssync.discord.commands.DiscordLinkCommand
import io.github.darinc.amssync.discord.commands.DiscordWhitelistCommand
import io.github.darinc.amssync.discord.commands.McStatsCommand
import io.github.darinc.amssync.discord.commands.McTopCommand
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

/**
 * Listens for Discord slash command interactions
 */
class SlashCommandListener(
    private val plugin: AMSSyncPlugin,
    private val amsStatsCommand: AmsStatsCommand?,
    private val amsTopCommand: AmsTopCommand?,
    private val whitelistEnabled: Boolean = true
) : ListenerAdapter() {

    private val mcStatsCommand = McStatsCommand(plugin)
    private val mcTopCommand = McTopCommand(plugin)
    private val discordLinkCommand = DiscordLinkCommand(plugin)
    private val discordWhitelistCommand: DiscordWhitelistCommand? = if (whitelistEnabled) {
        DiscordWhitelistCommand(plugin)
    } else {
        null
    }

    private val discordApi: DiscordApiWrapper?
        get() = plugin.services.discord.apiWrapper

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        val userId = event.user.id
        val userName = event.user.name

        plugin.logger.info("Received slash command: ${event.name} from $userName")

        // Check rate limit
        val rateLimiter = plugin.services.rateLimiter
        if (rateLimiter != null) {
            when (val result = rateLimiter.checkRateLimit(userId)) {
                is RateLimitResult.Cooldown -> {
                    plugin.services.auditLogger.logSecurityEvent(
                        event = SecurityEvent.RATE_LIMITED,
                        actor = "$userName ($userId)",
                        actorType = ActorType.DISCORD_USER,
                        details = mapOf(
                            "command" to event.name,
                            "reason" to "cooldown",
                            "remainingMs" to result.remainingMs
                        )
                    )
                    reply(event, "Please wait ${String.format("%.1f", result.remainingSeconds)} seconds before using another command.", ephemeral = true)
                    return
                }
                is RateLimitResult.BurstLimited -> {
                    plugin.services.auditLogger.logSecurityEvent(
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
                    reply(event, "You've made too many requests. Please try again in $seconds seconds.", ephemeral = true)
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
            "amsstats" -> {
                if (amsStatsCommand != null) {
                    amsStatsCommand.handle(event)
                } else {
                    reply(event, "Image cards are not enabled. Use `/mcstats` instead.", ephemeral = true)
                }
            }
            "amstop" -> {
                if (amsTopCommand != null) {
                    amsTopCommand.handle(event)
                } else {
                    reply(event, "Image cards are not enabled. Use `/mctop` instead.", ephemeral = true)
                }
            }
            "amssync" -> discordLinkCommand.handle(event)
            "amswhitelist" -> {
                if (discordWhitelistCommand != null) {
                    discordWhitelistCommand.handle(event)
                } else {
                    reply(event, "Whitelist management is not enabled.", ephemeral = true)
                }
            }
            else -> {
                reply(event, "Unknown command!", ephemeral = true)
            }
        }
    }

    private fun reply(event: SlashCommandInteractionEvent, message: String, ephemeral: Boolean = false) {
        discordApi?.reply(event, message, ephemeral)?.exceptionally { error ->
            plugin.logger.warning("Failed to send reply: ${error.message}")
            null
        } ?: event.reply(message).setEphemeral(ephemeral).queue(
            null,
            { error -> plugin.logger.warning("Failed to send reply: ${error.message}") }
        )
    }
}
