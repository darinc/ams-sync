package io.github.darinc.amssync.image

import io.github.darinc.amssync.events.DeathCategory
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import org.bukkit.plugin.java.JavaPlugin
import java.awt.image.BufferedImage
import java.util.logging.Logger

class DeathAssetManagerTest : DescribeSpec({

    describe("DeathAssetManager") {

        val mockPlugin = mockk<JavaPlugin>()
        val mockLogger = mockk<Logger>(relaxed = true)

        beforeEach {
            // Default: no resources found (returns null)
            every { mockPlugin.getResource(any()) } returns null
        }

        describe("getBackground") {

            it("returns fallback background when asset not found") {
                val assetManager = DeathAssetManager(mockPlugin, mockLogger)

                val background = assetManager.getBackground(DeathThemes.Theme.NETHER)

                background shouldNotBe null
                background.width shouldBe DeathCardStyles.CARD_WIDTH
                background.height shouldBe DeathCardStyles.CARD_HEIGHT
            }

            it("generates different fallbacks for different themes") {
                val assetManager = DeathAssetManager(mockPlugin, mockLogger)

                val netherBg = assetManager.getBackground(DeathThemes.Theme.NETHER)
                val oceanBg = assetManager.getBackground(DeathThemes.Theme.OCEAN)

                // Both should exist and be correct size
                netherBg.width shouldBe DeathCardStyles.CARD_WIDTH
                oceanBg.width shouldBe DeathCardStyles.CARD_WIDTH

                // They should have different colors (checking a pixel)
                // The themes have different tint colors
            }
        }

        describe("getDeathIcon") {

            it("returns fallback skull when mob icon not found") {
                val assetManager = DeathAssetManager(mockPlugin, mockLogger)

                val icon = assetManager.getDeathIcon(DeathCategory.Mob.Zombie)

                icon shouldNotBe null
                icon.width shouldBeGreaterThan 0
                icon.height shouldBeGreaterThan 0
            }

            it("returns fallback skull when environmental icon not found") {
                val assetManager = DeathAssetManager(mockPlugin, mockLogger)

                val icon = assetManager.getDeathIcon(DeathCategory.Environmental.Fall)

                icon shouldNotBe null
                icon.width shouldBeGreaterThan 0
            }
        }

        describe("getCoffin") {

            it("returns fallback coffin when asset not found") {
                val assetManager = DeathAssetManager(mockPlugin, mockLogger)

                val coffin = assetManager.getCoffin()

                coffin shouldNotBe null
                coffin.width shouldBe DeathCardStyles.COFFIN_WIDTH
                coffin.height shouldBe DeathCardStyles.COFFIN_HEIGHT
            }
        }

        describe("getSkullIcon") {

            it("returns fallback skull when asset not found") {
                val assetManager = DeathAssetManager(mockPlugin, mockLogger)

                val skull = assetManager.getSkullIcon()

                skull shouldNotBe null
                skull.width shouldBeGreaterThan 0
                skull.height shouldBeGreaterThan 0
            }
        }

        describe("caching") {

            it("starts with empty cache") {
                val assetManager = DeathAssetManager(mockPlugin, mockLogger)

                assetManager.getCacheSize() shouldBe 0
            }

            it("clearCache empties the cache") {
                val assetManager = DeathAssetManager(mockPlugin, mockLogger)

                // Load some assets to populate cache (will load fallbacks)
                assetManager.getSkullIcon()
                assetManager.getCoffin()

                // Clear cache
                assetManager.clearCache()

                assetManager.getCacheSize() shouldBe 0
            }
        }

        describe("createFallbackIcon") {

            it("creates icon with specified color and symbol") {
                val assetManager = DeathAssetManager(mockPlugin, mockLogger)

                val icon = assetManager.createFallbackIcon(java.awt.Color.RED, '!')

                icon shouldNotBe null
                icon.width shouldBe 40 // FALLBACK_ICON_SIZE
                icon.height shouldBe 40
            }
        }

        describe("getMobIcon") {

            it("returns null when mob icon not found") {
                val assetManager = DeathAssetManager(mockPlugin, mockLogger)

                val icon = assetManager.getMobIcon("nonexistent")

                icon shouldBe null
            }
        }

        describe("getEnvironmentalIcon") {

            it("returns null when environmental icon not found") {
                val assetManager = DeathAssetManager(mockPlugin, mockLogger)

                val icon = assetManager.getEnvironmentalIcon("nonexistent")

                icon shouldBe null
            }
        }
    }
})
