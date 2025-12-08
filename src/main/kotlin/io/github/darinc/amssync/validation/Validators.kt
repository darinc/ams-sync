package io.github.darinc.amssync.validation

/**
 * Input validation utilities for Minecraft usernames and Discord IDs.
 */
object Validators {
    /**
     * Valid Minecraft username pattern:
     * - 3-16 characters
     * - Alphanumeric and underscores only
     */
    private val MC_USERNAME_REGEX = Regex("^[a-zA-Z0-9_]{3,16}$")

    /**
     * Valid Discord snowflake ID pattern:
     * - 17-19 digits (Discord snowflakes are 64-bit integers)
     */
    private val DISCORD_ID_REGEX = Regex("^\\d{17,19}$")

    /**
     * Validate a Minecraft username.
     *
     * @param name The username to validate
     * @return true if the username matches Minecraft's naming rules
     */
    fun isValidMinecraftUsername(name: String): Boolean {
        return MC_USERNAME_REGEX.matches(name)
    }

    /**
     * Validate a Discord snowflake ID.
     *
     * @param id The Discord ID to validate
     * @return true if the ID is a valid snowflake format
     */
    fun isValidDiscordId(id: String): Boolean {
        return DISCORD_ID_REGEX.matches(id)
    }

    /**
     * Get a user-friendly error message for invalid Minecraft username.
     *
     * @param name The invalid username
     * @return Error message explaining the validation failure
     */
    fun getMinecraftUsernameError(name: String): String {
        return when {
            name.isEmpty() -> "Username cannot be empty"
            name.length < 3 -> "Username must be at least 3 characters (got ${name.length})"
            name.length > 16 -> "Username cannot exceed 16 characters (got ${name.length})"
            else -> "Username can only contain letters, numbers, and underscores"
        }
    }

    /**
     * Get a user-friendly error message for invalid Discord ID.
     *
     * @param id The invalid Discord ID
     * @return Error message explaining the validation failure
     */
    fun getDiscordIdError(id: String): String {
        return when {
            id.isEmpty() -> "Discord ID cannot be empty"
            !id.all { it.isDigit() } -> "Discord ID must contain only digits"
            id.length < 17 -> "Discord ID must be at least 17 digits (got ${id.length})"
            id.length > 19 -> "Discord ID cannot exceed 19 digits (got ${id.length})"
            else -> "Invalid Discord ID format"
        }
    }
}
