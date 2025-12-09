package io.github.darinc.amssync.discord.commands

import io.github.darinc.amssync.AMSSyncPlugin
import io.github.darinc.amssync.exceptions.PlayerDataNotFoundException
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
 * Handles the /amsstats slash command - generates a visual player stats card.
 */
class AmsStatsCommand(
    private val plugin: AMSSyncPlugin,
    private val imageConfig: ImageConfig,
    private val avatarFetcher: AvatarFetcher,
    private val cardRenderer: PlayerCardRenderer
) {

    fun handle(event: SlashCommandInteractionEvent) {
        // Defer reply immediately to avoid timeout (image generation takes time)
        event.deferReply().queue(
            null,
            { error -> plugin.logger.warning("Failed to defer reply for /amsstats: ${error.message}") }
        )

        val usernameOption = event.getOption("username")?.asString
        val invokerDiscordId = event.user.id

        // Run on main Bukkit thread for MCMMO data access
        Bukkit.getScheduler().runTask(plugin, Runnable {
            try {
                // Resolve the target Minecraft username
                val mcUsername = resolveMinecraftUsername(usernameOption, invokerDiscordId)

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
                event.hook.sendFiles(
                    FileUpload.fromData(imageBytes, "${mcUsername}_stats.png")
                ).queue(
                    { plugin.logger.fine("Sent stats card for $mcUsername") },
                    { error -> plugin.logger.warning("Failed to send stats card: ${error.message}") }
                )

            } catch (e: IllegalArgumentException) {
                plugin.logger.fine("Username resolution failed: ${e.message}")
                event.hook.sendMessage(e.message ?: "Invalid username")
                    .setEphemeral(true)
                    .queue(
                        null,
                        { error -> plugin.logger.warning("Failed to send error message: ${error.message}") }
                    )

            } catch (e: PlayerDataNotFoundException) {
                plugin.logger.fine("Player data not found: ${e.message}")
                event.hook.sendMessage(
                    "Player **${e.playerName}** has no MCMMO data.\n" +
                    "They may have never joined or haven't gained any XP yet."
                ).setEphemeral(true).queue(
                    null,
                    { error -> plugin.logger.warning("Failed to send error message: ${error.message}") }
                )

            } catch (e: Exception) {
                plugin.logger.warning("Error handling /amsstats: ${e.message}")
                e.printStackTrace()
                event.hook.sendMessage(
                    "An error occurred while generating the stats card.\n" +
                    "Please try again later."
                ).setEphemeral(true).queue(
                    null,
                    { error -> plugin.logger.warning("Failed to send error message: ${error.message}") }
                )
            }
        })
    }

    /**
     * Resolve a flexible username input to a Minecraft username.
     * Mirrors the logic from McStatsCommand.
     */
    private fun resolveMinecraftUsername(usernameInput: String?, invokerDiscordId: String): String {
        // Case 1: No username provided - use command invoker's Discord ID
        if (usernameInput.isNullOrBlank()) {
            val mcUsername = plugin.userMappingService.getMinecraftUsername(invokerDiscordId)
            if (mcUsername == null) {
                throw IllegalArgumentException(
                    "Your Discord account is not linked to a Minecraft account.\n" +
                    "Contact an administrator to use `/amslink add`."
                )
            }
            return mcUsername
        }

        // Case 2: Username provided - flexible detection
        var cleanedInput = usernameInput.trim()

        // Strip Discord mention format
        if (cleanedInput.startsWith("<@") && cleanedInput.endsWith(">")) {
            cleanedInput = cleanedInput.removePrefix("<@").removeSuffix(">")
            if (cleanedInput.startsWith("!")) {
                cleanedInput = cleanedInput.removePrefix("!")
            }
        }

        // Check if it's a Discord ID
        if (cleanedInput.matches(Regex("^\\d{17,19}$"))) {
            val mcUsername = plugin.userMappingService.getMinecraftUsername(cleanedInput)
            if (mcUsername == null) {
                throw IllegalArgumentException(
                    "Discord user <@$cleanedInput> is not linked to a Minecraft account."
                )
            }
            return mcUsername
        }

        // Validate Minecraft username format
        if (!Validators.isValidMinecraftUsername(cleanedInput)) {
            throw IllegalArgumentException(
                "${Validators.getMinecraftUsernameError(cleanedInput)}\n" +
                "Minecraft usernames must be 3-16 characters with only letters, numbers, and underscores."
            )
        }

        return cleanedInput
    }
}
