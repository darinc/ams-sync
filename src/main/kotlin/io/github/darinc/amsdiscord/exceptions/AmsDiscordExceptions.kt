package io.github.darinc.amsdiscord.exceptions

/**
 * Base sealed class for all AMS Discord plugin exceptions.
 *
 * Using a sealed hierarchy provides:
 * - Exhaustive when expressions (compiler-enforced handling)
 * - Type-safe exception categorization
 * - Better IDE support and documentation
 */
sealed class AmsDiscordException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

// ==================== Discord Connection Exceptions ====================

/**
 * Base class for Discord connection and initialization failures.
 *
 * These exceptions occur during the initial connection phase before
 * the bot is fully operational.
 */
sealed class DiscordConnectionException(
    message: String,
    cause: Throwable? = null
) : AmsDiscordException(message, cause)

/**
 * Thrown when Discord bot authentication fails.
 *
 * Common causes:
 * - Invalid bot token in config.yml
 * - Token revoked or bot deleted
 * - Network/firewall blocking authentication
 */
class DiscordAuthenticationException(
    message: String = "Invalid Discord bot token or authentication failed",
    cause: Throwable? = null
) : DiscordConnectionException(message, cause)

/**
 * Thrown when Discord connection/initialization exceeds timeout threshold.
 *
 * @property timeoutMs The timeout duration in milliseconds
 */
class DiscordTimeoutException(
    val timeoutMs: Long,
    message: String = "Discord operation exceeded timeout (${timeoutMs}ms)",
    cause: Throwable? = null
) : DiscordConnectionException(message, cause)

/**
 * Thrown when network-level Discord connection fails.
 *
 * Common causes:
 * - No internet connection
 * - Firewall blocking Discord API
 * - Discord API temporarily unavailable
 */
class DiscordNetworkException(
    message: String,
    cause: Throwable? = null
) : DiscordConnectionException(message, cause)

// ==================== Discord API Exceptions ====================

/**
 * Base class for Discord API operation failures.
 *
 * These exceptions occur when the bot is connected but API operations fail.
 */
sealed class DiscordApiException(
    message: String,
    cause: Throwable? = null
) : AmsDiscordException(message, cause)

/**
 * Thrown when Discord API rate limit is exceeded.
 *
 * @property retryAfterMs Milliseconds to wait before retrying
 */
class DiscordRateLimitException(
    val retryAfterMs: Long,
    message: String = "Discord API rate limit exceeded (retry after ${retryAfterMs}ms)",
    cause: Throwable? = null
) : DiscordApiException(message, cause)

/**
 * Thrown when the bot lacks required Discord permissions.
 *
 * @property requiredPermission The missing permission name
 */
class DiscordPermissionException(
    val requiredPermission: String,
    message: String = "Missing Discord permission: $requiredPermission",
    cause: Throwable? = null
) : DiscordApiException(message, cause)

/**
 * Thrown when slash command registration fails.
 *
 * Common causes:
 * - Invalid guild ID
 * - Bot not in the specified guild
 * - Malformed command definitions
 */
class DiscordCommandRegistrationException(
    message: String,
    cause: Throwable? = null
) : DiscordApiException(message, cause)

/**
 * Thrown when circuit breaker is open and requests are blocked.
 *
 * Indicates Discord API is experiencing failures and requests are being
 * fast-failed to prevent cascading failures.
 */
class CircuitBreakerOpenException(
    message: String = "Discord circuit breaker is OPEN - requests blocked to prevent cascading failures",
    cause: Throwable? = null
) : DiscordApiException(message, cause)

// ==================== MCMMO Query Exceptions ====================

/**
 * Base class for MCMMO data access failures.
 */
sealed class McmmoQueryException(
    message: String,
    cause: Throwable? = null
) : AmsDiscordException(message, cause)

/**
 * Thrown when a leaderboard query exceeds the configured timeout.
 *
 * @property durationMs How long the query took before timing out
 */
class LeaderboardTimeoutException(
    val durationMs: Long,
    message: String = "Leaderboard query exceeded timeout (${durationMs}ms)",
    cause: Throwable? = null
) : McmmoQueryException(message, cause)

