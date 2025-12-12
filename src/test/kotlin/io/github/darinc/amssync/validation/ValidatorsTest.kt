package io.github.darinc.amssync.validation

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class ValidatorsTest : DescribeSpec({

    describe("formatSkillName") {

        it("formats uppercase skill name to title case") {
            Validators.formatSkillName("MINING") shouldBe "Mining"
        }

        it("formats lowercase skill name to title case") {
            Validators.formatSkillName("woodcutting") shouldBe "Woodcutting"
        }

        it("formats mixed case to title case") {
            Validators.formatSkillName("ArChErY") shouldBe "Archery"
        }

        it("handles single character") {
            Validators.formatSkillName("a") shouldBe "A"
        }

        it("handles empty string") {
            Validators.formatSkillName("") shouldBe ""
        }
    }

    describe("Minecraft username validation") {

        describe("isValidMinecraftUsername") {

            it("accepts valid 3-character username") {
                Validators.isValidMinecraftUsername("abc") shouldBe true
            }

            it("accepts valid 16-character username") {
                Validators.isValidMinecraftUsername("a".repeat(16)) shouldBe true
            }

            it("accepts username with underscores") {
                Validators.isValidMinecraftUsername("Player_Name_1") shouldBe true
            }

            it("accepts alphanumeric username") {
                Validators.isValidMinecraftUsername("Player123") shouldBe true
            }

            it("rejects username shorter than 3 characters") {
                Validators.isValidMinecraftUsername("ab") shouldBe false
            }

            it("rejects empty username") {
                Validators.isValidMinecraftUsername("") shouldBe false
            }

            it("rejects username longer than 16 characters") {
                Validators.isValidMinecraftUsername("a".repeat(17)) shouldBe false
            }

            it("rejects username with spaces") {
                Validators.isValidMinecraftUsername("Player Name") shouldBe false
            }

            it("rejects username with special characters") {
                Validators.isValidMinecraftUsername("Player@Name") shouldBe false
                Validators.isValidMinecraftUsername("Player-Name") shouldBe false
                Validators.isValidMinecraftUsername("Player.Name") shouldBe false
            }
        }

        describe("getMinecraftUsernameError") {

            it("returns empty error for empty username") {
                Validators.getMinecraftUsernameError("") shouldContain "empty"
            }

            it("returns too short error for 2-char username") {
                val error = Validators.getMinecraftUsernameError("ab")
                error shouldContain "at least 3"
                error shouldContain "2"
            }

            it("returns too long error for 17-char username") {
                val error = Validators.getMinecraftUsernameError("a".repeat(17))
                error shouldContain "exceed 16"
                error shouldContain "17"
            }

            it("returns invalid characters error for special chars") {
                val error = Validators.getMinecraftUsernameError("Player@Name")
                error shouldContain "letters"
                error shouldContain "numbers"
                error shouldContain "underscores"
            }
        }
    }

    describe("Discord ID validation") {

        describe("isValidDiscordId") {

            it("accepts valid 17-digit ID") {
                Validators.isValidDiscordId("12345678901234567") shouldBe true
            }

            it("accepts valid 18-digit ID") {
                Validators.isValidDiscordId("123456789012345678") shouldBe true
            }

            it("accepts valid 19-digit ID") {
                Validators.isValidDiscordId("1234567890123456789") shouldBe true
            }

            it("rejects ID shorter than 17 digits") {
                Validators.isValidDiscordId("1234567890123456") shouldBe false
            }

            it("rejects empty ID") {
                Validators.isValidDiscordId("") shouldBe false
            }

            it("rejects ID longer than 19 digits") {
                Validators.isValidDiscordId("12345678901234567890") shouldBe false
            }

            it("rejects ID with letters") {
                Validators.isValidDiscordId("1234567890123456a") shouldBe false
            }

            it("rejects ID with spaces") {
                Validators.isValidDiscordId("1234567890123456 7") shouldBe false
            }

            it("rejects ID with special characters") {
                Validators.isValidDiscordId("1234567890123456-") shouldBe false
            }
        }

        describe("getDiscordIdError") {

            it("returns empty error for empty ID") {
                Validators.getDiscordIdError("") shouldContain "empty"
            }

            it("returns non-numeric error for ID with letters") {
                Validators.getDiscordIdError("1234567890123456a") shouldContain "digits"
            }

            it("returns too short error for 16-digit ID") {
                val error = Validators.getDiscordIdError("1234567890123456")
                error shouldContain "at least 17"
                error shouldContain "16"
            }

            it("returns too long error for 20-digit ID") {
                val error = Validators.getDiscordIdError("12345678901234567890")
                error shouldContain "exceed 19"
                error shouldContain "20"
            }
        }
    }
})
