package io.github.darinc.amssync.config

import io.github.darinc.amssync.exceptions.InvalidBotTokenException
import io.github.darinc.amssync.exceptions.InvalidGuildIdException
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.mockk
import io.mockk.verify
import java.util.logging.Logger

class ConfigValidatorTest : DescribeSpec({

    describe("ConfigValidator") {

        describe("isValidBotTokenFormat") {

            it("returns true for valid token format [base64].[timestamp].[hmac]") {
                // Valid token format: at least 18 chars, 6 chars, 27 chars separated by dots
                val validToken = "MTIzNDU2Nzg5MDEyMzQ1Njc4.ABCDEF.abcdefghijklmnopqrstuvwxyz1"
                ConfigValidator.isValidBotTokenFormat(validToken) shouldBe true
            }

            it("returns true for another valid token format") {
                // Longer base64 section
                val validToken = "OTg3NjU0MzIxMDk4NzY1NDMyMTA.Gh1jKl.ABCDEFGHIJKLMNOPQRSTUVWXYZ123"
                ConfigValidator.isValidBotTokenFormat(validToken) shouldBe true
            }

            it("returns false for blank token") {
                ConfigValidator.isValidBotTokenFormat("") shouldBe false
                ConfigValidator.isValidBotTokenFormat("   ") shouldBe false
            }

            it("returns false for token missing parts") {
                ConfigValidator.isValidBotTokenFormat("onlyonepart") shouldBe false
                ConfigValidator.isValidBotTokenFormat("two.parts") shouldBe false
            }

            it("returns false for token with invalid characters") {
                // Token with special characters that aren't allowed
                val invalidToken = "MTIzNDU2Nzg5MDEyMzQ1Njc4.ABC!@#.abcdefghijklmnopqrstuvwxyz1"
                ConfigValidator.isValidBotTokenFormat(invalidToken) shouldBe false
            }

            it("returns false for token with short parts") {
                // Timestamp part too short (less than 6 chars)
                val shortTimestamp = "MTIzNDU2Nzg5MDEyMzQ1Njc4.ABC.abcdefghijklmnopqrstuvwxyz1"
                ConfigValidator.isValidBotTokenFormat(shortTimestamp) shouldBe false

                // HMAC part too short (less than 27 chars)
                val shortHmac = "MTIzNDU2Nzg5MDEyMzQ1Njc4.ABCDEF.short"
                ConfigValidator.isValidBotTokenFormat(shortHmac) shouldBe false

                // Base64 part too short (less than 18 chars)
                val shortBase64 = "short.ABCDEF.abcdefghijklmnopqrstuvwxyz1"
                ConfigValidator.isValidBotTokenFormat(shortBase64) shouldBe false
            }
        }

        describe("isValidGuildIdFormat") {

            it("returns true for 17-digit snowflake") {
                ConfigValidator.isValidGuildIdFormat("12345678901234567") shouldBe true
            }

            it("returns true for 18-digit snowflake") {
                ConfigValidator.isValidGuildIdFormat("123456789012345678") shouldBe true
            }

            it("returns true for 19-digit snowflake") {
                ConfigValidator.isValidGuildIdFormat("1234567890123456789") shouldBe true
            }

            it("returns false for 16-digit id (too short)") {
                ConfigValidator.isValidGuildIdFormat("1234567890123456") shouldBe false
            }

            it("returns false for 20-digit id (too long)") {
                ConfigValidator.isValidGuildIdFormat("12345678901234567890") shouldBe false
            }

            it("returns false for non-numeric id") {
                ConfigValidator.isValidGuildIdFormat("1234567890123456a") shouldBe false
                ConfigValidator.isValidGuildIdFormat("abcdefghijklmnopq") shouldBe false
            }

            it("returns false for blank id") {
                ConfigValidator.isValidGuildIdFormat("") shouldBe false
                ConfigValidator.isValidGuildIdFormat("   ") shouldBe false
            }
        }

        describe("isValidDiscordIdFormat") {

            it("returns true for valid 17-digit snowflake") {
                ConfigValidator.isValidDiscordIdFormat("12345678901234567") shouldBe true
            }

            it("returns true for valid 18-digit snowflake") {
                ConfigValidator.isValidDiscordIdFormat("123456789012345678") shouldBe true
            }

            it("returns true for valid 19-digit snowflake") {
                ConfigValidator.isValidDiscordIdFormat("1234567890123456789") shouldBe true
            }

            it("returns false for invalid formats") {
                ConfigValidator.isValidDiscordIdFormat("") shouldBe false
                ConfigValidator.isValidDiscordIdFormat("abc") shouldBe false
                ConfigValidator.isValidDiscordIdFormat("1234567890123456") shouldBe false
            }
        }

        describe("maskToken") {

            it("shows first 10 chars plus ellipsis for long tokens") {
                val token = "MTIzNDU2Nzg5MDEyMzQ1Njc4.ABCDEF.abcdefghijklmnopqrstuvwxyz1"
                ConfigValidator.maskToken(token) shouldBe "MTIzNDU2Nz..."
            }

            it("returns [invalid token] for short tokens") {
                ConfigValidator.maskToken("short") shouldBe "[invalid token]"
                ConfigValidator.maskToken("1234567890") shouldBe "[invalid token]"
            }

            it("shows first 10 chars for exactly 11+ char tokens") {
                ConfigValidator.maskToken("12345678901") shouldBe "1234567890..."
            }
        }

        describe("validateBotToken") {

            it("does not throw for valid token") {
                val validToken = "MTIzNDU2Nzg5MDEyMzQ1Njc4.ABCDEF.abcdefghijklmnopqrstuvwxyz1"
                shouldNotThrow<InvalidBotTokenException> {
                    ConfigValidator.validateBotToken(validToken)
                }
            }

            it("throws InvalidBotTokenException for invalid token") {
                shouldThrow<InvalidBotTokenException> {
                    ConfigValidator.validateBotToken("invalid")
                }
            }

            it("throws InvalidBotTokenException for blank token") {
                shouldThrow<InvalidBotTokenException> {
                    ConfigValidator.validateBotToken("")
                }
            }
        }

        describe("validateGuildId") {

            it("does not throw for valid guild ID") {
                shouldNotThrow<InvalidGuildIdException> {
                    ConfigValidator.validateGuildId("123456789012345678")
                }
            }

            it("throws InvalidGuildIdException for invalid guild ID") {
                shouldThrow<InvalidGuildIdException> {
                    ConfigValidator.validateGuildId("invalid")
                }
            }

            it("throws InvalidGuildIdException for too short guild ID") {
                shouldThrow<InvalidGuildIdException> {
                    ConfigValidator.validateGuildId("1234567890123456")
                }
            }
        }

        describe("validateDiscordConfig") {

            val validToken = "MTIzNDU2Nzg5MDEyMzQ1Njc4.ABCDEF.abcdefghijklmnopqrstuvwxyz1"
            val validGuildId = "123456789012345678"

            it("returns success for valid token and guild ID") {
                val result = ConfigValidator.validateDiscordConfig(validToken, validGuildId)

                result.valid shouldBe true
                result.errors.shouldBeEmpty()
            }

            it("returns failure with error for invalid token format") {
                val result = ConfigValidator.validateDiscordConfig("invalid", validGuildId)

                result.valid shouldBe false
                result.errors.size shouldBe 1
                result.errors[0] shouldContain "token format"
            }

            it("returns failure for blank token") {
                val result = ConfigValidator.validateDiscordConfig("", validGuildId)

                result.valid shouldBe false
                result.errors[0] shouldContain "not configured"
            }

            it("returns success with warning for missing guild ID") {
                val result = ConfigValidator.validateDiscordConfig(validToken, "")

                result.valid shouldBe true
                result.warnings.size shouldBe 1
                result.warnings[0] shouldContain "Guild ID not configured"
            }

            it("returns failure for placeholder token YOUR_BOT_TOKEN_HERE") {
                val result = ConfigValidator.validateDiscordConfig("YOUR_BOT_TOKEN_HERE", validGuildId)

                result.valid shouldBe false
                result.errors[0] shouldContain "not configured"
            }

            it("returns success with warning for placeholder guild ID") {
                val result = ConfigValidator.validateDiscordConfig(validToken, "YOUR_GUILD_ID_HERE")

                result.valid shouldBe true
                result.warnings.size shouldBe 1
                result.warnings[0] shouldContain "Guild ID not configured"
            }

            it("returns failure for invalid guild ID format") {
                val result = ConfigValidator.validateDiscordConfig(validToken, "invalid_guild")

                result.valid shouldBe false
                result.errors[0] shouldContain "guild ID format is invalid"
            }

            it("logs errors and warnings when logger provided") {
                val logger = mockk<Logger>(relaxed = true)

                ConfigValidator.validateDiscordConfig("invalid", "invalid", logger)

                verify { logger.severe(any<String>()) }
            }

            it("logs warnings when logger provided and guild ID missing") {
                val logger = mockk<Logger>(relaxed = true)

                ConfigValidator.validateDiscordConfig(validToken, "", logger)

                verify { logger.warning(any<String>()) }
            }
        }

        describe("ValidationResult") {

            it("success factory creates valid result") {
                val result = ConfigValidator.ValidationResult.success()
                result.valid shouldBe true
                result.errors.shouldBeEmpty()
            }

            it("success factory includes warnings") {
                val warnings = listOf("warning1", "warning2")
                val result = ConfigValidator.ValidationResult.success(warnings)

                result.valid shouldBe true
                result.warnings shouldBe warnings
            }

            it("failure factory creates invalid result") {
                val errors = listOf("error1", "error2")
                val result = ConfigValidator.ValidationResult.failure(errors)

                result.valid shouldBe false
                result.errors shouldBe errors
            }
        }
    }
})
