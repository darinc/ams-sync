package io.github.darinc.amssync.discord

import io.github.darinc.amssync.AMSSyncPlugin
import io.papermc.paper.event.player.AsyncChatEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

/**
 * Two-way chat bridge between Minecraft and Discord.
 *
 * Relays messages from:
 * - Minecraft chat -> Discord text channel
 * - Discord text channel -> Minecraft broadcast
 *
 * @property plugin The parent plugin instance
 * @property config Chat bridge configuration
 * @property chatWebhookManager Optional webhook manager for rich Discord messages
 */
class ChatBridge(
    private val plugin: AMSSyncPlugin,
    private val config: ChatBridgeConfig,
    private val chatWebhookManager: ChatWebhookManager? = null,
    private val discordManager: DiscordManager? = null
) : Listener, ListenerAdapter() {

    companion object {
        // Matches @username patterns (3-16 chars, alphanumeric + underscore)
        private val MENTION_PATTERN = Regex("@([a-zA-Z0-9_]{3,16})")
    }

    /**
     * Handle Minecraft chat events - relay to Discord.
     * Uses Paper's AsyncChatEvent for modern Paper servers.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onAsyncChat(event: AsyncChatEvent) {
        if (!config.minecraftToDiscord) return

        // Serialize the message component to plain text
        val message = PlainTextComponentSerializer.plainText().serialize(event.message())

        // Skip messages that start with ignored prefixes (e.g., commands)
        if (config.ignorePrefixes.any { message.startsWith(it) }) {
            return
        }

        val player = event.player
        val playerName = player.name

        // Mention resolution requires main thread access to UserMappingService
        // Schedule on main thread, then send to Discord
        if (config.resolveMentions && message.contains("@")) {
            Bukkit.getScheduler().runTask(plugin, Runnable {
                val resolvedMessage = resolveMentions(message)
                sendMessageToDiscord(resolvedMessage, playerName, player.uniqueId)
            })
        } else {
            sendMessageToDiscord(message, playerName, player.uniqueId)
        }
    }

    /**
     * Send a message to Discord using either webhook or bot message.
     */
    private fun sendMessageToDiscord(message: String, playerName: String, playerUuid: java.util.UUID) {
        if (config.useWebhook && chatWebhookManager?.isWebhookAvailable() == true) {
            val avatarUrl = ChatBridgeConfig.getAvatarUrl(playerName, playerUuid, config.avatarProvider)
            sendViaWebhook(message, playerName, avatarUrl)
        } else {
            val formatted = config.discordFormat
                .replace("{player}", playerName)
                .replace("{message}", message)
            sendToDiscord(formatted)
        }
    }

    /**
     * Resolve @username mentions to Discord user mentions.
     * Looks up linked users in UserMappingService and converts to <@discordId> format.
     *
     * @param message The message containing potential @mentions
     * @return Message with resolved mentions (unlinked users kept as-is)
     */
    private fun resolveMentions(message: String): String {
        return MENTION_PATTERN.replace(message) { match ->
            val mentionedUsername = match.groupValues[1]
            val discordId = findDiscordIdCaseInsensitive(mentionedUsername)
            if (discordId != null) {
                "<@$discordId>"
            } else {
                match.value // Keep original @username
            }
        }
    }

    /**
     * Find Discord ID for a Minecraft username (case-insensitive).
     */
    private fun findDiscordIdCaseInsensitive(username: String): String? {
        val mappings = plugin.userMappingService.getAllMappings()
        // mappings is Discord ID -> Minecraft Username, need to search values
        val entry = mappings.entries.find { (_, mcUsername) ->
            mcUsername.equals(username, ignoreCase = true)
        }
        return entry?.key
    }

    /**
     * Send a message to the Discord chat channel.
     */
    private fun sendToDiscord(message: String) {
        val manager = discordManager ?: return
        val jda = manager.getJda()
        if (jda == null || !manager.isConnected()) {
            plugin.logger.fine("Skipping Discord relay - not connected")
            return
        }

        val channel = jda.getTextChannelById(config.channelId)
        if (channel == null) {
            plugin.logger.warning("Chat bridge channel not found: ${config.channelId}")
            return
        }

        // Sanitize to prevent @everyone/@here mentions
        val sanitized = sanitizeDiscordMessage(message)

        try {
            val circuitBreaker = plugin.resilience.circuitBreaker

            val sendAction = {
                channel.sendMessage(sanitized)
                    .setSuppressedNotifications(config.suppressNotifications)
                    .queue(
                        { plugin.logger.fine("Relayed chat to Discord: $sanitized") },
                        { e -> plugin.logger.warning("Failed to relay chat to Discord: ${e.message}") }
                    )
            }

            if (circuitBreaker != null) {
                val result = circuitBreaker.execute("Relay chat to Discord", sendAction)
                if (result is CircuitBreaker.CircuitResult.Rejected) {
                    plugin.logger.fine("Chat relay rejected by circuit breaker")
                }
            } else {
                sendAction()
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error relaying chat to Discord: ${e.message}")
        }
    }

    /**
     * Send a message to Discord via webhook with custom username and avatar.
     *
     * @param message The message content
     * @param playerName Player name to display as sender
     * @param avatarUrl URL to player's head image
     */
    private fun sendViaWebhook(message: String, playerName: String, avatarUrl: String) {
        plugin.logger.fine("Sending webhook with avatar URL: $avatarUrl")
        // Sanitize to prevent @everyone/@here mentions
        val sanitized = sanitizeDiscordMessage(message)
        chatWebhookManager?.sendMessage(sanitized, playerName, avatarUrl)
    }

    /**
     * Handle Discord message events - relay to Minecraft.
     */
    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (!config.discordToMinecraft) return

        // Ignore bot messages (including our own)
        if (event.author.isBot) return

        // Only process messages from the configured channel
        if (event.channel.id != config.channelId) return

        // Get the author's display name (nickname if set, otherwise username)
        val author = event.member?.effectiveName ?: event.author.name
        val message = event.message.contentDisplay

        // Skip empty messages (e.g., image-only messages)
        if (message.isBlank()) return

        // Format for Minecraft
        @Suppress("DEPRECATION")
        val formatted = ChatColor.translateAlternateColorCodes(
            '&',
            config.mcFormat
                .replace("{author}", author)
                .replace("{message}", sanitizeMinecraftMessage(message))
        )

        // Broadcast to all online players (must run on main thread)
        Bukkit.getScheduler().runTask(plugin, Runnable {
            Bukkit.broadcastMessage(formatted)
            plugin.logger.fine("Relayed Discord message to Minecraft: $author: $message")
        })
    }

    /**
     * Sanitize a message for Discord to prevent mention exploits.
     * Preserves valid user mentions in the format <@discordId>.
     */
    private fun sanitizeDiscordMessage(message: String): String {
        return message
            // Prevent @everyone and @here mentions
            .replace("@everyone", "@\u200Beveryone")
            .replace("@here", "@\u200Bhere")
            // Prevent role mentions and raw ID mentions, but preserve valid <@id> user mentions
            // Negative lookbehind (?<!<) ensures we don't break <@123456789> format
            .replace(Regex("(?<!<)@(&|!)?(\\d+)")) { match ->
                "@\u200B${match.groupValues[1]}${match.groupValues[2]}"
            }
    }

    /**
     * Sanitize a message for Minecraft to prevent format exploits.
     */
    private fun sanitizeMinecraftMessage(message: String): String {
        return message
            // Remove any color code attempts from Discord users
            .replace("&", "&\u200B")
            // Strip Unicode Variation Selectors (VS15 text-style, VS16 emoji-style)
            // These cause boxes in Minecraft as it lacks glyphs for them
            .replace("\uFE0E", "")
            .replace("\uFE0F", "")
    }
}

