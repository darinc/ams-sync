package io.github.darinc.amssync.discord.commands

import org.bukkit.Server
import java.util.UUID

/** Result of resolving a Minecraft username to an account. */
sealed class ProfileLookup {
    /** Account exists. [uuid] is null on offline-mode servers, where no Mojang lookup is done. */
    data class Found(val name: String, val uuid: UUID?) : ProfileLookup()
    object NotFound : ProfileLookup()
    data class Failed(val reason: String) : ProfileLookup()
}

/** Result of adding a resolved player to the whitelist. */
sealed class WhitelistAddResult {
    data class Added(val name: String, val uuid: UUID) : WhitelistAddResult()
    data class AlreadyWhitelisted(val name: String) : WhitelistAddResult()
    data class Failed(val reason: String) : WhitelistAddResult()
}

/**
 * Whitelist operations that work for players who have never joined the server.
 *
 * With an enforced whitelist, new players can't join until they're whitelisted, so lookups
 * must go through Mojang rather than the server's own player data.
 */
class WhitelistService(private val server: Server) {

    /**
     * Resolve [name] to an account. On online-mode servers this performs a blocking Mojang
     * lookup (cached by the server afterwards), so call it OFF the main thread.
     */
    fun lookupProfile(name: String): ProfileLookup {
        if (!server.onlineMode) {
            return ProfileLookup.Found(name, null)
        }
        return try {
            val profile = server.createProfile(name)
            val completed = profile.complete(false)
            val id = profile.id
            val resolvedName = profile.name
            if (completed && id != null && resolvedName != null) {
                ProfileLookup.Found(resolvedName, id)
            } else {
                ProfileLookup.NotFound
            }
        } catch (e: Exception) {
            ProfileLookup.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Whitelist a player previously resolved by [lookupProfile]. Call on the main thread.
     *
     * Goes through the server by name so the whitelist entry stores the player's name
     * (needed by name-based remove/check), and refuses if the UUID disagrees with the lookup.
     */
    fun addToWhitelist(profile: ProfileLookup.Found): WhitelistAddResult {
        val player = server.getOfflinePlayer(profile.name)
        if (profile.uuid != null && player.uniqueId != profile.uuid) {
            return WhitelistAddResult.Failed(
                "UUID mismatch for ${profile.name} (Mojang ${profile.uuid}, server ${player.uniqueId})"
            )
        }
        val name = player.name ?: profile.name
        if (player.isWhitelisted) {
            return WhitelistAddResult.AlreadyWhitelisted(name)
        }
        player.isWhitelisted = true
        return WhitelistAddResult.Added(name, player.uniqueId)
    }
}
