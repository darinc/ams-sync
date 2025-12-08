package io.github.darinc.amssync.discord

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import java.util.logging.Logger

class CircuitBreakerTest : DescribeSpec({

    val logger = mockk<Logger>(relaxed = true)

    describe("CircuitBreaker") {

        describe("CLOSED state") {

            it("starts in CLOSED state") {
                val cb = CircuitBreaker(logger = logger)
                cb.getState() shouldBe CircuitBreaker.State.CLOSED
            }

            it("executes operations successfully") {
                val cb = CircuitBreaker(logger = logger)
                val result = cb.execute("test") { "success" }

                result.shouldBeInstanceOf<CircuitBreaker.CircuitResult.Success<String>>()
                (result as CircuitBreaker.CircuitResult.Success).value shouldBe "success"
            }

            it("captures failures and returns Failure result") {
                val cb = CircuitBreaker(failureThreshold = 5, logger = logger)
                val result = cb.execute("test") { throw RuntimeException("oops") }

                result.shouldBeInstanceOf<CircuitBreaker.CircuitResult.Failure>()
                cb.getState() shouldBe CircuitBreaker.State.CLOSED
            }

            it("resets failure count on success") {
                val cb = CircuitBreaker(failureThreshold = 5, logger = logger)

                // Cause some failures (but not enough to open)
                repeat(3) {
                    cb.execute("test") { throw RuntimeException("fail") }
                }
                cb.getFailureCount() shouldBe 3

                // Success should reset
                cb.execute("test") { "success" }
                cb.getFailureCount() shouldBe 0
            }
        }

        describe("CLOSED to OPEN transition") {

            it("transitions to OPEN after reaching failure threshold") {
                val cb = CircuitBreaker(
                    failureThreshold = 3,
                    timeWindowMs = 60000L,
                    logger = logger
                )

                // Cause failures to reach threshold
                repeat(3) {
                    cb.execute("test") { throw RuntimeException("fail") }
                }

                cb.getState() shouldBe CircuitBreaker.State.OPEN
            }

            it("does not transition to OPEN if below threshold") {
                val cb = CircuitBreaker(
                    failureThreshold = 5,
                    logger = logger
                )

                repeat(4) {
                    cb.execute("test") { throw RuntimeException("fail") }
                }

                cb.getState() shouldBe CircuitBreaker.State.CLOSED
            }
        }

        describe("OPEN state") {

            it("rejects requests when OPEN") {
                val cb = CircuitBreaker(
                    failureThreshold = 2,
                    cooldownMs = 60000L,
                    logger = logger
                )

                // Open the circuit
                repeat(2) {
                    cb.execute("test") { throw RuntimeException("fail") }
                }
                cb.getState() shouldBe CircuitBreaker.State.OPEN

                // Next request should be rejected
                val result = cb.execute("test") { "should not execute" }
                result.shouldBeInstanceOf<CircuitBreaker.CircuitResult.Rejected>()
                (result as CircuitBreaker.CircuitResult.Rejected).state shouldBe CircuitBreaker.State.OPEN
            }

            it("rejected result contains retry message") {
                val cb = CircuitBreaker(
                    failureThreshold = 1,
                    cooldownMs = 30000L,
                    logger = logger
                )

                cb.execute("test") { throw RuntimeException("fail") }
                val result = cb.execute("test") { "ignored" }

                result.shouldBeInstanceOf<CircuitBreaker.CircuitResult.Rejected>()
                (result as CircuitBreaker.CircuitResult.Rejected).message.contains("OPEN") shouldBe true
            }
        }

        describe("OPEN to HALF_OPEN transition") {

            it("transitions to HALF_OPEN after cooldown expires") {
                val cb = CircuitBreaker(
                    failureThreshold = 1,
                    cooldownMs = 10L, // Very short cooldown for testing
                    logger = logger
                )

                // Open the circuit
                cb.execute("test") { throw RuntimeException("fail") }
                cb.getState() shouldBe CircuitBreaker.State.OPEN

                // Wait for cooldown
                Thread.sleep(20)

                // Next execute should transition to HALF_OPEN and try the operation
                val result = cb.execute("test") { "recovery" }
                // After successful execution, should transition to HALF_OPEN then possibly CLOSED
                result.shouldBeInstanceOf<CircuitBreaker.CircuitResult.Success<String>>()
            }
        }

        describe("HALF_OPEN state") {

            it("transitions to OPEN immediately on failure in HALF_OPEN") {
                val cb = CircuitBreaker(
                    failureThreshold = 1,
                    cooldownMs = 10L,
                    successThreshold = 3,
                    logger = logger
                )

                // Open the circuit
                cb.execute("test") { throw RuntimeException("fail") }
                cb.getState() shouldBe CircuitBreaker.State.OPEN

                // Wait for cooldown to transition to HALF_OPEN
                Thread.sleep(20)

                // Fail in HALF_OPEN - should immediately reopen
                cb.execute("test") { throw RuntimeException("fail again") }
                cb.getState() shouldBe CircuitBreaker.State.OPEN
            }

            it("transitions to CLOSED after success threshold in HALF_OPEN") {
                val cb = CircuitBreaker(
                    failureThreshold = 1,
                    cooldownMs = 10L,
                    successThreshold = 2,
                    logger = logger
                )

                // Open the circuit
                cb.execute("test") { throw RuntimeException("fail") }
                cb.getState() shouldBe CircuitBreaker.State.OPEN

                // Wait for cooldown
                Thread.sleep(20)

                // First success - transitions to HALF_OPEN
                cb.execute("test") { "success 1" }

                // Second success - should close the circuit
                cb.execute("test") { "success 2" }

                cb.getState() shouldBe CircuitBreaker.State.CLOSED
            }
        }

        describe("metrics") {

            it("reports correct metrics") {
                val cb = CircuitBreaker(
                    failureThreshold = 5,
                    successThreshold = 2,
                    logger = logger
                )

                repeat(3) {
                    cb.execute("test") { throw RuntimeException("fail") }
                }

                val metrics = cb.getMetrics()
                metrics.state shouldBe CircuitBreaker.State.CLOSED
                metrics.failureCount shouldBe 3
                metrics.failureThreshold shouldBe 5
                metrics.successThreshold shouldBe 2
            }
        }

        describe("manual reset") {

            it("resets to CLOSED state") {
                val cb = CircuitBreaker(
                    failureThreshold = 1,
                    cooldownMs = 60000L,
                    logger = logger
                )

                // Open the circuit
                cb.execute("test") { throw RuntimeException("fail") }
                cb.getState() shouldBe CircuitBreaker.State.OPEN

                // Manual reset
                cb.reset()

                cb.getState() shouldBe CircuitBreaker.State.CLOSED
                cb.getFailureCount() shouldBe 0
            }

            it("allows operations after reset") {
                val cb = CircuitBreaker(
                    failureThreshold = 1,
                    cooldownMs = 60000L,
                    logger = logger
                )

                // Open the circuit
                cb.execute("test") { throw RuntimeException("fail") }

                // Reset and verify operations work
                cb.reset()

                val result = cb.execute("test") { "works again" }
                result.shouldBeInstanceOf<CircuitBreaker.CircuitResult.Success<String>>()
                (result as CircuitBreaker.CircuitResult.Success).value shouldBe "works again"
            }
        }

        describe("CircuitBreakerConfig") {

            it("creates CircuitBreaker from config") {
                val config = CircuitBreakerConfig(
                    enabled = true,
                    failureThreshold = 10,
                    timeWindowSeconds = 120,
                    cooldownSeconds = 60,
                    successThreshold = 3
                )

                val cb = config.toCircuitBreaker(logger)

                cb.getState() shouldBe CircuitBreaker.State.CLOSED
                // Verify thresholds through metrics
                val metrics = cb.getMetrics()
                metrics.failureThreshold shouldBe 10
                metrics.successThreshold shouldBe 3
            }
        }
    }
})
