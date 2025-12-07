package io.github.darinc.amsdiscord.linking

import io.github.darinc.amsdiscord.AmsDiscordPlugin
import io.github.darinc.amsdiscord.exceptions.DuplicateMappingException
import io.github.darinc.amsdiscord.exceptions.InvalidDiscordIdException
import io.github.darinc.amsdiscord.exceptions.MappingNotFoundException
import org.bukkit.configuration.ConfigurationSection

/**
 * Manages mappings between Discord user IDs and Minecraft usernames.
 *
 * Provides bidirectional mapping with validation and exception-based error handling.
 */
class UserMappingService(private val plugin: AmsDiscordPlugin) {

    // Discord ID -> Minecraft Username
    private val discordToMinecraft = mutableMapOf<String, String>()

    // Minecraft Username -> Discord ID (for reverse lookups)
    private val minecraftToDiscord = mutableMapOf<String, String>()

    /**
     * Validates that a Discord ID has the correct snowflake format.
     *
     * Discord snowflakes are 17-19 digit unsigned integers.
     *
     * @param discordId The Discord ID to validate
     * @throws InvalidDiscordIdException if the ID format is invalid
     */
    private fun validateDiscordId(discordId: String) {
        if (!discordId.matches(Regex("^\\d{17,19}$"))) {
            throw InvalidDiscordIdException(discordId)
        }
    }

    /**
     * Load user mappings from config.yml
     */
    fun loadMappings() {
        discordToMinecraft.clear()
        minecraftToDiscord.clear()

        val mappingsSection = plugin.config.getConfigurationSection("user-mappings")
        if (mappingsSection != null) {
            for (key in mappingsSection.getKeys(false)) {
                val minecraftUsername = mappingsSection.getString(key)
                if (minecraftUsername != null) {
                    addMapping(key, minecraftUsername)
                }
            }
        }

        plugin.logger.info("Loaded ${discordToMinecraft.size} user mapping(s)")
    }

    /**
     * Save user mappings to config.yml
     */
    fun saveMappings() {
        // Clear existing mappings section
        plugin.config.set("user-mappings", null)

        // Save all mappings
        for ((discordId, minecraftUsername) in discordToMinecraft) {
            plugin.config.set("user-mappings.$discordId", minecraftUsername)
        }

        plugin.saveConfig()
        plugin.logger.info("Saved ${discordToMinecraft.size} user mapping(s)")
    }

    /**
     * Add a new mapping between Discord ID and Minecraft username.
     *
     * If the Discord ID is already mapped, the old mapping will be replaced.
     * If the Minecraft username is already mapped to a different Discord ID,
     * the old mapping will be removed.
     *
     * @param discordId The Discord user ID (17-19 digit snowflake)
     * @param minecraftUsername The Minecraft username
     * @param allowReplace If false, throws exception if mapping already exists (default: true)
     * @throws InvalidDiscordIdException if Discord ID format is invalid
     * @throws DuplicateMappingException if allowReplace is false and mapping exists
     */
    fun addMapping(
        discordId: String,
        minecraftUsername: String,
        allowReplace: Boolean = true
    ) {
        // Validate Discord ID format
        validateDiscordId(discordId)

        // Check for duplicate if not allowing replace
        if (!allowReplace && discordToMinecraft.containsKey(discordId)) {
            throw DuplicateMappingException(
                discordId = discordId,
                existingMapping = discordToMinecraft[discordId]!!
            )
        }

        // Remove old mapping if it exists
        discordToMinecraft[discordId]?.let { oldUsername ->
            minecraftToDiscord.remove(oldUsername)
            if (oldUsername != minecraftUsername) {
                plugin.logger.info("Replaced mapping for Discord $discordId: $oldUsername -> $minecraftUsername")
            }
        }

        // Remove old Discord ID if Minecraft username was mapped to someone else
        minecraftToDiscord[minecraftUsername]?.let { oldDiscordId ->
            if (oldDiscordId != discordId) {
                discordToMinecraft.remove(oldDiscordId)
                plugin.logger.info("Removed old Discord ID $oldDiscordId for Minecraft username $minecraftUsername")
            }
        }

        // Add new mapping
        discordToMinecraft[discordId] = minecraftUsername
        minecraftToDiscord[minecraftUsername] = discordId

        plugin.logger.fine("Added mapping: Discord $discordId -> Minecraft $minecraftUsername")
    }

