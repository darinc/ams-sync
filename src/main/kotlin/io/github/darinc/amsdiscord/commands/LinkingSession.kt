package io.github.darinc.amsdiscord.commands

import org.bukkit.command.CommandSender
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Manages linking sessions for command senders.
 * Each session stores number mappings for players and Discord members
 * to enable easy number-based linking.
 */
class LinkingSessionManager(private val plugin: Plugin) {

    private val sessions = ConcurrentHashMap<String, LinkingSession>()
    private val sessionTimeout = TimeUnit.MINUTES.toMillis(5) // 5 minutes

    init {
        // Schedule cleanup task every minute
        plugin.server.scheduler.runTaskTimerAsynchronously(plugin, Runnable {
            cleanupExpiredSessions()
        }, 20L * 60, 20L * 60) // Run every 60 seconds
    }

    /**
     * Gets or creates a session for the given sender
     */
    fun getOrCreateSession(sender: CommandSender): LinkingSession {
        val key = getSenderKey(sender)
        val now = System.currentTimeMillis()

        // Check for existing valid session first
        val existing = sessions[key]
        if (existing != null && !existing.isExpired(now)) {
            existing.updateLastAccess(now)
            return existing
        }

        // Create new session - use putIfAbsent for thread safety
        val newSession = LinkingSession()
        val previous = sessions.putIfAbsent(key, newSession)

        // If another thread beat us to it, use their session if still valid
        return if (previous != null && !previous.isExpired(now)) {
            previous.updateLastAccess(now)
            previous
        } else {
            // Either no previous, or previous was expired - use our new session
            sessions[key] = newSession
            newSession
        }
    }

    /**
     * Gets an existing session for the sender, or null if none exists or expired
     */
    fun getSession(sender: CommandSender): LinkingSession? {
        val key = getSenderKey(sender)
        val now = System.currentTimeMillis()

        val session = sessions[key]
        return if (session != null && !session.isExpired(now)) {
            session.updateLastAccess(now)
            session
        } else {
            null
        }
    }

    /**
     * Clears the session for the given sender
     */
    fun clearSession(sender: CommandSender) {
        val key = getSenderKey(sender)
        sessions.remove(key)
    }

    /**
     * Removes all expired sessions
     */
    private fun cleanupExpiredSessions() {
        val now = System.currentTimeMillis()
        sessions.entries.removeIf { it.value.isExpired(now) }
    }

    /**
     * Gets a unique key for the command sender
     */
    private fun getSenderKey(sender: CommandSender): String {
        return if (sender is org.bukkit.entity.Player) {
            sender.uniqueId.toString()
        } else {
            "CONSOLE"
        }
    }
}

/**
 * Data class to store Discord member information
 */
data class DiscordData(val id: String, val displayName: String)

/**
 * Represents a linking session for a single user
 */
class LinkingSession {
    private var lastAccessTime = System.currentTimeMillis()
    private val sessionTimeout = TimeUnit.MINUTES.toMillis(5)

    // Number -> Minecraft Username
    private val playerMappings = ConcurrentHashMap<Int, String>()

    // Number -> Discord Data (ID + Display Name)
    private val discordMappings = ConcurrentHashMap<Int, DiscordData>()

    fun updateLastAccess(time: Long = System.currentTimeMillis()) {
        lastAccessTime = time
    }

    fun isExpired(now: Long = System.currentTimeMillis()): Boolean {
        return (now - lastAccessTime) > sessionTimeout
    }

    fun getTimeRemaining(): Long {
        val elapsed = System.currentTimeMillis() - lastAccessTime
        val remaining = sessionTimeout - elapsed
        return TimeUnit.MILLISECONDS.toSeconds(remaining).coerceAtLeast(0)
    }

    // Player mappings
    fun setPlayerMappings(mappings: Map<Int, String>) {
        playerMappings.clear()
        playerMappings.putAll(mappings)
        updateLastAccess()
    }

    fun getPlayerName(number: Int): String? {
        return playerMappings[number]
    }

    fun hasPlayerMappings(): Boolean {
        return playerMappings.isNotEmpty()
    }

    // Discord mappings
    fun setDiscordMappings(mappings: Map<Int, DiscordData>) {
        discordMappings.clear()
        discordMappings.putAll(mappings)
        updateLastAccess()
    }

    fun getDiscordId(number: Int): String? {
        return discordMappings[number]?.id
    }

    fun getDiscordName(number: Int): String? {
        return discordMappings[number]?.displayName
    }

    fun getDiscordData(number: Int): DiscordData? {
        return discordMappings[number]
    }

    fun hasDiscordMappings(): Boolean {
        return discordMappings.isNotEmpty()
    }

    // Clear all data
    fun clear() {
        playerMappings.clear()
        discordMappings.clear()
    }
}
