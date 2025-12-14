package io.github.darinc.amssync.discord.commands

import io.github.darinc.amssync.AMSSyncPlugin
import io.github.darinc.amssync.discord.DiscordApiWrapper
import io.github.darinc.amssync.exceptions.PlayerDataNotFoundException
import io.github.darinc.amssync.image.AvatarFetcher
import io.github.darinc.amssync.image.ImageConfig
import io.github.darinc.amssync.image.PlayerCardRenderer
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.utils.FileUpload
import org.bukkit.Bukkit
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Handles the /amsstats slash command - generates a visual player stats card.
 */
class AmsStatsCommand(
    private val plugin: AMSSyncPlugin,
    private val imageConfig: ImageConfig,
    private val avatarFetcher: AvatarFetcher,
    private val cardRenderer: PlayerCardRenderer
) : SlashCommandHandler {

    override val commandName = "amsstats"

    private val discordApi: DiscordApiWrapper?
        get() = plugin.discord.apiWrapper

    private val usernameResolver = UsernameResolver(plugin.userMappingService)

    override fun handle(event: SlashCommandInteractionEvent) {
        // Defer reply immediately to avoid timeout (image generation takes time)
        CommandUtils.deferReply(event, "/amsstats", discordApi, plugin.logger)

        val usernameOption = event.getOption("username")?.asString
        val invokerDiscordId = event.user.id

        // Run on main Bukkit thread for MCMMO data access
        Bukkit.getScheduler().runTask(plugin, Runnable {
            try {
                // Resolve the target Minecraft username
                val mcUsername = usernameResolver.resolve(usernameOption, invokerDiscordId)

                // Get player stats and power level
                val stats = plugin.mcmmoApi.getPlayerStats(mcUsername)
                val powerLevel = plugin.mcmmoApi.getPowerLevel(mcUsername)

                // Get player UUID for avatar fetching
                val player = plugin.mcmmoApi.getOfflinePlayer(mcUsername)
                val uuid = player?.uniqueId

                // Fetch body render (async but we wait for it)
                val bodyImage = avatarFetcher.fetchBodyRender(
                    mcUsername,
                    uuid,
                    imageConfig.avatarProvider
                )

                // Render the card
                val cardImage = cardRenderer.renderStatsCard(
                    mcUsername,
                    stats,
                    powerLevel,
                    bodyImage
                )

                // Convert to PNG bytes
                val baos = ByteArrayOutputStream()
                ImageIO.write(cardImage, "PNG", baos)
                val imageBytes = baos.toByteArray()

                // Send as file attachment
                CommandUtils.sendFiles(event.hook, discordApi, plugin.logger, FileUpload.fromData(imageBytes, "${mcUsername}_stats.png"))
                plugin.logger.fine("Sent stats card for $mcUsername")

            } catch (e: IllegalArgumentException) {
                plugin.logger.fine("Username resolution failed: ${e.message}")
                CommandUtils.sendEphemeralMessage(event.hook, e.message ?: "Invalid username", discordApi, plugin.logger)

            } catch (e: PlayerDataNotFoundException) {
                plugin.logger.fine("Player data not found: ${e.message}")
                CommandUtils.sendEphemeralMessage(
                    event.hook,
                    "Player **${e.playerName}** has no MCMMO data.\n" +
                    "They may have never joined or haven't gained any XP yet.",
                    discordApi,
                    plugin.logger
                )

            } catch (e: Exception) {
                plugin.logger.warning("Error handling /amsstats: ${e.message}")
                e.printStackTrace()
                CommandUtils.sendEphemeralMessage(
                    event.hook,
                    "An error occurred while generating the stats card.\n" +
                    "Please try again later.",
                    discordApi,
                    plugin.logger
                )
            }
        })
    }
}
