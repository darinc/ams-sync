package io.github.darinc.amssync.image

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.awt.Color
import java.awt.Font

class CardStylesTest : DescribeSpec({

    describe("CardStyles") {

        describe("constants") {

            it("has correct branding text") {
                CardStyles.BRANDING_TEXT shouldBe "AMS Sync"
            }

            describe("card dimensions") {

                it("has stats card dimensions") {
                    CardStyles.STATS_CARD_WIDTH shouldBe 450
                    CardStyles.STATS_CARD_HEIGHT shouldBe 600
                }

                it("has leaderboard card dimensions") {
                    CardStyles.LEADERBOARD_CARD_WIDTH shouldBe 600
                    CardStyles.LEADERBOARD_CARD_HEIGHT shouldBe 450
                }
            }

            describe("border and padding") {

                it("has border width") {
                    CardStyles.CARD_BORDER_WIDTH shouldBe 3
                }

                it("has corner radius") {
                    CardStyles.CARD_CORNER_RADIUS shouldBe 15
                }

                it("has padding") {
                    CardStyles.CARD_PADDING shouldBe 15
                }
            }

            describe("avatar sizes") {

                it("has body render height") {
                    CardStyles.BODY_RENDER_HEIGHT shouldBe 128
                }

                it("has head avatar size") {
                    CardStyles.HEAD_AVATAR_SIZE shouldBe 64
                }

                it("has podium head size") {
                    CardStyles.PODIUM_HEAD_SIZE shouldBe 48
                }
            }

            describe("skill bar dimensions") {

                it("has skill bar width") {
                    CardStyles.SKILL_BAR_WIDTH shouldBe 100
                }

                it("has skill bar height") {
                    CardStyles.SKILL_BAR_HEIGHT shouldBe 8
                }

                it("has skill bar radius") {
                    CardStyles.SKILL_BAR_RADIUS shouldBe 4
                }
            }
        }

        describe("colors") {

            describe("background gradients") {

                it("has common tier gradient") {
                    CardStyles.BG_COMMON_START shouldNotBe null
                    CardStyles.BG_COMMON_END shouldNotBe null
                }

                it("has rare tier gradient") {
                    CardStyles.BG_RARE_START shouldNotBe null
                    CardStyles.BG_RARE_END shouldNotBe null
                }

                it("has epic tier gradient") {
                    CardStyles.BG_EPIC_START shouldNotBe null
                    CardStyles.BG_EPIC_END shouldNotBe null
                }

                it("has legendary tier gradient") {
                    CardStyles.BG_LEGENDARY_START shouldNotBe null
                    CardStyles.BG_LEGENDARY_END shouldNotBe null
                }
            }

            describe("border colors") {

                it("has bronze border color") {
                    CardStyles.BORDER_BRONZE shouldBe Color(205, 127, 50)
                }

                it("has silver border color") {
                    CardStyles.BORDER_SILVER shouldBe Color(192, 192, 192)
                }

                it("has gold border color") {
                    CardStyles.BORDER_GOLD shouldBe Color(255, 215, 0)
                }

                it("has diamond border color") {
                    CardStyles.BORDER_DIAMOND shouldBe Color(0, 255, 255)
                }
            }

            describe("text colors") {

                it("has white text color") {
                    CardStyles.TEXT_WHITE shouldBe Color(255, 255, 255)
                }

                it("has gray text color") {
                    CardStyles.TEXT_GRAY shouldNotBe null
                }

                it("has gold text color") {
                    CardStyles.TEXT_GOLD shouldBe Color(255, 215, 0)
                }

                it("has cyan text color") {
                    CardStyles.TEXT_CYAN shouldBe Color(0, 255, 255)
                }
            }

            describe("podium colors") {

                it("has gold podium color") {
                    CardStyles.PODIUM_GOLD shouldBe Color(255, 215, 0)
                }

                it("has silver podium color") {
                    CardStyles.PODIUM_SILVER shouldBe Color(192, 192, 192)
                }

                it("has bronze podium color") {
                    CardStyles.PODIUM_BRONZE shouldBe Color(205, 127, 50)
                }
            }
        }

        describe("fonts") {

            it("has title font") {
                CardStyles.FONT_TITLE shouldBe Font("SansSerif", Font.BOLD, 24)
            }

            it("has player name font") {
                CardStyles.FONT_PLAYER_NAME shouldBe Font("SansSerif", Font.BOLD, 20)
            }

            it("has power level font") {
                CardStyles.FONT_POWER_LEVEL shouldBe Font("SansSerif", Font.BOLD, 16)
            }

            it("has category font") {
                CardStyles.FONT_CATEGORY shouldBe Font("SansSerif", Font.BOLD, 14)
            }

            it("has skill font") {
                CardStyles.FONT_SKILL shouldBe Font("SansSerif", Font.PLAIN, 12)
            }

            it("has footer font with italic style") {
                CardStyles.FONT_FOOTER shouldBe Font("SansSerif", Font.ITALIC, 10)
            }
        }

        describe("getBorderColor") {

            it("returns bronze for power level < 1000") {
                CardStyles.getBorderColor(0) shouldBe CardStyles.BORDER_BRONZE
                CardStyles.getBorderColor(500) shouldBe CardStyles.BORDER_BRONZE
                CardStyles.getBorderColor(999) shouldBe CardStyles.BORDER_BRONZE
            }

            it("returns silver for power level 1000-4999") {
                CardStyles.getBorderColor(1000) shouldBe CardStyles.BORDER_SILVER
                CardStyles.getBorderColor(2500) shouldBe CardStyles.BORDER_SILVER
                CardStyles.getBorderColor(4999) shouldBe CardStyles.BORDER_SILVER
            }

            it("returns gold for power level 5000-9999") {
                CardStyles.getBorderColor(5000) shouldBe CardStyles.BORDER_GOLD
                CardStyles.getBorderColor(7500) shouldBe CardStyles.BORDER_GOLD
                CardStyles.getBorderColor(9999) shouldBe CardStyles.BORDER_GOLD
            }

            it("returns diamond for power level >= 10000") {
                CardStyles.getBorderColor(10000) shouldBe CardStyles.BORDER_DIAMOND
                CardStyles.getBorderColor(15000) shouldBe CardStyles.BORDER_DIAMOND
                CardStyles.getBorderColor(50000) shouldBe CardStyles.BORDER_DIAMOND
            }
        }

        describe("getRarityName") {

            it("returns COMMON for power level < 1000") {
                CardStyles.getRarityName(0) shouldBe "COMMON"
                CardStyles.getRarityName(500) shouldBe "COMMON"
                CardStyles.getRarityName(999) shouldBe "COMMON"
            }

            it("returns RARE for power level 1000-4999") {
                CardStyles.getRarityName(1000) shouldBe "RARE"
                CardStyles.getRarityName(2500) shouldBe "RARE"
                CardStyles.getRarityName(4999) shouldBe "RARE"
            }

            it("returns EPIC for power level 5000-9999") {
                CardStyles.getRarityName(5000) shouldBe "EPIC"
                CardStyles.getRarityName(7500) shouldBe "EPIC"
                CardStyles.getRarityName(9999) shouldBe "EPIC"
            }

            it("returns LEGENDARY for power level >= 10000") {
                CardStyles.getRarityName(10000) shouldBe "LEGENDARY"
                CardStyles.getRarityName(15000) shouldBe "LEGENDARY"
                CardStyles.getRarityName(50000) shouldBe "LEGENDARY"
            }
        }

        describe("getSkillBarColors") {

            it("returns combat colors for COMBAT category") {
                val (start, end) = CardStyles.getSkillBarColors(SkillCategory.COMBAT)

                start shouldBe CardStyles.COMBAT_BAR_START
                end shouldBe CardStyles.COMBAT_BAR_END
            }

            it("returns gathering colors for GATHERING category") {
                val (start, end) = CardStyles.getSkillBarColors(SkillCategory.GATHERING)

                start shouldBe CardStyles.GATHERING_BAR_START
                end shouldBe CardStyles.GATHERING_BAR_END
            }

            it("returns misc colors for MISC category") {
                val (start, end) = CardStyles.getSkillBarColors(SkillCategory.MISC)

                start shouldBe CardStyles.MISC_BAR_START
                end shouldBe CardStyles.MISC_BAR_END
            }
        }

        describe("getCategoryHeaderColor") {

            it("returns header color for COMBAT") {
                CardStyles.getCategoryHeaderColor(SkillCategory.COMBAT) shouldBe CardStyles.HEADER_COMBAT
            }

            it("returns header color for GATHERING") {
                CardStyles.getCategoryHeaderColor(SkillCategory.GATHERING) shouldBe CardStyles.HEADER_GATHERING
            }

            it("returns header color for MISC") {
                CardStyles.getCategoryHeaderColor(SkillCategory.MISC) shouldBe CardStyles.HEADER_MISC
            }
        }

        describe("getBackgroundGradient") {

            it("returns common gradient for power level < 1000") {
                val (start, end) = CardStyles.getBackgroundGradient(500)

                start shouldBe CardStyles.BG_COMMON_START
                end shouldBe CardStyles.BG_COMMON_END
            }

            it("returns rare gradient for power level 1000-4999") {
                val (start, end) = CardStyles.getBackgroundGradient(2500)

                start shouldBe CardStyles.BG_RARE_START
                end shouldBe CardStyles.BG_RARE_END
            }

            it("returns epic gradient for power level 5000-9999") {
                val (start, end) = CardStyles.getBackgroundGradient(7500)

                start shouldBe CardStyles.BG_EPIC_START
                end shouldBe CardStyles.BG_EPIC_END
            }

            it("returns legendary gradient for power level >= 10000") {
                val (start, end) = CardStyles.getBackgroundGradient(15000)

                start shouldBe CardStyles.BG_LEGENDARY_START
                end shouldBe CardStyles.BG_LEGENDARY_END
            }

            it("boundary at 1000 returns rare") {
                val (start, end) = CardStyles.getBackgroundGradient(1000)

                start shouldBe CardStyles.BG_RARE_START
                end shouldBe CardStyles.BG_RARE_END
            }

            it("boundary at 5000 returns epic") {
                val (start, end) = CardStyles.getBackgroundGradient(5000)

                start shouldBe CardStyles.BG_EPIC_START
                end shouldBe CardStyles.BG_EPIC_END
            }

            it("boundary at 10000 returns legendary") {
                val (start, end) = CardStyles.getBackgroundGradient(10000)

                start shouldBe CardStyles.BG_LEGENDARY_START
                end shouldBe CardStyles.BG_LEGENDARY_END
            }
        }
    }

    describe("SkillCategory enum") {

        it("has COMBAT category") {
            SkillCategory.COMBAT.name shouldBe "COMBAT"
        }

        it("has GATHERING category") {
            SkillCategory.GATHERING.name shouldBe "GATHERING"
        }

        it("has MISC category") {
            SkillCategory.MISC.name shouldBe "MISC"
        }

        it("has exactly 3 categories") {
            SkillCategory.values().size shouldBe 3
        }
    }
})
