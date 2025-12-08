package io.github.darinc.amssync.discord

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import java.util.UUID

class ChatBridgeConfigTest : DescribeSpec({

    describe("ChatBridgeConfig.getAvatarUrl") {

        val playerName = "Steve"
        val uuid = UUID.randomUUID()

        it("returns mc-heads URL for mc-heads provider") {
            val url = ChatBridgeConfig.getAvatarUrl(playerName, uuid, "mc-heads")
            url shouldBe "https://mc-heads.net/avatar/$playerName?size=64"
        }

        it("returns crafatar URL for crafatar provider") {
            val url = ChatBridgeConfig.getAvatarUrl(playerName, uuid, "crafatar")
            url shouldBe "https://crafatar.com/avatars/$uuid?size=64&overlay"
        }

        it("defaults to mc-heads for unknown provider") {
            val url = ChatBridgeConfig.getAvatarUrl(playerName, uuid, "unknown")
            url shouldBe "https://mc-heads.net/avatar/$playerName?size=64"
        }

        it("is case insensitive for provider") {
            val url = ChatBridgeConfig.getAvatarUrl(playerName, uuid, "CRAFATAR")
            url shouldBe "https://crafatar.com/avatars/$uuid?size=64&overlay"
        }
    }

    describe("ChatBridgeConfig defaults") {

        it("has expected default values") {
            val config = ChatBridgeConfig(
                enabled = false,
                channelId = "",
                minecraftToDiscord = true,
                discordToMinecraft = true,
                mcFormat = "&7[Discord] &b{author}&7: {message}",
                discordFormat = "**{player}**: {message}",
                ignorePrefixes = listOf("/"),
                suppressNotifications = true,
                useWebhook = false,
                webhookUrl = null,
                avatarProvider = "mc-heads"
            )

            config.enabled shouldBe false
            config.channelId shouldBe ""
            config.minecraftToDiscord shouldBe true
            config.discordToMinecraft shouldBe true
            config.useWebhook shouldBe false
            config.webhookUrl.shouldBeNull()
            config.avatarProvider shouldBe "mc-heads"
        }
    }

    describe("ChatBridgeConfig with webhook enabled") {

        it("stores webhook configuration correctly") {
            val webhookUrl = "https://discord.com/api/webhooks/123/abc"
            val config = ChatBridgeConfig(
                enabled = true,
                channelId = "123456789",
                minecraftToDiscord = true,
                discordToMinecraft = true,
                mcFormat = "&7[Discord] &b{author}&7: {message}",
                discordFormat = "**{player}**: {message}",
                ignorePrefixes = listOf("/"),
                suppressNotifications = true,
                useWebhook = true,
                webhookUrl = webhookUrl,
                avatarProvider = "crafatar"
            )

            config.enabled shouldBe true
            config.useWebhook shouldBe true
            config.webhookUrl shouldBe webhookUrl
            config.avatarProvider shouldBe "crafatar"
        }
    }
})
