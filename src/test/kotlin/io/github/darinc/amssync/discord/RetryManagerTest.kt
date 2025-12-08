package io.github.darinc.amssync.discord

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import java.util.logging.Logger

class RetryManagerTest : DescribeSpec({

    val logger = mockk<Logger>(relaxed = true)

    describe("RetryManager") {

        describe("successful operations") {

            it("returns Success on first attempt when operation succeeds") {
                val retryManager = RetryManager(
                    maxAttempts = 3,
                    initialDelayMs = 1L, // Use 1ms for fast tests
                    logger = logger
                )

                val result = retryManager.executeWithRetry("test") { "success" }

                result.shouldBeInstanceOf<RetryManager.RetryResult.Success<String>>()
                (result as RetryManager.RetryResult.Success).value shouldBe "success"
            }

            it("returns the correct value from successful operation") {
                val retryManager = RetryManager(
                    maxAttempts = 3,
                    initialDelayMs = 1L,
                    logger = logger
                )
                var callCount = 0

                val result = retryManager.executeWithRetry("test") {
                    callCount++
                    42
                }

                result.shouldBeInstanceOf<RetryManager.RetryResult.Success<Int>>()
                (result as RetryManager.RetryResult.Success).value shouldBe 42
                callCount shouldBe 1
            }
        }

        describe("retry behavior") {

            it("retries on failure and succeeds eventually") {
                val retryManager = RetryManager(
                    maxAttempts = 3,
                    initialDelayMs = 1L,
                    logger = logger
                )
                var attempts = 0

                val result = retryManager.executeWithRetry("test") {
                    attempts++
                    if (attempts < 2) throw RuntimeException("fail")
                    "success after retry"
                }

                result.shouldBeInstanceOf<RetryManager.RetryResult.Success<String>>()
                (result as RetryManager.RetryResult.Success).value shouldBe "success after retry"
                attempts shouldBe 2
            }

            it("returns Failure after max attempts exceeded") {
                val retryManager = RetryManager(
                    maxAttempts = 3,
                    initialDelayMs = 1L,
                    logger = logger
                )
                var attempts = 0

                val result = retryManager.executeWithRetry("test") {
                    attempts++
                    throw RuntimeException("always fail")
                }

                result.shouldBeInstanceOf<RetryManager.RetryResult.Failure>()
                (result as RetryManager.RetryResult.Failure).attempts shouldBe 3
                attempts shouldBe 3
            }

            it("captures the last exception in Failure result") {
                val retryManager = RetryManager(
                    maxAttempts = 2,
                    initialDelayMs = 1L,
                    logger = logger
                )

                val result = retryManager.executeWithRetry("test") {
                    throw IllegalStateException("specific error")
                }

                result.shouldBeInstanceOf<RetryManager.RetryResult.Failure>()
                val failure = result as RetryManager.RetryResult.Failure
                failure.lastException.shouldBeInstanceOf<IllegalStateException>()
                failure.lastException.message shouldBe "specific error"
            }
        }

        describe("delay calculation") {

            it("respects maxAttempts of 1 (no retries)") {
                val retryManager = RetryManager(
                    maxAttempts = 1,
                    initialDelayMs = 1L,
                    logger = logger
                )
                var attempts = 0

                val result = retryManager.executeWithRetry("test") {
                    attempts++
                    throw RuntimeException("fail")
                }

                result.shouldBeInstanceOf<RetryManager.RetryResult.Failure>()
                attempts shouldBe 1
            }

            it("retries correct number of times with exponential backoff") {
                val retryManager = RetryManager(
                    maxAttempts = 4,
                    initialDelayMs = 1L,
                    maxDelayMs = 100L,
                    backoffMultiplier = 2.0,
                    logger = logger
                )
                var attempts = 0

                retryManager.executeWithRetry("test") {
                    attempts++
                    throw RuntimeException("fail")
                }

                attempts shouldBe 4
            }
        }

        describe("RetryConfig") {

            it("has sensible defaults") {
                val config = RetryManager.RetryConfig()

                config.enabled shouldBe true
                config.maxAttempts shouldBe 5
                config.initialDelaySeconds shouldBe 5
                config.maxDelaySeconds shouldBe 300
                config.backoffMultiplier shouldBe 2.0
            }

            it("creates RetryManager that respects maxAttempts") {
                // Use minimal delays to avoid long test times
                val retryManager = RetryManager(
                    maxAttempts = 3,
                    initialDelayMs = 1L,
                    maxDelayMs = 10L,
                    backoffMultiplier = 2.0,
                    logger = logger
                )

                var attempts = 0
                val result = retryManager.executeWithRetry("test") {
                    attempts++
                    if (attempts < 3) throw RuntimeException("fail")
                    "finally"
                }

                result.shouldBeInstanceOf<RetryManager.RetryResult.Success<String>>()
                attempts shouldBe 3
            }
        }

        describe("edge cases") {

            it("handles operation that returns null") {
                val retryManager = RetryManager(
                    maxAttempts = 1,
                    initialDelayMs = 1L,
                    logger = logger
                )

                val result = retryManager.executeWithRetry("test") { null }

                result.shouldBeInstanceOf<RetryManager.RetryResult.Success<Any?>>()
                (result as RetryManager.RetryResult.Success).value shouldBe null
            }

            it("handles different exception types") {
                val retryManager = RetryManager(
                    maxAttempts = 2,
                    initialDelayMs = 1L,
                    logger = logger
                )

                val result = retryManager.executeWithRetry("test") {
                    throw IllegalArgumentException("bad arg")
                }

                result.shouldBeInstanceOf<RetryManager.RetryResult.Failure>()
                (result as RetryManager.RetryResult.Failure).lastException
                    .shouldBeInstanceOf<IllegalArgumentException>()
            }
        }
    }
})
