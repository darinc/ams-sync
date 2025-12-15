package io.github.darinc.amssync.features

import io.github.darinc.amssync.discord.ChatBridge
import io.github.darinc.amssync.discord.ChatBridgeConfig
import io.github.darinc.amssync.discord.ChatWebhookManager
import java.util.logging.Logger

/**
 * Coordinates bidirectional chat bridge between Minecraft and Discord.
 * Manages ChatBridge and ChatWebhookManager lifecycle.
 */
class ChatBridgeFeature(
    private val logger: Logger,
    val config: ChatBridgeConfig
) : Feature {

    var chatBridge: ChatBridge? = null
        private set

    var chatWebhookManager: ChatWebhookManager? = null
        private set

    override val isEnabled: Boolean
        get() = config.enabled

    override fun initialize() {
        // Chat bridge initialization requires DiscordManager - use setBridge() after connection
        if (!isEnabled) {
            logger.info("Chat bridge is disabled in config")
        }
    }

    /**
     * Set the chat bridge and webhook manager after Discord connection.
     */
    fun setBridge(bridge: ChatBridge?, webhook: ChatWebhookManager?) {
        chatBridge = bridge
        chatWebhookManager = webhook
    }

    override fun shutdown() {
        chatWebhookManager?.shutdown()
        chatBridge = null
        chatWebhookManager = null
    }
}
