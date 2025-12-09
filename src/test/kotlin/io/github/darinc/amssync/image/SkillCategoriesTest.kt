package io.github.darinc.amssync.image

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey

class SkillCategoriesTest : DescribeSpec({

    describe("SkillCategories.getCategory") {

        it("categorizes combat skills correctly") {
            SkillCategories.getCategory("SWORDS") shouldBe SkillCategory.COMBAT
            SkillCategories.getCategory("AXES") shouldBe SkillCategory.COMBAT
            SkillCategories.getCategory("ARCHERY") shouldBe SkillCategory.COMBAT
            SkillCategories.getCategory("UNARMED") shouldBe SkillCategory.COMBAT
            SkillCategories.getCategory("TAMING") shouldBe SkillCategory.COMBAT
        }

        it("categorizes gathering skills correctly") {
            SkillCategories.getCategory("MINING") shouldBe SkillCategory.GATHERING
            SkillCategories.getCategory("WOODCUTTING") shouldBe SkillCategory.GATHERING
            SkillCategories.getCategory("HERBALISM") shouldBe SkillCategory.GATHERING
            SkillCategories.getCategory("EXCAVATION") shouldBe SkillCategory.GATHERING
            SkillCategories.getCategory("FISHING") shouldBe SkillCategory.GATHERING
        }

        it("categorizes misc skills correctly") {
            SkillCategories.getCategory("REPAIR") shouldBe SkillCategory.MISC
            SkillCategories.getCategory("ACROBATICS") shouldBe SkillCategory.MISC
            SkillCategories.getCategory("ALCHEMY") shouldBe SkillCategory.MISC
        }

        it("is case insensitive") {
            SkillCategories.getCategory("swords") shouldBe SkillCategory.COMBAT
            SkillCategories.getCategory("Mining") shouldBe SkillCategory.GATHERING
            SkillCategories.getCategory("repair") shouldBe SkillCategory.MISC
        }

        it("defaults unknown skills to MISC") {
            SkillCategories.getCategory("UNKNOWN") shouldBe SkillCategory.MISC
        }
    }

    describe("SkillCategories.categorize") {

        it("separates skills into categories") {
            val stats = mapOf(
                "SWORDS" to 100,
                "MINING" to 200,
                "REPAIR" to 50,
                "AXES" to 75,
                "WOODCUTTING" to 150
            )

            val (combat, gathering, misc) = SkillCategories.categorize(stats)

            combat shouldContainKey "SWORDS"
            combat shouldContainKey "AXES"
            combat["SWORDS"] shouldBe 100
            combat["AXES"] shouldBe 75

            gathering shouldContainKey "MINING"
            gathering shouldContainKey "WOODCUTTING"
            gathering["MINING"] shouldBe 200
            gathering["WOODCUTTING"] shouldBe 150

            misc shouldContainKey "REPAIR"
            misc["REPAIR"] shouldBe 50
        }

        it("filters out unknown skills") {
            val stats = mapOf(
                "SWORDS" to 100,
                "SALVAGE" to 50,  // Child skill, should be filtered
                "UNKNOWN" to 25
            )

            val (combat, gathering, misc) = SkillCategories.categorize(stats)

            combat shouldContainKey "SWORDS"
            combat shouldNotContainKey "SALVAGE"
            combat shouldNotContainKey "UNKNOWN"
            gathering.isEmpty() shouldBe true
            misc.isEmpty() shouldBe true
        }
    }

    describe("SkillCategories.getDisplayName") {

        it("returns proper display names") {
            SkillCategories.getDisplayName("SWORDS") shouldBe "Swords"
            SkillCategories.getDisplayName("WOODCUTTING") shouldBe "Woodcut"
            SkillCategories.getDisplayName("ACROBATICS") shouldBe "Acrobatics"
        }

        it("capitalizes unknown skills") {
            SkillCategories.getDisplayName("unknown") shouldBe "Unknown"
        }
    }

    describe("SkillCategories.isMastered") {

        it("returns true for level 1000 and above") {
            SkillCategories.isMastered(1000) shouldBe true
            SkillCategories.isMastered(1500) shouldBe true
        }

        it("returns false for levels below 1000") {
            SkillCategories.isMastered(999) shouldBe false
            SkillCategories.isMastered(0) shouldBe false
        }
    }
})
