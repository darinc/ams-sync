package io.github.darinc.amssync.events

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.comparables.shouldBeGreaterThan

class DeathCommentaryTest : DescribeSpec({

    describe("DeathCategory") {

        describe("isEmbarrassing") {

            it("returns true for starvation") {
                DeathCategory.Environmental.Starvation.isEmbarrassing() shouldBe true
            }

            it("returns true for cactus") {
                DeathCategory.Environmental.Cactus.isEmbarrassing() shouldBe true
            }

            it("returns true for fall") {
                DeathCategory.Environmental.Fall.isEmbarrassing() shouldBe true
            }

            it("returns true for drowning") {
                DeathCategory.Environmental.Drowning.isEmbarrassing() shouldBe true
            }

            it("returns true for berry bush") {
                DeathCategory.Environmental.BerryBush.isEmbarrassing() shouldBe true
            }

            it("returns true for freeze") {
                DeathCategory.Environmental.Freeze.isEmbarrassing() shouldBe true
            }

            it("returns true for bee") {
                DeathCategory.Mob.Bee.isEmbarrassing() shouldBe true
            }

            it("returns true for slime") {
                DeathCategory.Mob.Slime.isEmbarrassing() shouldBe true
            }

            it("returns false for dragon") {
                DeathCategory.Mob.EnderDragon.isEmbarrassing() shouldBe false
            }

            it("returns false for PvP") {
                DeathCategory.PvP.isEmbarrassing() shouldBe false
            }

            it("returns false for creeper") {
                DeathCategory.Mob.Creeper.isEmbarrassing() shouldBe false
            }

            it("returns false for lava") {
                DeathCategory.Environmental.Lava.isEmbarrassing() shouldBe false
            }

            it("returns false for void") {
                DeathCategory.Environmental.Void.isEmbarrassing() shouldBe false
            }
        }
    }

    describe("DeathCommentaryRepository") {

        describe("getCommentary") {

            it("returns non-empty string for creeper") {
                val message = DeathCommentaryRepository.getCommentary(DeathCategory.Mob.Creeper)
                message.shouldNotBeBlank()
            }

            it("returns non-empty string for zombie") {
                val message = DeathCommentaryRepository.getCommentary(DeathCategory.Mob.Zombie)
                message.shouldNotBeBlank()
            }

            it("returns non-empty string for skeleton") {
                val message = DeathCommentaryRepository.getCommentary(DeathCategory.Mob.Skeleton)
                message.shouldNotBeBlank()
            }

            it("returns non-empty string for fall damage") {
                val message = DeathCommentaryRepository.getCommentary(DeathCategory.Environmental.Fall)
                message.shouldNotBeBlank()
            }

            it("returns non-empty string for lava") {
                val message = DeathCommentaryRepository.getCommentary(DeathCategory.Environmental.Lava)
                message.shouldNotBeBlank()
            }

            it("returns non-empty string for drowning") {
                val message = DeathCommentaryRepository.getCommentary(DeathCategory.Environmental.Drowning)
                message.shouldNotBeBlank()
            }

            it("returns non-empty string for starvation") {
                val message = DeathCommentaryRepository.getCommentary(DeathCategory.Environmental.Starvation)
                message.shouldNotBeBlank()
            }

            it("returns non-empty string for PvP") {
                val message = DeathCommentaryRepository.getCommentary(DeathCategory.PvP)
                message.shouldNotBeBlank()
            }

            it("returns non-empty string for unknown") {
                val message = DeathCommentaryRepository.getCommentary(DeathCategory.Unknown)
                message.shouldNotBeBlank()
            }

            it("returns different messages (randomness test)") {
                val messages = (1..20).map {
                    DeathCommentaryRepository.getCommentary(DeathCategory.Mob.Creeper)
                }.toSet()

                // With 10 messages and 20 samples, we should get variety
                messages.size shouldBeGreaterThan 1
            }

            it("returns messages for all mob types") {
                val mobCategories = listOf(
                    DeathCategory.Mob.Creeper,
                    DeathCategory.Mob.Skeleton,
                    DeathCategory.Mob.Zombie,
                    DeathCategory.Mob.Spider,
                    DeathCategory.Mob.Enderman,
                    DeathCategory.Mob.Wither,
                    DeathCategory.Mob.EnderDragon,
                    DeathCategory.Mob.Blaze,
                    DeathCategory.Mob.Ghast,
                    DeathCategory.Mob.Warden,
                    DeathCategory.Mob.GenericMob
                )

                mobCategories.forEach { category ->
                    val message = DeathCommentaryRepository.getCommentary(category)
                    message.shouldNotBeBlank()
                }
            }

            it("returns messages for all environmental types") {
                val envCategories = listOf(
                    DeathCategory.Environmental.Fall,
                    DeathCategory.Environmental.Lava,
                    DeathCategory.Environmental.Fire,
                    DeathCategory.Environmental.Drowning,
                    DeathCategory.Environmental.Void,
                    DeathCategory.Environmental.Lightning,
                    DeathCategory.Environmental.Cactus,
                    DeathCategory.Environmental.Suffocation,
                    DeathCategory.Environmental.Starvation,
                    DeathCategory.Environmental.BerryBush,
                    DeathCategory.Environmental.Freeze
                )

                envCategories.forEach { category ->
                    val message = DeathCommentaryRepository.getCommentary(category)
                    message.shouldNotBeBlank()
                }
            }
        }

        describe("getEliteRoast") {

            it("returns null when below default threshold") {
                val roast = DeathCommentaryRepository.getEliteRoast(
                    DeathCategory.Environmental.Starvation,
                    2000
                )
                roast.shouldBeNull()
            }

            it("returns null when below custom threshold") {
                val roast = DeathCommentaryRepository.getEliteRoast(
                    DeathCategory.Environmental.Starvation,
                    4500,
                    5000
                )
                roast.shouldBeNull()
            }

            it("returns null for non-embarrassing death even at high level") {
                val roast = DeathCommentaryRepository.getEliteRoast(
                    DeathCategory.Mob.EnderDragon,
                    5000
                )
                roast.shouldBeNull()
            }

            it("returns null for PvP death even at high level") {
                val roast = DeathCommentaryRepository.getEliteRoast(
                    DeathCategory.PvP,
                    5000
                )
                roast.shouldBeNull()
            }

            it("returns roast for starvation at high level") {
                val roast = DeathCommentaryRepository.getEliteRoast(
                    DeathCategory.Environmental.Starvation,
                    5000
                )
                roast.shouldNotBeNull()
                roast.shouldNotBeBlank()
            }

            it("returns roast for cactus at high level") {
                val roast = DeathCommentaryRepository.getEliteRoast(
                    DeathCategory.Environmental.Cactus,
                    4000
                )
                roast.shouldNotBeNull()
                roast.shouldNotBeBlank()
            }

            it("returns roast for fall at high level") {
                val roast = DeathCommentaryRepository.getEliteRoast(
                    DeathCategory.Environmental.Fall,
                    3500
                )
                roast.shouldNotBeNull()
                roast.shouldNotBeBlank()
            }

            it("returns roast for drowning at high level") {
                val roast = DeathCommentaryRepository.getEliteRoast(
                    DeathCategory.Environmental.Drowning,
                    6000
                )
                roast.shouldNotBeNull()
                roast.shouldNotBeBlank()
            }

            it("returns roast for bee at high level") {
                val roast = DeathCommentaryRepository.getEliteRoast(
                    DeathCategory.Mob.Bee,
                    3000
                )
                roast.shouldNotBeNull()
                roast.shouldNotBeBlank()
            }

            it("returns roast for slime at high level") {
                val roast = DeathCommentaryRepository.getEliteRoast(
                    DeathCategory.Mob.Slime,
                    10000
                )
                roast.shouldNotBeNull()
                roast.shouldNotBeBlank()
            }

            it("replaces {level} placeholder when present in message") {
                // Test multiple times to ensure we get a message with {level} placeholder
                var foundLevelInMessage = false
                repeat(50) {
                    val roast = DeathCommentaryRepository.getEliteRoast(
                        DeathCategory.Environmental.Starvation,
                        4567
                    )
                    roast.shouldNotBeNull()
                    roast shouldNotContain "{level}"
                    if (roast.contains("4567")) {
                        foundLevelInMessage = true
                    }
                }
                // Some messages include {level}, so we should find at least one
                foundLevelInMessage shouldBe true
            }

            it("returns roast when exactly at threshold") {
                val roast = DeathCommentaryRepository.getEliteRoast(
                    DeathCategory.Environmental.Starvation,
                    3000,
                    3000
                )
                roast.shouldNotBeNull()
            }

            it("returns null when one below threshold") {
                val roast = DeathCommentaryRepository.getEliteRoast(
                    DeathCategory.Environmental.Starvation,
                    2999,
                    3000
                )
                roast.shouldBeNull()
            }
        }
    }
})
