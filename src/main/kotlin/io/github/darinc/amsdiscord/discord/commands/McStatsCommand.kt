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
        event.deferReply().queue()

        val discordId = event.user.id
        val skillOption = event.getOption("skill")?.asString

        // Run on main Bukkit thread
        Bukkit.getScheduler().runTask(plugin, Runnable {
            try {
                // Get Minecraft username for this Discord user
                val mcUsername = plugin.userMappingService.getMinecraftUsername(discordId)

                if (mcUsername == null) {
                    event.hook.sendMessage(
                        "❌ **Account Not Linked**\n\n" +
                        "Your Discord account is not linked to a Minecraft account.\n\n" +
                        "**How to link:**\n" +
                        "Contact a server administrator to use `/amslink add` command."
                    ).setEphemeral(true).queue()
                    return@Runnable
                }

                if (skillOption != null) {
                    // Show specific skill
                    handleSpecificSkill(event, mcUsername, skillOption)
                } else {
                    // Show all skills
                    handleAllSkills(event, mcUsername)
                }

            } catch (e: PlayerDataNotFoundException) {
                plugin.logger.fine("Player data not found: ${e.message}")
                event.hook.sendMessage(
                    "❌ **Player Not Found**\n\n" +
                    "Player **${e.playerName}** has no MCMMO data.\n\n" +
                    "**Possible reasons:**\n" +
                    "• Player has never joined the server\n" +
                    "• Player has not gained any MCMMO experience\n" +
                    "• MCMMO data file is corrupted"
                ).setEphemeral(true).queue()

            } catch (e: InvalidSkillException) {
                plugin.logger.fine("Invalid skill requested: ${e.skillName}")
                event.hook.sendMessage(
                    "❌ **Invalid Skill**\n\n" +
                    "Skill **${e.skillName}** is not valid.\n\n" +
                    "**Valid skills:**\n" +
                    e.validSkills.joinToString(", ")
                ).setEphemeral(true).queue()

            } catch (e: Exception) {
                plugin.logger.warning("Unexpected error handling /mcstats command: ${e.message}")
                e.printStackTrace()
                event.hook.sendMessage(
                    "⚠️ **Error**\n\n" +
                    "An unexpected error occurred while fetching stats.\n" +
                    "Please try again later or contact an administrator."
                ).setEphemeral(true).queue()
            }
        })
    }

    private fun handleAllSkills(event: SlashCommandInteractionEvent, mcUsername: String) {
        // Throws PlayerDataNotFoundException if player not found
        val stats = plugin.mcmmoApi.getPlayerStats(mcUsername)
        val powerLevel = plugin.mcmmoApi.getPowerLevel(mcUsername)

        val embed = EmbedBuilder()
            .setTitle("MCMMO Stats for $mcUsername")
            .setColor(Color.GREEN)
            .setDescription("**Power Level:** $powerLevel")
            .setTimestamp(Instant.now())
            .setFooter("Amazing Minecraft Server", null)

        // Add fields for each skill (in columns)
        val sortedStats = stats.entries.sortedByDescending { it.value }

        for ((skill, level) in sortedStats) {
            embed.addField(formatSkillName(skill), level.toString(), true)
        }

        event.hook.sendMessageEmbeds(embed.build()).queue()
    }

    private fun handleSpecificSkill(event: SlashCommandInteractionEvent, mcUsername: String, skillName: String) {
        // Throws InvalidSkillException if skill invalid
        val skill = plugin.mcmmoApi.parseSkillType(skillName)

        // Throws PlayerDataNotFoundException if player not found
        val level = plugin.mcmmoApi.getPlayerSkillLevel(mcUsername, skill.name)

        val embed = EmbedBuilder()
            .setTitle("${formatSkillName(skill.name)} Stats for $mcUsername")
            .setColor(Color.CYAN)
            .setDescription("**Level:** $level")
            .setTimestamp(Instant.now())
            .setFooter("Amazing Minecraft Server", null)

        event.hook.sendMessageEmbeds(embed.build()).queue()
    }

    /**
     * Format skill name for display (capitalize first letter)
     */
    private fun formatSkillName(skill: String): String {
        return skill.lowercase().replaceFirstChar { it.uppercase() }
    }
}
