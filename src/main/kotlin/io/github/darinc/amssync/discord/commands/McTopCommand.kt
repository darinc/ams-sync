package io.github.darinc.amssync.discord.commands

import io.github.darinc.amssync.AMSSyncPlugin
import io.github.darinc.amssync.discord.DiscordApiWrapper
import io.github.darinc.amssync.discord.TimeoutManager
import io.github.darinc.amssync.exceptions.InvalidSkillException
import io.github.darinc.amssync.validation.Validators
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.bukkit.Bukkit
import java.awt.Color
import java.time.Instant

/**
 * Handles the /mctop slash command
 */
class McTopCommand(private val plugin: AMSSyncPlugin) : SlashCommandHandler {

    override val commandName = "mctop"

    private val leaderboardSize = 10

    private val discordApi: DiscordApiWrapper?
        get() = plugin.discord.apiWrapper

    override fun handle(event: SlashCommandInteractionEvent) {
        // Defer reply immediately to avoid timeout
        CommandUtils.deferReply(event, "/mctop", discordApi, plugin.logger)

        val skillName = event.getOption("skill")?.asString

        // Get timeout manager if available
        val timeoutManager = plugin.resilience.timeoutManager

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
                        CommandUtils.sendEphemeralMessage(
                            event.hook,
                            "The leaderboard query timed out (${result.timeoutMs}ms).\n" +
                            "Please try again later.",
                            discordApi,
                            plugin.logger
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
                    CommandUtils.sendEphemeralMessage(
                        event.hook,
                        "The leaderboard query timed out (${result.timeoutMs}ms).\n" +
                        "Please try again later.",
                        discordApi,
                        plugin.logger
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
                CommandUtils.sendEphemeralMessage(
                    event.hook,
                    "Invalid skill: **${exception.skillName}**\n\n" +
                    "Valid skills: ${exception.validSkills.joinToString(", ")}, power",
                    discordApi,
                    plugin.logger
                )
            }
            else -> {
                plugin.logger.warning("Unexpected error handling /mctop command: ${exception.message}")
                exception.printStackTrace()
                CommandUtils.sendEphemeralMessage(
                    event.hook,
                    "An error occurred while fetching the leaderboard.\n" +
                    "Please try again later.",
                    discordApi,
                    plugin.logger
                )
            }
        }
    }

    private fun handleSkillLeaderboard(event: SlashCommandInteractionEvent, skillName: String) {
        // Throws InvalidSkillException if skill invalid
        val skill = plugin.mcmmoApi.parseSkillType(skillName)
        val leaderboard = plugin.mcmmoApi.getLeaderboard(skill.name, leaderboardSize)

        if (leaderboard.isEmpty()) {
            CommandUtils.sendEphemeralMessage(
                event.hook,
                "No leaderboard data for **${Validators.formatSkillName(skill.name)}**.\n" +
                "Players need to gain experience in this skill first.",
                discordApi,
                plugin.logger
            )
            return
        }

        val embed = EmbedBuilder()
            .setTitle("Top $leaderboardSize - ${Validators.formatSkillName(skill.name)}")
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
        CommandUtils.sendEmbed(event.hook, embed.build(), discordApi, plugin.logger)
    }

    private fun handlePowerLevelLeaderboard(event: SlashCommandInteractionEvent) {
        val leaderboard = plugin.mcmmoApi.getPowerLevelLeaderboard(leaderboardSize)

        if (leaderboard.isEmpty()) {
            CommandUtils.sendEphemeralMessage(
                event.hook,
                "No power level leaderboard data found.\n" +
                "Players need to gain MCMMO experience first.",
                discordApi,
                plugin.logger
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
        CommandUtils.sendEmbed(event.hook, embed.build(), discordApi, plugin.logger)
    }
}
