package io.github.darinc.amssync.config

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.InputStreamReader
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.logging.Logger

/**
 * Handles automatic migration of config.yml to add new config keys
 * while preserving user values and comments.
 *
 * ## Migration Strategy
 *
 * 1. Check config-version in user config vs default config
 * 2. If user version is older, perform migration:
 *    a. Create backup of user config
 *    b. Load default config as template (with comments)
 *    c. Walk through all paths in default config
 *    d. For each path, keep user value if exists, else use default
 *    e. Write merged result using template structure (preserves comments)
 *    f. Update config-version
 * 3. Log all changes for user visibility
 */
class ConfigMigrator(
    private val plugin: JavaPlugin,
    private val logger: Logger
) {
    companion object {
        const val CONFIG_VERSION_KEY = "config-version"
        const val CURRENT_CONFIG_VERSION = 3
    }

    /**
     * Result of a migration operation.
     */
    sealed class MigrationResult {
        /** No migration needed - config is current */
        object UpToDate : MigrationResult()

        /** Migration completed successfully */
        data class Migrated(
            val fromVersion: Int,
            val toVersion: Int,
            val addedKeys: List<String>,
            val backupPath: String
        ) : MigrationResult()

        /** Migration failed */
        data class Failed(val reason: String, val exception: Exception? = null) : MigrationResult()

        /** First-time setup - no existing config */
        object FreshInstall : MigrationResult()
    }

    /**
     * Check if migration is needed and perform it if so.
     *
     * @return MigrationResult indicating what happened
     */
    fun migrateIfNeeded(): MigrationResult {
        val configFile = File(plugin.dataFolder, "config.yml")

        // If no config exists, let Bukkit create it fresh
        if (!configFile.exists()) {
            return MigrationResult.FreshInstall
        }

        // Load current user config
        val userConfig = try {
            YamlConfiguration.loadConfiguration(configFile)
        } catch (e: Exception) {
            return MigrationResult.Failed("Failed to load config: ${e.message}", e)
        }

        // Load current user config version
        val userVersion = userConfig.getInt(CONFIG_VERSION_KEY, 0)

        // If already current, no migration needed
        if (userVersion >= CURRENT_CONFIG_VERSION) {
            logger.fine("Config is up to date (version $userVersion)")
            return MigrationResult.UpToDate
        }

        // Perform migration
        return try {
            performMigration(configFile, userConfig, userVersion)
        } catch (e: Exception) {
            logger.severe("Config migration failed: ${e.message}")
            MigrationResult.Failed("Migration error: ${e.message}", e)
        }
    }

    /**
     * Perform the actual migration.
     */
    private fun performMigration(
        configFile: File,
        userConfig: YamlConfiguration,
        fromVersion: Int
    ): MigrationResult {
        logger.info("Migrating config from version $fromVersion to $CURRENT_CONFIG_VERSION")

        // Step 1: Create backup
        val backupPath = createBackup(configFile)
        logger.info("Created config backup: $backupPath")

        // Step 2: Load default config template (preserves comments as raw lines)
        val defaultLines = loadDefaultConfigLines()

        // Step 3: Parse user values (without comments)
        val userValues = extractAllValues(userConfig)

        // Step 3a: Apply version-specific migrations to user values
        val migratedValues = applyVersionMigrations(userValues, fromVersion)

        // Step 4: Load default config to find added keys
        val defaultConfig = loadDefaultConfig()
        val defaultValues = extractAllValues(defaultConfig)

        // Step 5: Find what keys were added
        val addedKeys = findAddedKeys(migratedValues, defaultValues)

        // Step 6: Merge - replace default values with user values where they exist
        val mergedLines = mergeConfigs(defaultLines, migratedValues)

        // Step 7: Append user-mappings section if it exists
        val finalLines = appendUserMappings(mergedLines, userConfig)

        // Step 8: Write merged config
        configFile.writeText(finalLines.joinToString("\n"))

        // Log added keys
        if (addedKeys.isNotEmpty()) {
            logger.info("Added ${addedKeys.size} new config option(s):")
            addedKeys.forEach { key ->
                logger.info("  + $key")
            }
        }

        return MigrationResult.Migrated(
            fromVersion = fromVersion,
            toVersion = CURRENT_CONFIG_VERSION,
            addedKeys = addedKeys,
            backupPath = backupPath
        )
    }

    /**
     * Create a timestamped backup of the config file.
     */
    private fun createBackup(configFile: File): String {
        val timestamp = LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        )
        val backupFile = File(configFile.parent, "config-backup-$timestamp.yml")
        configFile.copyTo(backupFile, overwrite = false)
        return backupFile.name
    }

    /**
     * Load default config as raw lines (preserves comments).
     */
    private fun loadDefaultConfigLines(): List<String> {
        val resource = plugin.getResource("config.yml")
            ?: error("Default config.yml not found in JAR")

        return resource.bufferedReader().use { it.readLines() }
    }

    /**
     * Load default config as YamlConfiguration.
     */
    private fun loadDefaultConfig(): YamlConfiguration {
        val resource = plugin.getResource("config.yml")
            ?: error("Default config.yml not found in JAR")

        return InputStreamReader(resource).use { reader ->
            YamlConfiguration.loadConfiguration(reader)
        }
    }

    /**
     * Extract all config values with their full paths.
     */
    private fun extractAllValues(config: YamlConfiguration): Map<String, Any?> {
        val values = mutableMapOf<String, Any?>()
        extractValuesRecursive(config, "", values)
        return values
    }

    /**
     * Recursively extract values from a ConfigurationSection.
     */
    private fun extractValuesRecursive(
        section: ConfigurationSection,
        prefix: String,
        values: MutableMap<String, Any?>
    ) {
        for (key in section.getKeys(false)) {
            val path = if (prefix.isEmpty()) key else "$prefix.$key"
            val value = section.get(key)

            if (value is ConfigurationSection) {
                // Recurse into nested sections
                extractValuesRecursive(value, path, values)
            } else {
                // Store leaf value
                values[path] = value
            }
        }
    }

    /**
     * Merge default config lines with user values.
     *
     * This walks through the default config line by line and replaces
     * values where the user has existing configuration.
     */
    private fun mergeConfigs(
        defaultLines: List<String>,
        userValues: Map<String, Any?>
    ): List<String> {
        val result = mutableListOf<String>()
        val pathStack = mutableListOf<Pair<Int, String>>() // (indent, key)

        for (line in defaultLines) {
            val trimmed = line.trimStart()

            // Preserve comment lines and empty lines as-is
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                result.add(line)
                continue
            }

            // Parse key-value line
            val colonIndex = trimmed.indexOf(':')
            if (colonIndex == -1) {
                result.add(line)
                continue
            }

            val key = trimmed.substring(0, colonIndex)
            val indent = line.length - line.trimStart().length

            // Update path stack based on indentation
            while (pathStack.isNotEmpty() && pathStack.last().first >= indent) {
                pathStack.removeLast()
            }

            // Build full path
            val fullPath = (pathStack.map { it.second } + key).joinToString(".")

            // Skip user-mappings section - we'll append it at the end
            if (fullPath == "user-mappings" || fullPath.startsWith("user-mappings.")) {
                continue
            }

            // Check if this is a section header (value after colon is empty or just whitespace)
            val valueAfterColon = trimmed.substring(colonIndex + 1).trim()
            val isSection = valueAfterColon.isEmpty()

            if (isSection) {
                // Section header - add to path stack
                pathStack.add(Pair(indent, key))
                result.add(line)
            } else {
                // Leaf value - check if user has a value for this path
                val userValue = userValues[fullPath]
                if (userValue != null && fullPath != CONFIG_VERSION_KEY) {
                    // Use user's value
                    val indentStr = " ".repeat(indent)
                    result.add("$indentStr$key: ${formatYamlValue(userValue)}")
                } else {
                    // Use default value
                    result.add(line)
                }
            }
        }

        return result
    }

    /**
     * Append user-mappings section from user config.
     */
    private fun appendUserMappings(
        mergedLines: List<String>,
        userConfig: YamlConfiguration
    ): List<String> {
        val result = mergedLines.toMutableList()

        val userMappingsSection = userConfig.getConfigurationSection("user-mappings")
        if (userMappingsSection != null && userMappingsSection.getKeys(false).isNotEmpty()) {
            result.add("")
            result.add("# Discord User ID to Minecraft Username mappings")
            result.add("# Format: \"discord_user_id\": \"MinecraftUsername\"")
            result.add("#")
            result.add("# IMPORTANT: Use Minecraft USERNAMES (not UUIDs) as values!")
            result.add("# You can find Discord IDs by right-clicking a user in Discord -> Copy ID (Developer Mode required)")
            result.add("#")
            result.add("# Example:")
            result.add("#   user-mappings:")
            result.add("#     \"123456789012345678\": \"Steve\"")
            result.add("#     \"987654321098765432\": \"Alex\"")
            result.add("#")
            result.add("# Use /amslink command in-game or Discord to add mappings")
            result.add("user-mappings:")

            for (discordId in userMappingsSection.getKeys(false)) {
                val minecraftName = userMappingsSection.getString(discordId)
                if (minecraftName != null) {
                    result.add("  \"$discordId\": \"$minecraftName\"")
                }
            }
        } else {
            // Add empty user-mappings section
            result.add("")
            result.add("# Discord User ID to Minecraft Username mappings")
            result.add("# Format: \"discord_user_id\": \"MinecraftUsername\"")
            result.add("#")
            result.add("# IMPORTANT: Use Minecraft USERNAMES (not UUIDs) as values!")
            result.add("# You can find Discord IDs by right-clicking a user in Discord -> Copy ID (Developer Mode required)")
            result.add("#")
            result.add("# Example:")
            result.add("#   user-mappings:")
            result.add("#     \"123456789012345678\": \"Steve\"")
            result.add("#     \"987654321098765432\": \"Alex\"")
            result.add("#")
            result.add("# Use /amslink command in-game or Discord to add mappings")
            result.add("user-mappings:")
        }

        return result
    }

    /**
     * Format a value for YAML output.
     */
    private fun formatYamlValue(value: Any?): String {
        return when (value) {
            null -> "null"
            is String -> {
                // Always quote strings for safety
                "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
            }
            is Boolean -> value.toString()
            is Number -> value.toString()
            is List<*> -> {
                // Handle lists
                if (value.isEmpty()) {
                    "[]"
                } else {
                    // Inline list for simple values
                    val items = value.map { item ->
                        when (item) {
                            is String -> "\"${item.replace("\\", "\\\\").replace("\"", "\\\"")}\""
                            else -> item.toString()
                        }
                    }
                    items.joinToString(", ", "[", "]")
                }
            }
            else -> "\"${value.toString().replace("\\", "\\\\").replace("\"", "\\\"")}\""
        }
    }

    /**
     * Apply version-specific migrations to user config values.
     * This remaps old config paths to new paths when the structure changes.
     */
    private fun applyVersionMigrations(
        userValues: Map<String, Any?>,
        fromVersion: Int
    ): Map<String, Any?> {
        var migratedValues = userValues.toMutableMap()

        // Migration from v1 to v2: Feature-aligned structure
        if (fromVersion < 2) {
            migratedValues = migrateV1ToV2(migratedValues)
        }

        return migratedValues
    }

    /**
     * Migrate config from version 1 to version 2.
     * Moves keys from old discord.* structure to feature-aligned sections.
     */
    private fun migrateV1ToV2(userValues: Map<String, Any?>): MutableMap<String, Any?> {
        val migrated = mutableMapOf<String, Any?>()

        // Map of old paths to new paths
        val pathMappings = mapOf(
            // Event Announcements: MCMMO Milestones
            "discord.announcements.enabled" to "event-announcements.mcmmo-milestones.enabled",
            "discord.announcements.text-channel-id" to "event-announcements.mcmmo-milestones.channel-id",
            "discord.announcements.webhook-url" to "event-announcements.webhook.url",
            "discord.announcements.skill-milestone-interval" to "event-announcements.mcmmo-milestones.skill-milestone-interval",
            "discord.announcements.power-milestone-interval" to "event-announcements.mcmmo-milestones.power-milestone-interval",
            "discord.announcements.use-embeds" to "event-announcements.mcmmo-milestones.use-embeds",
            "discord.announcements.use-image-cards" to "event-announcements.mcmmo-milestones.use-image-cards",
            "discord.announcements.show-avatars" to "event-announcements.mcmmo-milestones.show-avatars",
            "discord.announcements.avatar-provider" to "event-announcements.mcmmo-milestones.avatar-provider",

            // Event Announcements: Server Events, Deaths, Achievements
            "discord.events.enabled" to "event-announcements.server-events.enabled",
            "discord.events.text-channel-id" to "event-announcements.server-events.channel-id",
            "discord.events.webhook-url" to "event-announcements.webhook.url",
            "discord.events.use-embeds" to "event-announcements.webhook.use-embeds",
            "discord.events.show-avatars" to "event-announcements.webhook.show-avatars",
            "discord.events.avatar-provider" to "event-announcements.webhook.avatar-provider",
            "discord.events.server-start.enabled" to "event-announcements.server-events.start.enabled",
            "discord.events.server-start.message" to "event-announcements.server-events.start.message",
            "discord.events.server-stop.enabled" to "event-announcements.server-events.stop.enabled",
            "discord.events.server-stop.message" to "event-announcements.server-events.stop.message",
            "discord.events.player-deaths.enabled" to "event-announcements.player-deaths.enabled",
            "discord.events.achievements.enabled" to "event-announcements.achievements.enabled",
            "discord.events.achievements.exclude-recipes" to "event-announcements.achievements.exclude-recipes",

            // Chat Bridge
            "discord.chat-bridge.enabled" to "chat-bridge.enabled",
            "discord.chat-bridge.channel-id" to "chat-bridge.channel-id",
            "discord.chat-bridge.minecraft-to-discord" to "chat-bridge.minecraft-to-discord",
            "discord.chat-bridge.discord-to-minecraft" to "chat-bridge.discord-to-minecraft",
            "discord.chat-bridge.mc-format" to "chat-bridge.mc-format",
            "discord.chat-bridge.discord-format" to "chat-bridge.discord-format",
            "discord.chat-bridge.ignore-prefixes" to "chat-bridge.ignore-prefixes",
            "discord.chat-bridge.suppress-notifications" to "chat-bridge.suppress-notifications",
            "discord.chat-bridge.use-webhook" to "chat-bridge.use-webhook",
            "discord.chat-bridge.webhook-url" to "chat-bridge.webhook-url",
            "discord.chat-bridge.avatar-provider" to "chat-bridge.avatar-provider",
            "discord.chat-bridge.resolve-mentions" to "chat-bridge.resolve-mentions",

            // Player Count Display: Bot Presence
            "discord.presence.enabled" to "player-count-display.bot-presence.enabled",
            "discord.presence.min-update-interval-seconds" to "player-count-display.bot-presence.min-update-interval-seconds",
            "discord.presence.debounce-seconds" to "player-count-display.bot-presence.debounce-seconds",
            "discord.presence.activity.enabled" to "player-count-display.bot-presence.activity.enabled",
            "discord.presence.activity.type" to "player-count-display.bot-presence.activity.type",
            "discord.presence.activity.template" to "player-count-display.bot-presence.activity.template",
            "discord.presence.nickname.enabled" to "player-count-display.bot-presence.nickname.enabled",
            "discord.presence.nickname.template" to "player-count-display.bot-presence.nickname.template",
            "discord.presence.nickname.graceful-fallback" to "player-count-display.bot-presence.nickname.graceful-fallback",

            // Player Count Display: Status Channel
            "discord.status-channel.enabled" to "player-count-display.status-channel.enabled",
            "discord.status-channel.voice-channel-id" to "player-count-display.status-channel.channel-id",
            "discord.status-channel.template" to "player-count-display.status-channel.template",
            "discord.status-channel.update-interval-seconds" to "player-count-display.status-channel.update-interval-seconds"
        )

        // Apply path mappings
        for ((key, value) in userValues) {
            val newPath = pathMappings[key]
            if (newPath != null) {
                migrated[newPath] = value
                logger.fine("Migrated config: $key -> $newPath")
            } else {
                // Keep paths that don't need migration
                migrated[key] = value
            }
        }

        return migrated
    }

    /**
     * Find keys that exist in default config but not in user config.
     */
    private fun findAddedKeys(
        userValues: Map<String, Any?>,
        defaultValues: Map<String, Any?>
    ): List<String> {
        return defaultValues.keys
            .filter { key ->
                key != CONFIG_VERSION_KEY &&
                    !key.startsWith("user-mappings") &&
                    !userValues.containsKey(key)
            }
            .sorted()
    }
}
