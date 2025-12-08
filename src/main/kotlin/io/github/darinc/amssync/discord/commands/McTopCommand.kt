package io.github.darinc.amssync.discord.commands

import io.github.darinc.amssync.AMSSyncPlugin
import io.github.darinc.amssync.discord.TimeoutManager
import io.github.darinc.amssync.exceptions.InvalidSkillException
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.bukkit.Bukkit
import java.awt.Color
import java.time.Instant

/**
 * Handles the /mctop slash command
 */
class McTopCommand(private val plugin: AMSSyncPlugin) {

    private val leaderboardSize = 10

    fun handle(event: SlashCommandInteractionEvent) {
        // Defer reply immediately to avoid timeout
        event.deferReply().queue(
            null,
            { error -> plugin.logger.warning("Failed to defer reply for /mctop: ${error.message}") }
        )

        val skillName = event.getOption("skill")?.asString

        // Get timeout manager if available
        val timeoutManager = plugin.timeoutManager

        // If no skill specified, default to power level leaderboard
        if (skillName == null) {
            if (timeoutManager != null) {
                val result = timeoutManager.executeOnBukkitWithTimeout(
                    plugin,
                    "MCMMO power level leaderboard query"
                ) {
                    handlePowerLevelLeaderboard(event)
                }

                when (result) {
                    is TimeoutManager.TimeoutResult.Success -> {}
                    is TimeoutManager.TimeoutResult.Timeout -> {
                        event.hook.sendMessage(
                            "⏱️ **Operation Timed Out**\n\n" +
                            "The leaderboard query took too long to complete (${result.timeoutMs}ms)."
                        ).setEphemeral(true).queue(
                            null,
                            { error -> plugin.logger.warning("Failed to send timeout message: ${error.message}") }
                        )
                    }
                    is TimeoutManager.TimeoutResult.Failure -> {
                        handleException(event, result.exception)
                    }
                }
            } else {
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    try {
                        handlePowerLevelLeaderboard(event)
                    } catch (e: Exception) {
                        handleException(event, e)
                    }
                })
            }
            return
        }

        // Execute with timeout protection if available
        if (timeoutManager != null) {
            val result = timeoutManager.executeOnBukkitWithTimeout(
                plugin,
                "MCMMO leaderboard query"
            ) {
                if (skillName.equals("power", ignoreCase = true) ||
                    skillName.equals("powerlevel", ignoreCase = true) ||
                    skillName.equals("all", ignoreCase = true)) {
                    handlePowerLevelLeaderboard(event)
                } else {
                    handleSkillLeaderboard(event, skillName)
                }
            }

            // Handle timeout result
            when (result) {
                is TimeoutManager.TimeoutResult.Success -> {
                    // Success - result already sent by handler methods
                }
                is TimeoutManager.TimeoutResult.Timeout -> {
                    event.hook.sendMessage(
                        "⏱️ **Operation Timed Out**\n\n" +
                        "The leaderboard query took too long to complete (${result.timeoutMs}ms).\n\n" +
                        "**Possible causes:**\n" +
                        "• Server has too many players to scan\n" +
                        "• MCMMO database is slow or corrupted\n\n" +
                        "**Suggested actions:**\n" +
                        "• Contact an administrator to increase timeout limits\n" +
                        "• Try again in a few moments"
                    ).setEphemeral(true).queue(
                        null,
                        { error -> plugin.logger.warning("Failed to send timeout message: ${error.message}") }
                    )
                }
                is TimeoutManager.TimeoutResult.Failure -> {
                    handleException(event, result.exception)
                }
            }
        } else {
            // No timeout protection - run directly on Bukkit thread
            Bukkit.getScheduler().runTask(plugin, Runnable {
                try {
                    if (skillName.equals("power", ignoreCase = true) ||
                        skillName.equals("powerlevel", ignoreCase = true) ||
                        skillName.equals("all", ignoreCase = true)) {
                        handlePowerLevelLeaderboard(event)
                    } else {
                        handleSkillLeaderboard(event, skillName)
                    }

                } catch (e: Exception) {
                    handleException(event, e)
                }
            })
        }
    }

    /**
     * Handle exceptions from leaderboard queries
     */
    private fun handleException(event: SlashCommandInteractionEvent, exception: Exception) {
        when (exception) {
            is InvalidSkillException -> {
                plugin.logger.fine("Invalid skill requested: ${exception.skillName}")
                event.hook.sendMessage(
                    "❌ **Invalid Skill**\n\n" +
                    "Skill **${exception.skillName}** is not valid.\n\n" +
                    "**Valid skills:**\n" +
                    exception.validSkills.joinToString(", ") + ", power"
                ).setEphemeral(true).queue(
                    null,
                    { error -> plugin.logger.warning("Failed to send invalid skill message: ${error.message}") }
                )
            }
            else -> {
                plugin.logger.warning("Unexpected error handling /mctop command: ${exception.message}")
                exception.printStackTrace()
                event.hook.sendMessage(
                    "⚠️ **Error**\n\n" +
                    "An unexpected error occurred while fetching the leaderboard.\n" +
                    "Please try again later or contact an administrator."
                ).setEphemeral(true).queue(
                    null,
                    { error -> plugin.logger.warning("Failed to send error message: ${error.message}") }
                )
            }
        }
    }

    private fun handleSkillLeaderboard(event: SlashCommandInteractionEvent, skillName: String) {
        // Throws InvalidSkillException if skill invalid
        val skill = plugin.mcmmoApi.parseSkillType(skillName)
        val leaderboard = plugin.mcmmoApi.getLeaderboard(skill.name, leaderboardSize)

        if (leaderboard.isEmpty()) {
            event.hook.sendMessage(
                "📊 **No Data**\n\n" +
                "No leaderboard data found for **${formatSkillName(skill.name)}**.\n\n" +
                "Players need to gain experience in this skill first."
            ).setEphemeral(true).queue(
                null,
                { error -> plugin.logger.warning("Failed to send no data message: ${error.message}") }
            )
            return
        }

        val embed = EmbedBuilder()
            .setTitle("Top $leaderboardSize - ${formatSkillName(skill.name)}")
            .setColor(Color.ORANGE)
            .setTimestamp(Instant.now())
            .setFooter("Amazing Minecraft Server", null)

        // Build leaderboard text
        val leaderboardText = buildString {
            leaderboard.forEachIndexed { index, (playerName, level) ->
                val medal = when (index) {
                    0 -> ":first_place:"
                    1 -> ":second_place:"
                    2 -> ":third_place:"
                    else -> "${index + 1}."
                }
                appendLine("$medal **$playerName** - Level $level")
            }
        }

        embed.setDescription(leaderboardText)
        event.hook.sendMessageEmbeds(embed.build()).queue(
            null,
            { error -> plugin.logger.warning("Failed to send skill leaderboard embed: ${error.message}") }
        )
    }

    private fun handlePowerLevelLeaderboard(event: SlashCommandInteractionEvent) {
        val leaderboard = plugin.mcmmoApi.getPowerLevelLeaderboard(leaderboardSize)

        if (leaderboard.isEmpty()) {
            event.hook.sendMessage(
                "📊 **No Data**\n\n" +
                "No power level leaderboard data found.\n\n" +
                "Players need to gain MCMMO experience first."
            ).setEphemeral(true).queue(
                null,
                { error -> plugin.logger.warning("Failed to send no data message: ${error.message}") }
            )
            return
        }

        val embed = EmbedBuilder()
            .setTitle("Top $leaderboardSize - Power Level")
            .setColor(Color.MAGENTA)
            .setTimestamp(Instant.now())
            .setFooter("Amazing Minecraft Server", null)

        // Build leaderboard text
        val leaderboardText = buildString {
            leaderboard.forEachIndexed { index, (playerName, powerLevel) ->
                val medal = when (index) {
                    0 -> ":first_place:"
                    1 -> ":second_place:"
                    2 -> ":third_place:"
                    else -> "${index + 1}."
                }
                appendLine("$medal **$playerName** - $powerLevel")
            }
        }

        embed.setDescription(leaderboardText)
        event.hook.sendMessageEmbeds(embed.build()).queue(
            null,
            { error -> plugin.logger.warning("Failed to send power level leaderboard embed: ${error.message}") }
        )
    }

    /**
     * Format skill name for display (capitalize first letter)
     */
    private fun formatSkillName(skill: String): String {
        return skill.lowercase().replaceFirstChar { it.uppercase() }
    }
}
