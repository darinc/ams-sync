package io.github.darinc.amssync.discord.commands

import io.github.darinc.amssync.linking.UserMappingService
import io.github.darinc.amssync.validation.Validators

/**
 * Resolves flexible username inputs to Minecraft usernames.
 *
 * Supports multiple input formats:
 * - null/blank: Uses the invoker's linked Minecraft account
 * - Discord mention: `<@123456789012345678>` or `<@!123456789012345678>`
 * - Discord ID: `123456789012345678` (17-19 digits)
 * - Minecraft username: Direct passthrough after validation
 *
 * @property userMappingService Service for looking up Discord-to-Minecraft mappings
 */
class UsernameResolver(private val userMappingService: UserMappingService) {

    companion object {
        private val DISCORD_ID_REGEX = Regex("^\\d{17,19}$")
    }

    /**
     * Resolve a flexible username input to a Minecraft username.
     *
     * @param usernameInput The username parameter (can be null, Discord mention, Discord ID, or MC username)
     * @param invokerDiscordId The Discord ID of the command invoker
     * @return Minecraft username
     * @throws IllegalArgumentException if username cannot be resolved
     */
    fun resolve(usernameInput: String?, invokerDiscordId: String): String {
        // Case 1: No username provided - use invoker's Discord ID
        if (usernameInput.isNullOrBlank()) {
            return userMappingService.getMinecraftUsername(invokerDiscordId)
                ?: throw IllegalArgumentException(
                    "Your Discord account is not linked to a Minecraft account.\n" +
                    "Contact an administrator to use `/amslink add`."
                )
        }

        // Case 2: Username provided - flexible detection
        var cleanedInput = usernameInput.trim()

        // Strip Discord mention format: <@123...> or <@!123...>
        if (cleanedInput.startsWith("<@") && cleanedInput.endsWith(">")) {
            cleanedInput = cleanedInput.removePrefix("<@").removeSuffix(">")
            if (cleanedInput.startsWith("!")) {
                cleanedInput = cleanedInput.removePrefix("!")
            }
        }

        // Check if it's a Discord ID (17-19 digit snowflake)
        if (cleanedInput.matches(DISCORD_ID_REGEX)) {
            return userMappingService.getMinecraftUsername(cleanedInput)
                ?: throw IllegalArgumentException(
                    "Discord user <@$cleanedInput> is not linked to a Minecraft account."
                )
        }

        // Validate Minecraft username format
        if (!Validators.isValidMinecraftUsername(cleanedInput)) {
            throw IllegalArgumentException(
                "${Validators.getMinecraftUsernameError(cleanedInput)}\n" +
                "Minecraft usernames must be 3-16 characters with only letters, numbers, and underscores."
            )
        }

        return cleanedInput
    }
}
