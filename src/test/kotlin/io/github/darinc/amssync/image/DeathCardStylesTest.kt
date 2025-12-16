package io.github.darinc.amssync.image

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class DeathCardStylesTest : DescribeSpec({

    describe("DeathCardStyles") {

        describe("getTierName") {

            it("returns COMMON for power level 0") {
                DeathCardStyles.getTierName(0) shouldBe "COMMON"
            }

            it("returns COMMON for power level 999") {
                DeathCardStyles.getTierName(999) shouldBe "COMMON"
            }

            it("returns RARE for power level 1000") {
                DeathCardStyles.getTierName(1000) shouldBe "RARE"
            }

            it("returns RARE for power level 4999") {
                DeathCardStyles.getTierName(4999) shouldBe "RARE"
            }

            it("returns EPIC for power level 5000") {
                DeathCardStyles.getTierName(5000) shouldBe "EPIC"
            }

            it("returns EPIC for power level 9999") {
                DeathCardStyles.getTierName(9999) shouldBe "EPIC"
            }

            it("returns LEGENDARY for power level 10000") {
                DeathCardStyles.getTierName(10000) shouldBe "LEGENDARY"
            }

            it("returns LEGENDARY for very high power levels") {
                DeathCardStyles.getTierName(50000) shouldBe "LEGENDARY"
            }
        }

        describe("getTierColor") {

            it("returns TIER_COMMON for power level below 1000") {
                DeathCardStyles.getTierColor(500) shouldBe DeathCardStyles.TIER_COMMON
            }

            it("returns TIER_RARE for power level 1000-4999") {
                DeathCardStyles.getTierColor(2500) shouldBe DeathCardStyles.TIER_RARE
            }

            it("returns TIER_EPIC for power level 5000-9999") {
                DeathCardStyles.getTierColor(7500) shouldBe DeathCardStyles.TIER_EPIC
            }

            it("returns TIER_LEGENDARY for power level 10000+") {
                DeathCardStyles.getTierColor(15000) shouldBe DeathCardStyles.TIER_LEGENDARY
            }

            it("returns correct color at exact boundaries") {
                DeathCardStyles.getTierColor(999) shouldBe DeathCardStyles.TIER_COMMON
                DeathCardStyles.getTierColor(1000) shouldBe DeathCardStyles.TIER_RARE
                DeathCardStyles.getTierColor(4999) shouldBe DeathCardStyles.TIER_RARE
                DeathCardStyles.getTierColor(5000) shouldBe DeathCardStyles.TIER_EPIC
                DeathCardStyles.getTierColor(9999) shouldBe DeathCardStyles.TIER_EPIC
                DeathCardStyles.getTierColor(10000) shouldBe DeathCardStyles.TIER_LEGENDARY
            }
        }

        describe("formatPowerLevel") {

            it("formats small numbers without commas") {
                DeathCardStyles.formatPowerLevel(0) shouldBe "0"
                DeathCardStyles.formatPowerLevel(999) shouldBe "999"
            }

            it("formats thousands with commas") {
                DeathCardStyles.formatPowerLevel(1000) shouldBe "1,000"
                DeathCardStyles.formatPowerLevel(5000) shouldBe "5,000"
            }

            it("formats large numbers with multiple commas") {
                DeathCardStyles.formatPowerLevel(10000) shouldBe "10,000"
                DeathCardStyles.formatPowerLevel(100000) shouldBe "100,000"
                DeathCardStyles.formatPowerLevel(1000000) shouldBe "1,000,000"
            }

            it("formats typical power levels correctly") {
                DeathCardStyles.formatPowerLevel(1234) shouldBe "1,234"
                DeathCardStyles.formatPowerLevel(12345) shouldBe "12,345"
            }
        }

        describe("constants") {

            it("has expected card dimensions") {
                DeathCardStyles.CARD_WIDTH shouldBe 500
                DeathCardStyles.CARD_HEIGHT shouldBe 350
            }

            it("has non-zero section heights") {
                DeathCardStyles.HEADER_HEIGHT shouldBe 55
                DeathCardStyles.DEATH_MESSAGE_BOX_HEIGHT shouldBe 50
                DeathCardStyles.COFFIN_SECTION_HEIGHT shouldBe 130
                DeathCardStyles.COMMENTARY_HEIGHT shouldBe 55
                DeathCardStyles.FOOTER_HEIGHT shouldBe 30
            }
        }
    }
})
