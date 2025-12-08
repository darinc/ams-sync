package io.github.darinc.amssync.commands

import io.github.darinc.amssync.AMSSyncPlugin
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * Handles the /amssync command for managing Discord-Minecraft user mappings
 */
class AMSSyncCommand(private val plugin: AMSSyncPlugin) : CommandExecutor, TabCompleter {

    private val sessionManager = LinkingSessionManager(plugin)

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (!sender.hasPermission("amssync.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command.")
            return true
        }

        if (args.isEmpty()) {
            sendHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "add" -> handleAdd(sender, args)
            "remove" -> handleRemove(sender, args)
            "list" -> handleList(sender)
            "players" -> handlePlayers(sender)
            "discord" -> handleDiscordList(sender, args)
            "link" -> handleLinkByNumber(sender, args)
            "quick" -> handleQuick(sender, args)
            "metrics" -> handleMetrics(sender)
            else -> sendHelp(sender)
        }

        return true
    }

    private fun handleAdd(sender: CommandSender, args: Array<out String>) {
        if (args.size < 3) {
            sender.sendMessage("§cUsage: /amssync add <discordId> <minecraftUsername>")
            return
        }

        val discordId = args[1]
        val minecraftUsername = args[2]

        // Validate Discord ID (should be all digits, 17-19 characters)
        if (!discordId.matches(Regex("\\d{17,19}"))) {
            sender.sendMessage("§cInvalid Discord ID. Must be a numeric ID (17-19 digits).")
            sender.sendMessage("§7Find it by right-clicking a user in Discord → Copy ID (Developer Mode required)")
            return
        }

        // Add the mapping
        plugin.userMappingService.addMapping(discordId, minecraftUsername)
        plugin.userMappingService.saveMappings()

        sender.sendMessage("§aSuccessfully linked Discord ID §f$discordId §ato Minecraft user §f$minecraftUsername")
    }

    private fun handleRemove(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendMessage("§cUsage: /amssync remove <discordId>")
            return
        }

        val discordId = args[1]

        if (plugin.userMappingService.removeMappingByDiscordId(discordId)) {
            plugin.userMappingService.saveMappings()
            sender.sendMessage("§aSuccessfully removed mapping for Discord ID §f$discordId")
        } else {
            sender.sendMessage("§cNo mapping found for Discord ID §f$discordId")
        }
    }

    private fun handleList(sender: CommandSender) {
        val mappings = plugin.userMappingService.getAllMappings()

        if (mappings.isEmpty()) {
            sender.sendMessage("§eNo user mappings configured yet.")
            sender.sendMessage("§7Use §f/amssync add <discordId> <minecraftUsername> §7to add one.")
            return
        }

        sender.sendMessage("§6§l=== Discord-Minecraft User Mappings (${mappings.size}) ===")

        // Try to fetch Discord member names
        val jda = plugin.discordManager.getJda()
        if (jda == null) {
            // Discord bot not connected - show IDs only
            sender.sendMessage("§7(Discord bot offline - showing IDs only)")
            mappings.forEach { (discordId, minecraftUsername) ->
                sender.sendMessage("§8$discordId §7→ §f$minecraftUsername")
            }
            return
        }

        val guildId = plugin.config.getString("discord.guild-id")
        if (guildId.isNullOrBlank() || guildId == "YOUR_GUILD_ID_HERE") {
            // Guild not configured - show IDs only
            mappings.forEach { (discordId, minecraftUsername) ->
                sender.sendMessage("§8$discordId §7→ §f$minecraftUsername")
            }
            return
        }

        val guild = jda.getGuildById(guildId)
        if (guild == null) {
            // Guild not found - show IDs only
            mappings.forEach { (discordId, minecraftUsername) ->
                sender.sendMessage("§8$discordId §7→ §f$minecraftUsername")
            }
            return
        }

        sender.sendMessage("§7Loading Discord member names...")

        // Load all guild members to get their display names
        guild.loadMembers().onSuccess { members ->
            Bukkit.getScheduler().runTask(plugin, Runnable {
                // Create a map of Discord ID -> Display Name
                val memberNames = members.associate { it.id to it.effectiveName }

                sender.sendMessage("")
                mappings.forEach { (discordId, minecraftUsername) ->
                    val discordName = memberNames[discordId]
                    if (discordName != null) {
                        sender.sendMessage("§b$discordName §7→ §f$minecraftUsername")
                    } else {
                        // Member not found (left server?) - show ID
                        sender.sendMessage("§8$discordId §7→ §f$minecraftUsername §8(member not found)")
                    }
                }
            })
        }.onError { _ ->
            Bukkit.getScheduler().runTask(plugin, Runnable {
                // Failed to load members - show IDs only
                sender.sendMessage("§7(Failed to load Discord members - showing IDs)")
                mappings.forEach { (discordId, minecraftUsername) ->
                    sender.sendMessage("§8$discordId §7→ §f$minecraftUsername")
                }
            })
        }
    }

    private fun handlePlayers(sender: CommandSender) {
        sender.sendMessage("§6§l=== Minecraft Players ===")
        sender.sendMessage("")

        var index = 1
        val playerNumbers = mutableMapOf<Int, String>()

        // Show online players
        val onlinePlayers = Bukkit.getOnlinePlayers().sortedBy { it.name }
        if (onlinePlayers.isNotEmpty()) {
            sender.sendMessage("§a§lOnline (${onlinePlayers.size}):")
            onlinePlayers.forEach { player ->
                val linkedStatus = if (plugin.userMappingService.isMinecraftLinked(player.name)) "§a✓ Linked" else "§8✗ Not Linked"
                val discordInfo = if (plugin.userMappingService.isMinecraftLinked(player.name)) {
                    val discordId = plugin.userMappingService.getDiscordId(player.name)
                    " §7(Discord: $discordId)"
                } else ""
                sender.sendMessage("  §7[$index] $linkedStatus §f${player.name}$discordInfo")
                playerNumbers[index] = player.name
                index++
            }
        }

        // Show whitelisted players
        val whitelistedPlayers = Bukkit.getWhitelistedPlayers()
            .filter { it.name != null && !onlinePlayers.any { p -> p.name == it.name } }
            .sortedBy { it.name }

        if (whitelistedPlayers.isNotEmpty()) {
            sender.sendMessage("")
            val countMessage = if (whitelistedPlayers.size <= 15) {
                "§b§lWhitelisted (${whitelistedPlayers.size} players):"
            } else {
                "§b§lWhitelisted (showing first 15 of ${whitelistedPlayers.size}):"
            }
            sender.sendMessage(countMessage)
            whitelistedPlayers.take(15).forEach { offlinePlayer ->
                val name = offlinePlayer.name ?: "Unknown"
                val linkedStatus = if (plugin.userMappingService.isMinecraftLinked(name)) "§a✓ Linked" else "§8✗ Not Linked"
                val discordInfo = if (plugin.userMappingService.isMinecraftLinked(name)) {
                    val discordId = plugin.userMappingService.getDiscordId(name)
                    " §7(Discord: $discordId)"
                } else ""
                sender.sendMessage("  §7[$index] $linkedStatus §f$name$discordInfo")
                playerNumbers[index] = name
                index++
            }
            if (whitelistedPlayers.size > 15) {
                sender.sendMessage("  §7... and ${whitelistedPlayers.size - 15} more")
            }
        }

        // Store player mappings in session
        val session = sessionManager.getOrCreateSession(sender)
        session.setPlayerMappings(playerNumbers)

        sender.sendMessage("")
        sender.sendMessage("§e§l--- Commands ---")
        sender.sendMessage("§7/amssync add <discordId> <mcUsername> §f- Link by Discord ID")
        sender.sendMessage("§7/amssync discord §f- Show Discord members with numbers")
        sender.sendMessage("§7/amssync link <player#> <discord#> §f- Link by number")
        sender.sendMessage("§7/amssync quick [player#] [discord#] §f- Quick linking workflow")
        sender.sendMessage("")
        sender.sendMessage("§aSession stored for ${session.getTimeRemaining()} seconds")
    }

    private fun handleDiscordList(sender: CommandSender, @Suppress("UNUSED_PARAMETER") args: Array<out String> = arrayOf()) {
        val jda = plugin.discordManager.getJda()
        if (jda == null) {
            sender.sendMessage("§cDiscord bot is not connected!")
            return
        }

        // Get the guild
        val guildId = plugin.config.getString("discord.guild-id")
        if (guildId.isNullOrBlank() || guildId == "YOUR_GUILD_ID_HERE") {
            sender.sendMessage("§cGuild ID not configured in config.yml")
            return
        }

        val guild = jda.getGuildById(guildId)
        if (guild == null) {
            sender.sendMessage("§cCould not find Discord guild with ID: $guildId")
            return
        }

        sender.sendMessage("§6§l=== Discord Members (${guild.name}) ===")
        sender.sendMessage("§7Loading members...")

        // Load members asynchronously
        guild.loadMembers().onSuccess { members ->
            Bukkit.getScheduler().runTask(plugin, Runnable {
                sender.sendMessage("")
                val sortedMembers = members
                    .filter { !it.user.isBot } // Exclude bots
                    .sortedBy { it.effectiveName }

                // Build Discord number mappings
                val discordNumbers = mutableMapOf<Int, DiscordData>()

                sender.sendMessage("§b§lDiscord Members (${sortedMembers.size} users):")
                sortedMembers.take(20).forEachIndexed { index, member ->
                    val num = index + 1
                    discordNumbers[num] = DiscordData(member.id, member.effectiveName)

                    val linkedStatus = if (plugin.userMappingService.isDiscordLinked(member.id)) {
                        val mcName = plugin.userMappingService.getMinecraftUsername(member.id)
                        "§a✓ → $mcName"
                    } else {
                        "§8✗ Not Linked"
                    }
                    val displayName = member.effectiveName
                    val username = member.user.name
                    val discordId = member.id

                    sender.sendMessage("  §7[${num}] $linkedStatus §f$displayName")
                    sender.sendMessage("       §7Discord: §f$username §8(ID: $discordId)")
                }

                if (sortedMembers.size > 20) {
                    sender.sendMessage("  §7... and ${sortedMembers.size - 20} more")
                }

                // Store Discord mappings in session
                val session = sessionManager.getOrCreateSession(sender)
                session.setDiscordMappings(discordNumbers)

                sender.sendMessage("")
                sender.sendMessage("§e§l--- Link a Member ---")
                sender.sendMessage("§7/amssync add <fullDiscordId> <mcUsername>")
                sender.sendMessage("§7/amssync link <player#> <discord#> §f- Link by number")
                sender.sendMessage("§7/amssync quick [player#] [discord#] §f- Quick linking workflow")
                sender.sendMessage("")
                sender.sendMessage("§aSession stored for ${session.getTimeRemaining()} seconds")
            })
        }.onError { error ->
            Bukkit.getScheduler().runTask(plugin, Runnable {
                sender.sendMessage("§cFailed to load Discord members: ${error.message}")
                plugin.logger.warning("Failed to load Discord members: ${error.message}")
            })
        }
    }

    private fun handleLinkByNumber(sender: CommandSender, args: Array<out String>) {
        if (args.size < 3) {
            sender.sendMessage("§cUsage: /amssync link <player#> <discord#>")
            sender.sendMessage("§7Run §f/amssync players §7and §f/amssync discord §7first to get numbers")
            return
        }

        // Get the session
        val session = sessionManager.getSession(sender)
        if (session == null) {
            sender.sendMessage("§cNo active session found!")
            sender.sendMessage("§7Run §f/amssync players §7and §f/amssync discord §7first")
            return
        }

        // Parse player number
        val playerNum = args[1].toIntOrNull()
        if (playerNum == null) {
            sender.sendMessage("§cInvalid player number: ${args[1]}")
            return
        }

        // Parse discord number
        val discordNum = args[2].toIntOrNull()
        if (discordNum == null) {
            sender.sendMessage("§cInvalid discord number: ${args[2]}")
            return
        }

        // Look up player name
        val playerName = session.getPlayerName(playerNum)
        if (playerName == null) {
            sender.sendMessage("§cPlayer number $playerNum not found in session")
            sender.sendMessage("§7Run §f/amssync players §7to refresh the list")
            return
        }

        // Look up discord data
        val discordData = session.getDiscordData(discordNum)
        if (discordData == null) {
            sender.sendMessage("§cDiscord number $discordNum not found in session")
            sender.sendMessage("§7Run §f/amssync discord §7to refresh the list")
            return
        }

        // Create the link
        sender.sendMessage("§aLinking §f$playerName §a(#$playerNum) to §f${discordData.displayName} §a(#$discordNum)...")
        plugin.userMappingService.addMapping(discordData.id, playerName)
        plugin.userMappingService.saveMappings()

        sender.sendMessage("§aSuccessfully linked §f$playerName §a→ §f${discordData.displayName}§a!")
    }

    private fun handleQuick(sender: CommandSender, args: Array<out String>) {
        // If arguments provided, try to link immediately
        if (args.size >= 3) {
            // User wants to link: /amslink quick <player#> <discord#>
            handleLinkByNumber(sender, arrayOf("link", args[1], args[2]))
            return
        }

        // No arguments - show both lists in compact format
        sender.sendMessage("§6§l=== Quick Link ===")
        sender.sendMessage("§7Loading players and Discord members...")
        sender.sendMessage("")

        // Get players
        val onlinePlayers = Bukkit.getOnlinePlayers().sortedBy { it.name }
        val whitelistedPlayers = Bukkit.getWhitelistedPlayers()
            .filter { it.name != null && !onlinePlayers.any { p -> p.name == it.name } }
            .sortedBy { it.name }

        val allPlayers = (onlinePlayers.map { it.name } + whitelistedPlayers.mapNotNull { it.name }).take(20)
        val playerNumbers = mutableMapOf<Int, String>()

        // Display players
        sender.sendMessage("§e§lMinecraft Players:")
        allPlayers.forEachIndexed { index, name ->
            val num = index + 1
            playerNumbers[num] = name
            val linkedStatus = if (plugin.userMappingService.isMinecraftLinked(name)) "§a✓" else "§8✗"
            sender.sendMessage("  §7[$num] $linkedStatus §f$name")
        }

        if (onlinePlayers.size + whitelistedPlayers.size > 20) {
            sender.sendMessage("  §7... and ${onlinePlayers.size + whitelistedPlayers.size - 20} more")
        }

        sender.sendMessage("")
        sender.sendMessage("§7Loading Discord members...")

        // Get Discord members
        val jda = plugin.discordManager.getJda()
        if (jda == null) {
            sender.sendMessage("§cDiscord bot is not connected!")
            // Store player session anyway
            val session = sessionManager.getOrCreateSession(sender)
            session.setPlayerMappings(playerNumbers)
            return
        }

        val guildId = plugin.config.getString("discord.guild-id")
        if (guildId.isNullOrBlank() || guildId == "YOUR_GUILD_ID_HERE") {
            sender.sendMessage("§cGuild ID not configured in config.yml")
            return
        }

        val guild = jda.getGuildById(guildId)
        if (guild == null) {
            sender.sendMessage("§cCould not find Discord guild with ID: $guildId")
            return
        }

        // Load Discord members asynchronously
        guild.loadMembers().onSuccess { members ->
            Bukkit.getScheduler().runTask(plugin, Runnable {
                val sortedMembers = members
                    .filter { !it.user.isBot }
                    .sortedBy { it.effectiveName }

                val discordNumbers = mutableMapOf<Int, DiscordData>()

                sender.sendMessage("§b§lDiscord Members:")
                sortedMembers.take(20).forEachIndexed { index, member ->
                    val num = index + 1
                    discordNumbers[num] = DiscordData(member.id, member.effectiveName)

                    val linkedStatus = if (plugin.userMappingService.isDiscordLinked(member.id)) "§a✓" else "§8✗"
                    sender.sendMessage("  §7[$num] $linkedStatus §f${member.effectiveName} §8(${member.user.name})")
                }

                if (sortedMembers.size > 20) {
                    sender.sendMessage("  §7... and ${sortedMembers.size - 20} more")
                }

                // Store session
                val session = sessionManager.getOrCreateSession(sender)
                session.setPlayerMappings(playerNumbers)
                session.setDiscordMappings(discordNumbers)

                sender.sendMessage("")
                sender.sendMessage("§a§l✓ Quick link ready!")
                sender.sendMessage("§eType: §f/amssync quick <player#> <discord#>")
                sender.sendMessage("§7Example: §f/amssync quick 1 1 §7to link player #1 to Discord member #1")
                sender.sendMessage("")
                sender.sendMessage("§7Session expires in ${session.getTimeRemaining()} seconds")
            })
        }.onError { error ->
            Bukkit.getScheduler().runTask(plugin, Runnable {
                sender.sendMessage("§cFailed to load Discord members: ${error.message}")
                plugin.logger.warning("Failed to load Discord members: ${error.message}")
            })
        }
    }

    private fun handleMetrics(sender: CommandSender) {
        val snapshot = plugin.errorMetrics.getSnapshot()

        sender.sendMessage("§6§l=== AMSSync Metrics ===")
        sender.sendMessage("§7Uptime: §f${snapshot.uptimeFormatted}")
        sender.sendMessage("")

        // Discord API stats
        sender.sendMessage("§e§lDiscord API:")
        sender.sendMessage("  §aSuccess: §f${snapshot.discordApiStats.successCount}")
        sender.sendMessage("  §cFailures: §f${snapshot.discordApiStats.failureCount}")
        sender.sendMessage("  §6Rejected: §f${snapshot.discordApiStats.rejectedCount}")
        snapshot.discordApiStats.successRate?.let {
            val color = when {
                it >= 0.99 -> "§a"
                it >= 0.95 -> "§e"
                else -> "§c"
            }
            sender.sendMessage("  §7Success Rate: $color${String.format("%.1f", it * 100)}%")
        }

        // Circuit breaker stats
        sender.sendMessage("")
        sender.sendMessage("§e§lCircuit Breaker:")
        sender.sendMessage("  §cTrips: §f${snapshot.circuitBreakerStats.tripCount}")
        sender.sendMessage("  §aRecoveries: §f${snapshot.circuitBreakerStats.recoveryCount}")

        // Current circuit state
        plugin.discordApiWrapper?.getCircuitState()?.let { state ->
            val stateColor = when (state) {
                io.github.darinc.amssync.discord.CircuitBreaker.State.CLOSED -> "§a"
                io.github.darinc.amssync.discord.CircuitBreaker.State.OPEN -> "§c"
                io.github.darinc.amssync.discord.CircuitBreaker.State.HALF_OPEN -> "§e"
            }
            sender.sendMessage("  §7Current State: $stateColor$state")
        }

        // Connection stats
        sender.sendMessage("")
        sender.sendMessage("§e§lConnections:")
        sender.sendMessage("  §7Attempts: §f${snapshot.connectionStats.attemptCount}")
        sender.sendMessage("  §aSuccess: §f${snapshot.connectionStats.successCount}")
        sender.sendMessage("  §cFailures: §f${snapshot.connectionStats.failureCount}")

        // Command stats (if any)
        if (snapshot.commandStats.isNotEmpty()) {
            sender.sendMessage("")
            sender.sendMessage("§e§lCommands:")
            snapshot.commandStats.forEach { (cmd, stats) ->
                val rate = stats.successRate?.let { String.format("%.0f%%", it * 100) } ?: "N/A"
                val latency = stats.avgLatencyMs?.let { String.format("%.0fms", it) } ?: "N/A"
                sender.sendMessage("  §f$cmd§7: ${stats.successCount}/${stats.successCount + stats.failureCount} ($rate), avg: $latency")
            }
        }

        // Error types (if any)
        if (snapshot.errorStats.isNotEmpty()) {
            sender.sendMessage("")
            sender.sendMessage("§e§lErrors by Type:")
            snapshot.errorStats.forEach { (type, count) ->
                sender.sendMessage("  §c$type§7: $count")
            }
        }
    }

    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage("§6§l=== AMSSync Commands ===")
        sender.sendMessage("")
        sender.sendMessage("§a§l⚡ Recommended:")
        sender.sendMessage("  §f/amssync quick §7- Quick 2-step linking (shows both lists)")
        sender.sendMessage("  §f/amssync quick <player#> <discord#> §7- Direct link with numbers")
        sender.sendMessage("")
        sender.sendMessage("§e§lViewing:")
        sender.sendMessage("  §f/amssync list §7- List all current mappings")
        sender.sendMessage("  §f/amssync players §7- Show Minecraft players only")
        sender.sendMessage("  §f/amssync discord §7- Show Discord members only")
        sender.sendMessage("  §f/amssync metrics §7- Show plugin health metrics")
        sender.sendMessage("")
        sender.sendMessage("§e§lOther Linking Methods:")
        sender.sendMessage("  §f/amssync add <discordId> <mcUsername> §7- Link by Discord ID")
        sender.sendMessage("  §f/amssync link <player#> <discord#> §7- Link by number (requires session)")
        sender.sendMessage("  §f/amssync remove <discordId> §7- Remove a link")
        sender.sendMessage("")
        sender.sendMessage("§7💡 Tip: Use Discord for easiest linking: §f/amssync add @user <mcUsername>")
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String>? {
        if (!sender.hasPermission("amssync.admin")) {
            return emptyList()
        }

        return when (args.size) {
            1 -> listOf("quick", "add", "remove", "list", "players", "discord", "link", "metrics").filter { it.startsWith(args[0].lowercase()) }
            2 -> when (args[0].lowercase()) {
                "remove" -> plugin.userMappingService.getAllMappings().keys.toList()
                else -> emptyList()
            }
            3 -> when (args[0].lowercase()) {
                "add" -> Bukkit.getOnlinePlayers().map { it.name } +
                         Bukkit.getWhitelistedPlayers().mapNotNull { it.name }
                else -> emptyList()
            }
            else -> emptyList()
        }
    }
}
