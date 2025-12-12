package io.github.darinc.amssync.discord.commands

import io.github.darinc.amssync.linking.UserMappingService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk

class UsernameResolverTest : DescribeSpec({

    val userMappingService = mockk<UserMappingService>()
    val resolver = UsernameResolver(userMappingService)

    describe("resolve") {

        describe("when input is null or blank") {

            it("returns linked MC username for invoker when null") {
                every { userMappingService.getMinecraftUsername("123456789012345678") } returns "Steve"

                val result = resolver.resolve(null, "123456789012345678")

                result shouldBe "Steve"
            }

            it("returns linked MC username for invoker when blank") {
                every { userMappingService.getMinecraftUsername("123456789012345678") } returns "Steve"

                val result = resolver.resolve("   ", "123456789012345678")

                result shouldBe "Steve"
            }

            it("throws when invoker is not linked") {
                every { userMappingService.getMinecraftUsername("123456789012345678") } returns null

                val exception = shouldThrow<IllegalArgumentException> {
                    resolver.resolve(null, "123456789012345678")
                }

                exception.message shouldContain "not linked"
            }
        }

        describe("when input is a Discord mention") {

            it("strips mention format and resolves Discord ID") {
                every { userMappingService.getMinecraftUsername("123456789012345678") } returns "Alex"

                val result = resolver.resolve("<@123456789012345678>", "999999999999999999")

                result shouldBe "Alex"
            }

            it("strips nickname mention format and resolves Discord ID") {
                every { userMappingService.getMinecraftUsername("123456789012345678") } returns "Alex"

                val result = resolver.resolve("<@!123456789012345678>", "999999999999999999")

                result shouldBe "Alex"
            }

            it("throws when mentioned user is not linked") {
                every { userMappingService.getMinecraftUsername("123456789012345678") } returns null

                val exception = shouldThrow<IllegalArgumentException> {
                    resolver.resolve("<@123456789012345678>", "999999999999999999")
                }

                exception.message shouldContain "not linked"
                exception.message shouldContain "<@123456789012345678>"
            }
        }

        describe("when input is a Discord ID") {

            it("resolves 17-digit Discord ID") {
                every { userMappingService.getMinecraftUsername("12345678901234567") } returns "Player17"

                val result = resolver.resolve("12345678901234567", "999999999999999999")

                result shouldBe "Player17"
            }

            it("resolves 18-digit Discord ID") {
                every { userMappingService.getMinecraftUsername("123456789012345678") } returns "Player18"

                val result = resolver.resolve("123456789012345678", "999999999999999999")

                result shouldBe "Player18"
            }

            it("resolves 19-digit Discord ID") {
                every { userMappingService.getMinecraftUsername("1234567890123456789") } returns "Player19"

                val result = resolver.resolve("1234567890123456789", "999999999999999999")

                result shouldBe "Player19"
            }

            it("throws when Discord ID is not linked") {
                every { userMappingService.getMinecraftUsername("123456789012345678") } returns null

                val exception = shouldThrow<IllegalArgumentException> {
                    resolver.resolve("123456789012345678", "999999999999999999")
                }

                exception.message shouldContain "not linked"
            }
        }

        describe("when input is a Minecraft username") {

            it("passes through valid MC username") {
                val result = resolver.resolve("Steve", "999999999999999999")

                result shouldBe "Steve"
            }

            it("passes through username with underscores") {
                val result = resolver.resolve("Player_Name_1", "999999999999999999")

                result shouldBe "Player_Name_1"
            }

            it("trims whitespace from username") {
                val result = resolver.resolve("  Steve  ", "999999999999999999")

                result shouldBe "Steve"
            }

            it("throws for username that is too short") {
                val exception = shouldThrow<IllegalArgumentException> {
                    resolver.resolve("ab", "999999999999999999")
                }

                exception.message shouldContain "at least 3"
            }

            it("throws for username that is too long") {
                val exception = shouldThrow<IllegalArgumentException> {
                    resolver.resolve("a".repeat(17), "999999999999999999")
                }

                exception.message shouldContain "exceed 16"
            }

            it("throws for username with invalid characters") {
                val exception = shouldThrow<IllegalArgumentException> {
                    resolver.resolve("Player@Name", "999999999999999999")
                }

                exception.message shouldContain "letters"
            }
        }
    }
})
