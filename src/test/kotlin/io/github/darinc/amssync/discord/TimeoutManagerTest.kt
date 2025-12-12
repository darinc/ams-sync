package io.github.darinc.amssync.discord

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Logger

class TimeoutManagerTest : DescribeSpec({

    val logger = mockk<Logger>(relaxed = true)

    describe("TimeoutManager") {

        describe("executeWithTimeout") {

            it("returns Success when operation completes within timeout") {
                val tm = TimeoutManager(
                    warningThresholdMs = 100L,
                    hardTimeoutMs = 500L,
                    logger = logger
                )

                val result = tm.executeWithTimeout("fastOp") { "done" }

                result.shouldBeInstanceOf<TimeoutManager.TimeoutResult.Success<String>>()
                tm.shutdown()
            }

            it("returns Success with correct value") {
                val tm = TimeoutManager(
                    warningThresholdMs = 100L,
                    hardTimeoutMs = 500L,
                    logger = logger
                )

                val result = tm.executeWithTimeout("valueOp") { 42 }

                result.shouldBeInstanceOf<TimeoutManager.TimeoutResult.Success<Int>>()
                (result as TimeoutManager.TimeoutResult.Success).value shouldBe 42
                tm.shutdown()
            }

            it("returns Timeout when operation exceeds hardTimeoutMs") {
                val tm = TimeoutManager(
                    warningThresholdMs = 10L,
                    hardTimeoutMs = 50L,
                    logger = logger
                )

                val result = tm.executeWithTimeout("slowOp") {
                    Thread.sleep(200) // Way longer than timeout
                    "never returned"
                }

                result.shouldBeInstanceOf<TimeoutManager.TimeoutResult.Timeout>()
                (result as TimeoutManager.TimeoutResult.Timeout).operationName shouldBe "slowOp"
                result.timeoutMs shouldBeGreaterThanOrEqual 50L
                tm.shutdown()
            }

            it("returns Failure when operation throws exception") {
                val tm = TimeoutManager(
                    warningThresholdMs = 100L,
                    hardTimeoutMs = 500L,
                    logger = logger
                )

                val result = tm.executeWithTimeout("failingOp") {
                    throw RuntimeException("test error")
                }

                result.shouldBeInstanceOf<TimeoutManager.TimeoutResult.Failure>()
                (result as TimeoutManager.TimeoutResult.Failure).operationName shouldBe "failingOp"
                result.exception.message shouldBe "test error"
                tm.shutdown()
            }

            it("calls warningCallback when warningThreshold exceeded") {
                val tm = TimeoutManager(
                    warningThresholdMs = 10L,
                    hardTimeoutMs = 500L,
                    logger = logger
                )

                val callbackInvoked = AtomicBoolean(false)

                tm.executeWithTimeout(
                    operationName = "mediumOp",
                    warningCallback = { _, _ -> callbackInvoked.set(true) }
                ) {
                    Thread.sleep(50) // Long enough to trigger warning
                    "done"
                }

                callbackInvoked.get() shouldBe true
                tm.shutdown()
            }

            it("does not call warningCallback for fast operations") {
                val tm = TimeoutManager(
                    warningThresholdMs = 1000L,
                    hardTimeoutMs = 5000L,
                    logger = logger
                )

                val callbackInvoked = AtomicBoolean(false)

                tm.executeWithTimeout(
                    operationName = "quickOp",
                    warningCallback = { _, _ -> callbackInvoked.set(true) }
                ) {
                    "instant"
                }

                // Give a small delay to ensure callback would have fired if it was going to
                Thread.sleep(20)
                callbackInvoked.get() shouldBe false
                tm.shutdown()
            }

            it("logs warning when warningThreshold exceeded") {
                val loggerMock = mockk<Logger>(relaxed = true)
                val tm = TimeoutManager(
                    warningThresholdMs = 10L,
                    hardTimeoutMs = 500L,
                    logger = loggerMock
                )

                tm.executeWithTimeout("slowishOp") {
                    Thread.sleep(50)
                    "done"
                }

                verify { loggerMock.warning(match<String> { it.contains("slowishOp") && it.contains("taking longer") }) }
                tm.shutdown()
            }

            it("logs severe when operation times out") {
                val loggerMock = mockk<Logger>(relaxed = true)
                val tm = TimeoutManager(
                    warningThresholdMs = 5L,
                    hardTimeoutMs = 20L,
                    logger = loggerMock
                )

                tm.executeWithTimeout("timeoutOp") {
                    Thread.sleep(100)
                    "never"
                }

                verify { loggerMock.severe(match<String> { it.contains("timeoutOp") && it.contains("timed out") }) }
                tm.shutdown()
            }
        }

        describe("TimeoutResult sealed class") {

            it("Success contains the operation result") {
                val success = TimeoutManager.TimeoutResult.Success("test value")

                success.value shouldBe "test value"
            }

            it("Success can contain different types") {
                val intSuccess = TimeoutManager.TimeoutResult.Success(123)
                val listSuccess = TimeoutManager.TimeoutResult.Success(listOf(1, 2, 3))

                intSuccess.value shouldBe 123
                listSuccess.value shouldBe listOf(1, 2, 3)
            }

            it("Timeout contains operationName and elapsed time") {
                val timeout = TimeoutManager.TimeoutResult.Timeout(
                    operationName = "testOp",
                    timeoutMs = 5000L
                )

                timeout.operationName shouldBe "testOp"
                timeout.timeoutMs shouldBe 5000L
            }

            it("Failure contains exception and operationName") {
                val exception = RuntimeException("test error")
                val failure = TimeoutManager.TimeoutResult.Failure(
                    exception = exception,
                    operationName = "failedOp"
                )

                failure.exception shouldBe exception
                failure.operationName shouldBe "failedOp"
            }
        }

        describe("TimeoutConfig") {

            it("creates TimeoutManager from config") {
                val config = TimeoutConfig(
                    enabled = true,
                    warningThresholdSeconds = 5,
                    hardTimeoutSeconds = 15
                )

                val tm = config.toTimeoutManager(logger)

                // Test that it works correctly by executing an operation
                val result = tm.executeWithTimeout("test") { "success" }
                result.shouldBeInstanceOf<TimeoutManager.TimeoutResult.Success<String>>()
                tm.shutdown()
            }

            it("converts seconds to milliseconds correctly") {
                val config = TimeoutConfig(
                    enabled = true,
                    warningThresholdSeconds = 3,
                    hardTimeoutSeconds = 10
                )

                val tm = config.toTimeoutManager(logger)

                // Verify by testing with known timing
                // A 50ms operation should succeed with 10s timeout
                val result = tm.executeWithTimeout("test") {
                    Thread.sleep(50)
                    "done"
                }
                result.shouldBeInstanceOf<TimeoutManager.TimeoutResult.Success<String>>()
                tm.shutdown()
            }
        }

        describe("shutdown") {

            it("terminates executor gracefully") {
                val tm = TimeoutManager(
                    warningThresholdMs = 100L,
                    hardTimeoutMs = 500L,
                    logger = logger
                )

                // Execute an operation first
                tm.executeWithTimeout("test") { "done" }

                // Shutdown should complete without error
                tm.shutdown()

                // Verify shutdown was logged
                verify { logger.info(match<String> { it.contains("Shutting down") }) }
            }

            it("can be called multiple times safely") {
                val tm = TimeoutManager(
                    warningThresholdMs = 100L,
                    hardTimeoutMs = 500L,
                    logger = logger
                )

                tm.shutdown()
                tm.shutdown() // Should not throw
            }
        }

        describe("edge cases") {

            it("handles null return values") {
                val tm = TimeoutManager(
                    warningThresholdMs = 100L,
                    hardTimeoutMs = 500L,
                    logger = logger
                )

                val result = tm.executeWithTimeout("nullOp") { null }

                result.shouldBeInstanceOf<TimeoutManager.TimeoutResult.Success<Any?>>()
                (result as TimeoutManager.TimeoutResult.Success).value shouldBe null
                tm.shutdown()
            }

            it("handles operation that completes just before timeout") {
                val tm = TimeoutManager(
                    warningThresholdMs = 10L,
                    hardTimeoutMs = 100L,
                    logger = logger
                )

                val result = tm.executeWithTimeout("justInTime") {
                    Thread.sleep(50) // Well under the 100ms timeout
                    "made it"
                }

                result.shouldBeInstanceOf<TimeoutManager.TimeoutResult.Success<String>>()
                tm.shutdown()
            }

            it("captures different exception types") {
                val tm = TimeoutManager(
                    warningThresholdMs = 100L,
                    hardTimeoutMs = 500L,
                    logger = logger
                )

                val result1 = tm.executeWithTimeout("illegalArg") {
                    throw IllegalArgumentException("bad arg")
                }
                result1.shouldBeInstanceOf<TimeoutManager.TimeoutResult.Failure>()
                (result1 as TimeoutManager.TimeoutResult.Failure).exception.shouldBeInstanceOf<IllegalArgumentException>()

                val result2 = tm.executeWithTimeout("illegalState") {
                    throw IllegalStateException("bad state")
                }
                result2.shouldBeInstanceOf<TimeoutManager.TimeoutResult.Failure>()
                (result2 as TimeoutManager.TimeoutResult.Failure).exception.shouldBeInstanceOf<IllegalStateException>()

                tm.shutdown()
            }
        }
    }
})