/**
 * Thrown when player data is not found or player has no MCMMO stats.
 *
 * @property playerName The player name that was not found
 */
class PlayerDataNotFoundException(
    val playerName: String,
    message: String = "Player not found or has no MCMMO data: $playerName",
    cause: Throwable? = null
) : McmmoQueryException(message, cause)

/**
 * Thrown when an invalid skill name is provided.
 *
 * @property skillName The invalid skill name
 * @property validSkills List of valid skill names
 */
class InvalidSkillException(
    val skillName: String,
    val validSkills: List<String>,
    message: String = "Invalid skill: $skillName (valid skills: ${validSkills.joinToString(", ")})",
    cause: Throwable? = null
) : McmmoQueryException(message, cause)

// ==================== User Mapping Exceptions ====================

/**
 * Base class for user mapping management failures.
 */
sealed class UserMappingException(
    message: String,
    cause: Throwable? = null
) : AmsDiscordException(message, cause)

/**
 * Thrown when attempting to create a duplicate mapping.
 *
 * @property discordId The Discord ID that's already mapped
 * @property existingMapping The current Minecraft username mapping
 */
class DuplicateMappingException(
    val discordId: String,
    val existingMapping: String,
    message: String = "Discord ID $discordId is already linked to $existingMapping",
    cause: Throwable? = null
) : UserMappingException(message, cause)

/**
 * Thrown when a requested mapping is not found.
 *
 * @property identifier The Discord ID or Minecraft username that was not found
 */
class MappingNotFoundException(
    val identifier: String,
    message: String = "No mapping found for: $identifier",
    cause: Throwable? = null
) : UserMappingException(message, cause)

/**
 * Thrown when a Discord ID has an invalid format.
 *
 * @property discordId The invalid Discord ID
 */
class InvalidDiscordIdException(
    val discordId: String,
    message: String = "Invalid Discord ID format: $discordId (must be 17-19 digit snowflake)",
    cause: Throwable? = null
) : UserMappingException(message, cause)

// ==================== Configuration Exceptions ====================

/**
 * Base class for configuration and validation failures.
 */
sealed class ConfigurationException(
    message: String,
    cause: Throwable? = null
) : AmsDiscordException(message, cause)

/**
 * Thrown when a required configuration value is missing.
 *
 * @property configKey The missing configuration key path
 */
class MissingConfigurationException(
    val configKey: String,
    message: String = "Missing required configuration: $configKey",
    cause: Throwable? = null
) : ConfigurationException(message, cause)

/**
 * Thrown when a configuration value is invalid.
 *
 * @property configKey The configuration key path
 * @property configValue The invalid value
 */
class InvalidConfigurationException(
    val configKey: String,
    val configValue: String,
    message: String = "Invalid configuration value for $configKey: $configValue",
    cause: Throwable? = null
) : ConfigurationException(message, cause)

/**
 * Thrown when the Discord bot token has an invalid format.
 *
 * Discord bot tokens follow a specific format:
 * - Base64-encoded user ID (variable length)
 * - A period separator
 * - 6-character timestamp
 * - A period separator
 * - 27+ character HMAC
 *
 * Example: MTIzNDU2Nzg5MDEyMzQ1Njc4.XXXXXX.XXXXXXXXXXXXXXXXXXXXXXXXXXX
 *
 * @property maskedToken First 10 chars + "..." for security
 */
class InvalidBotTokenException(
    val maskedToken: String,
    message: String = "Invalid Discord bot token format. Token appears malformed: $maskedToken... " +
        "(expected format: [base64].[timestamp].[hmac])",
    cause: Throwable? = null
) : ConfigurationException(message, cause)

/**
 * Thrown when the Discord guild ID has an invalid format.
 *
 * Discord guild IDs (snowflakes) must be 17-19 digit numeric strings.
 *
 * @property guildId The invalid guild ID
 */
class InvalidGuildIdException(
    val guildId: String,
    message: String = "Invalid Discord guild ID format: '$guildId' (must be 17-19 digit snowflake)",
    cause: Throwable? = null
) : ConfigurationException(message, cause)
