package io.github.darinc.amssync.discord.commands

import io.github.darinc.amssync.AMSSyncPlugin
import io.github.darinc.amssync.audit.ActorType
import io.github.darinc.amssync.audit.AuditAction
import io.github.darinc.amssync.audit.SecurityEvent
import io.github.darinc.amssync.discord.DiscordApiWrapper
import io.github.darinc.amssync.validation.Validators
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import org.bukkit.Bukkit
import java.awt.Color
import java.time.Instant

/**
 * Handles the /amswhitelist Discord slash command for server whitelist management
 */
class DiscordWhitelistCommand(
    private val plugin: AMSSyncPlugin,
    private val whitelistService: WhitelistService = WhitelistService(plugin.server)
) : SlashCommandHandler {

    override val commandName = "amswhitelist"

    private val discordApi: DiscordApiWrapper?
        get() = plugin.discord.apiWrapper

    override fun handle(event: SlashCommandInteractionEvent) {
        val actorName = "${event.user.name} (${event.user.id})"

        // Check admin permission
        if (event.member?.hasPermission(Permission.MANAGE_SERVER) != true) {
            plugin.auditLogger.logSecurityEvent(
                event = SecurityEvent.PERMISSION_DENIED,
                actor = actorName,
                actorType = ActorType.DISCORD_USER,
                details = mapOf("command" to "amswhitelist", "subcommand" to (event.subcommandName ?: "none"))
            )
            reply(event, "⛔ This command requires **Manage Server** permission.", ephemeral = true)
            return
        }

        when (event.subcommandName) {
            "add" -> handleAdd(event)
            "remove" -> handleRemove(event)
            "list" -> handleList(event)
            "check" -> handleCheck(event)
            else -> {
                reply(event, "Unknown subcommand.", ephemeral = true)
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

    private fun deferReply(event: SlashCommandInteractionEvent, ephemeral: Boolean = false) {
        discordApi?.deferReply(event, ephemeral)?.exceptionally { error ->
            plugin.logger.warning("Failed to defer reply: ${error.message}")
            null
        } ?: event.deferReply().setEphemeral(ephemeral).queue(
            null,
            { error -> plugin.logger.warning("Failed to defer reply: ${error.message}") }
        )
    }

    private fun sendEphemeralMessage(hook: InteractionHook, message: String) {
        discordApi?.sendMessage(hook, message, ephemeral = true)?.exceptionally { error ->
            plugin.logger.warning("Failed to send message: ${error.message}")
            null
        } ?: hook.sendMessage(message).setEphemeral(true).queue(
            null,
            { error -> plugin.logger.warning("Failed to send message: ${error.message}") }
        )
    }

    private fun sendMessage(hook: InteractionHook, message: String) {
        discordApi?.sendMessage(hook, message, ephemeral = false)?.exceptionally { error ->
            plugin.logger.warning("Failed to send message: ${error.message}")
            null
        } ?: hook.sendMessage(message).queue(
            null,
            { error -> plugin.logger.warning("Failed to send message: ${error.message}") }
        )
    }

    private fun sendEphemeralEmbed(hook: InteractionHook, embed: MessageEmbed) {
        discordApi?.sendMessageEmbed(hook, embed, ephemeral = true)?.exceptionally { error ->
            plugin.logger.warning("Failed to send embed: ${error.message}")
            null
        } ?: hook.sendMessageEmbeds(embed).setEphemeral(true).queue(
            null,
            { error -> plugin.logger.warning("Failed to send embed: ${error.message}") }
        )
    }

    private fun sendEmbed(hook: InteractionHook, embed: MessageEmbed) {
        discordApi?.sendMessageEmbed(hook, embed, ephemeral = false)?.exceptionally { error ->
            plugin.logger.warning("Failed to send embed: ${error.message}")
            null
        } ?: hook.sendMessageEmbeds(embed).queue(
            null,
            { error -> plugin.logger.warning("Failed to send embed: ${error.message}") }
        )
    }

    private fun handleAdd(event: SlashCommandInteractionEvent) {
        val actorName = "${event.user.name} (${event.user.id})"

        deferReply(event, ephemeral = true)

        val minecraftName = event.getOption("minecraft_username")?.asString

        if (minecraftName == null) {
            sendEphemeralMessage(event.hook, "❌ Missing required parameter: minecraft_username")
            return
        }

        // Validate Minecraft username
        if (!Validators.isValidMinecraftUsername(minecraftName)) {
            plugin.auditLogger.logSecurityEvent(
                event = SecurityEvent.INVALID_INPUT,
                actor = actorName,
                actorType = ActorType.DISCORD_USER,
                details = mapOf(
                    "field" to "minecraftUsername",
                    "value" to minecraftName,
                    "error" to Validators.getMinecraftUsernameError(minecraftName)
                )
            )
            sendEphemeralMessage(
                event.hook,
                "❌ **Invalid Minecraft Username**\n\n" +
                "${Validators.getMinecraftUsernameError(minecraftName)}\n\n" +
                "Minecraft usernames must be 3-16 characters and contain only letters, numbers, and underscores."
            )
            return
        }

        // Mojang lookup blocks on network I/O, so resolve the account off the main thread.
        // New players can't join an enforced whitelist, so we can't rely on server player data.
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            when (val lookup = whitelistService.lookupProfile(minecraftName)) {
                is ProfileLookup.NotFound -> {
                    logAddFailure(actorName, minecraftName, "account_not_found")
                    sendEphemeralMessage(
                        event.hook,
                        "❌ **Player Not Found**\n\n" +
                        "No Minecraft account named `$minecraftName` exists. Check the spelling."
                    )
                }
                is ProfileLookup.Failed -> {
                    plugin.logger.warning("Profile lookup failed for $minecraftName: ${lookup.reason}")
                    logAddFailure(actorName, minecraftName, "lookup_failed")
                    sendEphemeralMessage(
                        event.hook,
                        "⚠️ **Lookup Failed**\n\n" +
                        "Couldn't reach Mojang to look up `$minecraftName`. Try again in a moment."
                    )
                }
                is ProfileLookup.Found -> Bukkit.getScheduler().runTask(plugin, Runnable {
                    applyWhitelistAdd(event, actorName, minecraftName, lookup)
                })
            }
        })
    }

    private fun applyWhitelistAdd(
        event: SlashCommandInteractionEvent,
        actorName: String,
        requestedName: String,
        lookup: ProfileLookup.Found
    ) {
        try {
            when (val result = whitelistService.addToWhitelist(lookup)) {
                is WhitelistAddResult.AlreadyWhitelisted -> sendEphemeralMessage(
                    event.hook,
                    "⚠️ **${result.name}** is already whitelisted."
                )
                is WhitelistAddResult.Failed -> {
                    plugin.logger.warning("Whitelist add failed for $requestedName: ${result.reason}")
                    logAddFailure(actorName, requestedName, "uuid_mismatch")
                    sendEphemeralMessage(
                        event.hook,
                        "⚠️ **Error**\n\n" +
                        "The server and Mojang disagree on `${lookup.name}`'s UUID, so nothing was changed. " +
                        "Try `/whitelist add ${lookup.name}` from the console."
                    )
                }
                is WhitelistAddResult.Added -> {
                    plugin.auditLogger.logAdminAction(
                        action = AuditAction.WHITELIST_ADD,
                        actor = actorName,
                        actorType = ActorType.DISCORD_USER,
                        target = result.name,
                        success = true,
                        details = mapOf("uuid" to result.uuid.toString())
                    )

                    val embed = EmbedBuilder()
                        .setTitle("✅ Player Whitelisted")
                        .setColor(Color.GREEN)
                        .addField("Minecraft Username", result.name, false)
                        .addField("UUID", result.uuid.toString(), false)
                        .setFooter("Amazing Minecraft Server", null)
                        .setTimestamp(Instant.now())

                    if (!Bukkit.hasWhitelist()) {
                        embed.setDescription("⚠️ The server whitelist is currently **off**, so this has no effect yet.")
                    }

                    sendEphemeralEmbed(event.hook, embed.build())

                    plugin.logger.info("Whitelisted ${result.name} via Discord command by ${event.user.name}")
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("Unexpected error in Discord /amswhitelist add: ${e.message}")
            e.printStackTrace()
            sendEphemeralMessage(
                event.hook,
                "⚠️ **Error**\n\n" +
                "An unexpected error occurred: ${e.message}"
            )
        }
    }

    private fun logAddFailure(actorName: String, target: String, reason: String) {
        plugin.auditLogger.logAdminAction(
            action = AuditAction.WHITELIST_ADD,
            actor = actorName,
            actorType = ActorType.DISCORD_USER,
            target = target,
            success = false,
            details = mapOf("reason" to reason)
        )
    }

    private fun handleRemove(event: SlashCommandInteractionEvent) {
        val actorName = "${event.user.name} (${event.user.id})"

        deferReply(event, ephemeral = true)

        val minecraftName = event.getOption("minecraft_username")?.asString

        if (minecraftName == null) {
            sendEphemeralMessage(event.hook, "❌ Missing required parameter: minecraft_username")
            return
        }

        // Run on Bukkit main thread for safety
        Bukkit.getScheduler().runTask(plugin, Runnable {
            try {
                // Find player in whitelist by name
                val whitelistedPlayer = Bukkit.getWhitelistedPlayers()
                    .find { it.name?.equals(minecraftName, ignoreCase = true) == true }

                if (whitelistedPlayer == null) {
                    plugin.auditLogger.logAdminAction(
                        action = AuditAction.WHITELIST_REMOVE,
                        actor = actorName,
                        actorType = ActorType.DISCORD_USER,
                        target = minecraftName,
                        success = false,
                        details = mapOf("reason" to "not_whitelisted")
                    )
                    sendEphemeralMessage(
                        event.hook,
                        "⚠️ **$minecraftName** is not currently whitelisted."
                    )
                    return@Runnable
                }

                // Remove from whitelist
                whitelistedPlayer.isWhitelisted = false

                plugin.auditLogger.logAdminAction(
                    action = AuditAction.WHITELIST_REMOVE,
                    actor = actorName,
                    actorType = ActorType.DISCORD_USER,
                    target = whitelistedPlayer.name ?: minecraftName,
                    success = true,
                    details = mapOf("uuid" to whitelistedPlayer.uniqueId.toString())
                )

                val embed = EmbedBuilder()
                    .setTitle("🔓 Player Removed from Whitelist")
                    .setColor(Color.ORANGE)
                    .addField("Minecraft Username", whitelistedPlayer.name ?: minecraftName, false)
                    .addField("UUID", whitelistedPlayer.uniqueId.toString(), false)
                    .setFooter("Amazing Minecraft Server", null)
                    .setTimestamp(Instant.now())

                sendEphemeralEmbed(event.hook, embed.build())

                plugin.logger.info("Removed ${whitelistedPlayer.name} from whitelist via Discord command by ${event.user.name}")

            } catch (e: Exception) {
                plugin.logger.warning("Unexpected error in Discord /amswhitelist remove: ${e.message}")
                e.printStackTrace()
                sendEphemeralMessage(
                    event.hook,
                    "⚠️ **Error**\n\n" +
                    "An unexpected error occurred: ${e.message}"
                )
            }
        })
    }

    private fun handleList(event: SlashCommandInteractionEvent) {
        deferReply(event, ephemeral = false)

        Bukkit.getScheduler().runTask(plugin, Runnable {
            try {
                val whitelistedPlayers = Bukkit.getWhitelistedPlayers()
                    .filter { it.name != null }
                    .sortedBy { it.name?.lowercase() }

                if (whitelistedPlayers.isEmpty()) {
                    sendMessage(event.hook, "📋 No players are currently whitelisted.")
                    return@Runnable
                }

                val embed = EmbedBuilder()
                    .setTitle("📋 Whitelisted Players")
                    .setColor(Color.CYAN)
                    .setDescription("Total: ${whitelistedPlayers.size} player(s)")
                    .setTimestamp(Instant.now())
                    .setFooter("Amazing Minecraft Server", null)

                // Show players in chunks to fit embed limits
                val playerNames = whitelistedPlayers.take(50).mapNotNull { it.name }
                val chunkedNames = playerNames.chunked(15)

                chunkedNames.forEachIndexed { index, chunk ->
                    val fieldName = if (chunkedNames.size > 1) "Players (${index + 1})" else "Players"
                    embed.addField(fieldName, chunk.joinToString("\n") { "`$it`" }, true)
                }

                if (whitelistedPlayers.size > 50) {
                    embed.appendDescription("\n\n*Showing first 50 of ${whitelistedPlayers.size} players*")
                }

                sendEmbed(event.hook, embed.build())

            } catch (e: Exception) {
                plugin.logger.warning("Unexpected error in Discord /amswhitelist list: ${e.message}")
                e.printStackTrace()
                sendMessage(
                    event.hook,
                    "⚠️ **Error**\n\n" +
                    "An unexpected error occurred while fetching the whitelist."
                )
            }
        })
    }

    private fun handleCheck(event: SlashCommandInteractionEvent) {
        deferReply(event, ephemeral = true)

        val minecraftName = event.getOption("minecraft_username")?.asString

        if (minecraftName == null) {
            sendEphemeralMessage(event.hook, "❌ Missing required parameter: minecraft_username")
            return
        }

        Bukkit.getScheduler().runTask(plugin, Runnable {
            try {
                // Check if player is in whitelist
                val whitelistedPlayer = Bukkit.getWhitelistedPlayers()
                    .find { it.name?.equals(minecraftName, ignoreCase = true) == true }

                val embed = EmbedBuilder()
                    .setTitle("🔍 Whitelist Status Check")
                    .addField("Minecraft Username", minecraftName, false)
                    .setTimestamp(Instant.now())
                    .setFooter("Amazing Minecraft Server", null)

                if (whitelistedPlayer != null) {
                    embed.setColor(Color.GREEN)
                    embed.addField("Status", "✅ Whitelisted", true)
                    embed.addField("UUID", whitelistedPlayer.uniqueId.toString(), true)
                } else {
                    embed.setColor(Color.GRAY)
                    embed.addField("Status", "❌ Not Whitelisted", true)
                }

                sendEphemeralEmbed(event.hook, embed.build())

            } catch (e: Exception) {
                plugin.logger.warning("Unexpected error in Discord /amswhitelist check: ${e.message}")
                e.printStackTrace()
                sendEphemeralMessage(
                    event.hook,
                    "⚠️ **Error**\n\n" +
                    "An unexpected error occurred while checking whitelist status."
                )
            }
        })
    }
}
