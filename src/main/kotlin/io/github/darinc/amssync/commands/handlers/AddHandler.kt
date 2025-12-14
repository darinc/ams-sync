package io.github.darinc.amssync.commands.handlers

import io.github.darinc.amssync.audit.AuditAction
import io.github.darinc.amssync.audit.SecurityEvent
import io.github.darinc.amssync.commands.CommandContext
import io.github.darinc.amssync.commands.SubcommandHandler
import io.github.darinc.amssync.validation.Validators
import org.bukkit.Bukkit

/**
 * Handles the /amssync add <discordId> <minecraftUsername> command.
 */
class AddHandler : SubcommandHandler {
    override val name = "add"
    override val usage = "/amssync add <discordId> <minecraftUsername>"

    override fun execute(context: CommandContext, args: List<String>) {
        val sender = context.sender
        val plugin = context.plugin
        val actorType = context.actorType
        val actorName = context.actorName

        if (args.size < 2) {
            sender.sendMessage("§cUsage: $usage")
            return
        }

        val discordId = args[0]
        val minecraftUsername = args[1]

        // Validate Discord ID
        if (!Validators.isValidDiscordId(discordId)) {
            plugin.auditLogger.logSecurityEvent(
                event = SecurityEvent.INVALID_INPUT,
                actor = actorName,
                actorType = actorType,
                details = mapOf("field" to "discordId", "value" to discordId, "error" to Validators.getDiscordIdError(discordId))
            )
            sender.sendMessage("§cInvalid Discord ID: ${Validators.getDiscordIdError(discordId)}")
            sender.sendMessage("§7Find it by right-clicking a user in Discord -> Copy ID (Developer Mode required)")
            return
        }

        // Validate Minecraft username
        if (!Validators.isValidMinecraftUsername(minecraftUsername)) {
            plugin.auditLogger.logSecurityEvent(
                event = SecurityEvent.INVALID_INPUT,
                actor = actorName,
                actorType = actorType,
                details = mapOf(
                    "field" to "minecraftUsername",
                    "value" to minecraftUsername,
                    "error" to Validators.getMinecraftUsernameError(minecraftUsername)
                )
            )
            sender.sendMessage("§cInvalid Minecraft username: ${Validators.getMinecraftUsernameError(minecraftUsername)}")
            return
        }

        // Add the mapping
        plugin.userMappingService.addMapping(discordId, minecraftUsername)
        plugin.userMappingService.saveMappings()

        plugin.auditLogger.logAdminAction(
            action = AuditAction.LINK_USER,
            actor = actorName,
            actorType = actorType,
            target = minecraftUsername,
            success = true,
            details = mapOf("discordId" to discordId)
        )

        sender.sendMessage("§aSuccessfully linked Discord ID §f$discordId §ato Minecraft user §f$minecraftUsername")
    }

    override fun tabComplete(context: CommandContext, args: List<String>): List<String> {
        return when (args.size) {
            2 -> Bukkit.getOnlinePlayers().map { it.name } +
                Bukkit.getWhitelistedPlayers().mapNotNull { it.name }
            else -> emptyList()
        }
    }
}
