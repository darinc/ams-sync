package io.github.darinc.amssync.image

import io.github.darinc.amssync.events.DeathCategory
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull

class DeathThemesTest : DescribeSpec({

    describe("DeathThemes") {

        describe("getTheme") {

            it("returns DEEP_DARK for Warden") {
                DeathThemes.getTheme(DeathCategory.Mob.Warden) shouldBe DeathThemes.Theme.DEEP_DARK
            }

            it("returns NETHER for fire-related deaths") {
                DeathThemes.getTheme(DeathCategory.Mob.Blaze) shouldBe DeathThemes.Theme.NETHER
                DeathThemes.getTheme(DeathCategory.Mob.Ghast) shouldBe DeathThemes.Theme.NETHER
                DeathThemes.getTheme(DeathCategory.Environmental.Lava) shouldBe DeathThemes.Theme.NETHER
                DeathThemes.getTheme(DeathCategory.Environmental.Fire) shouldBe DeathThemes.Theme.NETHER
                DeathThemes.getTheme(DeathCategory.Environmental.Magma) shouldBe DeathThemes.Theme.NETHER
            }

            it("returns OCEAN for water-related deaths") {
                DeathThemes.getTheme(DeathCategory.Environmental.Drowning) shouldBe DeathThemes.Theme.OCEAN
                DeathThemes.getTheme(DeathCategory.Mob.Guardian) shouldBe DeathThemes.Theme.OCEAN
            }

            it("returns END for End dimension deaths") {
                DeathThemes.getTheme(DeathCategory.Mob.EnderDragon) shouldBe DeathThemes.Theme.END
                DeathThemes.getTheme(DeathCategory.Mob.Enderman) shouldBe DeathThemes.Theme.END
                DeathThemes.getTheme(DeathCategory.Environmental.Void) shouldBe DeathThemes.Theme.END
            }

            it("returns FROZEN for freeze deaths") {
                DeathThemes.getTheme(DeathCategory.Environmental.Freeze) shouldBe DeathThemes.Theme.FROZEN
            }

            it("returns DESERT for cactus deaths") {
                DeathThemes.getTheme(DeathCategory.Environmental.Cactus) shouldBe DeathThemes.Theme.DESERT
            }

            it("returns CAVE for underground deaths") {
                DeathThemes.getTheme(DeathCategory.Environmental.Fall) shouldBe DeathThemes.Theme.CAVE
                DeathThemes.getTheme(DeathCategory.Environmental.Suffocation) shouldBe DeathThemes.Theme.CAVE
                DeathThemes.getTheme(DeathCategory.Environmental.FallingBlock) shouldBe DeathThemes.Theme.CAVE
            }

            it("returns PVP_ARENA for player kills") {
                DeathThemes.getTheme(DeathCategory.PvP) shouldBe DeathThemes.Theme.PVP_ARENA
            }

            it("returns OVERWORLD as default for other mobs") {
                DeathThemes.getTheme(DeathCategory.Mob.Zombie) shouldBe DeathThemes.Theme.OVERWORLD
                DeathThemes.getTheme(DeathCategory.Mob.Skeleton) shouldBe DeathThemes.Theme.OVERWORLD
                DeathThemes.getTheme(DeathCategory.Mob.Creeper) shouldBe DeathThemes.Theme.OVERWORLD
            }
        }

        describe("getMobIconAsset") {

            it("returns correct filename for mob categories") {
                DeathThemes.getMobIconAsset(DeathCategory.Mob.Warden) shouldBe "warden.png"
                DeathThemes.getMobIconAsset(DeathCategory.Mob.Creeper) shouldBe "creeper.png"
                DeathThemes.getMobIconAsset(DeathCategory.Mob.Skeleton) shouldBe "skeleton.png"
                DeathThemes.getMobIconAsset(DeathCategory.Mob.Zombie) shouldBe "zombie.png"
                DeathThemes.getMobIconAsset(DeathCategory.Mob.Spider) shouldBe "spider.png"
                DeathThemes.getMobIconAsset(DeathCategory.Mob.Enderman) shouldBe "enderman.png"
                DeathThemes.getMobIconAsset(DeathCategory.Mob.EnderDragon) shouldBe "ender_dragon.png"
            }

            it("returns null for non-mob categories") {
                DeathThemes.getMobIconAsset(DeathCategory.Environmental.Fall).shouldBeNull()
                DeathThemes.getMobIconAsset(DeathCategory.PvP).shouldBeNull()
                DeathThemes.getMobIconAsset(DeathCategory.Unknown).shouldBeNull()
            }
        }

        describe("getEnvironmentalIconAsset") {

            it("returns correct filename for environmental categories") {
                DeathThemes.getEnvironmentalIconAsset(DeathCategory.Environmental.Fall) shouldBe "fall.png"
                DeathThemes.getEnvironmentalIconAsset(DeathCategory.Environmental.Lava) shouldBe "lava.png"
                DeathThemes.getEnvironmentalIconAsset(DeathCategory.Environmental.Fire) shouldBe "fire.png"
                DeathThemes.getEnvironmentalIconAsset(DeathCategory.Environmental.Drowning) shouldBe "water.png"
                DeathThemes.getEnvironmentalIconAsset(DeathCategory.Environmental.Void) shouldBe "void.png"
                DeathThemes.getEnvironmentalIconAsset(DeathCategory.Environmental.Lightning) shouldBe "lightning.png"
            }

            it("returns null for non-environmental categories") {
                DeathThemes.getEnvironmentalIconAsset(DeathCategory.Mob.Zombie).shouldBeNull()
                DeathThemes.getEnvironmentalIconAsset(DeathCategory.PvP).shouldBeNull()
            }
        }

        describe("getIconAsset") {

            it("returns mob icon for mob deaths") {
                DeathThemes.getIconAsset(DeathCategory.Mob.Creeper) shouldBe "creeper.png"
                DeathThemes.getIconAsset(DeathCategory.Mob.Zombie) shouldBe "zombie.png"
            }

            it("returns environmental icon for environmental deaths") {
                DeathThemes.getIconAsset(DeathCategory.Environmental.Fall) shouldBe "fall.png"
                DeathThemes.getIconAsset(DeathCategory.Environmental.Lava) shouldBe "lava.png"
            }

            it("returns pvp.png for PvP deaths") {
                DeathThemes.getIconAsset(DeathCategory.PvP) shouldBe "pvp.png"
            }

            it("returns skull.png for unknown deaths") {
                DeathThemes.getIconAsset(DeathCategory.Unknown) shouldBe "skull.png"
            }
        }

        describe("getIconDirectory") {

            it("returns 'mobs' for mob deaths") {
                DeathThemes.getIconDirectory(DeathCategory.Mob.Zombie) shouldBe "mobs"
                DeathThemes.getIconDirectory(DeathCategory.Mob.Creeper) shouldBe "mobs"
                DeathThemes.getIconDirectory(DeathCategory.Mob.Warden) shouldBe "mobs"
            }

            it("returns 'environmental' for environmental deaths") {
                DeathThemes.getIconDirectory(DeathCategory.Environmental.Fall) shouldBe "environmental"
                DeathThemes.getIconDirectory(DeathCategory.Environmental.Lava) shouldBe "environmental"
            }

            it("returns 'environmental' for PvP and Unknown") {
                DeathThemes.getIconDirectory(DeathCategory.PvP) shouldBe "environmental"
                DeathThemes.getIconDirectory(DeathCategory.Unknown) shouldBe "environmental"
            }
        }

        describe("getDeathCauseName") {

            it("returns uppercase display names for mobs") {
                DeathThemes.getDeathCauseName(DeathCategory.Mob.Warden) shouldBe "WARDEN"
                DeathThemes.getDeathCauseName(DeathCategory.Mob.Creeper) shouldBe "CREEPER"
                DeathThemes.getDeathCauseName(DeathCategory.Mob.Skeleton) shouldBe "SKELETON"
                DeathThemes.getDeathCauseName(DeathCategory.Mob.EnderDragon) shouldBe "ENDER DRAGON"
                DeathThemes.getDeathCauseName(DeathCategory.Mob.Golem) shouldBe "IRON GOLEM"
            }

            it("returns uppercase display names for environmental causes") {
                DeathThemes.getDeathCauseName(DeathCategory.Environmental.Fall) shouldBe "GRAVITY"
                DeathThemes.getDeathCauseName(DeathCategory.Environmental.Lava) shouldBe "LAVA"
                DeathThemes.getDeathCauseName(DeathCategory.Environmental.Drowning) shouldBe "WATER"
                DeathThemes.getDeathCauseName(DeathCategory.Environmental.Void) shouldBe "THE VOID"
            }

            it("returns display name for PvP and Unknown") {
                DeathThemes.getDeathCauseName(DeathCategory.PvP) shouldBe "ANOTHER PLAYER"
                DeathThemes.getDeathCauseName(DeathCategory.Unknown) shouldBe "UNKNOWN CAUSE"
            }
        }

        describe("Theme enum") {

            it("has valid background asset filenames") {
                DeathThemes.Theme.entries.forEach { theme ->
                    theme.backgroundAsset.shouldNotBeNull()
                    theme.backgroundAsset.endsWith(".png") shouldBe true
                }
            }

            it("has 9 themes total") {
                DeathThemes.Theme.entries.size shouldBe 9
            }
        }
    }
})
