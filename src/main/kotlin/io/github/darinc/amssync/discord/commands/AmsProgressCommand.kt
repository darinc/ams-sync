package io.github.darinc.amssync.discord.commands

import io.github.darinc.amssync.AMSSyncPlugin
import io.github.darinc.amssync.discord.DiscordApiWrapper
import io.github.darinc.amssync.image.AvatarFetcher
import io.github.darinc.amssync.image.ImageConfig
import io.github.darinc.amssync.image.ProgressionChartRenderer
import io.github.darinc.amssync.progression.ProgressionQueryService
import io.github.darinc.amssync.progression.Timeframe
import io.github.darinc.amssync.progression.TrendResult
import io.github.darinc.amssync.validation.Validators
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.utils.FileUpload
import org.bukkit.Bukkit
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Handles the /amsprogress slash command - generates a skill progression chart.
 *
 * Parameters:
 * - username: Optional player name (defaults to invoker's linked account)
 * - skill: Optional skill name (defaults to "power" for total power level)
 * - timeframe: Optional time period (defaults to 30 days)
 */
class AmsProgressCommand(
    private val plugin: AMSSyncPlugin,
    private val imageConfig: ImageConfig,
    private val avatarFetcher: AvatarFetcher,
    private val chartRenderer: ProgressionChartRenderer,
    private val queryService: ProgressionQueryService
) : SlashCommandHandler {

    override val commandName = "amsprogress"

    private val discordApi: DiscordApiWrapper?
        get() = plugin.services.discord.apiWrapper

    private val usernameResolver = UsernameResolver(plugin.services.userMappingService)

    override fun handle(event: SlashCommandInteractionEvent) {
        // Defer reply immediately to avoid timeout (chart generation takes time)
        CommandUtils.deferReply(event, "/amsprogress", discordApi, plugin.logger)

        val usernameOption = event.getOption("username")?.asString
        val skillOption = event.getOption("skill")?.asString
        val timeframeOption = event.getOption("timeframe")?.asString
        val invokerDiscordId = event.user.id

        // Parse timeframe (defaults to 30 days)
        val timeframe = Timeframe.fromChoiceValue(timeframeOption)

        // Determine skill display name
        val (skill, skillDisplayName) = parseSkillOption(skillOption)

        // Run on main Bukkit thread for MCMMO data access
        Bukkit.getScheduler().runTask(plugin, Runnable {
            try {
                // Resolve the target Minecraft username
                val mcUsername = usernameResolver.resolve(usernameOption, invokerDiscordId)

                // Get player UUID
                val player = plugin.services.mcmmoApi.getOfflinePlayer(mcUsername)
                val uuid = player?.uniqueId

                if (uuid == null) {
                    CommandUtils.sendEphemeralMessage(
                        event.hook,
                        "Player **$mcUsername** has never joined this server.",
                        discordApi,
                        plugin.logger
                    )
                    return@Runnable
                }

                // Query progression data
                val result = queryService.getTrend(uuid, mcUsername, skill, timeframe)

                when (result) {
                    is TrendResult.Success -> {
                        // Fetch player avatar for header
                        val avatar = avatarFetcher.fetchHeadAvatar(
                            mcUsername,
                            uuid,
                            imageConfig.avatarProvider
                        )

                        // Render the chart
                        val chartImage = chartRenderer.renderChart(
                            mcUsername,
                            skillDisplayName,
                            result.points,
                            timeframe,
                            avatar
                        )

                        // Convert to PNG bytes
                        val baos = ByteArrayOutputStream()
                        ImageIO.write(chartImage, "PNG", baos)
                        val imageBytes = baos.toByteArray()

                        // Send as file attachment
                        val filename = "${mcUsername}_${skill.lowercase()}_${timeframe.choiceValue}.png"
                        CommandUtils.sendFiles(
                            event.hook,
                            discordApi,
                            plugin.logger,
                            FileUpload.fromData(imageBytes, filename)
                        )
                        plugin.logger.fine("Sent progression chart for $mcUsername ($skill, ${timeframe.displayName})")
                    }

                    is TrendResult.NoData -> {
                        CommandUtils.sendEphemeralMessage(
                            event.hook,
                            result.reason,
                            discordApi,
                            plugin.logger
                        )
                    }

                    is TrendResult.Error -> {
                        plugin.logger.warning("Error querying progression for $mcUsername: ${result.message}")
                        CommandUtils.sendEphemeralMessage(
                            event.hook,
                            "An error occurred while querying progression data.\n" +
                                "Please try again later.",
                            discordApi,
                            plugin.logger
                        )
                    }
                }

            } catch (e: IllegalArgumentException) {
                plugin.logger.fine("Username resolution failed: ${e.message}")
                CommandUtils.sendEphemeralMessage(
                    event.hook,
                    e.message ?: "Invalid username",
                    discordApi,
                    plugin.logger
                )

            } catch (e: Exception) {
                plugin.logger.warning("Error handling /amsprogress: ${e.message}")
                e.printStackTrace()
                CommandUtils.sendEphemeralMessage(
                    event.hook,
                    "An error occurred while generating the progression chart.\n" +
                        "Please try again later.",
                    discordApi,
                    plugin.logger
                )
            }
        })
    }

    /**
     * Parse the skill option into internal skill name and display name.
     *
     * @param skillOption The raw skill option from Discord
     * @return Pair of (internal skill name, display name)
     */
    private fun parseSkillOption(skillOption: String?): Pair<String, String> {
        if (skillOption.isNullOrBlank() || skillOption.equals("power", ignoreCase = true)) {
            return ProgressionQueryService.POWER_SKILL to "Power Level"
        }

        val skillName = skillOption.trim().uppercase()
        val displayName = Validators.formatSkillName(skillName)
        return skillName to displayName
    }
}
