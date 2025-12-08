package io.github.darinc.amsdiscord.discord.commands

import io.github.darinc.amsdiscord.AmsDiscordPlugin
import io.github.darinc.amsdiscord.exceptions.InvalidSkillException
import io.github.darinc.amsdiscord.exceptions.PlayerDataNotFoundException
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.bukkit.Bukkit
import java.awt.Color
import java.time.Instant

/**
 * Handles the /mcstats slash command
 */
class McStatsCommand(private val plugin: AmsDiscordPlugin) {

    fun handle(event: SlashCommandInteractionEvent) {
        // Defer reply immediately to avoid timeout
        event.deferReply().queue(
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
                event.hook.sendMessage(e.message ?: "Invalid username")
                    .setEphemeral(true)
                    .queue(
                        null,
                        { error -> plugin.logger.warning("Failed to send error message for /mcstats: ${error.message}") }
                    )

            } catch (e: PlayerDataNotFoundException) {
                plugin.logger.fine("Player data not found: ${e.message}")
                event.hook.sendMessage(
                    "❌ **Player Not Found**\n\n" +
                    "Player **${e.playerName}** has no MCMMO data.\n\n" +
                    "**Possible reasons:**\n" +
                    "• Player has never joined the server\n" +
                    "• Player has not gained any MCMMO experience\n" +
                    "• MCMMO data file is corrupted"
                ).setEphemeral(true).queue(
                    null,
                    { error -> plugin.logger.warning("Failed to send player not found message: ${error.message}") }
                )

            } catch (e: InvalidSkillException) {
                plugin.logger.fine("Invalid skill requested: ${e.skillName}")
                event.hook.sendMessage(
                    "❌ **Invalid Skill**\n\n" +
                    "Skill **${e.skillName}** is not valid.\n\n" +
                    "**Valid skills:**\n" +
                    e.validSkills.joinToString(", ")
                ).setEphemeral(true).queue(
                    null,
                    { error -> plugin.logger.warning("Failed to send invalid skill message: ${error.message}") }
                )

            } catch (e: Exception) {
                plugin.logger.warning("Unexpected error handling /mcstats command: ${e.message}")
                e.printStackTrace()
                event.hook.sendMessage(
                    "⚠️ **Error**\n\n" +
                    "An unexpected error occurred while fetching stats.\n" +
                    "Please try again later or contact an administrator."
                ).setEphemeral(true).queue(
                    null,
                    { error -> plugin.logger.warning("Failed to send error message: ${error.message}") }
                )
            }
        })
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

        event.hook.sendMessageEmbeds(embed.build()).queue(
            null,
            { error -> plugin.logger.warning("Failed to send stats embed: ${error.message}") }
        )
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

        event.hook.sendMessageEmbeds(embed.build()).queue(
            null,
            { error -> plugin.logger.warning("Failed to send skill stats embed: ${error.message}") }
        )
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

        // Assume it's a Minecraft username - return as-is
        // MCMMO API will validate if player exists
        return cleanedInput
    }

    /**
     * Format skill name for display (capitalize first letter)
     */
    private fun formatSkillName(skill: String): String {
        return skill.lowercase().replaceFirstChar { it.uppercase() }
    }
}
