package io.github.darinc.amssync.discord.commands

import io.github.darinc.amssync.AMSSyncPlugin
import io.github.darinc.amssync.discord.DiscordApiWrapper
import io.github.darinc.amssync.discord.TimeoutManager
import io.github.darinc.amssync.exceptions.InvalidSkillException
import io.github.darinc.amssync.image.AvatarFetcher
import io.github.darinc.amssync.image.ImageConfig
import io.github.darinc.amssync.image.PlayerCardRenderer
import io.github.darinc.amssync.validation.Validators
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.utils.FileUpload
import org.bukkit.Bukkit
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Handles the /amstop slash command - generates a visual leaderboard podium card.
 */
class AmsTopCommand(
    private val plugin: AMSSyncPlugin,
    private val imageConfig: ImageConfig,
    private val avatarFetcher: AvatarFetcher,
    private val cardRenderer: PlayerCardRenderer
) {

    private val leaderboardSize = 10

    private val discordApi: DiscordApiWrapper?
        get() = plugin.services.discord.apiWrapper

    fun handle(event: SlashCommandInteractionEvent) {
        // Defer reply immediately (image generation takes time)
        CommandUtils.deferReply(event, "/amstop", discordApi, plugin.logger)

        val skillName = event.getOption("skill")?.asString
        val timeoutManager = plugin.services.resilience.timeoutManager

        // Determine if power level or skill leaderboard
        val isPowerLevel = skillName == null ||
            skillName.equals("power", ignoreCase = true) ||
            skillName.equals("powerlevel", ignoreCase = true) ||
            skillName.equals("all", ignoreCase = true)

        // Execute with timeout protection if available
        if (timeoutManager != null) {
            val result = timeoutManager.executeOnBukkitWithTimeout(
                plugin,
                "MCMMO leaderboard query"
            ) {
                if (isPowerLevel) {
                    handlePowerLevelLeaderboard(event)
                } else {
                    handleSkillLeaderboard(event, skillName!!)
                }
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
                    if (isPowerLevel) {
                        handlePowerLevelLeaderboard(event)
                    } else {
                        handleSkillLeaderboard(event, skillName!!)
                    }
                } catch (e: Exception) {
                    handleException(event, e)
                }
            })
        }
    }

    private fun handlePowerLevelLeaderboard(event: SlashCommandInteractionEvent) {
        val leaderboard = plugin.services.mcmmoApi.getPowerLevelLeaderboard(leaderboardSize)

        if (leaderboard.isEmpty()) {
            CommandUtils.sendEphemeralMessage(
                event.hook,
                "No leaderboard data available.\n" +
                "Players need to gain MCMMO experience first.",
                discordApi,
                plugin.logger
            )
            return
        }

        renderAndSendCard(event, "Power Level", leaderboard)
    }

    private fun handleSkillLeaderboard(event: SlashCommandInteractionEvent, skillName: String) {
        // Parse and validate skill name
        val skill = try {
            plugin.services.mcmmoApi.parseSkillType(skillName)
        } catch (e: InvalidSkillException) {
            CommandUtils.sendEphemeralMessage(
                event.hook,
                "Invalid skill: **$skillName**\n\n" +
                "Valid skills: ${e.validSkills.joinToString(", ")}",
                discordApi,
                plugin.logger
            )
            return
        }

        val leaderboard = plugin.services.mcmmoApi.getLeaderboard(skill.name, leaderboardSize)

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

        renderAndSendCard(event, Validators.formatSkillName(skill.name), leaderboard)
    }

    private fun renderAndSendCard(
        event: SlashCommandInteractionEvent,
        title: String,
        leaderboard: List<Pair<String, Int>>
    ) {
        // Fetch head avatars for top 10 players in parallel
        val playerNames = leaderboard.map { it.first }
        val playersWithUuids = playerNames.map { name ->
            val player = plugin.services.mcmmoApi.getOfflinePlayer(name)
            name to player?.uniqueId
        }

        val avatarImages = avatarFetcher.fetchHeadAvatarsBatch(
            playersWithUuids,
            imageConfig.avatarProvider,
            AvatarFetcher.PODIUM_HEAD_SIZE
        )

        // Render the leaderboard card
        val cardImage = cardRenderer.renderLeaderboardCard(
            title,
            leaderboard,
            avatarImages
        )

        // Convert to PNG bytes
        val baos = ByteArrayOutputStream()
        ImageIO.write(cardImage, "PNG", baos)
        val imageBytes = baos.toByteArray()

        // Send as file attachment
        val filename = "${title.lowercase().replace(" ", "_")}_leaderboard.png"
        CommandUtils.sendFiles(event.hook, discordApi, plugin.logger, FileUpload.fromData(imageBytes, filename))
        plugin.logger.fine("Sent leaderboard card for $title")
    }

    private fun handleException(event: SlashCommandInteractionEvent, e: Exception) {
        when (e) {
            is InvalidSkillException -> {
                CommandUtils.sendEphemeralMessage(
                    event.hook,
                    "Invalid skill: **${e.skillName}**\n\n" +
                    "Valid skills: ${e.validSkills.joinToString(", ")}",
                    discordApi,
                    plugin.logger
                )
            }
            else -> {
                plugin.logger.warning("Error handling /amstop: ${e.message}")
                e.printStackTrace()
                CommandUtils.sendEphemeralMessage(
                    event.hook,
                    "An error occurred while generating the leaderboard.\n" +
                    "Please try again later.",
                    discordApi,
                    plugin.logger
                )
            }
        }
    }
}
