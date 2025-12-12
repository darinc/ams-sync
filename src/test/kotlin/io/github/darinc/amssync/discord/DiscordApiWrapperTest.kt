package io.github.darinc.amssync.discord

import io.github.darinc.amssync.metrics.ErrorMetrics
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.logging.Logger

class DiscordApiWrapperTest : DescribeSpec({

    describe("DiscordApiWrapper") {

        describe("circuit breaker integration") {

            it("isCircuitBreakerEnabled returns true when circuit breaker provided") {
                val circuitBreaker = mockk<CircuitBreaker>(relaxed = true)
                val logger = mockk<Logger>(relaxed = true)

                val wrapper = DiscordApiWrapper(circuitBreaker, logger)

                wrapper.isCircuitBreakerEnabled() shouldBe true
            }

            it("isCircuitBreakerEnabled returns false when circuit breaker is null") {
                val logger = mockk<Logger>(relaxed = true)

                val wrapper = DiscordApiWrapper(null, logger)

                wrapper.isCircuitBreakerEnabled() shouldBe false
            }

            it("getCircuitState returns state from circuit breaker") {
                val circuitBreaker = mockk<CircuitBreaker>()
                val logger = mockk<Logger>(relaxed = true)
                every { circuitBreaker.getState() } returns CircuitBreaker.State.CLOSED

                val wrapper = DiscordApiWrapper(circuitBreaker, logger)

                wrapper.getCircuitState() shouldBe CircuitBreaker.State.CLOSED
            }

            it("getCircuitState returns null when circuit breaker is null") {
                val logger = mockk<Logger>(relaxed = true)

                val wrapper = DiscordApiWrapper(null, logger)

                wrapper.getCircuitState().shouldBeNull()
            }

            it("getMetrics returns metrics from circuit breaker") {
                val circuitBreaker = mockk<CircuitBreaker>()
                val logger = mockk<Logger>(relaxed = true)
                val expectedMetrics = CircuitBreakerMetrics(
                    state = CircuitBreaker.State.CLOSED,
                    failureCount = 2,
                    successCount = 10,
                    failureThreshold = 5,
                    successThreshold = 3,
                    timeSinceStateChangeMs = 5000L
                )
                every { circuitBreaker.getMetrics() } returns expectedMetrics

                val wrapper = DiscordApiWrapper(circuitBreaker, logger)

                val metrics = wrapper.getMetrics()
                metrics shouldBe expectedMetrics
            }

            it("getMetrics returns null when circuit breaker is null") {
                val logger = mockk<Logger>(relaxed = true)

                val wrapper = DiscordApiWrapper(null, logger)

                wrapper.getMetrics().shouldBeNull()
            }
        }

        describe("circuit state context formatting") {

            it("provides failure count in CLOSED state when there are failures") {
                val circuitBreaker = mockk<CircuitBreaker>()
                val logger = mockk<Logger>(relaxed = true)
                every { circuitBreaker.getMetrics() } returns CircuitBreakerMetrics(
                    state = CircuitBreaker.State.CLOSED,
                    failureCount = 2,
                    successCount = 0,
                    failureThreshold = 5,
                    successThreshold = 3,
                    timeSinceStateChangeMs = 1000L
                )

                val wrapper = DiscordApiWrapper(circuitBreaker, logger)

                // Verify via getMetrics that the state is available
                val metrics = wrapper.getMetrics()!!
                metrics.state shouldBe CircuitBreaker.State.CLOSED
                metrics.failureCount shouldBe 2
                metrics.failureThreshold shouldBe 5
            }

            it("provides recovery progress in HALF_OPEN state") {
                val circuitBreaker = mockk<CircuitBreaker>()
                val logger = mockk<Logger>(relaxed = true)
                every { circuitBreaker.getMetrics() } returns CircuitBreakerMetrics(
                    state = CircuitBreaker.State.HALF_OPEN,
                    failureCount = 0,
                    successCount = 1,
                    failureThreshold = 5,
                    successThreshold = 3,
                    timeSinceStateChangeMs = 2000L
                )

                val wrapper = DiscordApiWrapper(circuitBreaker, logger)

                val metrics = wrapper.getMetrics()!!
                metrics.state shouldBe CircuitBreaker.State.HALF_OPEN
                metrics.successCount shouldBe 1
                metrics.successThreshold shouldBe 3
            }

            it("provides cooldown info in OPEN state") {
                val circuitBreaker = mockk<CircuitBreaker>()
                val logger = mockk<Logger>(relaxed = true)
                every { circuitBreaker.getMetrics() } returns CircuitBreakerMetrics(
                    state = CircuitBreaker.State.OPEN,
                    failureCount = 5,
                    successCount = 0,
                    failureThreshold = 5,
                    successThreshold = 3,
                    timeSinceStateChangeMs = 10000L
                )

                val wrapper = DiscordApiWrapper(circuitBreaker, logger)

                val metrics = wrapper.getMetrics()!!
                metrics.state shouldBe CircuitBreaker.State.OPEN
                metrics.timeSinceStateChangeMs shouldBe 10000L
            }
        }

        describe("error metrics integration") {

            it("accepts error metrics parameter") {
                val circuitBreaker = mockk<CircuitBreaker>(relaxed = true)
                val logger = mockk<Logger>(relaxed = true)
                val errorMetrics = ErrorMetrics()

                val wrapper = DiscordApiWrapper(circuitBreaker, logger, errorMetrics)

                // Should construct without error
                wrapper.isCircuitBreakerEnabled() shouldBe true
            }

            it("works without error metrics") {
                val circuitBreaker = mockk<CircuitBreaker>(relaxed = true)
                val logger = mockk<Logger>(relaxed = true)

                val wrapper = DiscordApiWrapper(circuitBreaker, logger)

                // Should construct without error
                wrapper.isCircuitBreakerEnabled() shouldBe true
            }

            it("works with null circuit breaker and error metrics") {
                val logger = mockk<Logger>(relaxed = true)
                val errorMetrics = ErrorMetrics()

                val wrapper = DiscordApiWrapper(null, logger, errorMetrics)

                wrapper.isCircuitBreakerEnabled() shouldBe false
            }
        }

        describe("state transitions") {

            it("reflects CLOSED state correctly") {
                val circuitBreaker = mockk<CircuitBreaker>()
                val logger = mockk<Logger>(relaxed = true)
                every { circuitBreaker.getState() } returns CircuitBreaker.State.CLOSED

                val wrapper = DiscordApiWrapper(circuitBreaker, logger)

                wrapper.getCircuitState() shouldBe CircuitBreaker.State.CLOSED
            }

            it("reflects OPEN state correctly") {
                val circuitBreaker = mockk<CircuitBreaker>()
                val logger = mockk<Logger>(relaxed = true)
                every { circuitBreaker.getState() } returns CircuitBreaker.State.OPEN

                val wrapper = DiscordApiWrapper(circuitBreaker, logger)

                wrapper.getCircuitState() shouldBe CircuitBreaker.State.OPEN
            }

            it("reflects HALF_OPEN state correctly") {
                val circuitBreaker = mockk<CircuitBreaker>()
                val logger = mockk<Logger>(relaxed = true)
                every { circuitBreaker.getState() } returns CircuitBreaker.State.HALF_OPEN

                val wrapper = DiscordApiWrapper(circuitBreaker, logger)

                wrapper.getCircuitState() shouldBe CircuitBreaker.State.HALF_OPEN
            }
        }
    }
})
