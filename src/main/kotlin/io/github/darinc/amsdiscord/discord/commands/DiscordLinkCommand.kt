package io.github.darinc.amsdiscord.discord.commands

import io.github.darinc.amsdiscord.AmsDiscordPlugin
import io.github.darinc.amsdiscord.exceptions.InvalidDiscordIdException
import io.github.darinc.amsdiscord.exceptions.MappingNotFoundException
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.bukkit.Bukkit
import java.awt.Color
import java.time.Instant

/**
 * Handles the /amslink Discord slash command for admin user linking
 */
class DiscordLinkCommand(private val plugin: AmsDiscordPlugin) {

    fun handle(event: SlashCommandInteractionEvent) {
        // Check admin permission
        if (event.member?.hasPermission(Permission.MANAGE_SERVER) != true) {
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
        event.deferReply().setEphemeral(true).queue(
            null,
            { error -> plugin.logger.warning("Failed to defer reply for /amslink add: ${error.message}") }
        )

        val targetUser = event.getOption("user")?.asUser
        val minecraftName = event.getOption("minecraft_username")?.asString

        if (targetUser == null || minecraftName == null) {
            event.hook.sendMessage("❌ Missing required parameters.")
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
                val discordId = targetUser.id

                // Check if already linked
                val existingLink = plugin.userMappingService.getMinecraftUsername(discordId)
                if (existingLink != null) {
                    event.hook.sendMessage(
                        "⚠️ **${targetUser.name}** is already linked to `$existingLink`.\n" +
                        "Use `/amslink remove` first to unlink."
                    ).setEphemeral(true).queue(
                        null,
                        { error -> plugin.logger.warning("Failed to send already linked message: ${error.message}") }
                    )
                    return@Runnable
                }

                // Add the mapping
                plugin.userMappingService.addMapping(discordId, minecraftName)
                plugin.userMappingService.saveMappings()

                val embed = EmbedBuilder()
                    .setTitle("✅ User Linked Successfully")
                    .setColor(Color.GREEN)
                    .addField("Discord User", "${targetUser.name} (${targetUser.id})", false)
                    .addField("Minecraft Username", minecraftName, false)
                    .setFooter("Amazing Minecraft Server", null)
                    .setTimestamp(Instant.now())

                event.hook.sendMessageEmbeds(embed.build())
                    .setEphemeral(true)
                    .queue(
                        null,
                        { error -> plugin.logger.warning("Failed to send link success embed: ${error.message}") }
                    )

                plugin.logger.info("Linked Discord ${targetUser.name} (${discordId}) to Minecraft $minecraftName via Discord command")

            } catch (e: InvalidDiscordIdException) {
                plugin.logger.warning("Invalid Discord ID format: ${e.discordId}")
                event.hook.sendMessage(
                    "❌ **Invalid Discord ID**\n\n" +
                    "The Discord ID `${e.discordId}` has an invalid format.\n\n" +
                    "Discord IDs must be 17-19 digit numbers."
                ).setEphemeral(true).queue(
                    null,
                    { error -> plugin.logger.warning("Failed to send invalid ID error: ${error.message}") }
                )

            } catch (e: Exception) {
                plugin.logger.warning("Unexpected error in Discord /amslink add: ${e.message}")
                e.printStackTrace()
                event.hook.sendMessage(
                    "⚠️ **Error**\n\n" +
                    "An unexpected error occurred: ${e.message}\n\n" +
                    "Please contact an administrator."
                ).setEphemeral(true).queue(
                    null,
                    { error -> plugin.logger.warning("Failed to send error message: ${error.message}") }
                )
            }
        })
    }

    private fun handleRemove(event: SlashCommandInteractionEvent) {
        event.deferReply().setEphemeral(true).queue(
            null,
            { error -> plugin.logger.warning("Failed to defer reply for /amslink remove: ${error.message}") }
        )

        val targetUser = event.getOption("user")?.asUser

        if (targetUser == null) {
            event.hook.sendMessage("❌ Missing user parameter.")
                .setEphemeral(true)
                .queue(
                    null,
                    { error -> plugin.logger.warning("Failed to send missing user error: ${error.message}") }
                )
            return
        }

        Bukkit.getScheduler().runTask(plugin, Runnable {
            try {
                val discordId = targetUser.id
                val minecraftName = plugin.userMappingService.getMinecraftUsername(discordId)

                if (minecraftName == null) {
                    event.hook.sendMessage(
                        "⚠️ **${targetUser.name}** is not currently linked to any Minecraft account."
                    ).setEphemeral(true).queue(
                        null,
                        { error -> plugin.logger.warning("Failed to send not linked message: ${error.message}") }
                    )
                    return@Runnable
                }

                plugin.userMappingService.removeMappingByDiscordId(discordId)
                plugin.userMappingService.saveMappings()

                val embed = EmbedBuilder()
                    .setTitle("🔓 User Unlinked Successfully")
                    .setColor(Color.ORANGE)
                    .addField("Discord User", "${targetUser.name} (${targetUser.id})", false)
                    .addField("Previously Linked To", minecraftName, false)
                    .setFooter("Amazing Minecraft Server", null)
                    .setTimestamp(Instant.now())

                event.hook.sendMessageEmbeds(embed.build())
                    .setEphemeral(true)
                    .queue(
                        null,
                        { error -> plugin.logger.warning("Failed to send unlink success embed: ${error.message}") }
                    )

                plugin.logger.info("Unlinked Discord ${targetUser.name} (${discordId}) from Minecraft $minecraftName via Discord command")

            } catch (e: InvalidDiscordIdException) {
                plugin.logger.warning("Invalid Discord ID format: ${e.discordId}")
                event.hook.sendMessage(
                    "❌ **Invalid Discord ID**\n\n" +
                    "The Discord ID `${e.discordId}` has an invalid format."
                ).setEphemeral(true).queue(
                    null,
                    { error -> plugin.logger.warning("Failed to send invalid ID error: ${error.message}") }
                )

            } catch (e: Exception) {
                plugin.logger.warning("Unexpected error in Discord /amslink remove: ${e.message}")
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
            { error -> plugin.logger.warning("Failed to defer reply for /amslink list: ${error.message}") }
        )

        Bukkit.getScheduler().runTask(plugin, Runnable {
            try {
                val mappings = plugin.userMappingService.getAllMappings()

                if (mappings.isEmpty()) {
                    event.hook.sendMessage("📋 No user mappings configured yet.").queue(
                        null,
                        { error -> plugin.logger.warning("Failed to send no mappings message: ${error.message}") }
                    )
                    return@Runnable
                }

                val embed = EmbedBuilder()
                    .setTitle("📋 Discord-Minecraft User Links")
                    .setColor(Color.CYAN)
                    .setDescription("Total: ${mappings.size} linked user(s)")
                    .setTimestamp(Instant.now())
                    .setFooter("Amazing Minecraft Server", null)

                // Get Discord names for the linked users
                val guild = event.guild
                mappings.entries.take(25).forEach { (discordId, minecraftName) ->
                    val member = guild?.getMemberById(discordId)
                    val discordName = member?.effectiveName ?: member?.user?.name ?: "Unknown"
                    val displayName = if (member != null) {
                        "$discordName (${member.user.name})"
                    } else {
                        "User Left Server"
                    }

                    embed.addField(displayName, "→ `$minecraftName`", false)
                }

                if (mappings.size > 25) {
                    embed.appendDescription("\n\n*Showing first 25 of ${mappings.size} mappings*")
                }

                event.hook.sendMessageEmbeds(embed.build()).queue(
                    null,
                    { error -> plugin.logger.warning("Failed to send mappings list embed: ${error.message}") }
                )

            } catch (e: Exception) {
                plugin.logger.warning("Unexpected error in Discord /amslink list: ${e.message}")
                e.printStackTrace()
                event.hook.sendMessage(
                    "⚠️ **Error**\n\n" +
                    "An unexpected error occurred while fetching the list."
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
            { error -> plugin.logger.warning("Failed to defer reply for /amslink check: ${error.message}") }
        )

        val targetUser = event.getOption("user")?.asUser

        if (targetUser == null) {
            event.hook.sendMessage("❌ Missing user parameter.")
                .setEphemeral(true)
                .queue(
                    null,
                    { error -> plugin.logger.warning("Failed to send missing user error: ${error.message}") }
                )
            return
        }

        Bukkit.getScheduler().runTask(plugin, Runnable {
            try {
                val discordId = targetUser.id
                val minecraftName = plugin.userMappingService.getMinecraftUsername(discordId)

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

                event.hook.sendMessageEmbeds(embed.build())
                    .setEphemeral(true)
                    .queue(
                        null,
                        { error -> plugin.logger.warning("Failed to send check status embed: ${error.message}") }
                    )

            } catch (e: InvalidDiscordIdException) {
                plugin.logger.warning("Invalid Discord ID format: ${e.discordId}")
                event.hook.sendMessage(
                    "❌ **Invalid Discord ID**\n\n" +
                    "The Discord ID `${e.discordId}` has an invalid format."
                ).setEphemeral(true).queue(
                    null,
                    { error -> plugin.logger.warning("Failed to send invalid ID error: ${error.message}") }
                )

            } catch (e: Exception) {
                plugin.logger.warning("Unexpected error in Discord /amslink check: ${e.message}")
                e.printStackTrace()
                event.hook.sendMessage(
                    "⚠️ **Error**\n\n" +
                    "An unexpected error occurred while checking link status."
                ).setEphemeral(true).queue(
                    null,
                    { error -> plugin.logger.warning("Failed to send error message: ${error.message}") }
                )
            }
        })
    }
}
