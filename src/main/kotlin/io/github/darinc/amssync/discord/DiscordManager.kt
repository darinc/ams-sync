package io.github.darinc.amssync.discord

import io.github.darinc.amssync.AMSSyncPlugin
import io.github.darinc.amssync.discord.commands.SlashCommandHandler
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.requests.GatewayIntent

/**
 * Manages the Discord bot lifecycle and slash command registration.
 *
 * @param plugin The plugin instance
 * @param commandHandlers Map of command names to their handlers for routing
 */
class DiscordManager(
    private val plugin: AMSSyncPlugin,
    private val commandHandlers: Map<String, SlashCommandHandler>
) {

    private var jda: JDA? = null
    private var connected: Boolean = false

    /**
     * Initialize the Discord bot and register slash commands.
     * This method should be called within a retry wrapper for production resilience.
     *
     * @param token Discord bot token
     * @param guildId Discord guild ID for command registration
     * @throws Exception if connection fails (to be caught by retry logic)
     */
    fun initialize(token: String, guildId: String) {
        plugin.logger.info("Connecting to Discord...")

        try {
            // Build JDA instance
            // Note: Activity is set by PlayerCountPresence after initialization
            jda = JDABuilder.createDefault(token)
                .enableIntents(
                    GatewayIntent.GUILD_MEMBERS,   // Needed for user lookups
                    GatewayIntent.GUILD_MESSAGES,  // Needed for chat bridge (receive messages)
                    GatewayIntent.MESSAGE_CONTENT  // Needed for chat bridge (read message content)
                )
                .addEventListeners(SlashCommandListener(plugin, commandHandlers))
                .build()
                .awaitReady()

            connected = true
            plugin.logger.info("Discord bot is ready! Connected as ${jda?.selfUser?.name}")

            // Register slash commands
            registerSlashCommands(guildId)

        } catch (e: Exception) {
            // Clean up on failure
            connected = false
            jda?.shutdownNow()
            jda = null

            // Re-throw to allow retry logic to catch
            throw e
        }
    }

    /**
     * Check if the Discord bot is currently connected and ready.
     *
     * @return true if connected and ready, false otherwise
     */
    fun isConnected(): Boolean {
        return connected && jda != null && jda?.status == JDA.Status.CONNECTED
    }

    /**
     * Register slash commands to Discord
     */
    private fun registerSlashCommands(guildId: String) {
        val commands = mutableListOf<SlashCommandData>(
            // Player commands
            Commands.slash("mcstats", "View MCMMO stats for yourself or another player")
                .addOption(OptionType.STRING, "username", "Minecraft or Discord username (leave empty for your own stats)", false)
                .addOption(OptionType.STRING, "skill", "View stats for a specific skill (leave empty for all)", false),

            Commands.slash("mctop", "View MCMMO leaderboard")
                .addOption(OptionType.STRING, "skill", "Skill to show leaderboard for (leave empty for power level)", false),

            // Visual card commands
            Commands.slash("amsstats", "View MCMMO stats as a visual player card")
                .addOption(OptionType.STRING, "username", "Minecraft or Discord username (leave empty for your own stats)", false),

            Commands.slash("amstop", "View MCMMO leaderboard as a visual podium card")
                .addOption(OptionType.STRING, "skill", "Skill to show leaderboard for (leave empty for power level)", false),

            // Admin linking command
            Commands.slash("amssync", "Admin: Link Discord users to Minecraft players")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(net.dv8tion.jda.api.Permission.MANAGE_SERVER))
                .setGuildOnly(true)
                .addSubcommands(
                    SubcommandData("add", "Link a Discord user to a Minecraft player")
                        .addOption(OptionType.USER, "user", "Discord user to link", true)
                        .addOption(OptionType.STRING, "minecraft_username", "Minecraft username", true),

                    SubcommandData("remove", "Unlink a Discord user")
                        .addOption(OptionType.USER, "user", "Discord user to unlink", true),

                    SubcommandData("list", "Show all current Discord-Minecraft links"),

                    SubcommandData("check", "Check if a user is linked")
                        .addOption(OptionType.USER, "user", "Discord user to check", true)
                )
        )

        // Conditionally add whitelist command (only if handler is registered)
        if (commandHandlers.containsKey("amswhitelist")) {
            commands.add(
                Commands.slash("amswhitelist", "Admin: Manage server whitelist")
                    .setDefaultPermissions(DefaultMemberPermissions.enabledFor(net.dv8tion.jda.api.Permission.MANAGE_SERVER))
                    .setGuildOnly(true)
                    .addSubcommands(
                        SubcommandData("add", "Add a player to the whitelist")
                            .addOption(OptionType.STRING, "minecraft_username", "Minecraft username to whitelist", true),

                        SubcommandData("remove", "Remove a player from the whitelist")
                            .addOption(OptionType.STRING, "minecraft_username", "Minecraft username to remove", true),

                        SubcommandData("list", "Show all whitelisted players"),

                        SubcommandData("check", "Check if a player is whitelisted")
                            .addOption(OptionType.STRING, "minecraft_username", "Minecraft username to check", true)
                    )
            )
        }

        if (guildId.isNotBlank() && guildId != "YOUR_GUILD_ID_HERE") {
            // Register to specific guild (instant)
            val guild = jda?.getGuildById(guildId)
            if (guild != null) {
                guild.updateCommands().addCommands(commands).queue(
                    { plugin.logger.info("Slash commands registered to guild: ${guild.name}") },
                    { error -> plugin.logger.warning("Failed to register slash commands: ${error.message}") }
                )
            } else {
                plugin.logger.warning("Guild ID $guildId not found. Bot may not be in that server.")
            }
        } else {
            // Register globally (takes up to 1 hour)
            jda?.updateCommands()?.addCommands(commands)?.queue(
                { plugin.logger.info("Slash commands registered globally (may take up to 1 hour to appear)") },
                { error -> plugin.logger.warning("Failed to register slash commands: ${error.message}") }
            )
        }
    }

    /**
     * Gracefully shutdown the Discord bot
     */
    fun shutdown() {
        plugin.logger.info("Disconnecting from Discord...")
        jda?.shutdownNow() // Force immediate shutdown to prevent background thread issues
        jda = null
        connected = false
    }

    /**
     * Get the JDA instance (for advanced usage)
     */
    fun getJda(): JDA? = jda
}
