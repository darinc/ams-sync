package io.github.darinc.amssync.events

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.mockk.every
import io.mockk.mockk
import org.bukkit.configuration.file.FileConfiguration
import java.util.UUID

class EventAnnouncementConfigTest : DescribeSpec({

    describe("EventAnnouncementConfig") {

        describe("fromConfig") {

            it("loads all values from config") {
                val config = mockk<FileConfiguration>()
                every { config.getBoolean("discord.events.enabled", false) } returns true
                every { config.getString("discord.events.text-channel-id", "") } returns "123456789012345678"
                every { config.getString("discord.events.webhook-url", "") } returns "https://discord.com/api/webhooks/123/abc"
                every { config.getBoolean("discord.events.use-embeds", true) } returns true
                every { config.getBoolean("discord.events.show-avatars", true) } returns true
                every { config.getString("discord.events.avatar-provider", "mc-heads") } returns "crafatar"
                every { config.getBoolean("discord.events.server-start.enabled", true) } returns true
                every { config.getString("discord.events.server-start.message", "Server is now online!") } returns "Server started!"
                every { config.getBoolean("discord.events.server-stop.enabled", true) } returns true
                every { config.getString("discord.events.server-stop.message", "Server is shutting down...") } returns "Server stopping..."
                every { config.getBoolean("discord.events.player-deaths.enabled", true) } returns true
                every { config.getBoolean("discord.events.achievements.enabled", true) } returns true
                every { config.getBoolean("discord.events.achievements.exclude-recipes", true) } returns false

                val eventConfig = EventAnnouncementConfig.fromConfig(config)

                eventConfig.enabled shouldBe true
                eventConfig.channelId shouldBe "123456789012345678"
                eventConfig.webhookUrl shouldBe "https://discord.com/api/webhooks/123/abc"
                eventConfig.useEmbeds shouldBe true
                eventConfig.showAvatars shouldBe true
                eventConfig.avatarProvider shouldBe "crafatar"
                eventConfig.serverStart.enabled shouldBe true
                eventConfig.serverStart.message shouldBe "Server started!"
                eventConfig.serverStop.enabled shouldBe true
                eventConfig.serverStop.message shouldBe "Server stopping..."
                eventConfig.playerDeaths.enabled shouldBe true
                eventConfig.achievements.enabled shouldBe true
                eventConfig.achievements.excludeRecipes shouldBe false
            }

            it("uses default values when config keys missing") {
                val config = mockk<FileConfiguration>()
                every { config.getBoolean("discord.events.enabled", false) } returns false
                every { config.getString("discord.events.text-channel-id", "") } returns ""
                every { config.getString("discord.events.webhook-url", "") } returns ""
                every { config.getBoolean("discord.events.use-embeds", true) } returns true
                every { config.getBoolean("discord.events.show-avatars", true) } returns true
                every { config.getString("discord.events.avatar-provider", "mc-heads") } returns "mc-heads"
                every { config.getBoolean("discord.events.server-start.enabled", true) } returns true
                every { config.getString("discord.events.server-start.message", "Server is now online!") } returns "Server is now online!"
                every { config.getBoolean("discord.events.server-stop.enabled", true) } returns true
                every { config.getString("discord.events.server-stop.message", "Server is shutting down...") } returns "Server is shutting down..."
                every { config.getBoolean("discord.events.player-deaths.enabled", true) } returns true
                every { config.getBoolean("discord.events.achievements.enabled", true) } returns true
                every { config.getBoolean("discord.events.achievements.exclude-recipes", true) } returns true

                val eventConfig = EventAnnouncementConfig.fromConfig(config)

                eventConfig.enabled shouldBe false
                eventConfig.channelId shouldBe ""
                eventConfig.webhookUrl.shouldBeNull()
                eventConfig.useEmbeds shouldBe true
                eventConfig.showAvatars shouldBe true
                eventConfig.avatarProvider shouldBe "mc-heads"
                eventConfig.serverStart.enabled shouldBe true
                eventConfig.serverStart.message shouldBe "Server is now online!"
                eventConfig.serverStop.enabled shouldBe true
                eventConfig.serverStop.message shouldBe "Server is shutting down..."
                eventConfig.playerDeaths.enabled shouldBe true
                eventConfig.achievements.enabled shouldBe true
                eventConfig.achievements.excludeRecipes shouldBe true
            }

            it("converts blank webhook URL to null") {
                val config = mockk<FileConfiguration>()
                every { config.getBoolean("discord.events.enabled", false) } returns true
                every { config.getString("discord.events.text-channel-id", "") } returns "123456789012345678"
                every { config.getString("discord.events.webhook-url", "") } returns "   "
                every { config.getBoolean("discord.events.use-embeds", true) } returns true
                every { config.getBoolean("discord.events.show-avatars", true) } returns true
                every { config.getString("discord.events.avatar-provider", "mc-heads") } returns "mc-heads"
                every { config.getBoolean("discord.events.server-start.enabled", true) } returns true
                every { config.getString("discord.events.server-start.message", "Server is now online!") } returns "Server is now online!"
                every { config.getBoolean("discord.events.server-stop.enabled", true) } returns true
                every { config.getString("discord.events.server-stop.message", "Server is shutting down...") } returns "Server is shutting down..."
                every { config.getBoolean("discord.events.player-deaths.enabled", true) } returns true
                every { config.getBoolean("discord.events.achievements.enabled", true) } returns true
                every { config.getBoolean("discord.events.achievements.exclude-recipes", true) } returns true

                val eventConfig = EventAnnouncementConfig.fromConfig(config)

                eventConfig.webhookUrl.shouldBeNull()
            }

            it("handles null string values with defaults") {
                val config = mockk<FileConfiguration>()
                every { config.getBoolean("discord.events.enabled", false) } returns true
                every { config.getString("discord.events.text-channel-id", "") } returns null
                every { config.getString("discord.events.webhook-url", "") } returns null
                every { config.getBoolean("discord.events.use-embeds", true) } returns true
                every { config.getBoolean("discord.events.show-avatars", true) } returns true
                every { config.getString("discord.events.avatar-provider", "mc-heads") } returns null
                every { config.getBoolean("discord.events.server-start.enabled", true) } returns true
                every { config.getString("discord.events.server-start.message", "Server is now online!") } returns null
                every { config.getBoolean("discord.events.server-stop.enabled", true) } returns true
                every { config.getString("discord.events.server-stop.message", "Server is shutting down...") } returns null
                every { config.getBoolean("discord.events.player-deaths.enabled", true) } returns true
                every { config.getBoolean("discord.events.achievements.enabled", true) } returns true
                every { config.getBoolean("discord.events.achievements.exclude-recipes", true) } returns true

                val eventConfig = EventAnnouncementConfig.fromConfig(config)

                eventConfig.channelId shouldBe ""
                eventConfig.webhookUrl.shouldBeNull()
                eventConfig.avatarProvider shouldBe "mc-heads"
                eventConfig.serverStart.message shouldBe "Server is now online!"
                eventConfig.serverStop.message shouldBe "Server is shutting down..."
            }

            it("handles disabled sub-features") {
                val config = mockk<FileConfiguration>()
                every { config.getBoolean("discord.events.enabled", false) } returns true
                every { config.getString("discord.events.text-channel-id", "") } returns "123456789012345678"
                every { config.getString("discord.events.webhook-url", "") } returns ""
                every { config.getBoolean("discord.events.use-embeds", true) } returns false
                every { config.getBoolean("discord.events.show-avatars", true) } returns false
                every { config.getString("discord.events.avatar-provider", "mc-heads") } returns "mc-heads"
                every { config.getBoolean("discord.events.server-start.enabled", true) } returns false
                every { config.getString("discord.events.server-start.message", "Server is now online!") } returns "Server is now online!"
                every { config.getBoolean("discord.events.server-stop.enabled", true) } returns false
                every { config.getString("discord.events.server-stop.message", "Server is shutting down...") } returns "Server is shutting down..."
                every { config.getBoolean("discord.events.player-deaths.enabled", true) } returns false
                every { config.getBoolean("discord.events.achievements.enabled", true) } returns false
                every { config.getBoolean("discord.events.achievements.exclude-recipes", true) } returns true

                val eventConfig = EventAnnouncementConfig.fromConfig(config)

                eventConfig.useEmbeds shouldBe false
                eventConfig.showAvatars shouldBe false
                eventConfig.serverStart.enabled shouldBe false
                eventConfig.serverStop.enabled shouldBe false
                eventConfig.playerDeaths.enabled shouldBe false
                eventConfig.achievements.enabled shouldBe false
            }
        }

        describe("getAvatarUrl") {

            val playerName = "TestPlayer"
            val uuid = UUID.fromString("12345678-1234-1234-1234-123456789abc")

            it("generates mc-heads URL for mc-heads provider") {
                val url = EventAnnouncementConfig.getAvatarUrl(playerName, uuid, "mc-heads")

                url shouldBe "https://mc-heads.net/avatar/TestPlayer/64"
            }

            it("generates crafatar URL for crafatar provider") {
                val url = EventAnnouncementConfig.getAvatarUrl(playerName, uuid, "crafatar")

                url shouldBe "https://crafatar.com/avatars/12345678123412341234123456789abc?size=64&overlay"
            }

            it("removes dashes from UUID for crafatar") {
                val uuidWithDashes = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
                val url = EventAnnouncementConfig.getAvatarUrl(playerName, uuidWithDashes, "crafatar")

                url shouldBe "https://crafatar.com/avatars/aaaaaaaabbbbccccddddeeeeeeeeeeee?size=64&overlay"
            }

            it("defaults to mc-heads for unknown provider") {
                val url = EventAnnouncementConfig.getAvatarUrl(playerName, uuid, "unknown")

                url shouldBe "https://mc-heads.net/avatar/TestPlayer/64"
            }

            it("is case-insensitive for provider") {
                val urlUpper = EventAnnouncementConfig.getAvatarUrl(playerName, uuid, "CRAFATAR")
                val urlMixed = EventAnnouncementConfig.getAvatarUrl(playerName, uuid, "Crafatar")
                val urlLower = EventAnnouncementConfig.getAvatarUrl(playerName, uuid, "crafatar")

                urlUpper shouldBe urlLower
                urlMixed shouldBe urlLower
            }

            it("uses player name in mc-heads URL") {
                val url = EventAnnouncementConfig.getAvatarUrl("Steve", uuid, "mc-heads")

                url shouldBe "https://mc-heads.net/avatar/Steve/64"
            }
        }

        describe("ServerStartConfig") {

            it("stores enabled and message") {
                val config = ServerStartConfig(
                    enabled = true,
                    message = "The server has started!"
                )

                config.enabled shouldBe true
                config.message shouldBe "The server has started!"
            }
        }

        describe("ServerStopConfig") {

            it("stores enabled and message") {
                val config = ServerStopConfig(
                    enabled = true,
                    message = "The server is stopping..."
                )

                config.enabled shouldBe true
                config.message shouldBe "The server is stopping..."
            }
        }

        describe("PlayerDeathConfig") {

            it("stores enabled flag") {
                val config = PlayerDeathConfig(enabled = true)

                config.enabled shouldBe true
            }
        }

        describe("AchievementConfig") {

            it("stores enabled and excludeRecipes flags") {
                val config = AchievementConfig(
                    enabled = true,
                    excludeRecipes = true
                )

                config.enabled shouldBe true
                config.excludeRecipes shouldBe true
            }

            it("can include recipe achievements") {
                val config = AchievementConfig(
                    enabled = true,
                    excludeRecipes = false
                )

                config.excludeRecipes shouldBe false
            }
        }
    }
})
