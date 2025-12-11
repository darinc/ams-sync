package io.github.darinc.amssync.discord.commands

import io.github.darinc.amssync.AMSSyncPlugin
import io.github.darinc.amssync.discord.DiscordApiWrapper
import io.github.darinc.amssync.discord.TimeoutManager
import io.github.darinc.amssync.exceptions.InvalidSkillException
import io.github.darinc.amssync.image.AvatarFetcher
import io.github.darinc.amssync.image.ImageConfig
import io.github.darinc.amssync.image.PlayerCardRenderer
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
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
        get() = plugin.discordApiWrapper

    fun handle(event: SlashCommandInteractionEvent) {
        // Defer reply immediately (image generation takes time)
        discordApi?.deferReply(event)?.exceptionally { error ->
            plugin.logger.warning("Failed to defer reply for /amstop: ${error.message}")
            null
        } ?: event.deferReply().queue(
            null,
            { error -> plugin.logger.warning("Failed to defer reply for /amstop: ${error.message}") }
        )

        val skillName = event.getOption("skill")?.asString
        val timeoutManager = plugin.timeoutManager

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
                    sendEphemeralMessage(
                        event.hook,
                        "The leaderboard query timed out (${result.timeoutMs}ms).\n" +
                        "Please try again later."
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
        val leaderboard = plugin.mcmmoApi.getPowerLevelLeaderboard(leaderboardSize)

        if (leaderboard.isEmpty()) {
            sendEphemeralMessage(
                event.hook,
                "No leaderboard data available.\n" +
                "Players need to gain MCMMO experience first."
            )
            return
        }

        renderAndSendCard(event, "Power Level", leaderboard)
    }

    private fun handleSkillLeaderboard(event: SlashCommandInteractionEvent, skillName: String) {
        // Parse and validate skill name
        val skill = try {
            plugin.mcmmoApi.parseSkillType(skillName)
        } catch (e: InvalidSkillException) {
            sendEphemeralMessage(
                event.hook,
                "Invalid skill: **$skillName**\n\n" +
                "Valid skills: ${e.validSkills.joinToString(", ")}"
            )
            return
        }

        val leaderboard = plugin.mcmmoApi.getLeaderboard(skill.name, leaderboardSize)

        if (leaderboard.isEmpty()) {
            sendEphemeralMessage(
                event.hook,
                "No leaderboard data for **${formatSkillName(skill.name)}**.\n" +
                "Players need to gain experience in this skill first."
            )
            return
        }

        renderAndSendCard(event, formatSkillName(skill.name), leaderboard)
    }

    private fun renderAndSendCard(
        event: SlashCommandInteractionEvent,
        title: String,
        leaderboard: List<Pair<String, Int>>
    ) {
        // Fetch head avatars for top 10 players in parallel
        val playerNames = leaderboard.map { it.first }
        val playersWithUuids = playerNames.map { name ->
            val player = plugin.mcmmoApi.getOfflinePlayer(name)
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
        sendFiles(event.hook, FileUpload.fromData(imageBytes, filename))
        plugin.logger.fine("Sent leaderboard card for $title")
    }

    private fun handleException(event: SlashCommandInteractionEvent, e: Exception) {
        when (e) {
            is InvalidSkillException -> {
                sendEphemeralMessage(
                    event.hook,
                    "Invalid skill: **${e.skillName}**\n\n" +
                    "Valid skills: ${e.validSkills.joinToString(", ")}"
                )
            }
            else -> {
                plugin.logger.warning("Error handling /amstop: ${e.message}")
                e.printStackTrace()
                sendEphemeralMessage(
                    event.hook,
                    "An error occurred while generating the leaderboard.\n" +
                    "Please try again later."
                )
            }
        }
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

    private fun sendFiles(hook: InteractionHook, vararg files: FileUpload) {
        discordApi?.sendFiles(hook, *files)?.exceptionally { error ->
            plugin.logger.warning("Failed to send files: ${error.message}")
            null
        } ?: hook.sendFiles(*files).queue(
            null,
            { error -> plugin.logger.warning("Failed to send files: ${error.message}") }
        )
    }

    private fun formatSkillName(skill: String): String {
        return skill.lowercase().replaceFirstChar { it.uppercase() }
    }
}
