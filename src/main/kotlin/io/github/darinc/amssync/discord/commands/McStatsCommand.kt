package io.github.darinc.amssync.discord.commands

import io.github.darinc.amssync.AMSSyncPlugin
import io.github.darinc.amssync.discord.DiscordApiWrapper
import io.github.darinc.amssync.exceptions.InvalidSkillException
import io.github.darinc.amssync.exceptions.PlayerDataNotFoundException
import io.github.darinc.amssync.validation.Validators
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.bukkit.Bukkit
import java.awt.Color
import java.time.Instant

/**
 * Handles the /mcstats slash command
 */
class McStatsCommand(private val plugin: AMSSyncPlugin) : SlashCommandHandler {

    override val commandName = "mcstats"

    private val discordApi: DiscordApiWrapper?
        get() = plugin.discord.apiWrapper

    private val usernameResolver = UsernameResolver(plugin.userMappingService)

    override fun handle(event: SlashCommandInteractionEvent) {
        // Defer reply immediately to avoid timeout
        CommandUtils.deferReply(event, "/mcstats", discordApi, plugin.logger)

        val usernameOption = event.getOption("username")?.asString
        val skillOption = event.getOption("skill")?.asString
        val invokerDiscordId = event.user.id
        val invokerTag = event.user.name

        // Run on main Bukkit thread
        Bukkit.getScheduler().runTask(plugin, Runnable {
            try {
                // Resolve the target Minecraft username (self or other player)
                val mcUsername = usernameResolver.resolve(usernameOption, invokerDiscordId)

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

            } catch (e: InvalidSkillException) {
                plugin.logger.fine("Invalid skill requested: ${e.skillName}")
                CommandUtils.sendEphemeralMessage(
                    event.hook,
                    "Invalid skill: **${e.skillName}**\n\n" +
                    "Valid skills: ${e.validSkills.joinToString(", ")}",
                    discordApi,
                    plugin.logger
                )

            } catch (e: Exception) {
                plugin.logger.warning("Unexpected error handling /mcstats command: ${e.message}")
                e.printStackTrace()
                CommandUtils.sendEphemeralMessage(
                    event.hook,
                    "An error occurred while fetching stats.\n" +
                    "Please try again later.",
                    discordApi,
                    plugin.logger
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
            embed.addField(Validators.formatSkillName(skill), level.toString(), true)
        }

        CommandUtils.sendEmbed(event.hook, embed.build(), discordApi, plugin.logger)
    }

    private fun handleSpecificSkill(event: SlashCommandInteractionEvent, mcUsername: String, skillName: String, invokerTag: String) {
        // Throws InvalidSkillException if skill invalid
        val skill = plugin.mcmmoApi.parseSkillType(skillName)

        // Throws PlayerDataNotFoundException if player not found
        val level = plugin.mcmmoApi.getPlayerSkillLevel(mcUsername, skill.name)

        val embed = EmbedBuilder()
            .setTitle("${Validators.formatSkillName(skill.name)} Stats for $mcUsername")
            .setColor(Color.CYAN)
            .setDescription("**Level:** $level")
            .setTimestamp(Instant.now())
            .setFooter("Requested by $invokerTag", null)

        CommandUtils.sendEmbed(event.hook, embed.build(), discordApi, plugin.logger)
    }
}
