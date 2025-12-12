package io.github.darinc.amssync.discord.commands

import io.github.darinc.amssync.AMSSyncPlugin
import io.github.darinc.amssync.audit.ActorType
import io.github.darinc.amssync.audit.AuditAction
import io.github.darinc.amssync.audit.SecurityEvent
import io.github.darinc.amssync.discord.DiscordApiWrapper
import io.github.darinc.amssync.exceptions.InvalidDiscordIdException
import io.github.darinc.amssync.exceptions.MappingNotFoundException
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
 * Handles the /amslink Discord slash command for admin user linking
 */
class DiscordLinkCommand(private val plugin: AMSSyncPlugin) {

    private val discordApi: DiscordApiWrapper?
        get() = plugin.services.discord.apiWrapper

    fun handle(event: SlashCommandInteractionEvent) {
        val actorName = "${event.user.name} (${event.user.id})"

        // Check admin permission
        if (event.member?.hasPermission(Permission.MANAGE_SERVER) != true) {
            plugin.services.auditLogger.logSecurityEvent(
                event = SecurityEvent.PERMISSION_DENIED,
                actor = actorName,
                actorType = ActorType.DISCORD_USER,
                details = mapOf("command" to "amssync", "subcommand" to (event.subcommandName ?: "none"))
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

        val targetUser = event.getOption("user")?.asUser
        val minecraftName = event.getOption("minecraft_username")?.asString

        if (targetUser == null || minecraftName == null) {
            sendEphemeralMessage(event.hook, "❌ Missing required parameters.")
            return
        }

        // Validate Minecraft username
        if (!Validators.isValidMinecraftUsername(minecraftName)) {
            plugin.services.auditLogger.logSecurityEvent(
                event = SecurityEvent.INVALID_INPUT,
                actor = actorName,
                actorType = ActorType.DISCORD_USER,
                details = mapOf("field" to "minecraftUsername", "value" to minecraftName, "error" to Validators.getMinecraftUsernameError(minecraftName))
            )
            sendEphemeralMessage(
                event.hook,
                "❌ **Invalid Minecraft Username**\n\n" +
                "${Validators.getMinecraftUsernameError(minecraftName)}\n\n" +
                "Minecraft usernames must be 3-16 characters and contain only letters, numbers, and underscores."
            )
            return
        }

        // Run on Bukkit main thread for safety
        Bukkit.getScheduler().runTask(plugin, Runnable {
            try {
                val discordId = targetUser.id

                // Check if already linked
                val existingLink = plugin.services.userMappingService.getMinecraftUsername(discordId)
                if (existingLink != null) {
                    sendEphemeralMessage(
                        event.hook,
                        "⚠️ **${targetUser.name}** is already linked to `$existingLink`.\n" +
                        "Use `/amssync remove` first to unlink."
                    )
                    return@Runnable
                }

                // Add the mapping
                plugin.services.userMappingService.addMapping(discordId, minecraftName)
                plugin.services.userMappingService.saveMappings()

                plugin.services.auditLogger.logAdminAction(
                    action = AuditAction.LINK_USER,
                    actor = actorName,
                    actorType = ActorType.DISCORD_USER,
                    target = minecraftName,
                    success = true,
                    details = mapOf("discordId" to discordId, "discordName" to targetUser.name)
                )

                val embed = EmbedBuilder()
                    .setTitle("✅ User Linked Successfully")
                    .setColor(Color.GREEN)
                    .addField("Discord User", "${targetUser.name} (${targetUser.id})", false)
                    .addField("Minecraft Username", minecraftName, false)
                    .setFooter("Amazing Minecraft Server", null)
                    .setTimestamp(Instant.now())

                sendEphemeralEmbed(event.hook, embed.build())

                plugin.logger.info("Linked Discord ${targetUser.name} (${discordId}) to Minecraft $minecraftName via Discord command")

            } catch (e: InvalidDiscordIdException) {
                plugin.logger.warning("Invalid Discord ID format: ${e.discordId}")
                sendEphemeralMessage(
                    event.hook,
                    "❌ **Invalid Discord ID**\n\n" +
                    "The Discord ID `${e.discordId}` has an invalid format.\n\n" +
                    "Discord IDs must be 17-19 digit numbers."
                )

            } catch (e: Exception) {
                plugin.logger.warning("Unexpected error in Discord /amslink add: ${e.message}")
                e.printStackTrace()
                sendEphemeralMessage(
                    event.hook,
                    "⚠️ **Error**\n\n" +
                    "An unexpected error occurred: ${e.message}\n\n" +
                    "Please contact an administrator."
                )
            }
        })
    }

    private fun handleRemove(event: SlashCommandInteractionEvent) {
        val actorName = "${event.user.name} (${event.user.id})"

        deferReply(event, ephemeral = true)

        val targetUser = event.getOption("user")?.asUser

        if (targetUser == null) {
            sendEphemeralMessage(event.hook, "❌ Missing user parameter.")
            return
        }

        Bukkit.getScheduler().runTask(plugin, Runnable {
            try {
                val discordId = targetUser.id
                val minecraftName = plugin.services.userMappingService.getMinecraftUsername(discordId)

                if (minecraftName == null) {
                    plugin.services.auditLogger.logAdminAction(
                        action = AuditAction.UNLINK_USER,
                        actor = actorName,
                        actorType = ActorType.DISCORD_USER,
                        target = targetUser.name,
                        success = false,
                        details = mapOf("discordId" to discordId, "reason" to "not_linked")
                    )
                    sendEphemeralMessage(
                        event.hook,
                        "⚠️ **${targetUser.name}** is not currently linked to any Minecraft account."
                    )
                    return@Runnable
                }

                plugin.services.userMappingService.removeMappingByDiscordId(discordId)
                plugin.services.userMappingService.saveMappings()

                plugin.services.auditLogger.logAdminAction(
                    action = AuditAction.UNLINK_USER,
                    actor = actorName,
                    actorType = ActorType.DISCORD_USER,
                    target = minecraftName,
                    success = true,
                    details = mapOf("discordId" to discordId, "discordName" to targetUser.name)
                )

                val embed = EmbedBuilder()
                    .setTitle("🔓 User Unlinked Successfully")
                    .setColor(Color.ORANGE)
                    .addField("Discord User", "${targetUser.name} (${targetUser.id})", false)
                    .addField("Previously Linked To", minecraftName, false)
                    .setFooter("Amazing Minecraft Server", null)
                    .setTimestamp(Instant.now())

                sendEphemeralEmbed(event.hook, embed.build())

                plugin.logger.info("Unlinked Discord ${targetUser.name} (${discordId}) from Minecraft $minecraftName via Discord command")

            } catch (e: InvalidDiscordIdException) {
                plugin.logger.warning("Invalid Discord ID format: ${e.discordId}")
                sendEphemeralMessage(
                    event.hook,
                    "❌ **Invalid Discord ID**\n\n" +
                    "The Discord ID `${e.discordId}` has an invalid format."
                )

            } catch (e: Exception) {
                plugin.logger.warning("Unexpected error in Discord /amslink remove: ${e.message}")
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

        // First, fetch all Discord users asynchronously, then build the embed
        val mappings = plugin.services.userMappingService.getAllMappings()

        if (mappings.isEmpty()) {
            sendMessage(event.hook, "📋 No user mappings configured yet.")
            return
        }

        val jda = event.jda
        val guild = event.guild
        val entriesToShow = mappings.entries.take(25)

        // Collect user info asynchronously
        // Note: getMemberById only checks the cache, which may not have all members loaded.
        // We must use retrieveMemberById to make an API call for accurate results.
        val userFutures = entriesToShow.map { (discordId, minecraftName) ->
            if (guild != null) {
                // Try to retrieve as guild member first (gives us nickname info)
                guild.retrieveMemberById(discordId).submit().handle { member, memberError ->
                    if (memberError == null && member != null) {
                        // User is still in the guild
                        Triple(discordId, minecraftName, "${member.effectiveName} (${member.user.name})")
                    } else {
                        // Member retrieval failed - try to get basic user info
                        // This happens when user left the server
                        try {
                            val user = jda.retrieveUserById(discordId).complete()
                            Triple(discordId, minecraftName, "${user.name} (left server)")
                        } catch (e: Exception) {
                            Triple(discordId, minecraftName, "Unknown ($discordId)")
                        }
                    }
                }
            } else {
                // No guild context - just get user info
                jda.retrieveUserById(discordId).submit().handle { user, error ->
                    val discordName = if (error != null || user == null) {
                        "Unknown ($discordId)"
                    } else {
                        user.name
                    }
                    Triple(discordId, minecraftName, discordName)
                }
            }
        }

        // Wait for all user lookups to complete
        @Suppress("SpreadOperator") // Required for CompletableFuture.allOf() varargs
        java.util.concurrent.CompletableFuture.allOf(*userFutures.toTypedArray()).thenRun {
            Bukkit.getScheduler().runTask(plugin, Runnable {
                try {
                    val resolvedEntries = userFutures.map { it.join() }

                    val embed = EmbedBuilder()
                        .setTitle("📋 Discord-Minecraft User Links")
                        .setColor(Color.CYAN)
                        .setTimestamp(Instant.now())
                        .setFooter("Amazing Minecraft Server", null)

                    // Build table-style description
                    val tableBuilder = StringBuilder()
                    tableBuilder.append("**Total:** ${mappings.size} linked user(s)\n\n")
                    tableBuilder.append("```\n")
                    tableBuilder.append(String.format("%-25s │ %-16s%n", "Discord", "Minecraft"))
                    tableBuilder.append("──────────────────────────┼──────────────────\n")

                    resolvedEntries.forEach { (_, minecraftName, discordName) ->
                        // Truncate long names to fit table
                        val truncatedDiscord = if (discordName.length > 25) {
                            discordName.take(22) + "..."
                        } else {
                            discordName
                        }
                        val truncatedMinecraft = if (minecraftName.length > 16) {
                            minecraftName.take(13) + "..."
                        } else {
                            minecraftName
                        }
                        tableBuilder.append(String.format("%-25s │ %-16s%n", truncatedDiscord, truncatedMinecraft))
                    }

                    tableBuilder.append("```")

                    if (mappings.size > 25) {
                        tableBuilder.append("\n*Showing first 25 of ${mappings.size} mappings*")
                    }

                    embed.setDescription(tableBuilder.toString())

                    sendEmbed(event.hook, embed.build())

                } catch (e: Exception) {
                    plugin.logger.warning("Unexpected error in Discord /amslink list: ${e.message}")
                    e.printStackTrace()
                    sendMessage(
                        event.hook,
                        "⚠️ **Error**\n\n" +
                        "An unexpected error occurred while fetching the list."
                    )
                }
            })
        }
    }

    private fun handleCheck(event: SlashCommandInteractionEvent) {
        deferReply(event, ephemeral = true)

        val targetUser = event.getOption("user")?.asUser

        if (targetUser == null) {
            sendEphemeralMessage(event.hook, "❌ Missing user parameter.")
            return
        }

        Bukkit.getScheduler().runTask(plugin, Runnable {
            try {
                val discordId = targetUser.id
                val minecraftName = plugin.services.userMappingService.getMinecraftUsername(discordId)

                val embed = EmbedBuilder()
                    .setTitle("🔍 Link Status Check")
                    .addField("Discord User", "${targetUser.name} (${targetUser.id})", false)
                    .setTimestamp(Instant.now())
                    .setFooter("Amazing Minecraft Server", null)

                if (minecraftName != null) {
                    embed.setColor(Color.GREEN)
                    embed.addField("Status", "✅ Linked", true)
                    embed.addField("Minecraft Username", minecraftName, true)
                } else {
                    embed.setColor(Color.GRAY)
                    embed.addField("Status", "❌ Not Linked", true)
                    embed.addField("Minecraft Username", "None", true)
                }

                sendEphemeralEmbed(event.hook, embed.build())

            } catch (e: InvalidDiscordIdException) {
                plugin.logger.warning("Invalid Discord ID format: ${e.discordId}")
                sendEphemeralMessage(
                    event.hook,
                    "❌ **Invalid Discord ID**\n\n" +
                    "The Discord ID `${e.discordId}` has an invalid format."
                )

            } catch (e: Exception) {
                plugin.logger.warning("Unexpected error in Discord /amslink check: ${e.message}")
                e.printStackTrace()
                sendEphemeralMessage(
                    event.hook,
                    "⚠️ **Error**\n\n" +
                    "An unexpected error occurred while checking link status."
                )
            }
        })
    }
}
