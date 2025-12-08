package io.github.darinc.amsdiscord.config

import io.github.darinc.amsdiscord.exceptions.InvalidBotTokenException
import io.github.darinc.amsdiscord.exceptions.InvalidGuildIdException
import java.util.logging.Logger

/**
 * Validates Discord configuration values before attempting connection.
 *
 * Pre-validating configuration catches common errors early with clear messages,
 * rather than waiting for cryptic JDA exceptions during connection.
 */
object ConfigValidator {

    /**
     * Discord snowflake regex pattern.
     * Snowflakes are 17-19 digit unsigned integers representing timestamps.
     */
    private val SNOWFLAKE_PATTERN = Regex("^\\d{17,19}$")

    /**
     * Discord bot token regex pattern.
     *
     * Bot tokens have the format: [base64_user_id].[timestamp].[hmac]
     * - Part 1: Base64-encoded bot user ID (variable length, typically 18-26 chars)
     * - Part 2: 6-character timestamp
     * - Part 3: 27+ character HMAC signature
     *
     * Example: MTIzNDU2Nzg5MDEyMzQ1Njc4.XXXXXX.XXXXXXXXXXXXXXXXXXXXXXXXXXX
     *
     * Note: This is a format check only - it does not validate if the token is actually valid.
     */
    private val BOT_TOKEN_PATTERN = Regex("^[A-Za-z0-9_-]{18,}\\.[A-Za-z0-9_-]{6}\\.[A-Za-z0-9_-]{27,}$")

    /**
     * Validates a Discord bot token format.
     *
     * @param token The bot token to validate
     * @return true if the token format appears valid
     */
    fun isValidBotTokenFormat(token: String): Boolean {
        return token.isNotBlank() && BOT_TOKEN_PATTERN.matches(token)
    }

    /**
     * Validates a Discord guild ID (snowflake) format.
     *
     * @param guildId The guild ID to validate
     * @return true if the guild ID format is valid
     */
    fun isValidGuildIdFormat(guildId: String): Boolean {
        return guildId.isNotBlank() && SNOWFLAKE_PATTERN.matches(guildId)
    }

    /**
     * Validates a Discord ID (user, channel, role, etc.) format.
     *
     * @param discordId The Discord ID to validate
     * @return true if the Discord ID format is valid
     */
    fun isValidDiscordIdFormat(discordId: String): Boolean {
        return discordId.isNotBlank() && SNOWFLAKE_PATTERN.matches(discordId)
    }

    /**
     * Masks a bot token for safe logging.
     * Shows first 10 characters only to help with debugging without exposing the full token.
     *
     * @param token The token to mask
     * @return Masked token string safe for logging
     */
    fun maskToken(token: String): String {
        return if (token.length > 10) {
            token.take(10) + "..."
        } else {
            "[invalid token]"
        }
    }

    /**
     * Validates bot token and throws exception if invalid format.
     *
     * @param token The bot token to validate
     * @throws InvalidBotTokenException if the token format is invalid
     */
    fun validateBotToken(token: String) {
        if (!isValidBotTokenFormat(token)) {
            throw InvalidBotTokenException(maskToken(token))
        }
    }

    /**
     * Validates guild ID and throws exception if invalid format.
     *
     * @param guildId The guild ID to validate
     * @throws InvalidGuildIdException if the guild ID format is invalid
     */
    fun validateGuildId(guildId: String) {
        if (!isValidGuildIdFormat(guildId)) {
            throw InvalidGuildIdException(guildId)
        }
    }

    /**
     * Result of configuration validation.
     */
    data class ValidationResult(
        val valid: Boolean,
        val warnings: List<String> = emptyList(),
        val errors: List<String> = emptyList()
    ) {
        companion object {
            fun success(warnings: List<String> = emptyList()) = ValidationResult(true, warnings)
            fun failure(errors: List<String>) = ValidationResult(false, errors = errors)
        }
    }

    /**
     * Validates all Discord configuration and returns detailed results.
     *
     * @param token The bot token
     * @param guildId The guild ID (can be blank for global commands)
     * @param logger Optional logger for detailed output
     * @return ValidationResult with any warnings or errors found
     */
    fun validateDiscordConfig(
        token: String,
        guildId: String,
        logger: Logger? = null
    ): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // Validate token
        when {
            token.isBlank() || token == "YOUR_BOT_TOKEN_HERE" -> {
                errors.add("Discord bot token not configured. Set 'discord.token' in config.yml")
            }
            !isValidBotTokenFormat(token) -> {
                errors.add("Discord bot token format appears invalid (${maskToken(token)}). " +
                    "Expected format: [base64].[timestamp].[hmac]")
            }
        }

        // Validate guild ID (optional but recommended)
        when {
            guildId.isBlank() || guildId == "YOUR_GUILD_ID_HERE" -> {
                warnings.add("Guild ID not configured. Slash commands will register globally (may take up to 1 hour)")
            }
            !isValidGuildIdFormat(guildId) -> {
                errors.add("Discord guild ID format is invalid: '$guildId'. Must be a 17-19 digit snowflake")
            }
        }

        // Log results if logger provided
        logger?.let { log ->
            errors.forEach { log.severe(it) }
            warnings.forEach { log.warning(it) }
        }

        return if (errors.isEmpty()) {
            ValidationResult.success(warnings)
        } else {
            ValidationResult.failure(errors)
        }
    }
}
