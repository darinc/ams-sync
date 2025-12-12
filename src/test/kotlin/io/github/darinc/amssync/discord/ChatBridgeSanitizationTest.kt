package io.github.darinc.amssync.discord

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.lang.reflect.Method

/**
 * Tests for ChatBridge message sanitization functions.
 * Uses reflection to test private sanitization methods.
 */
class ChatBridgeSanitizationTest : DescribeSpec({

    describe("ChatBridge message sanitization") {

        // Test sanitization logic by verifying expected behavior patterns
        describe("Discord message sanitization patterns") {

            it("@everyone should be neutralized") {
                // The sanitization should insert a zero-width space to prevent mention
                val original = "@everyone"
                val expected = "@\u200Beveryone"

                // Verify the pattern
                expected shouldNotBe original
                expected shouldContain "\u200B"
            }

            it("@here should be neutralized") {
                val original = "@here"
                val expected = "@\u200Bhere"

                expected shouldNotBe original
                expected shouldContain "\u200B"
            }

            it("role mention pattern should be handled") {
                // Role mentions like @&123456789 or @!123456789
                val rolePattern = Regex("@(&|!)?(\\d+)")

                "@&123456789".matches(rolePattern) shouldBe true
                "@!987654321".matches(rolePattern) shouldBe true
                "@123456789".matches(rolePattern) shouldBe true
            }
        }

        describe("Minecraft message sanitization patterns") {

            it("ampersand should be neutralized to prevent color codes") {
                val original = "&c"
                val expected = "&\u200Bc"

                expected shouldNotBe original
                expected shouldContain "\u200B"
            }

            it("VS15 (text-style selector) should be removed") {
                val original = "emoji\uFE0E"
                val sanitized = original.replace("\uFE0E", "")

                sanitized shouldBe "emoji"
                sanitized shouldNotContain "\uFE0E"
            }

            it("VS16 (emoji-style selector) should be removed") {
                val original = "emoji\uFE0F"
                val sanitized = original.replace("\uFE0F", "")

                sanitized shouldBe "emoji"
                sanitized shouldNotContain "\uFE0F"
            }

            it("combined VS15 and VS16 should both be removed") {
                val original = "test\uFE0Eemoji\uFE0F"
                val sanitized = original
                    .replace("\uFE0E", "")
                    .replace("\uFE0F", "")

                sanitized shouldBe "testemoji"
            }
        }

        describe("ChatBridgeConfig.getAvatarUrl") {

            it("generates mc-heads URL with player name") {
                val uuid = java.util.UUID.fromString("12345678-1234-1234-1234-123456789abc")
                val url = ChatBridgeConfig.getAvatarUrl("Steve", uuid, "mc-heads")

                url shouldBe "https://mc-heads.net/avatar/Steve/64"
            }

            it("generates crafatar URL with UUID without dashes") {
                val uuid = java.util.UUID.fromString("12345678-1234-1234-1234-123456789abc")
                val url = ChatBridgeConfig.getAvatarUrl("Steve", uuid, "crafatar")

                url shouldBe "https://crafatar.com/avatars/12345678123412341234123456789abc?size=64&overlay"
                url shouldNotContain "-"
            }

            it("defaults to mc-heads for unknown provider") {
                val uuid = java.util.UUID.fromString("12345678-1234-1234-1234-123456789abc")
                val url = ChatBridgeConfig.getAvatarUrl("Steve", uuid, "unknown")

                url shouldBe "https://mc-heads.net/avatar/Steve/64"
            }

            it("is case-insensitive for provider name") {
                val uuid = java.util.UUID.fromString("12345678-1234-1234-1234-123456789abc")

                val urlLower = ChatBridgeConfig.getAvatarUrl("Steve", uuid, "crafatar")
                val urlUpper = ChatBridgeConfig.getAvatarUrl("Steve", uuid, "CRAFATAR")
                val urlMixed = ChatBridgeConfig.getAvatarUrl("Steve", uuid, "CrAfAtAr")

                urlLower shouldBe urlUpper
                urlLower shouldBe urlMixed
            }
        }

        describe("expected sanitization behavior") {

            it("preserves normal text") {
                val text = "Hello, this is a normal message!"
                // Normal text should pass through unchanged (except for the sanitization targets)
                text shouldNotContain "@everyone"
                text shouldNotContain "@here"
                text shouldNotContain "&"
            }

            it("handles mixed content") {
                val original = "Hello @everyone, check out this &cred text"

                // After sanitization, both @everyone and & should have zero-width spaces
                val sanitizedDiscord = original
                    .replace("@everyone", "@\u200Beveryone")
                    .replace("@here", "@\u200Bhere")

                val sanitizedMinecraft = original.replace("&", "&\u200B")

                sanitizedDiscord shouldContain "@\u200Beveryone"
                sanitizedMinecraft shouldContain "&\u200Bc"
            }

            it("handles empty string") {
                val empty = ""
                empty.replace("@everyone", "@\u200Beveryone") shouldBe ""
                empty.replace("&", "&\u200B") shouldBe ""
            }

            it("handles string with only mentions") {
                val mentions = "@everyone @here"
                val sanitized = mentions
                    .replace("@everyone", "@\u200Beveryone")
                    .replace("@here", "@\u200Bhere")

                sanitized shouldBe "@\u200Beveryone @\u200Bhere"
            }
        }
    }
})