/**
 * Configuration for chat bridge.
 *
 * @property enabled Enable/disable chat bridge
 * @property channelId Text channel ID for chat relay
 * @property minecraftToDiscord Relay Minecraft chat to Discord
 * @property discordToMinecraft Relay Discord chat to Minecraft
 * @property mcFormat Format for Discord messages shown in Minecraft
 * @property discordFormat Format for Minecraft messages shown in Discord
 * @property ignorePrefixes Message prefixes to ignore (e.g., commands)
 * @property suppressNotifications Suppress Discord push notifications
 * @property useWebhook Use webhook for richer Discord messages
 * @property webhookUrl Webhook URL (empty = auto-create in channel)
 * @property avatarProvider Avatar service: "mc-heads" or "crafatar"
 * @property resolveMentions Convert @username to Discord mentions for linked users
 * @property channelName Channel name for lookup or auto-creation
 */
data class ChatBridgeConfig(
    val enabled: Boolean,
    val channelId: String,
    val channelName: String,
    val minecraftToDiscord: Boolean,
    val discordToMinecraft: Boolean,
    val mcFormat: String,
    val discordFormat: String,
    val ignorePrefixes: List<String>,
    val suppressNotifications: Boolean,
    val useWebhook: Boolean,
    val webhookUrl: String?,
    val avatarProvider: String,
    val resolveMentions: Boolean
) {
    /**
     * Create a ChannelConfig for channel resolution.
     */
    fun toChannelConfig(): io.github.darinc.amssync.discord.channels.ChannelConfig {
        return io.github.darinc.amssync.discord.channels.ChannelConfig(
            channelId = channelId,
            channelName = channelName,
            channelType = io.github.darinc.amssync.discord.channels.ChannelConfig.ChannelType.TEXT
        )
    }
    companion object {
        /**
         * Load chat bridge configuration from Bukkit config file.
         *
         * @param config The Bukkit FileConfiguration to load from
         * @return ChatBridgeConfig with loaded or default values
         */
        fun fromConfig(config: FileConfiguration): ChatBridgeConfig {
            return ChatBridgeConfig(
                enabled = config.getBoolean("chat-bridge.enabled", false),
                channelId = config.getString("chat-bridge.channel-id", "") ?: "",
                channelName = config.getString("chat-bridge.channel-name", "ams-chats") ?: "ams-chats",
                minecraftToDiscord = config.getBoolean("chat-bridge.minecraft-to-discord", true),
                discordToMinecraft = config.getBoolean("chat-bridge.discord-to-minecraft", true),
                mcFormat = config.getString("chat-bridge.mc-format", "&7[Discord] &b{author}&7: {message}")
                    ?: "&7[Discord] &b{author}&7: {message}",
                discordFormat = config.getString("chat-bridge.discord-format", "**{player}**: {message}")
                    ?: "**{player}**: {message}",
                ignorePrefixes = config.getStringList("chat-bridge.ignore-prefixes").ifEmpty { listOf("/") },
                suppressNotifications = config.getBoolean("chat-bridge.suppress-notifications", true),
                useWebhook = config.getBoolean("chat-bridge.use-webhook", false),
                webhookUrl = config.getString("chat-bridge.webhook-url", "")?.takeIf { it.isNotBlank() },
                avatarProvider = config.getString("chat-bridge.avatar-provider", "mc-heads") ?: "mc-heads",
                resolveMentions = config.getBoolean("chat-bridge.resolve-mentions", true)
            )
        }

        /**
         * Get avatar URL for a player.
         *
         * @param playerName Minecraft username
         * @param uuid Player's UUID
         * @param provider Avatar service ("mc-heads" or "crafatar")
         * @return URL to player's avatar image
         */
        fun getAvatarUrl(playerName: String, uuid: java.util.UUID, provider: String): String {
            return when (provider.lowercase()) {
                "crafatar" -> {
                    // Crafatar requires UUID without dashes
                    val id = uuid.toString().replace("-", "")
                    "https://crafatar.com/avatars/$id?size=64&overlay"
                }
                else -> "https://mc-heads.net/avatar/$playerName/64"
            }
        }
    }
}
