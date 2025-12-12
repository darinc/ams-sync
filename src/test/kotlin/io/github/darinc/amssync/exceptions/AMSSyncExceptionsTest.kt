package io.github.darinc.amssync.exceptions

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

class AMSSyncExceptionsTest : DescribeSpec({

    describe("Discord Connection Exceptions") {

        describe("DiscordAuthenticationException") {

            it("has default message") {
                val exception = DiscordAuthenticationException()

                exception.message shouldBe "Invalid Discord bot token or authentication failed"
            }

            it("accepts custom message") {
                val exception = DiscordAuthenticationException("Custom auth error")

                exception.message shouldBe "Custom auth error"
            }

            it("accepts cause") {
                val cause = RuntimeException("Root cause")
                val exception = DiscordAuthenticationException(cause = cause)

                exception.cause shouldBe cause
            }

            it("is a DiscordConnectionException") {
                val exception = DiscordAuthenticationException()

                exception.shouldBeInstanceOf<DiscordConnectionException>()
            }

            it("is an AMSSyncException") {
                val exception = DiscordAuthenticationException()

                exception.shouldBeInstanceOf<AMSSyncException>()
            }
        }

        describe("DiscordTimeoutException") {

            it("includes timeout duration in message") {
                val exception = DiscordTimeoutException(timeoutMs = 5000L)

                exception.message shouldContain "5000ms"
                exception.timeoutMs shouldBe 5000L
            }

            it("accepts custom message") {
                val exception = DiscordTimeoutException(
                    timeoutMs = 3000L,
                    message = "Connection timed out after 3 seconds"
                )

                exception.message shouldBe "Connection timed out after 3 seconds"
            }

            it("is a DiscordConnectionException") {
                val exception = DiscordTimeoutException(timeoutMs = 1000L)

                exception.shouldBeInstanceOf<DiscordConnectionException>()
            }
        }

        describe("DiscordNetworkException") {

            it("stores message") {
                val exception = DiscordNetworkException("Connection refused")

                exception.message shouldBe "Connection refused"
            }

            it("is a DiscordConnectionException") {
                val exception = DiscordNetworkException("Network error")

                exception.shouldBeInstanceOf<DiscordConnectionException>()
            }
        }
    }

    describe("Discord API Exceptions") {

        describe("DiscordRateLimitException") {

            it("includes retry after in message") {
                val exception = DiscordRateLimitException(retryAfterMs = 10000L)

                exception.message shouldContain "10000ms"
                exception.retryAfterMs shouldBe 10000L
            }

            it("is a DiscordApiException") {
                val exception = DiscordRateLimitException(retryAfterMs = 5000L)

                exception.shouldBeInstanceOf<DiscordApiException>()
            }
        }

        describe("DiscordPermissionException") {

            it("includes required permission in message") {
                val exception = DiscordPermissionException(requiredPermission = "MANAGE_MESSAGES")

                exception.message shouldContain "MANAGE_MESSAGES"
                exception.requiredPermission shouldBe "MANAGE_MESSAGES"
            }

            it("is a DiscordApiException") {
                val exception = DiscordPermissionException(requiredPermission = "SEND_MESSAGES")

                exception.shouldBeInstanceOf<DiscordApiException>()
            }
        }

        describe("DiscordCommandRegistrationException") {

            it("stores message") {
                val exception = DiscordCommandRegistrationException("Invalid guild ID")

                exception.message shouldBe "Invalid guild ID"
            }

            it("is a DiscordApiException") {
                val exception = DiscordCommandRegistrationException("Registration failed")

                exception.shouldBeInstanceOf<DiscordApiException>()
            }
        }

        describe("CircuitBreakerOpenException") {

            it("has default message") {
                val exception = CircuitBreakerOpenException()

                exception.message shouldContain "circuit breaker is OPEN"
            }

            it("is a DiscordApiException") {
                val exception = CircuitBreakerOpenException()

                exception.shouldBeInstanceOf<DiscordApiException>()
            }
        }
    }

    describe("MCMMO Query Exceptions") {

        describe("LeaderboardTimeoutException") {

            it("includes duration in message") {
                val exception = LeaderboardTimeoutException(durationMs = 30000L)

                exception.message shouldContain "30000ms"
                exception.durationMs shouldBe 30000L
            }

            it("is a McmmoQueryException") {
                val exception = LeaderboardTimeoutException(durationMs = 5000L)

                exception.shouldBeInstanceOf<McmmoQueryException>()
            }
        }

        describe("PlayerDataNotFoundException") {

            it("includes player name in message") {
                val exception = PlayerDataNotFoundException(playerName = "Steve")

                exception.message shouldContain "Steve"
                exception.playerName shouldBe "Steve"
            }

            it("is a McmmoQueryException") {
                val exception = PlayerDataNotFoundException(playerName = "Alex")

                exception.shouldBeInstanceOf<McmmoQueryException>()
            }
        }

        describe("InvalidSkillException") {

            it("includes skill name and valid skills in message") {
                val exception = InvalidSkillException(
                    skillName = "INVALID",
                    validSkills = listOf("MINING", "WOODCUTTING", "FISHING")
                )

                exception.message shouldContain "INVALID"
                exception.message shouldContain "MINING"
                exception.skillName shouldBe "INVALID"
                exception.validSkills shouldBe listOf("MINING", "WOODCUTTING", "FISHING")
            }

            it("is a McmmoQueryException") {
                val exception = InvalidSkillException(
                    skillName = "FOO",
                    validSkills = listOf("BAR")
                )

                exception.shouldBeInstanceOf<McmmoQueryException>()
            }
        }
    }

    describe("User Mapping Exceptions") {

        describe("DuplicateMappingException") {

            it("includes Discord ID and existing mapping in message") {
                val exception = DuplicateMappingException(
                    discordId = "123456789012345678",
                    existingMapping = "Steve"
                )

                exception.message shouldContain "123456789012345678"
                exception.message shouldContain "Steve"
                exception.discordId shouldBe "123456789012345678"
                exception.existingMapping shouldBe "Steve"
            }

            it("is a UserMappingException") {
                val exception = DuplicateMappingException(
                    discordId = "111",
                    existingMapping = "Alex"
                )

                exception.shouldBeInstanceOf<UserMappingException>()
            }
        }

        describe("MappingNotFoundException") {

            it("includes identifier in message") {
                val exception = MappingNotFoundException(identifier = "Steve")

                exception.message shouldContain "Steve"
                exception.identifier shouldBe "Steve"
            }

            it("is a UserMappingException") {
                val exception = MappingNotFoundException(identifier = "123")

                exception.shouldBeInstanceOf<UserMappingException>()
            }
        }

        describe("InvalidDiscordIdException") {

            it("includes Discord ID in message") {
                val exception = InvalidDiscordIdException(discordId = "not-a-snowflake")

                exception.message shouldContain "not-a-snowflake"
                exception.message shouldContain "17-19 digit"
                exception.discordId shouldBe "not-a-snowflake"
            }

            it("is a UserMappingException") {
                val exception = InvalidDiscordIdException(discordId = "abc")

                exception.shouldBeInstanceOf<UserMappingException>()
            }
        }
    }

    describe("Configuration Exceptions") {

        describe("MissingConfigurationException") {

            it("includes config key in message") {
                val exception = MissingConfigurationException(configKey = "discord.token")

                exception.message shouldContain "discord.token"
                exception.configKey shouldBe "discord.token"
            }

            it("is a ConfigurationException") {
                val exception = MissingConfigurationException(configKey = "test.key")

                exception.shouldBeInstanceOf<ConfigurationException>()
            }
        }

        describe("InvalidConfigurationException") {

            it("includes config key and value in message") {
                val exception = InvalidConfigurationException(
                    configKey = "discord.guild-id",
                    configValue = "not-a-number"
                )

                exception.message shouldContain "discord.guild-id"
                exception.message shouldContain "not-a-number"
                exception.configKey shouldBe "discord.guild-id"
                exception.configValue shouldBe "not-a-number"
            }

            it("is a ConfigurationException") {
                val exception = InvalidConfigurationException(
                    configKey = "test",
                    configValue = "value"
                )

                exception.shouldBeInstanceOf<ConfigurationException>()
            }
        }

        describe("InvalidBotTokenException") {

            it("includes masked token in message") {
                val exception = InvalidBotTokenException(maskedToken = "MTIzNDU2Nz...")

                exception.message shouldContain "MTIzNDU2Nz..."
                exception.message shouldContain "malformed"
                exception.maskedToken shouldBe "MTIzNDU2Nz..."
            }

            it("is a ConfigurationException") {
                val exception = InvalidBotTokenException(maskedToken = "abc...")

                exception.shouldBeInstanceOf<ConfigurationException>()
            }
        }

        describe("InvalidGuildIdException") {

            it("includes guild ID in message") {
                val exception = InvalidGuildIdException(guildId = "not-a-snowflake")

                exception.message shouldContain "not-a-snowflake"
                exception.message shouldContain "17-19 digit"
                exception.guildId shouldBe "not-a-snowflake"
            }

            it("is a ConfigurationException") {
                val exception = InvalidGuildIdException(guildId = "123")

                exception.shouldBeInstanceOf<ConfigurationException>()
            }
        }
    }

    describe("Exception hierarchy") {

        it("all exceptions extend AMSSyncException") {
            val exceptions = listOf(
                DiscordAuthenticationException(),
                DiscordTimeoutException(timeoutMs = 1000L),
                DiscordNetworkException("test"),
                DiscordRateLimitException(retryAfterMs = 1000L),
                DiscordPermissionException(requiredPermission = "test"),
                DiscordCommandRegistrationException("test"),
                CircuitBreakerOpenException(),
                LeaderboardTimeoutException(durationMs = 1000L),
                PlayerDataNotFoundException(playerName = "test"),
                InvalidSkillException(skillName = "test", validSkills = listOf()),
                DuplicateMappingException(discordId = "123", existingMapping = "test"),
                MappingNotFoundException(identifier = "test"),
                InvalidDiscordIdException(discordId = "test"),
                MissingConfigurationException(configKey = "test"),
                InvalidConfigurationException(configKey = "test", configValue = "value"),
                InvalidBotTokenException(maskedToken = "test"),
                InvalidGuildIdException(guildId = "test")
            )

            exceptions.forEach { exception ->
                exception.shouldBeInstanceOf<AMSSyncException>()
            }
        }

        it("all exceptions can have a cause") {
            val cause = RuntimeException("root cause")

            val exception = PlayerDataNotFoundException(
                playerName = "test",
                cause = cause
            )

            exception.cause shouldBe cause
        }
    }
})
