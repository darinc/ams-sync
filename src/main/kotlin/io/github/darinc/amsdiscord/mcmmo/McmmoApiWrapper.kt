package io.github.darinc.amsdiscord.mcmmo

import com.gmail.nossr50.datatypes.skills.PrimarySkillType
import com.gmail.nossr50.mcMMO
import com.gmail.nossr50.util.player.UserManager
import io.github.darinc.amsdiscord.AmsDiscordPlugin
import io.github.darinc.amsdiscord.exceptions.InvalidSkillException
import io.github.darinc.amsdiscord.exceptions.PlayerDataNotFoundException
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Wrapper for MCMMO API calls with performance optimizations.
 *
 * @property plugin The parent plugin instance
 * @property maxPlayersToScan Maximum number of players to scan for leaderboards (prevents timeout)
 * @property cacheTtlMs Cache time-to-live in milliseconds for leaderboard results
 */
class McmmoApiWrapper(
    private val plugin: AmsDiscordPlugin,
    private val maxPlayersToScan: Int = 1000,
    private val cacheTtlMs: Long = 60000L // 60 seconds default
) {

    /**
     * Cached leaderboard data with timestamp
     */
    private data class CachedLeaderboard(
        val data: List<Pair<String, Int>>,
        val timestamp: Long
    )

    // Thread-safe cache for leaderboard results
    private val leaderboardCache = ConcurrentHashMap<String, CachedLeaderboard>()

    /**
     * Get all skill levels for a player.
     *
     * @param playerName The Minecraft username
     * @return Map of skill name to level
     * @throws PlayerDataNotFoundException if player not found or has no MCMMO data
     */
    fun getPlayerStats(playerName: String): Map<String, Int> {
        val offlinePlayer = getOfflinePlayer(playerName)
        if (offlinePlayer == null) {
            plugin.logger.warning("Player lookup failed for '$playerName' - player not found in server records")
            throw PlayerDataNotFoundException(playerName)
        }

        plugin.logger.info("Found player '$playerName' with UUID ${offlinePlayer.uniqueId}, hasPlayedBefore=${offlinePlayer.hasPlayedBefore()}")

        // For flatfile storage, must load profile from database manager
        // UserManager only returns LOADED profiles, not from disk
        // See: https://github.com/mcMMO-Dev/mcMMO/issues/5163
        val profile = mcMMO.getDatabaseManager().loadPlayerProfile(offlinePlayer.uniqueId)

        if (!profile.isLoaded) {
            plugin.logger.warning("MCMMO profile not loaded for player '$playerName' (UUID: ${offlinePlayer.uniqueId})")
            throw PlayerDataNotFoundException(playerName)
        }

        // Calculate power level by summing all skill levels
        val powerLevel = PrimarySkillType.values()
            .filter { !it.isChildSkill }
            .sumOf { profile.getSkillLevel(it) }

        plugin.logger.info("Successfully loaded MCMMO profile for '$playerName', power level: $powerLevel")

        return PrimarySkillType.values()
            .filter { !it.isChildSkill } // Exclude child skills
            .associate { skill ->
                skill.name to profile.getSkillLevel(skill)
            }
    }

    /**
     * Get a specific skill level for a player.
     *
     * @param playerName The Minecraft username
     * @param skillName The skill name (case-insensitive)
     * @return The skill level
     * @throws PlayerDataNotFoundException if player not found or has no MCMMO data
     * @throws InvalidSkillException if skill name is not valid
     */
    fun getPlayerSkillLevel(playerName: String, skillName: String): Int {
        val offlinePlayer = getOfflinePlayer(playerName)
            ?: throw PlayerDataNotFoundException(playerName)

        // Load profile from database manager
        val profile = mcMMO.getDatabaseManager().loadPlayerProfile(offlinePlayer.uniqueId)
        if (!profile.isLoaded) {
            throw PlayerDataNotFoundException(playerName)
        }

        val skill = parseSkillType(skillName) // Throws InvalidSkillException if invalid
        return profile.getSkillLevel(skill)
    }

    /**
     * Get player's power level (sum of all skill levels).
     *
     * @param playerName The Minecraft username
     * @return The power level
     * @throws PlayerDataNotFoundException if player not found or has no MCMMO data
     */
    fun getPowerLevel(playerName: String): Int {
        val offlinePlayer = getOfflinePlayer(playerName)
            ?: throw PlayerDataNotFoundException(playerName)

        // Load profile from database manager
        val profile = mcMMO.getDatabaseManager().loadPlayerProfile(offlinePlayer.uniqueId)
        if (!profile.isLoaded) {
            throw PlayerDataNotFoundException(playerName)
        }

        // Calculate power level by summing all skill levels
        return PrimarySkillType.values()
            .filter { !it.isChildSkill }
            .sumOf { profile.getSkillLevel(it) }
    }

    /**
     * Get leaderboard for a specific skill with caching and query limits.
     *
     * @param skillName The skill to get leaderboard for
     * @param limit Maximum number of entries to return (default 10)
     * @return List of pairs (player name, level) ordered by level descending
     * @throws InvalidSkillException if skill name is not valid
     */
    fun getLeaderboard(skillName: String, limit: Int = 10): List<Pair<String, Int>> {
        val skill = parseSkillType(skillName) // Throws InvalidSkillException if invalid

        val cacheKey = "skill:${skill.name}:$limit"

        // Check cache first
        val cached = leaderboardCache[cacheKey]
        if (cached != null && System.currentTimeMillis() - cached.timestamp < cacheTtlMs) {
            plugin.logger.fine("Returning cached leaderboard for ${skill.name}")
            return cached.data
        }

        plugin.logger.fine("Generating leaderboard for ${skill.name} (scanning up to $maxPlayersToScan players)")

        // Get all offline players with hard limit
        val allPlayers = Bukkit.getOfflinePlayers()
        val playersToScan = allPlayers.take(maxPlayersToScan)

        if (allPlayers.size > maxPlayersToScan) {
            plugin.logger.warning(
                "Server has ${allPlayers.size} players, only scanning first $maxPlayersToScan for leaderboard. " +
                "Increase 'mcmmo.leaderboard.max-players-to-scan' in config.yml for more complete results."
            )
        }

        // Build leaderboard with safe null handling
        val leaderboard = playersToScan
            .mapNotNull { offlinePlayer ->
                val name = offlinePlayer.name ?: return@mapNotNull null

                // Load profile from database manager
                val profile = mcMMO.getDatabaseManager().loadPlayerProfile(offlinePlayer.uniqueId)
                if (!profile.isLoaded) return@mapNotNull null

                val level = profile.getSkillLevel(skill)

                if (level > 0) {
                    name to level
                } else {
                    null
                }
            }
            .sortedByDescending { it.second }
            .take(limit)

        // Cache the result
        leaderboardCache[cacheKey] = CachedLeaderboard(leaderboard, System.currentTimeMillis())

        return leaderboard
    }

    /**
     * Get power level leaderboard with caching and query limits.
     *
     * @param limit Maximum number of entries to return (default 10)
     * @return List of pairs (player name, power level) ordered by power level descending
     */
    fun getPowerLevelLeaderboard(limit: Int = 10): List<Pair<String, Int>> {
        val cacheKey = "power:$limit"

        // Check cache first
        val cached = leaderboardCache[cacheKey]
        if (cached != null && System.currentTimeMillis() - cached.timestamp < cacheTtlMs) {
            plugin.logger.fine("Returning cached power level leaderboard")
            return cached.data
        }

        plugin.logger.fine("Generating power level leaderboard (scanning up to $maxPlayersToScan players)")

        // Get all offline players with hard limit
        val allPlayers = Bukkit.getOfflinePlayers()
        val playersToScan = allPlayers.take(maxPlayersToScan)

        if (allPlayers.size > maxPlayersToScan) {
            plugin.logger.warning(
                "Server has ${allPlayers.size} players, only scanning first $maxPlayersToScan for leaderboard. " +
                "Increase 'mcmmo.leaderboard.max-players-to-scan' in config.yml for more complete results."
            )
        }

        // Build leaderboard with safe null handling
        val leaderboard = playersToScan
            .mapNotNull { offlinePlayer ->
                val name = offlinePlayer.name ?: return@mapNotNull null

                // Load profile from database manager
                val profile = mcMMO.getDatabaseManager().loadPlayerProfile(offlinePlayer.uniqueId)
                if (!profile.isLoaded) return@mapNotNull null

                // Calculate power level by summing all skill levels
                val powerLevel = PrimarySkillType.values()
                    .filter { !it.isChildSkill }
                    .sumOf { profile.getSkillLevel(it) }

                if (powerLevel > 0) {
                    name to powerLevel
                } else {
                    null
                }
            }
            .sortedByDescending { it.second }
            .take(limit)

        // Cache the result
        leaderboardCache[cacheKey] = CachedLeaderboard(leaderboard, System.currentTimeMillis())

        return leaderboard
    }

    /**
     * Parse skill name to PrimarySkillType.
     *
     * @param skillName The skill name to parse (case-insensitive)
     * @return The matching PrimarySkillType
     * @throws InvalidSkillException if skill name is not valid
     */
    fun parseSkillType(skillName: String): PrimarySkillType {
        val skill = try {
            // Try exact match first
            PrimarySkillType.valueOf(skillName.uppercase())
        } catch (e: IllegalArgumentException) {
            // Try case-insensitive search
            PrimarySkillType.values().find {
                it.name.equals(skillName, ignoreCase = true)
            }
        }

        return skill ?: throw InvalidSkillException(
            skillName = skillName,
            validSkills = getAllSkillNames()
        )
    }

    /**
     * Get all valid skill names
     */
    fun getAllSkillNames(): List<String> {
        return PrimarySkillType.values()
            .filter { !it.isChildSkill }
            .map { it.name }
    }

    /**
     * Get offline player by name.
     *
     * Searches through all players to find one with matching name.
     * This is necessary because Bukkit.getOfflinePlayer(name) doesn't actually
     * search by name - it creates a new OfflinePlayer with an offline-mode UUID,
     * which won't match the player's actual UUID if they haven't joined recently.
     */
    private fun getOfflinePlayer(playerName: String): OfflinePlayer? {
        plugin.logger.info("Searching for player: '$playerName'")

        // First check online players (most efficient)
        val onlinePlayer = Bukkit.getOnlinePlayers().find {
            it.name.equals(playerName, ignoreCase = true)
        }
        if (onlinePlayer != null) {
            plugin.logger.info("Found '$playerName' in online players")
            return onlinePlayer
        }

        // Search through all offline players for exact name match
        val allOfflinePlayers = Bukkit.getOfflinePlayers()
        plugin.logger.info("Searching through ${allOfflinePlayers.size} offline players for '$playerName'")

        val offlinePlayer = allOfflinePlayers.find {
            it.name?.equals(playerName, ignoreCase = true) == true
        }

        if (offlinePlayer == null) {
            plugin.logger.warning("Player '$playerName' not found in ${allOfflinePlayers.size} offline players")
            plugin.logger.info("Available player names: ${allOfflinePlayers.mapNotNull { it.name }.take(10).joinToString(", ")}...")
            return null
        }

        // Check if player has ever played
        if (!offlinePlayer.hasPlayedBefore() && !offlinePlayer.isOnline) {
            plugin.logger.warning("Player '$playerName' found but hasPlayedBefore=false")
            return null
        }

        plugin.logger.info("Found offline player '$playerName' (UUID: ${offlinePlayer.uniqueId})")
        return offlinePlayer
    }
}
