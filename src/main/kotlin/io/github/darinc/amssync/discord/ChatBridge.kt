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
 */
class ChatBridge(
    private val plugin: AMSSyncPlugin,
    private val config: ChatBridgeConfig
) : Listener, ListenerAdapter() {

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

        val playerName = event.player.name

        // Format and send to Discord
        val formatted = config.discordFormat
            .replace("{player}", playerName)
            .replace("{message}", message)

        sendToDiscord(formatted)
    }

    /**
     * Send a message to the Discord chat channel.
     */
    private fun sendToDiscord(message: String) {
        val jda = plugin.discordManager.getJda()
        if (jda == null || !plugin.discordManager.isConnected()) {
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
            val circuitBreaker = plugin.circuitBreaker

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
     */
    private fun sanitizeDiscordMessage(message: String): String {
        return message
            // Prevent @everyone and @here mentions
            .replace("@everyone", "@\u200Beveryone")
            .replace("@here", "@\u200Bhere")
            // Prevent role mentions (@ followed by role name)
            .replace(Regex("@(&|!)?(\\d+)")) { match -> "@\u200B${match.groupValues[1]}${match.groupValues[2]}" }
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
 */
data class ChatBridgeConfig(
    val enabled: Boolean,
    val channelId: String,
    val minecraftToDiscord: Boolean,
    val discordToMinecraft: Boolean,
    val mcFormat: String,
    val discordFormat: String,
    val ignorePrefixes: List<String>,
    val suppressNotifications: Boolean
) {
    companion object {
        /**
         * Load chat bridge configuration from Bukkit config file.
         *
         * @param config The Bukkit FileConfiguration to load from
         * @return ChatBridgeConfig with loaded or default values
         */
        fun fromConfig(config: FileConfiguration): ChatBridgeConfig {
            return ChatBridgeConfig(
                enabled = config.getBoolean("discord.chat-bridge.enabled", false),
                channelId = config.getString("discord.chat-bridge.channel-id", "") ?: "",
                minecraftToDiscord = config.getBoolean("discord.chat-bridge.minecraft-to-discord", true),
                discordToMinecraft = config.getBoolean("discord.chat-bridge.discord-to-minecraft", true),
                mcFormat = config.getString("discord.chat-bridge.mc-format", "&7[Discord] &b{author}&7: {message}")
                    ?: "&7[Discord] &b{author}&7: {message}",
                discordFormat = config.getString("discord.chat-bridge.discord-format", "**{player}**: {message}")
                    ?: "**{player}**: {message}",
                ignorePrefixes = config.getStringList("discord.chat-bridge.ignore-prefixes").ifEmpty { listOf("/") },
                suppressNotifications = config.getBoolean("discord.chat-bridge.suppress-notifications", true)
            )
        }
    }
}
