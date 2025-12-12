package io.github.darinc.amssync.commands

import io.github.darinc.amssync.AMSSyncPlugin
import io.github.darinc.amssync.audit.ActorType
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Player

/**
 * Interface for subcommand handlers.
 */
interface SubcommandHandler {
    val name: String
    val usage: String

    fun execute(context: CommandContext, args: List<String>)
    fun tabComplete(context: CommandContext, args: List<String>): List<String> = emptyList()
}

/**
 * Context passed to subcommand handlers containing common dependencies.
 */
data class CommandContext(
    val sender: CommandSender,
    val plugin: AMSSyncPlugin,
    val sessionManager: LinkingSessionManager,
    val actorType: ActorType,
    val actorName: String
) {
    companion object {
        fun create(sender: CommandSender, plugin: AMSSyncPlugin, sessionManager: LinkingSessionManager): CommandContext {
            val actorType = when (sender) {
                is Player -> ActorType.MINECRAFT_PLAYER
                is ConsoleCommandSender -> ActorType.CONSOLE
                else -> ActorType.CONSOLE
            }
            val actorName = when (sender) {
                is Player -> "${sender.name} (${sender.uniqueId})"
                else -> "Console"
            }
            return CommandContext(sender, plugin, sessionManager, actorType, actorName)
        }
    }
}
