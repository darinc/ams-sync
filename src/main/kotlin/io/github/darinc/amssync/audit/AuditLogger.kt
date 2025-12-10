package io.github.darinc.amssync.audit

import io.github.darinc.amssync.AMSSyncPlugin
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Audit logger for tracking admin actions and security events.
 * Logs to both server console (INFO level) and a dedicated audit.log file.
 */
class AuditLogger(private val plugin: AMSSyncPlugin) {

    private val auditFile: File by lazy {
        File(plugin.dataFolder, "audit.log").also {
            if (!it.exists()) {
                it.createNewFile()
            }
        }
    }

    private val dateFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
        .withZone(ZoneId.systemDefault())

    /**
     * Log an admin action to both console and audit file.
     *
     * @param action The type of action being performed
     * @param actor The identifier of who performed the action
     * @param actorType The type of actor (Discord user, Minecraft player, or console)
     * @param target The target of the action (e.g., username being linked)
     * @param success Whether the action succeeded
     * @param details Additional details about the action
     */
    fun logAdminAction(
        action: AuditAction,
        actor: String,
        actorType: ActorType,
        target: String? = null,
        success: Boolean = true,
        details: Map<String, Any> = emptyMap()
    ) {
        val timestamp = Instant.now()
        val formattedTime = dateFormatter.format(timestamp)

        // Build console message
        val statusIcon = if (success) "+" else "-"
        val targetStr = target?.let { " -> $it" } ?: ""
        val detailsStr = if (details.isNotEmpty()) " ${details.entries.joinToString(", ") { "${it.key}=${it.value}" }}" else ""
        val consoleMessage = "[AUDIT] [$statusIcon] ${action.displayName}: $actor ($actorType)$targetStr$detailsStr"

        // Log to console at INFO level
        plugin.logger.info(consoleMessage)

        // Build JSON line for audit file
        val jsonLine = buildJsonLine(
            timestamp = formattedTime,
            action = action.name,
            actor = actor,
            actorType = actorType.name,
            target = target,
            success = success,
            details = details
        )

        // Append to audit file
        try {
            auditFile.appendText("$jsonLine\n")
        } catch (e: Exception) {
            plugin.logger.warning("Failed to write to audit log: ${e.message}")
        }
    }

    /**
     * Log a security event (permission denial, rate limiting, invalid input).
     */
    fun logSecurityEvent(
        event: SecurityEvent,
        actor: String,
        actorType: ActorType,
        details: Map<String, Any> = emptyMap()
    ) {
        logAdminAction(
            action = event.toAuditAction(),
            actor = actor,
            actorType = actorType,
            success = false,
            details = details
        )
    }

    private fun buildJsonLine(
        timestamp: String,
        action: String,
        actor: String,
        actorType: String,
        target: String?,
        success: Boolean,
        details: Map<String, Any>
    ): String {
        val parts = mutableListOf(
            """"timestamp":"$timestamp"""",
            """"action":"$action"""",
            """"actor":"${escapeJson(actor)}"""",
            """"actorType":"$actorType"""",
            """"success":$success"""
        )

        target?.let {
            parts.add(""""target":"${escapeJson(it)}"""")
        }

        if (details.isNotEmpty()) {
            val detailsJson = details.entries.joinToString(",") { (key, value) ->
                val jsonValue = when (value) {
                    is String -> "\"${escapeJson(value)}\""
                    is Number -> value.toString()
                    is Boolean -> value.toString()
                    else -> "\"${escapeJson(value.toString())}\""
                }
                """"$key":$jsonValue"""
            }
            parts.add(""""details":{$detailsJson}""")
        }

        return "{${parts.joinToString(",")}}"
    }

    private fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}

/**
 * Types of auditable admin actions.
 */
enum class AuditAction(val displayName: String) {
    LINK_USER("Link User"),
    UNLINK_USER("Unlink User"),
    LIST_MAPPINGS("List Mappings"),
    CHECK_USER("Check User"),
    PERMISSION_DENIED("Permission Denied"),
    RATE_LIMITED("Rate Limited"),
    INVALID_INPUT("Invalid Input"),
    WHITELIST_ADD("Whitelist Add"),
    WHITELIST_REMOVE("Whitelist Remove"),
    WHITELIST_LIST("Whitelist List"),
    WHITELIST_CHECK("Whitelist Check")
}

/**
 * Types of actors that can perform actions.
 */
enum class ActorType {
    DISCORD_USER,
    MINECRAFT_PLAYER,
    CONSOLE
}

/**
 * Security events that trigger audit logging.
 */
enum class SecurityEvent {
    PERMISSION_DENIED,
    RATE_LIMITED,
    INVALID_INPUT;

    fun toAuditAction(): AuditAction = when (this) {
        PERMISSION_DENIED -> AuditAction.PERMISSION_DENIED
        RATE_LIMITED -> AuditAction.RATE_LIMITED
        INVALID_INPUT -> AuditAction.INVALID_INPUT
    }
}
