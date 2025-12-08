package io.github.darinc.amssync.discord

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class RateLimiterTest : DescribeSpec({

    describe("RateLimiter") {

        describe("allowed requests") {

            it("allows first request") {
                val limiter = RateLimiter(maxRequests = 10, windowMs = 60000L)

                val result = limiter.checkRateLimit("user1")

                result shouldBe RateLimitResult.Allowed
            }

            it("allows multiple requests under limit") {
                val limiter = RateLimiter(maxRequests = 5, windowMs = 60000L)

                repeat(4) {
                    val result = limiter.checkRateLimit("user1")
                    result shouldBe RateLimitResult.Allowed
                }
            }

            it("tracks different users separately") {
                val limiter = RateLimiter(maxRequests = 2, windowMs = 60000L)

                // Each user gets their own quota
                limiter.checkRateLimit("user1") shouldBe RateLimitResult.Allowed
                limiter.checkRateLimit("user1") shouldBe RateLimitResult.Allowed

                limiter.checkRateLimit("user2") shouldBe RateLimitResult.Allowed
                limiter.checkRateLimit("user2") shouldBe RateLimitResult.Allowed
            }
        }

        describe("burst limiting") {

            it("returns BurstLimited when exceeding max requests") {
                val limiter = RateLimiter(
                    maxRequests = 3,
                    windowMs = 60000L,
                    penaltyCooldownMs = 5000L
                )

                // Use up quota
                repeat(3) {
                    limiter.checkRateLimit("user1")
                }

                // Next request should be burst limited
                val result = limiter.checkRateLimit("user1")
                result.shouldBeInstanceOf<RateLimitResult.BurstLimited>()
                (result as RateLimitResult.BurstLimited).retryAfterMs shouldBe 5000L
            }

            it("calculates retryAfterSeconds correctly") {
                val limiter = RateLimiter(
                    maxRequests = 1,
                    penaltyCooldownMs = 10000L
                )

                limiter.checkRateLimit("user1")
                val result = limiter.checkRateLimit("user1")

                result.shouldBeInstanceOf<RateLimitResult.BurstLimited>()
                (result as RateLimitResult.BurstLimited).retryAfterSeconds shouldBe 10.0
            }
        }

        describe("penalty cooldown") {

            it("enforces cooldown after burst limit exceeded") {
                val limiter = RateLimiter(
                    maxRequests = 1,
                    windowMs = 60000L,
                    penaltyCooldownMs = 100L // Short for testing
                )

                // Exceed limit
                limiter.checkRateLimit("user1")
                limiter.checkRateLimit("user1") // This triggers penalty

                // During cooldown, should return Cooldown result
                val result = limiter.checkRateLimit("user1")
                result.shouldBeInstanceOf<RateLimitResult.Cooldown>()
            }

            it("allows requests after cooldown expires") {
                val limiter = RateLimiter(
                    maxRequests = 1,
                    windowMs = 50L, // Short window for testing
                    penaltyCooldownMs = 50L
                )

                // Exceed limit
                limiter.checkRateLimit("user1")
                limiter.checkRateLimit("user1")

                // Wait for both cooldown AND window to expire
                Thread.sleep(70)

                // Should be allowed again (cooldown expired AND window reset)
                val result = limiter.checkRateLimit("user1")
                result shouldBe RateLimitResult.Allowed
            }

            it("cooldown has remaining time info") {
                val limiter = RateLimiter(
                    maxRequests = 1,
                    penaltyCooldownMs = 10000L
                )

                limiter.checkRateLimit("user1")
                limiter.checkRateLimit("user1")

                val result = limiter.checkRateLimit("user1")
                result.shouldBeInstanceOf<RateLimitResult.Cooldown>()

                val cooldown = result as RateLimitResult.Cooldown
                // Should have significant remaining time
                (cooldown.remainingMs > 0) shouldBe true
                (cooldown.remainingSeconds > 0) shouldBe true
            }
        }

        describe("request counting") {

            it("tracks request count per user") {
                val limiter = RateLimiter(maxRequests = 10)

                repeat(5) {
                    limiter.checkRateLimit("user1")
                }

                limiter.getRequestCount("user1") shouldBe 5
            }

            it("returns zero for unknown users") {
                val limiter = RateLimiter()

                limiter.getRequestCount("unknown") shouldBe 0
            }

            it("does not count requests from different users") {
                val limiter = RateLimiter()

                repeat(3) { limiter.checkRateLimit("user1") }
                repeat(2) { limiter.checkRateLimit("user2") }

                limiter.getRequestCount("user1") shouldBe 3
                limiter.getRequestCount("user2") shouldBe 2
            }
        }

        describe("reset") {

            it("resets rate limit for specific user") {
                val limiter = RateLimiter(maxRequests = 2, penaltyCooldownMs = 60000L)

                // Use up quota and get penalized
                limiter.checkRateLimit("user1")
                limiter.checkRateLimit("user1")
                limiter.checkRateLimit("user1") // Triggers penalty

                // Verify user is rate limited
                limiter.checkRateLimit("user1").shouldBeInstanceOf<RateLimitResult.Cooldown>()

                // Reset the user
                limiter.reset("user1")

                // Should be allowed again
                limiter.checkRateLimit("user1") shouldBe RateLimitResult.Allowed
                limiter.getRequestCount("user1") shouldBe 1
            }

            it("does not affect other users when resetting one") {
                val limiter = RateLimiter(maxRequests = 5)

                repeat(3) { limiter.checkRateLimit("user1") }
                repeat(2) { limiter.checkRateLimit("user2") }

                limiter.reset("user1")

                limiter.getRequestCount("user1") shouldBe 0
                limiter.getRequestCount("user2") shouldBe 2
            }
        }

        describe("resetAll") {

            it("clears all rate limits") {
                val limiter = RateLimiter(maxRequests = 2, penaltyCooldownMs = 60000L)

                // Rate limit multiple users
                repeat(3) { limiter.checkRateLimit("user1") }
                repeat(3) { limiter.checkRateLimit("user2") }

                limiter.resetAll()

                // Both users should be allowed
                limiter.checkRateLimit("user1") shouldBe RateLimitResult.Allowed
                limiter.checkRateLimit("user2") shouldBe RateLimitResult.Allowed
                limiter.getRequestCount("user1") shouldBe 1
                limiter.getRequestCount("user2") shouldBe 1
            }
        }

        describe("cleanup") {

            it("removes expired entries") {
                val limiter = RateLimiter(
                    maxRequests = 10,
                    windowMs = 50L, // Very short window
                    penaltyCooldownMs = 50L
                )

                // Make some requests
                repeat(5) { limiter.checkRateLimit("user1") }

                // Wait for window to expire
                Thread.sleep(60)

                // Cleanup should remove the expired timestamps
                limiter.cleanup()

                // Count should be 0 after cleanup (all timestamps expired)
                limiter.getRequestCount("user1") shouldBe 0
            }
        }

        describe("RateLimiterConfig") {

            it("creates RateLimiter from config") {
                val config = RateLimiterConfig(
                    enabled = true,
                    penaltyCooldownMs = 20000L,
                    maxRequestsPerMinute = 30
                )

                val limiter = config.toRateLimiter()

                // Verify by using up the quota
                repeat(30) {
                    limiter.checkRateLimit("user1") shouldBe RateLimitResult.Allowed
                }

                // Next should be rate limited
                val result = limiter.checkRateLimit("user1")
                result.shouldBeInstanceOf<RateLimitResult.BurstLimited>()
                (result as RateLimitResult.BurstLimited).retryAfterMs shouldBe 20000L
            }

            it("has sensible defaults") {
                val config = RateLimiterConfig()

                config.enabled shouldBe true
                config.penaltyCooldownMs shouldBe 10000L
                config.maxRequestsPerMinute shouldBe 60
            }
        }
    }
})
