package io.github.darinc.amssync.events

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.string.shouldNotContain
import kotlin.random.Random

class DeathCardCommentaryTest : DescribeSpec({

    /** Random that always rolls 0, so optional skill roasts always fire. */
    val alwaysRoast = object : Random() {
        override fun nextBits(bitCount: Int) = 0
    }

    val allCategories = listOf(
        DeathCategory.Mob.Warden, DeathCategory.Mob.Creeper, DeathCategory.Mob.Skeleton, DeathCategory.Mob.Zombie,
        DeathCategory.Mob.Spider, DeathCategory.Mob.Enderman, DeathCategory.Mob.Blaze, DeathCategory.Mob.Ghast,
        DeathCategory.Mob.EnderDragon, DeathCategory.Mob.Wither, DeathCategory.Mob.Guardian, DeathCategory.Mob.Phantom,
        DeathCategory.Mob.Witch, DeathCategory.Mob.Bee, DeathCategory.Mob.Slime, DeathCategory.Mob.Wolf,
        DeathCategory.Mob.Golem, DeathCategory.Mob.Piglin, DeathCategory.Mob.GenericMob,
        DeathCategory.Environmental.Fall, DeathCategory.Environmental.Lava, DeathCategory.Environmental.Fire,
        DeathCategory.Environmental.Drowning, DeathCategory.Environmental.Void, DeathCategory.Environmental.Lightning,
        DeathCategory.Environmental.Cactus, DeathCategory.Environmental.Suffocation, DeathCategory.Environmental.Starvation,
        DeathCategory.Environmental.FallingBlock, DeathCategory.Environmental.Magma, DeathCategory.Environmental.BerryBush,
        DeathCategory.Environmental.Freeze, DeathCategory.Environmental.Explosion, DeathCategory.Environmental.Thorns,
        DeathCategory.PvP, DeathCategory.Unknown
    )

    describe("headlines") {

        allCategories.forEach { category ->
            it("are short and filled in for ${category::class.simpleName}") {
                repeat(10) {
                    val (headline, epitaph) = DeathCardCommentary.generateMessages(category, null, null)
                    headline.shouldNotBeBlank()
                    epitaph.shouldNotBeBlank()
                    headline shouldNotContain "{"
                    headline.length shouldBeLessThanOrEqual DeathCardCommentary.MAX_HEADLINE_LENGTH
                }
            }
        }

        it("never mention the power tier") {
            allCategories.forEach { category ->
                DeathCardCommentary.headlinesFor(category).forEach { line ->
                    line shouldNotContain "RARE"
                    line shouldNotContain "EPIC"
                    line shouldNotContain "LEGENDARY"
                }
            }
        }
    }

    describe("skill roasts") {

        it("roasts high Acrobatics on a fall death") {
            val (_, epitaph) = DeathCardCommentary.generateMessages(
                DeathCategory.Environmental.Fall, mapOf("ACROBATICS" to 500), 500, random = alwaysRoast
            )
            epitaph shouldContain "500"
        }

        it("roasts low Fishing on a drowning death") {
            val (_, epitaph) = DeathCardCommentary.generateMessages(
                DeathCategory.Environmental.Drowning, mapOf("FISHING" to 12), 12, random = alwaysRoast
            )
            epitaph shouldContain "12"
        }

        it("uses the best combat skill for mob deaths, not the top skill overall") {
            val stats = mapOf("MINING" to 900, "SWORDS" to 450, "AXES" to 30)
            DeathCardCommentary.relevantSkill(DeathCategory.Mob.Zombie, stats) shouldBe "SWORDS"
            repeat(5) {
                val (_, epitaph) = DeathCardCommentary.generateMessages(
                    DeathCategory.Mob.Zombie, stats, 1380, random = alwaysRoast
                )
                epitaph shouldContain "Swords"
                epitaph shouldNotContain "Mining"
            }
        }

        it("leaves mid-level skills to the standard commentary") {
            val (_, epitaph) = DeathCardCommentary.generateMessages(
                DeathCategory.Environmental.Fall, mapOf("ACROBATICS" to 200), 200, random = alwaysRoast
            )
            epitaph shouldNotContain "200"
        }
    }

    describe("PvP cards") {

        val plain = PvpKillContext(killerStreak = 1, isRevenge = false, endedStreak = 0)
        fun info(
            context: PvpKillContext = plain,
            weapon: String? = null,
            skill: String? = "SWORDS",
            killerStats: Map<String, Int>? = mapOf("SWORDS" to 412)
        ) = PvpCardInfo("Silver_Parallax", weapon, skill, killerStats, context)

        it("names the killer in the headline") {
            repeat(5) {
                val (headline, _) = DeathCardCommentary.generateMessages(
                    DeathCategory.PvP, null, null, "Noaln_", info()
                )
                headline shouldContain "Silver_Parallax"
            }
        }

        it("mentions a short named weapon") {
            repeat(5) {
                val (headline, _) = DeathCardCommentary.generateMessages(
                    DeathCategory.PvP, null, null, "Noaln_", info(weapon = "[Fork]")
                )
                headline shouldContain "[Fork]"
            }
        }

        it("drops a weapon name too long for the headline") {
            val (headline, _) = DeathCardCommentary.generateMessages(
                DeathCategory.PvP, null, null, "Noaln_", info(weapon = "[The Extremely Long Sword of Destiny]")
            )
            headline shouldNotContain "Destiny"
            headline.length shouldBeLessThanOrEqual DeathCardCommentary.MAX_HEADLINE_LENGTH
        }

        it("compares skill levels for the killer's weapon") {
            val (_, epitaph) = DeathCardCommentary.generateMessages(
                DeathCategory.PvP, mapOf("SWORDS" to 30), null, "Noaln_", info()
            )
            epitaph shouldContain "412"
        }

        it("prefers revenge over the skill matchup") {
            val (_, epitaph) = DeathCardCommentary.generateMessages(
                DeathCategory.PvP, mapOf("SWORDS" to 30), null, "Noaln_", info(plain.copy(isRevenge = true))
            )
            epitaph shouldNotContain "412"
            epitaph.shouldNotBeBlank()
        }

        it("falls back to generic PvP commentary without skill data") {
            val (_, epitaph) = DeathCardCommentary.generateMessages(
                DeathCategory.PvP, null, null, "Noaln_", info(killerStats = null)
            )
            epitaph.shouldNotBeBlank()
        }
    }

    describe("weaponSkillFor") {

        it("maps weapons to mcMMO skills") {
            DeathCardCommentary.weaponSkillFor("DIAMOND_SWORD") shouldBe "SWORDS"
            DeathCardCommentary.weaponSkillFor("NETHERITE_AXE") shouldBe "AXES"
            DeathCardCommentary.weaponSkillFor("BOW") shouldBe "ARCHERY"
            DeathCardCommentary.weaponSkillFor("CROSSBOW") shouldBe "CROSSBOWS"
            DeathCardCommentary.weaponSkillFor("TRIDENT") shouldBe "TRIDENTS"
            DeathCardCommentary.weaponSkillFor("MACE") shouldBe "MACES"
            DeathCardCommentary.weaponSkillFor("IRON_SPEAR") shouldBe "SPEARS"
            DeathCardCommentary.weaponSkillFor("AIR") shouldBe "UNARMED"
        }

        it("returns null for non-weapons") {
            DeathCardCommentary.weaponSkillFor("DIAMOND_PICKAXE") shouldBe null
            DeathCardCommentary.weaponSkillFor("STICK") shouldBe null
        }
    }
})