    /**
     * Remove a mapping by Discord ID.
     *
     * @param discordId The Discord user ID
     * @param throwIfNotFound If true, throws exception if mapping not found (default: false)
     * @return true if mapping was removed, false if not found (when throwIfNotFound is false)
     * @throws MappingNotFoundException if throwIfNotFound is true and mapping not found
     * @throws InvalidDiscordIdException if Discord ID format is invalid
     */
    fun removeMappingByDiscordId(discordId: String, throwIfNotFound: Boolean = false): Boolean {
        validateDiscordId(discordId)

        val minecraftUsername = discordToMinecraft.remove(discordId)
        if (minecraftUsername != null) {
            minecraftToDiscord.remove(minecraftUsername)
            plugin.logger.fine("Removed mapping for Discord ID: $discordId")
            return true
        }

        if (throwIfNotFound) {
            throw MappingNotFoundException(discordId)
        }
        return false
    }

    /**
     * Remove a mapping by Minecraft username.
     *
     * @param minecraftUsername The Minecraft username
     * @param throwIfNotFound If true, throws exception if mapping not found (default: false)
     * @return true if mapping was removed, false if not found (when throwIfNotFound is false)
     * @throws MappingNotFoundException if throwIfNotFound is true and mapping not found
     */
    fun removeMappingByMinecraftUsername(minecraftUsername: String, throwIfNotFound: Boolean = false): Boolean {
        val discordId = minecraftToDiscord.remove(minecraftUsername)
        if (discordId != null) {
            discordToMinecraft.remove(discordId)
            plugin.logger.fine("Removed mapping for Minecraft username: $minecraftUsername")
            return true
        }

        if (throwIfNotFound) {
            throw MappingNotFoundException(minecraftUsername)
        }
        return false
    }

    /**
     * Get Minecraft username for a Discord ID (nullable version).
     *
     * @param discordId The Discord user ID
     * @return The Minecraft username, or null if not mapped
     */
    fun getMinecraftUsername(discordId: String): String? {
        return discordToMinecraft[discordId]
    }

    /**
     * Get Minecraft username for a Discord ID (exception-throwing version).
     *
     * @param discordId The Discord user ID
     * @return The Minecraft username
     * @throws MappingNotFoundException if no mapping exists for this Discord ID
     * @throws InvalidDiscordIdException if Discord ID format is invalid
     */
    fun getMinecraftUsernameOrThrow(discordId: String): String {
        validateDiscordId(discordId)
        return discordToMinecraft[discordId]
            ?: throw MappingNotFoundException(discordId)
    }

    /**
     * Get Discord ID for a Minecraft username (nullable version).
     *
     * @param minecraftUsername The Minecraft username
     * @return The Discord ID, or null if not mapped
     */
    fun getDiscordId(minecraftUsername: String): String? {
        return minecraftToDiscord[minecraftUsername]
    }

    /**
     * Get Discord ID for a Minecraft username (exception-throwing version).
     *
     * @param minecraftUsername The Minecraft username
     * @return The Discord ID
     * @throws MappingNotFoundException if no mapping exists for this Minecraft username
     */
    fun getDiscordIdOrThrow(minecraftUsername: String): String {
        return minecraftToDiscord[minecraftUsername]
            ?: throw MappingNotFoundException(minecraftUsername)
    }

    /**
     * Check if a Discord ID is linked
     */
    fun isDiscordLinked(discordId: String): Boolean {
        return discordToMinecraft.containsKey(discordId)
    }

    /**
     * Check if a Minecraft username is linked
     */
    fun isMinecraftLinked(minecraftUsername: String): Boolean {
        return minecraftToDiscord.containsKey(minecraftUsername)
    }

    /**
     * Get the total number of mappings
     */
    fun getMappingCount(): Int {
        return discordToMinecraft.size
    }

    /**
     * Get all mappings (Discord ID -> Minecraft Username)
     */
    fun getAllMappings(): Map<String, String> {
        return discordToMinecraft.toMap()
    }
}
