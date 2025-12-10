package io.github.darinc.amssync.discord.commands

import io.github.darinc.amssync.AMSSyncPlugin
import io.github.darinc.amssync.audit.ActorType
import io.github.darinc.amssync.audit.AuditAction
import io.github.darinc.amssync.audit.SecurityEvent
import io.github.darinc.amssync.validation.Validators
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.bukkit.Bukkit
import java.awt.Color
import java.time.Instant

/**
 * Handles the /amswhitelist Discord slash command for server whitelist management
 */
class DiscordWhitelistCommand(private val plugin: AMSSyncPlugin) {

    fun handle(event: SlashCommandInteractionEvent) {
        val actorName = "${event.user.name} (${event.user.id})"

        // Check admin permission
        if (event.member?.hasPermission(Permission.MANAGE_SERVER) != true) {
            plugin.auditLogger.logSecurityEvent(
                event = SecurityEvent.PERMISSION_DENIED,
                actor = actorName,
                actorType = ActorType.DISCORD_USER,
                details = mapOf("command" to "amswhitelist", "subcommand" to (event.subcommandName ?: "none"))
            )
            event.reply("⛔ This command requires **Manage Server** permission.")
                .setEphemeral(true)
                .queue(
                    null,
                    { error -> plugin.logger.warning("Failed to send permission error: ${error.message}") }
                )
            return
        }

        when (event.subcommandName) {
            "add" -> handleAdd(event)
            "remove" -> handleRemove(event)
            "list" -> handleList(event)
            "check" -> handleCheck(event)
            else -> {
                event.reply("Unknown subcommand.")
                    .setEphemeral(true)
                    .queue(
                        null,
                        { error -> plugin.logger.warning("Failed to send unknown subcommand error: ${error.message}") }
                    )
            }
        }
    }

    private fun handleAdd(event: SlashCommandInteractionEvent) {
        val actorName = "${event.user.name} (${event.user.id})"

        event.deferReply().setEphemeral(true).queue(
            null,
            { error -> plugin.logger.warning("Failed to defer reply for /amswhitelist add: ${error.message}") }
        )

        val minecraftName = event.getOption("minecraft_username")?.asString

        if (minecraftName == null) {
            event.hook.sendMessage("❌ Missing required parameter: minecraft_username")
                .setEphemeral(true)
                .queue(
                    null,
                    { error -> plugin.logger.warning("Failed to send missing params error: ${error.message}") }
                )
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
            event.hook.sendMessage(
                "❌ **Invalid Minecraft Username**\n\n" +
                "${Validators.getMinecraftUsernameError(minecraftName)}\n\n" +
                "Minecraft usernames must be 3-16 characters and contain only letters, numbers, and underscores."
            ).setEphemeral(true).queue(
                null,
                { error -> plugin.logger.warning("Failed to send validation error: ${error.message}") }
            )
            return
        }

        // Run on Bukkit main thread for safety
        Bukkit.getScheduler().runTask(plugin, Runnable {
            try {
                // Find player by name (search through offline players)
                val offlinePlayer = findOfflinePlayerByName(minecraftName)

                if (offlinePlayer == null || !offlinePlayer.hasPlayedBefore()) {
                    plugin.auditLogger.logAdminAction(
                        action = AuditAction.WHITELIST_ADD,
                        actor = actorName,
                        actorType = ActorType.DISCORD_USER,
                        target = minecraftName,
                        success = false,
                        details = mapOf("reason" to "player_never_joined")
                    )
                    event.hook.sendMessage(
                        "❌ **Player Not Found**\n\n" +
                        "Player `$minecraftName` has never joined this server.\n" +
                        "Only players who have previously joined can be whitelisted."
                    ).setEphemeral(true).queue(
                        null,
                        { error -> plugin.logger.warning("Failed to send player not found error: ${error.message}") }
                    )
                    return@Runnable
                }

                // Check if already whitelisted
                if (offlinePlayer.isWhitelisted) {
                    event.hook.sendMessage(
                        "⚠️ **${offlinePlayer.name}** is already whitelisted."
                    ).setEphemeral(true).queue(
                        null,
                        { error -> plugin.logger.warning("Failed to send already whitelisted message: ${error.message}") }
                    )
                    return@Runnable
                }

                // Add to whitelist
                offlinePlayer.isWhitelisted = true

                plugin.auditLogger.logAdminAction(
                    action = AuditAction.WHITELIST_ADD,
                    actor = actorName,
                    actorType = ActorType.DISCORD_USER,
                    target = offlinePlayer.name ?: minecraftName,
                    success = true,
                    details = mapOf("uuid" to offlinePlayer.uniqueId.toString())
                )

                val embed = EmbedBuilder()
                    .setTitle("✅ Player Whitelisted")
                    .setColor(Color.GREEN)
                    .addField("Minecraft Username", offlinePlayer.name ?: minecraftName, false)
                    .addField("UUID", offlinePlayer.uniqueId.toString(), false)
                    .setFooter("Amazing Minecraft Server", null)
                    .setTimestamp(Instant.now())

                event.hook.sendMessageEmbeds(embed.build())
                    .setEphemeral(true)
                    .queue(
                        null,
                        { error -> plugin.logger.warning("Failed to send whitelist success embed: ${error.message}") }
                    )

                plugin.logger.info("Whitelisted ${offlinePlayer.name} via Discord command by ${event.user.name}")

            } catch (e: Exception) {
                plugin.logger.warning("Unexpected error in Discord /amswhitelist add: ${e.message}")
                e.printStackTrace()
                event.hook.sendMessage(
                    "⚠️ **Error**\n\n" +
                    "An unexpected error occurred: ${e.message}"
                ).setEphemeral(true).queue(
                    null,
                    { error -> plugin.logger.warning("Failed to send error message: ${error.message}") }
                )
            }
        })
    }

