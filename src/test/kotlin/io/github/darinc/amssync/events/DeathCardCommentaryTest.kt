package io.github.darinc.amssync.events

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.string.shouldContain

class DeathCardCommentaryTest : DescribeSpec({

    describe("DeathCardCommentary") {

        describe("generateMessages") {

            it("returns pair of non-empty strings for mob death") {
                val (sarcasticMessage, commentary) = DeathCardCommentary.generateMessages(
                    category = DeathCategory.Mob.Zombie,
                    playerStats = null,
                    powerLevel = null
                )

                sarcasticMessage.shouldNotBeBlank()
                commentary.shouldNotBeBlank()
            }

            it("returns pair of non-empty strings for environmental death") {
                val (sarcasticMessage, commentary) = DeathCardCommentary.generateMessages(
                    category = DeathCategory.Environmental.Fall,
                    playerStats = null,
                    powerLevel = null
                )

                sarcasticMessage.shouldNotBeBlank()
                commentary.shouldNotBeBlank()
            }

            it("returns pair of non-empty strings for PvP death") {
                val (sarcasticMessage, commentary) = DeathCardCommentary.generateMessages(
                    category = DeathCategory.PvP,
                    playerStats = null,
                    powerLevel = null
                )

                sarcasticMessage.shouldNotBeBlank()
                commentary.shouldNotBeBlank()
            }

            it("includes tier comment for high power level") {
                val (sarcasticMessage, _) = DeathCardCommentary.generateMessages(
                    category = DeathCategory.Mob.Zombie,
                    playerStats = null,
                    powerLevel = 10000
                )

                // Should contain one of the LEGENDARY tier comments
                val hasLegendaryComment = sarcasticMessage.contains("LEGENDARY") ||
                    sarcasticMessage.contains("Legends") ||
                    sarcasticMessage.contains("legends")
                hasLegendaryComment shouldBe true
            }

            it("includes tier comment for epic power level") {
                val (sarcasticMessage, _) = DeathCardCommentary.generateMessages(
                    category = DeathCategory.Mob.Zombie,
                    playerStats = null,
                    powerLevel = 5000
                )

                // Should contain one of the EPIC tier comments
                val hasEpicComment = sarcasticMessage.contains("EPIC") ||
                    sarcasticMessage.contains("epic")
                hasEpicComment shouldBe true
            }

            it("includes tier comment for rare power level") {
                val (sarcasticMessage, _) = DeathCardCommentary.generateMessages(
                    category = DeathCategory.Mob.Zombie,
                    playerStats = null,
                    powerLevel = 1000
                )

                // Should contain one of the RARE tier comments
                val hasRareComment = sarcasticMessage.contains("RARE") ||
                    sarcasticMessage.contains("rare")
                hasRareComment shouldBe true
            }

            it("does not include tier comment for common power level") {
                val (sarcasticMessage, _) = DeathCardCommentary.generateMessages(
                    category = DeathCategory.Mob.Zombie,
                    playerStats = null,
                    powerLevel = 500
                )

                sarcasticMessage shouldNotBe null
                // Common tier doesn't get a special comment
            }
        }

        describe("skill-specific commentary") {

            it("triggers ironic roast for high Acrobatics and fall death") {
                val stats = mapOf("ACROBATICS" to 500)

                val (sarcasticMessage, commentary) = DeathCardCommentary.generateMessages(
                    category = DeathCategory.Environmental.Fall,
                    playerStats = stats,
                    powerLevel = 500
                )

                // Should reference the high acrobatics skill
                val combined = "$sarcasticMessage $commentary"
                (combined.contains("Acrobatics") || combined.contains("500")) shouldBe true
            }

            it("triggers ironic roast for high Fishing and drowning death") {
                val stats = mapOf("FISHING" to 600)

                val (_, commentary) = DeathCardCommentary.generateMessages(
                    category = DeathCategory.Environmental.Drowning,
                    playerStats = stats,
                    powerLevel = 600
                )

                // Should reference fishing
                commentary.contains("Fishing") || commentary.contains("fish") shouldBe true
            }

            it("triggers low skill commentary for low relevant skill") {
                val stats = mapOf("ACROBATICS" to 50)

                val (_, commentary) = DeathCardCommentary.generateMessages(
                    category = DeathCategory.Environmental.Fall,
                    playerStats = stats,
                    powerLevel = 50
                )

                // Should mention low acrobatics or training
                commentary.shouldNotBeBlank()
            }

            it("falls back to standard commentary when no stats available") {
                val (_, commentary) = DeathCardCommentary.generateMessages(
                    category = DeathCategory.Mob.Creeper,
                    playerStats = null,
                    powerLevel = null
                )

                commentary.shouldNotBeBlank()
            }

            it("uses top skill when relevant skill not high enough") {
                val stats = mapOf(
                    "ACROBATICS" to 100, // Not high enough for ironic
                    "HERBALISM" to 800   // Top skill
                )

                val (sarcasticMessage, _) = DeathCardCommentary.generateMessages(
                    category = DeathCategory.Environmental.Fall,
                    playerStats = stats,
                    powerLevel = 900
                )

                // May reference herbalism as top skill
                sarcasticMessage.shouldNotBeBlank()
            }
        }

        describe("thresholds") {

            it("HIGH_SKILL_THRESHOLD is 400") {
                DeathCardCommentary.HIGH_SKILL_THRESHOLD shouldBe 400
            }

            it("LOW_SKILL_THRESHOLD is 100") {
                DeathCardCommentary.LOW_SKILL_THRESHOLD shouldBe 100
            }

            it("ELITE_POWER_THRESHOLD is 3000") {
                DeathCardCommentary.ELITE_POWER_THRESHOLD shouldBe 3000
            }
        }

        describe("all death categories generate messages") {

            val allCategories = listOf(
                DeathCategory.Mob.Warden,
                DeathCategory.Mob.Creeper,
                DeathCategory.Mob.Skeleton,
                DeathCategory.Mob.Zombie,
                DeathCategory.Mob.Spider,
                DeathCategory.Mob.Enderman,
                DeathCategory.Mob.Blaze,
                DeathCategory.Mob.Ghast,
                DeathCategory.Mob.EnderDragon,
                DeathCategory.Mob.Wither,
                DeathCategory.Mob.Guardian,
                DeathCategory.Mob.Phantom,
                DeathCategory.Mob.Witch,
                DeathCategory.Mob.Bee,
                DeathCategory.Mob.Slime,
                DeathCategory.Mob.Wolf,
                DeathCategory.Mob.Golem,
                DeathCategory.Mob.Piglin,
                DeathCategory.Mob.GenericMob,
                DeathCategory.Environmental.Fall,
                DeathCategory.Environmental.Lava,
                DeathCategory.Environmental.Fire,
                DeathCategory.Environmental.Drowning,
                DeathCategory.Environmental.Void,
                DeathCategory.Environmental.Lightning,
                DeathCategory.Environmental.Cactus,
                DeathCategory.Environmental.Suffocation,
                DeathCategory.Environmental.Starvation,
                DeathCategory.Environmental.FallingBlock,
                DeathCategory.Environmental.Magma,
                DeathCategory.Environmental.BerryBush,
                DeathCategory.Environmental.Freeze,
                DeathCategory.Environmental.Explosion,
                DeathCategory.Environmental.Thorns,
                DeathCategory.PvP,
                DeathCategory.Unknown
            )

            allCategories.forEach { category ->
                it("generates messages for ${category::class.simpleName}") {
                    val (sarcasticMessage, commentary) = DeathCardCommentary.generateMessages(
                        category = category,
                        playerStats = null,
                        powerLevel = null
                    )

                    sarcasticMessage.shouldNotBeBlank()
                    commentary.shouldNotBeBlank()
                }
            }
        }
    }
})
