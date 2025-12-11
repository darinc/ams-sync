package io.github.darinc.amssync.discord.commands

import io.github.darinc.amssync.AMSSyncPlugin
import io.github.darinc.amssync.discord.DiscordApiWrapper
import io.github.darinc.amssync.exceptions.InvalidSkillException
import io.github.darinc.amssync.exceptions.PlayerDataNotFoundException
import io.github.darinc.amssync.validation.Validators
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import org.bukkit.Bukkit
import java.awt.Color
import java.time.Instant

/**
 * Handles the /mcstats slash command
 */
class McStatsCommand(private val plugin: AMSSyncPlugin) {

    private val discordApi: DiscordApiWrapper?
        get() = plugin.discordApiWrapper

    fun handle(event: SlashCommandInteractionEvent) {
        // Defer reply immediately to avoid timeout
        discordApi?.deferReply(event)?.exceptionally { error ->
            plugin.logger.warning("Failed to defer reply for /mcstats: ${error.message}")
            null
        } ?: event.deferReply().queue(
            null,
            { error -> plugin.logger.warning("Failed to defer reply for /mcstats: ${error.message}") }
        )

        val usernameOption = event.getOption("username")?.asString
        val skillOption = event.getOption("skill")?.asString
        val invokerDiscordId = event.user.id
        val invokerTag = event.user.name

        // Run on main Bukkit thread
        Bukkit.getScheduler().runTask(plugin, Runnable {
            try {
                // Resolve the target Minecraft username (self or other player)
                val mcUsername = resolveMinecraftUsername(usernameOption, invokerDiscordId)

                if (skillOption != null) {
                    // Show specific skill
                    handleSpecificSkill(event, mcUsername, skillOption, invokerTag)
                } else {
                    // Show all skills
                    handleAllSkills(event, mcUsername, invokerTag)
                }

            } catch (e: IllegalArgumentException) {
                // Username resolution errors (not linked, Discord user not found, etc.)
                plugin.logger.fine("Username resolution failed: ${e.message}")
                sendEphemeralMessage(event.hook, e.message ?: "Invalid username")

            } catch (e: PlayerDataNotFoundException) {
                plugin.logger.fine("Player data not found: ${e.message}")
                sendEphemeralMessage(
                    event.hook,
                    "❌ **Player Not Found**\n\n" +
                    "Player **${e.playerName}** has no MCMMO data.\n\n" +
                    "**Possible reasons:**\n" +
                    "• Player has never joined the server\n" +
                    "• Player has not gained any MCMMO experience\n" +
                    "• MCMMO data file is corrupted"
                )

            } catch (e: InvalidSkillException) {
                plugin.logger.fine("Invalid skill requested: ${e.skillName}")
                sendEphemeralMessage(
                    event.hook,
                    "❌ **Invalid Skill**\n\n" +
                    "Skill **${e.skillName}** is not valid.\n\n" +
                    "**Valid skills:**\n" +
                    e.validSkills.joinToString(", ")
                )

            } catch (e: Exception) {
                plugin.logger.warning("Unexpected error handling /mcstats command: ${e.message}")
                e.printStackTrace()
                sendEphemeralMessage(
                    event.hook,
                    "⚠️ **Error**\n\n" +
                    "An unexpected error occurred while fetching stats.\n" +
                    "Please try again later or contact an administrator."
                )
            }
        })
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

    private fun sendEmbed(hook: InteractionHook, embed: net.dv8tion.jda.api.entities.MessageEmbed) {
        discordApi?.sendMessageEmbed(hook, embed)?.exceptionally { error ->
            plugin.logger.warning("Failed to send embed: ${error.message}")
            null
        } ?: hook.sendMessageEmbeds(embed).queue(
            null,
            { error -> plugin.logger.warning("Failed to send embed: ${error.message}") }
        )
    }

    private fun handleAllSkills(event: SlashCommandInteractionEvent, mcUsername: String, invokerTag: String) {
        // Throws PlayerDataNotFoundException if player not found
        val stats = plugin.mcmmoApi.getPlayerStats(mcUsername)
        val powerLevel = plugin.mcmmoApi.getPowerLevel(mcUsername)

        val embed = EmbedBuilder()
            .setTitle("MCMMO Stats for $mcUsername")
            .setColor(Color.GREEN)
            .setDescription("**Power Level:** $powerLevel")
            .setTimestamp(Instant.now())
            .setFooter("Requested by $invokerTag", null)

        // Add fields for each skill (in columns)
        val sortedStats = stats.entries.sortedByDescending { it.value }

        for ((skill, level) in sortedStats) {
            embed.addField(formatSkillName(skill), level.toString(), true)
        }

        sendEmbed(event.hook, embed.build())
    }

    private fun handleSpecificSkill(event: SlashCommandInteractionEvent, mcUsername: String, skillName: String, invokerTag: String) {
        // Throws InvalidSkillException if skill invalid
        val skill = plugin.mcmmoApi.parseSkillType(skillName)

        // Throws PlayerDataNotFoundException if player not found
        val level = plugin.mcmmoApi.getPlayerSkillLevel(mcUsername, skill.name)

        val embed = EmbedBuilder()
            .setTitle("${formatSkillName(skill.name)} Stats for $mcUsername")
            .setColor(Color.CYAN)
            .setDescription("**Level:** $level")
            .setTimestamp(Instant.now())
            .setFooter("Requested by $invokerTag", null)

        sendEmbed(event.hook, embed.build())
    }

    /**
     * Resolve a flexible username input to a Minecraft username.
     *
     * Strategy:
     * 1. If usernameInput is null/empty, use command invoker's Discord ID (requires linking)
     * 2. If usernameInput is provided:
     *    a. Strip Discord mention format if present (<@123...> or <@!123...>)
     *    b. If it's a Discord ID (17-19 digits), lookup via UserMappingService
     *    c. Otherwise, assume it's a Minecraft username and use directly
     *
     * @param usernameInput The username parameter from the command (can be null)
     * @param invokerDiscordId The Discord ID of the user who invoked the command
     * @return Minecraft username to query
     * @throws IllegalArgumentException if username cannot be resolved
     */
    private fun resolveMinecraftUsername(usernameInput: String?, invokerDiscordId: String): String {
        // Case 1: No username provided - use command invoker's Discord ID
        if (usernameInput.isNullOrBlank()) {
            val mcUsername = plugin.userMappingService.getMinecraftUsername(invokerDiscordId)
            if (mcUsername == null) {
                throw IllegalArgumentException(
                    "❌ **Account Not Linked**\n\n" +
                    "Your Discord account is not linked to a Minecraft account.\n\n" +
                    "**How to link:**\n" +
                    "Contact a server administrator to use `/amslink add` command."
                )
            }
            return mcUsername
        }

        // Case 2: Username provided - flexible detection
        var cleanedInput = usernameInput.trim()

        // Strip Discord mention format: <@123456789012345678> or <@!123456789012345678>
        if (cleanedInput.startsWith("<@") && cleanedInput.endsWith(">")) {
            cleanedInput = cleanedInput.removePrefix("<@").removeSuffix(">")
            if (cleanedInput.startsWith("!")) {
                cleanedInput = cleanedInput.removePrefix("!")
            }
        }

        // Check if it's a Discord ID (17-19 digit snowflake)
        if (cleanedInput.matches(Regex("^\\d{17,19}$"))) {
            // Try to resolve via UserMappingService
            val mcUsername = plugin.userMappingService.getMinecraftUsername(cleanedInput)
            if (mcUsername == null) {
                throw IllegalArgumentException(
                    "❌ **Discord User Not Linked**\n\n" +
                    "Discord user <@$cleanedInput> is not linked to a Minecraft account.\n\n" +
                    "**How to link:**\n" +
                    "Contact a server administrator to use `/amslink add` command."
                )
            }
            return mcUsername
        }

        // Assume it's a Minecraft username - validate format before passing to MCMMO
        if (!Validators.isValidMinecraftUsername(cleanedInput)) {
            throw IllegalArgumentException(
                "❌ **Invalid Username**\n\n" +
                "${Validators.getMinecraftUsernameError(cleanedInput)}\n\n" +
                "**Minecraft usernames must:**\n" +
                "• Be 3-16 characters long\n" +
                "• Contain only letters, numbers, and underscores"
            )
        }

        return cleanedInput
    }

    /**
     * Format skill name for display (capitalize first letter)
     */
    private fun formatSkillName(skill: String): String {
        return skill.lowercase().replaceFirstChar { it.uppercase() }
    }
}