    private fun handleRemove(event: SlashCommandInteractionEvent) {
        val actorName = "${event.user.name} (${event.user.id})"

        event.deferReply().setEphemeral(true).queue(
            null,
            { error -> plugin.logger.warning("Failed to defer reply for /amswhitelist remove: ${error.message}") }
        )

        val minecraftName = event.getOption("minecraft_username")?.asString

        if (minecraftName == null) {
            event.hook.sendMessage("❌ Missing required parameter: minecraft_username")
                .setEphemeral(true)
                .queue(
                    null,
                    { error -> plugin.logger.warning("Failed to send missing params error: ${error.message}") }
                )
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
                    event.hook.sendMessage(
                        "⚠️ **$minecraftName** is not currently whitelisted."
                    ).setEphemeral(true).queue(
                        null,
                        { error -> plugin.logger.warning("Failed to send not whitelisted message: ${error.message}") }
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

                event.hook.sendMessageEmbeds(embed.build())
                    .setEphemeral(true)
                    .queue(
                        null,
                        { error -> plugin.logger.warning("Failed to send remove success embed: ${error.message}") }
                    )

                plugin.logger.info("Removed ${whitelistedPlayer.name} from whitelist via Discord command by ${event.user.name}")

            } catch (e: Exception) {
                plugin.logger.warning("Unexpected error in Discord /amswhitelist remove: ${e.message}")
                e.printStackTrace()
                event.hook.sendMessage(
                    "⚠️ **Error**\n\n" +
                    "An unexpected error occurred: ${e.message}"
                ).setEphemeral(true).queue(
                    null,
                    { error -> plugin.logger.warning("Failed to send error message: ${error.message}") }
                )
            }
        })
    }

    private fun handleList(event: SlashCommandInteractionEvent) {
        event.deferReply().queue(
            null,
            { error -> plugin.logger.warning("Failed to defer reply for /amswhitelist list: ${error.message}") }
        )

        Bukkit.getScheduler().runTask(plugin, Runnable {
            try {
                val whitelistedPlayers = Bukkit.getWhitelistedPlayers()
                    .filter { it.name != null }
                    .sortedBy { it.name?.lowercase() }

                if (whitelistedPlayers.isEmpty()) {
                    event.hook.sendMessage("📋 No players are currently whitelisted.").queue(
                        null,
                        { error -> plugin.logger.warning("Failed to send empty whitelist message: ${error.message}") }
                    )
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

                event.hook.sendMessageEmbeds(embed.build()).queue(
                    null,
                    { error -> plugin.logger.warning("Failed to send whitelist embed: ${error.message}") }
                )

            } catch (e: Exception) {
                plugin.logger.warning("Unexpected error in Discord /amswhitelist list: ${e.message}")
                e.printStackTrace()
                event.hook.sendMessage(
                    "⚠️ **Error**\n\n" +
                    "An unexpected error occurred while fetching the whitelist."
                ).queue(
                    null,
                    { error -> plugin.logger.warning("Failed to send error message: ${error.message}") }
                )
            }
        })
    }

    private fun handleCheck(event: SlashCommandInteractionEvent) {
        event.deferReply().setEphemeral(true).queue(
            null,
            { error -> plugin.logger.warning("Failed to defer reply for /amswhitelist check: ${error.message}") }
        )

        val minecraftName = event.getOption("minecraft_username")?.asString

        if (minecraftName == null) {
            event.hook.sendMessage("❌ Missing required parameter: minecraft_username")
                .setEphemeral(true)
                .queue(
                    null,
                    { error -> plugin.logger.warning("Failed to send missing params error: ${error.message}") }
                )
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

                event.hook.sendMessageEmbeds(embed.build())
                    .setEphemeral(true)
                    .queue(
                        null,
                        { error -> plugin.logger.warning("Failed to send check status embed: ${error.message}") }
                    )

            } catch (e: Exception) {
                plugin.logger.warning("Unexpected error in Discord /amswhitelist check: ${e.message}")
                e.printStackTrace()
                event.hook.sendMessage(
                    "⚠️ **Error**\n\n" +
                    "An unexpected error occurred while checking whitelist status."
                ).setEphemeral(true).queue(
                    null,
                    { error -> plugin.logger.warning("Failed to send error message: ${error.message}") }
                )
            }
        })
    }

    /**
     * Find an offline player by name. Searches online players first, then offline players.
     * Note: Bukkit.getOfflinePlayer(name) creates a new player if not found, so we must
     * iterate through existing players instead.
     */
    private fun findOfflinePlayerByName(name: String): org.bukkit.OfflinePlayer? {
        // Check online players first (most efficient)
        Bukkit.getOnlinePlayers().find { it.name.equals(name, ignoreCase = true) }?.let {
            return it
        }

        // Search through offline players
        return Bukkit.getOfflinePlayers().find { it.name?.equals(name, ignoreCase = true) == true }
    }
}
