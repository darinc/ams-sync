package io.github.darinc.amssync.commands

import io.github.darinc.amssync.AMSSyncPlugin
import io.github.darinc.amssync.audit.SecurityEvent
import io.github.darinc.amssync.commands.handlers.AddHandler
import io.github.darinc.amssync.commands.handlers.DiscordHandler
import io.github.darinc.amssync.commands.handlers.LinkHandler
import io.github.darinc.amssync.commands.handlers.ListHandler
import io.github.darinc.amssync.commands.handlers.MetricsHandler
import io.github.darinc.amssync.commands.handlers.PlayersHandler
import io.github.darinc.amssync.commands.handlers.QuickHandler
import io.github.darinc.amssync.commands.handlers.ReloadHandler
import io.github.darinc.amssync.commands.handlers.RemoveHandler
import io.github.darinc.amssync.discord.RateLimitResult
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * Handles the /amssync command for managing Discord-Minecraft user mappings.
 * Routes to specialized handlers for each subcommand.
 */
class AMSSyncCommand(private val plugin: AMSSyncPlugin) : CommandExecutor, TabCompleter {

    private val sessionManager = LinkingSessionManager(plugin)

    private val linkHandler = LinkHandler()
    private val handlers: Map<String, SubcommandHandler> = mapOf(
        "add" to AddHandler(),
        "remove" to RemoveHandler(),
        "list" to ListHandler(),
        "players" to PlayersHandler(),
        "discord" to DiscordHandler(),
        "link" to linkHandler,
        "quick" to QuickHandler(linkHandler),
        "metrics" to MetricsHandler(),
        "reload" to ReloadHandler()
    )

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        val context = CommandContext.create(sender, plugin, sessionManager)

        if (!checkPermission(sender, context)) {
            return true
        }

        if (!checkRateLimit(sender, context)) {
            return true
        }

        if (args.isEmpty()) {
            sendHelp(sender)
            return true
        }

        val subcommand = args[0].lowercase()
        val handler = handlers[subcommand]

        if (handler != null) {
            handler.execute(context, args.drop(1))
        } else {
            sendHelp(sender)
        }

        return true
    }

    private fun checkPermission(sender: CommandSender, context: CommandContext): Boolean {
        if (sender.hasPermission("amssync.admin")) {
            return true
        }

        plugin.auditLogger.logSecurityEvent(
            event = SecurityEvent.PERMISSION_DENIED,
            actor = context.actorName,
            actorType = context.actorType,
            details = mapOf("command" to "amssync")
        )
        sender.sendMessage("§cYou don't have permission to use this command.")
        return false
    }

    private fun checkRateLimit(sender: CommandSender, context: CommandContext): Boolean {
        // Console commands are exempt
        if (sender is ConsoleCommandSender) {
            return true
        }

        val rateLimiter = plugin.rateLimiter ?: return true

        if (sender !is Player) {
            return true
        }

        when (val result = rateLimiter.checkRateLimit(sender.uniqueId.toString())) {
            is RateLimitResult.Cooldown -> {
                plugin.auditLogger.logSecurityEvent(
                    event = SecurityEvent.RATE_LIMITED,
                    actor = context.actorName,
                    actorType = context.actorType,
                    details = mapOf("reason" to "cooldown", "remainingMs" to result.remainingMs)
                )
                sender.sendMessage("§cPlease wait ${String.format("%.1f", result.remainingSeconds)} seconds before using another command.")
                return false
            }
            is RateLimitResult.BurstLimited -> {
                plugin.auditLogger.logSecurityEvent(
                    event = SecurityEvent.RATE_LIMITED,
                    actor = context.actorName,
                    actorType = context.actorType,
                    details = mapOf("reason" to "burst", "retryAfterMs" to result.retryAfterMs)
                )
                sender.sendMessage("§cYou've made too many requests. Please try again in ${String.format("%.0f", result.retryAfterSeconds)} seconds.")
                return false
            }
            is RateLimitResult.Allowed -> {
                return true
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
        sender.sendMessage("§e§lAdmin:")
        sender.sendMessage("  §f/amssync reload §7- Reload config and reconnect Discord")
        sender.sendMessage("")
        sender.sendMessage("§e§lOther Linking Methods:")
        sender.sendMessage("  §f/amssync add <discordId> <mcUsername> §7- Link by Discord ID")
        sender.sendMessage("  §f/amssync link <player#> <discord#> §7- Link by number (requires session)")
        sender.sendMessage("  §f/amssync remove <discordId> §7- Remove a link")
        sender.sendMessage("")
        sender.sendMessage("§7Tip: Use Discord for easiest linking: §f/amssync add @user <mcUsername>")
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
            1 -> handlers.keys.toList().filter { it.startsWith(args[0].lowercase()) }
            else -> {
                val subcommand = args[0].lowercase()
                val handler = handlers[subcommand]
                val context = CommandContext.create(sender, plugin, sessionManager)
                handler?.tabComplete(context, args.drop(1).toList()) ?: emptyList()
            }
        }
    }
}
