package io.github.darinc.amssync.commands.handlers

import io.github.darinc.amssync.audit.AuditAction
import io.github.darinc.amssync.commands.CommandContext
import io.github.darinc.amssync.commands.SubcommandHandler

/**
 * Handles the /amssync remove <discordId> command.
 */
class RemoveHandler : SubcommandHandler {
    override val name = "remove"
    override val usage = "/amssync remove <discordId>"

    override fun execute(context: CommandContext, args: List<String>) {
        val sender = context.sender
        val plugin = context.plugin
        val actorType = context.actorType
        val actorName = context.actorName

        if (args.isEmpty()) {
            sender.sendMessage("§cUsage: $usage")
            return
        }

        val discordId = args[0]

        // Get the linked username before removal for audit logging
        val linkedUsername = plugin.userMappingService.getMinecraftUsername(discordId)

        if (plugin.userMappingService.removeMappingByDiscordId(discordId)) {
            plugin.userMappingService.saveMappings()

            plugin.auditLogger.logAdminAction(
                action = AuditAction.UNLINK_USER,
                actor = actorName,
                actorType = actorType,
                target = linkedUsername,
                success = true,
                details = mapOf("discordId" to discordId)
            )

            sender.sendMessage("§aSuccessfully removed mapping for Discord ID §f$discordId")
        } else {
            plugin.auditLogger.logAdminAction(
                action = AuditAction.UNLINK_USER,
                actor = actorName,
                actorType = actorType,
                target = discordId,
                success = false,
                details = mapOf("reason" to "not_found")
            )

            sender.sendMessage("§cNo mapping found for Discord ID §f$discordId")
        }
    }

    override fun tabComplete(context: CommandContext, args: List<String>): List<String> {
        return when (args.size) {
            1 -> context.plugin.userMappingService.getAllMappings().keys.toList()
            else -> emptyList()
        }
    }
}
