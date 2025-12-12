package io.github.darinc.amssync.image

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.bukkit.configuration.file.FileConfiguration

class ImageConfigTest : DescribeSpec({

    describe("ImageConfig") {

        describe("fromConfig") {

            it("loads all values from config") {
                val config = mockk<FileConfiguration>()
                every { config.getBoolean("image-cards.enabled", true) } returns true
                every { config.getString("image-cards.avatar-provider", "mc-heads") } returns "crafatar"
                every { config.getString("image-cards.server-name", "Minecraft Server") } returns "My Awesome Server"
                every { config.getInt("image-cards.avatar-cache-ttl-seconds", 300) } returns 600
                every { config.getInt("image-cards.avatar-cache-max-size", 100) } returns 200

                val imageConfig = ImageConfig.fromConfig(config)

                imageConfig.enabled shouldBe true
                imageConfig.avatarProvider shouldBe "crafatar"
                imageConfig.serverName shouldBe "My Awesome Server"
                imageConfig.avatarCacheTtlSeconds shouldBe 600
                imageConfig.avatarCacheMaxSize shouldBe 200
            }

            it("uses default values when config keys missing") {
                val config = mockk<FileConfiguration>()
                every { config.getBoolean("image-cards.enabled", true) } returns true
                every { config.getString("image-cards.avatar-provider", "mc-heads") } returns "mc-heads"
                every { config.getString("image-cards.server-name", "Minecraft Server") } returns "Minecraft Server"
                every { config.getInt("image-cards.avatar-cache-ttl-seconds", 300) } returns 300
                every { config.getInt("image-cards.avatar-cache-max-size", 100) } returns 100

                val imageConfig = ImageConfig.fromConfig(config)

                imageConfig.enabled shouldBe true
                imageConfig.avatarProvider shouldBe "mc-heads"
                imageConfig.serverName shouldBe "Minecraft Server"
                imageConfig.avatarCacheTtlSeconds shouldBe 300
                imageConfig.avatarCacheMaxSize shouldBe 100
            }

            it("handles null string values with defaults") {
                val config = mockk<FileConfiguration>()
                every { config.getBoolean("image-cards.enabled", true) } returns true
                every { config.getString("image-cards.avatar-provider", "mc-heads") } returns null
                every { config.getString("image-cards.server-name", "Minecraft Server") } returns null
                every { config.getInt("image-cards.avatar-cache-ttl-seconds", 300) } returns 300
                every { config.getInt("image-cards.avatar-cache-max-size", 100) } returns 100

                val imageConfig = ImageConfig.fromConfig(config)

                imageConfig.avatarProvider shouldBe "mc-heads"
                imageConfig.serverName shouldBe "Minecraft Server"
            }

            it("handles disabled state") {
                val config = mockk<FileConfiguration>()
                every { config.getBoolean("image-cards.enabled", true) } returns false
                every { config.getString("image-cards.avatar-provider", "mc-heads") } returns "mc-heads"
                every { config.getString("image-cards.server-name", "Minecraft Server") } returns "Minecraft Server"
                every { config.getInt("image-cards.avatar-cache-ttl-seconds", 300) } returns 300
                every { config.getInt("image-cards.avatar-cache-max-size", 100) } returns 100

                val imageConfig = ImageConfig.fromConfig(config)

                imageConfig.enabled shouldBe false
            }
        }

        describe("getCacheTtlMs") {

            it("converts seconds to milliseconds") {
                val imageConfig = ImageConfig(
                    enabled = true,
                    avatarProvider = "mc-heads",
                    serverName = "Test Server",
                    avatarCacheTtlSeconds = 300,
                    avatarCacheMaxSize = 100
                )

                imageConfig.getCacheTtlMs() shouldBe 300000L
            }

            it("handles larger TTL values") {
                val imageConfig = ImageConfig(
                    enabled = true,
                    avatarProvider = "mc-heads",
                    serverName = "Test Server",
                    avatarCacheTtlSeconds = 3600,
                    avatarCacheMaxSize = 100
                )

                imageConfig.getCacheTtlMs() shouldBe 3600000L
            }

            it("handles small TTL values") {
                val imageConfig = ImageConfig(
                    enabled = true,
                    avatarProvider = "mc-heads",
                    serverName = "Test Server",
                    avatarCacheTtlSeconds = 60,
                    avatarCacheMaxSize = 100
                )

                imageConfig.getCacheTtlMs() shouldBe 60000L
            }

            it("handles zero TTL") {
                val imageConfig = ImageConfig(
                    enabled = true,
                    avatarProvider = "mc-heads",
                    serverName = "Test Server",
                    avatarCacheTtlSeconds = 0,
                    avatarCacheMaxSize = 100
                )

                imageConfig.getCacheTtlMs() shouldBe 0L
            }
        }

        describe("avatar providers") {

            it("supports mc-heads provider") {
                val imageConfig = ImageConfig(
                    enabled = true,
                    avatarProvider = "mc-heads",
                    serverName = "Test Server",
                    avatarCacheTtlSeconds = 300,
                    avatarCacheMaxSize = 100
                )

                imageConfig.avatarProvider shouldBe "mc-heads"
            }

            it("supports crafatar provider") {
                val imageConfig = ImageConfig(
                    enabled = true,
                    avatarProvider = "crafatar",
                    serverName = "Test Server",
                    avatarCacheTtlSeconds = 300,
                    avatarCacheMaxSize = 100
                )

                imageConfig.avatarProvider shouldBe "crafatar"
            }
        }

        describe("cache configuration") {

            it("stores cache max size") {
                val imageConfig = ImageConfig(
                    enabled = true,
                    avatarProvider = "mc-heads",
                    serverName = "Test Server",
                    avatarCacheTtlSeconds = 300,
                    avatarCacheMaxSize = 500
                )

                imageConfig.avatarCacheMaxSize shouldBe 500
            }

            it("allows small cache size") {
                val imageConfig = ImageConfig(
                    enabled = true,
                    avatarProvider = "mc-heads",
                    serverName = "Test Server",
                    avatarCacheTtlSeconds = 300,
                    avatarCacheMaxSize = 10
                )

                imageConfig.avatarCacheMaxSize shouldBe 10
            }
        }
    }
})
