package io.github.darinc.amssync.discord

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.bukkit.configuration.file.FileConfiguration

class PresenceConfigTest : DescribeSpec({

    describe("PresenceConfig") {

        describe("fromConfig") {

            it("loads all values from config") {
                val config = mockk<FileConfiguration>()
                every { config.getBoolean("discord.presence.enabled", true) } returns true
                every { config.getInt("discord.presence.min-update-interval-seconds", 30) } returns 60
                every { config.getInt("discord.presence.debounce-seconds", 5) } returns 10
                every { config.getBoolean("discord.presence.activity.enabled", true) } returns true
                every { config.getString("discord.presence.activity.type", "playing") } returns "watching"
                every { config.getString("discord.presence.activity.template", "{count} players online") } returns "{count}/{max} online"
                every { config.getBoolean("discord.presence.nickname.enabled", false) } returns true
                every { config.getString("discord.presence.nickname.template", "[{count}] {name}") } returns "[{count}/{max}] {name}"
                every { config.getBoolean("discord.presence.nickname.graceful-fallback", true) } returns false

                val presenceConfig = PresenceConfig.fromConfig(config)

                presenceConfig.enabled shouldBe true
                presenceConfig.minIntervalMs shouldBe 60000L
                presenceConfig.debounceMs shouldBe 10000L
                presenceConfig.activity.enabled shouldBe true
                presenceConfig.activity.type shouldBe "watching"
                presenceConfig.activity.template shouldBe "{count}/{max} online"
                presenceConfig.nickname.enabled shouldBe true
                presenceConfig.nickname.template shouldBe "[{count}/{max}] {name}"
                presenceConfig.nickname.gracefulFallback shouldBe false
            }

            it("uses default values when config keys missing") {
                val config = mockk<FileConfiguration>()
                every { config.getBoolean("discord.presence.enabled", true) } returns true
                every { config.getInt("discord.presence.min-update-interval-seconds", 30) } returns 30
                every { config.getInt("discord.presence.debounce-seconds", 5) } returns 5
                every { config.getBoolean("discord.presence.activity.enabled", true) } returns true
                every { config.getString("discord.presence.activity.type", "playing") } returns "playing"
                every { config.getString("discord.presence.activity.template", "{count} players online") } returns "{count} players online"
                every { config.getBoolean("discord.presence.nickname.enabled", false) } returns false
                every { config.getString("discord.presence.nickname.template", "[{count}] {name}") } returns "[{count}] {name}"
                every { config.getBoolean("discord.presence.nickname.graceful-fallback", true) } returns true

                val presenceConfig = PresenceConfig.fromConfig(config)

                presenceConfig.enabled shouldBe true
                presenceConfig.minIntervalMs shouldBe 30000L
                presenceConfig.debounceMs shouldBe 5000L
                presenceConfig.activity.enabled shouldBe true
                presenceConfig.activity.type shouldBe "playing"
                presenceConfig.activity.template shouldBe "{count} players online"
                presenceConfig.nickname.enabled shouldBe false
                presenceConfig.nickname.template shouldBe "[{count}] {name}"
                presenceConfig.nickname.gracefulFallback shouldBe true
            }

            it("converts seconds to milliseconds correctly") {
                val config = mockk<FileConfiguration>()
                every { config.getBoolean("discord.presence.enabled", true) } returns true
                every { config.getInt("discord.presence.min-update-interval-seconds", 30) } returns 120
                every { config.getInt("discord.presence.debounce-seconds", 5) } returns 15
                every { config.getBoolean("discord.presence.activity.enabled", true) } returns true
                every { config.getString("discord.presence.activity.type", "playing") } returns "playing"
                every { config.getString("discord.presence.activity.template", "{count} players online") } returns "{count} players online"
                every { config.getBoolean("discord.presence.nickname.enabled", false) } returns false
                every { config.getString("discord.presence.nickname.template", "[{count}] {name}") } returns "[{count}] {name}"
                every { config.getBoolean("discord.presence.nickname.graceful-fallback", true) } returns true

                val presenceConfig = PresenceConfig.fromConfig(config)

                presenceConfig.minIntervalMs shouldBe 120000L
                presenceConfig.debounceMs shouldBe 15000L
            }

            it("handles null string values with defaults") {
                val config = mockk<FileConfiguration>()
                every { config.getBoolean("discord.presence.enabled", true) } returns true
                every { config.getInt("discord.presence.min-update-interval-seconds", 30) } returns 30
                every { config.getInt("discord.presence.debounce-seconds", 5) } returns 5
                every { config.getBoolean("discord.presence.activity.enabled", true) } returns true
                every { config.getString("discord.presence.activity.type", "playing") } returns null
                every { config.getString("discord.presence.activity.template", "{count} players online") } returns null
                every { config.getBoolean("discord.presence.nickname.enabled", false) } returns false
                every { config.getString("discord.presence.nickname.template", "[{count}] {name}") } returns null
                every { config.getBoolean("discord.presence.nickname.graceful-fallback", true) } returns true

                val presenceConfig = PresenceConfig.fromConfig(config)

                presenceConfig.activity.type shouldBe "playing"
                presenceConfig.activity.template shouldBe "{count} players online"
                presenceConfig.nickname.template shouldBe "[{count}] {name}"
            }

            it("handles disabled state") {
                val config = mockk<FileConfiguration>()
                every { config.getBoolean("discord.presence.enabled", true) } returns false
                every { config.getInt("discord.presence.min-update-interval-seconds", 30) } returns 30
                every { config.getInt("discord.presence.debounce-seconds", 5) } returns 5
                every { config.getBoolean("discord.presence.activity.enabled", true) } returns false
                every { config.getString("discord.presence.activity.type", "playing") } returns "playing"
                every { config.getString("discord.presence.activity.template", "{count} players online") } returns "{count} players online"
                every { config.getBoolean("discord.presence.nickname.enabled", false) } returns false
                every { config.getString("discord.presence.nickname.template", "[{count}] {name}") } returns "[{count}] {name}"
                every { config.getBoolean("discord.presence.nickname.graceful-fallback", true) } returns true

                val presenceConfig = PresenceConfig.fromConfig(config)

                presenceConfig.enabled shouldBe false
                presenceConfig.activity.enabled shouldBe false
                presenceConfig.nickname.enabled shouldBe false
            }
        }

        describe("ActivityConfig") {

            it("stores activity type correctly") {
                val activity = ActivityConfig(
                    enabled = true,
                    type = "listening",
                    template = "to {count} players"
                )

                activity.enabled shouldBe true
                activity.type shouldBe "listening"
                activity.template shouldBe "to {count} players"
            }

            it("supports all activity types") {
                val types = listOf("playing", "watching", "listening", "competing")

                types.forEach { type ->
                    val activity = ActivityConfig(enabled = true, type = type, template = "test")
                    activity.type shouldBe type
                }
            }
        }

        describe("NicknameConfig") {

            it("stores nickname configuration correctly") {
                val nickname = NicknameConfig(
                    enabled = true,
                    template = "[{count}] MyBot",
                    gracefulFallback = false
                )

                nickname.enabled shouldBe true
                nickname.template shouldBe "[{count}] MyBot"
                nickname.gracefulFallback shouldBe false
            }

            it("graceful fallback defaults to true in typical usage") {
                val nickname = NicknameConfig(
                    enabled = true,
                    template = "[{count}] {name}",
                    gracefulFallback = true
                )

                nickname.gracefulFallback shouldBe true
            }
        }
    }
})
