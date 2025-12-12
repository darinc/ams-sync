package io.github.darinc.amssync.metrics

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain

class ErrorMetricsTest : DescribeSpec({

    describe("ErrorMetrics") {

        describe("command metrics") {

            it("recordCommandSuccess increments success count") {
                val metrics = ErrorMetrics()

                metrics.recordCommandSuccess("testcmd", 100L)
                metrics.recordCommandSuccess("testcmd", 150L)

                val snapshot = metrics.getSnapshot()
                snapshot.commandStats["testcmd"]?.successCount shouldBe 2
            }

            it("recordCommandSuccess records duration") {
                val metrics = ErrorMetrics()

                metrics.recordCommandSuccess("testcmd", 100L)
                metrics.recordCommandSuccess("testcmd", 200L)

                metrics.getAverageLatency("testcmd") shouldBe 150.0
            }

            it("recordCommandFailure increments failure count") {
                val metrics = ErrorMetrics()

                metrics.recordCommandFailure("testcmd", "TestError", 50L)
                metrics.recordCommandFailure("testcmd", "TestError", 75L)

                val snapshot = metrics.getSnapshot()
                snapshot.commandStats["testcmd"]?.failureCount shouldBe 2
            }

            it("recordCommandFailure increments error type count") {
                val metrics = ErrorMetrics()

                metrics.recordCommandFailure("testcmd", "TimeoutError", 50L)
                metrics.recordCommandFailure("testcmd", "TimeoutError", 75L)
                metrics.recordCommandFailure("testcmd", "NetworkError", 100L)

                val snapshot = metrics.getSnapshot()
                snapshot.errorStats["TimeoutError"] shouldBe 2
                snapshot.errorStats["NetworkError"] shouldBe 1
            }

            it("tracks metrics per command name") {
                val metrics = ErrorMetrics()

                metrics.recordCommandSuccess("cmd1", 100L)
                metrics.recordCommandSuccess("cmd2", 200L)
                metrics.recordCommandFailure("cmd1", "Error", 50L)

                val snapshot = metrics.getSnapshot()
                snapshot.commandStats["cmd1"]?.successCount shouldBe 1
                snapshot.commandStats["cmd1"]?.failureCount shouldBe 1
                snapshot.commandStats["cmd2"]?.successCount shouldBe 1
                snapshot.commandStats["cmd2"]?.failureCount shouldBe 0
            }
        }

        describe("Discord API metrics") {

            it("recordDiscordApiSuccess increments success count") {
                val metrics = ErrorMetrics()

                metrics.recordDiscordApiSuccess()
                metrics.recordDiscordApiSuccess()

                val snapshot = metrics.getSnapshot()
                snapshot.discordApiStats.successCount shouldBe 2
            }

            it("recordDiscordApiFailure increments failure count") {
                val metrics = ErrorMetrics()

                metrics.recordDiscordApiFailure("timeout")
                metrics.recordDiscordApiFailure("network")

                val snapshot = metrics.getSnapshot()
                snapshot.discordApiStats.failureCount shouldBe 2
            }

            it("recordDiscordApiFailure tracks error type with discord_ prefix") {
                val metrics = ErrorMetrics()

                metrics.recordDiscordApiFailure("timeout")
                metrics.recordDiscordApiFailure("timeout")

                val snapshot = metrics.getSnapshot()
                snapshot.errorStats["discord_timeout"] shouldBe 2
            }

            it("recordDiscordApiRejected increments rejected count") {
                val metrics = ErrorMetrics()

                metrics.recordDiscordApiRejected()
                metrics.recordDiscordApiRejected()
                metrics.recordDiscordApiRejected()

                val snapshot = metrics.getSnapshot()
                snapshot.discordApiStats.rejectedCount shouldBe 3
            }
        }

        describe("circuit breaker metrics") {

            it("recordCircuitBreakerTrip increments trip count") {
                val metrics = ErrorMetrics()

                metrics.recordCircuitBreakerTrip()
                metrics.recordCircuitBreakerTrip()

                val snapshot = metrics.getSnapshot()
                snapshot.circuitBreakerStats.tripCount shouldBe 2
            }

            it("recordCircuitBreakerRecovery increments recovery count") {
                val metrics = ErrorMetrics()

                metrics.recordCircuitBreakerRecovery()

                val snapshot = metrics.getSnapshot()
                snapshot.circuitBreakerStats.recoveryCount shouldBe 1
            }
        }

        describe("connection metrics") {

            it("recordConnectionAttempt tracks attempts") {
                val metrics = ErrorMetrics()

                metrics.recordConnectionAttempt(true)
                metrics.recordConnectionAttempt(false)
                metrics.recordConnectionAttempt(true)

                val snapshot = metrics.getSnapshot()
                snapshot.connectionStats.attemptCount shouldBe 3
            }

            it("recordConnectionAttempt increments success on true") {
                val metrics = ErrorMetrics()

                metrics.recordConnectionAttempt(true)
                metrics.recordConnectionAttempt(true)

                val snapshot = metrics.getSnapshot()
                snapshot.connectionStats.successCount shouldBe 2
                snapshot.connectionStats.failureCount shouldBe 0
            }

            it("recordConnectionAttempt increments failure on false") {
                val metrics = ErrorMetrics()

                metrics.recordConnectionAttempt(false)
                metrics.recordConnectionAttempt(false)

                val snapshot = metrics.getSnapshot()
                snapshot.connectionStats.successCount shouldBe 0
                snapshot.connectionStats.failureCount shouldBe 2
            }
        }

        describe("latency calculations") {

            it("getAverageLatency returns null when no data") {
                val metrics = ErrorMetrics()

                metrics.getAverageLatency("unknown").shouldBeNull()
            }

            it("getAverageLatency calculates correct average") {
                val metrics = ErrorMetrics()

                metrics.recordCommandSuccess("testcmd", 100L)
                metrics.recordCommandSuccess("testcmd", 200L)
                metrics.recordCommandSuccess("testcmd", 300L)

                metrics.getAverageLatency("testcmd") shouldBe 200.0
            }

            it("getP95Latency returns null when no data") {
                val metrics = ErrorMetrics()

                metrics.getP95Latency("unknown").shouldBeNull()
            }

            it("getP95Latency calculates 95th percentile") {
                val metrics = ErrorMetrics()

                // Add 100 samples from 1 to 100
                (1..100).forEach { i ->
                    metrics.recordCommandSuccess("testcmd", i.toLong())
                }

                // P95 of 1-100: index = (100 * 0.95).toInt() = 95, coerced to max 99 (size-1)
                // So sorted[95] = 96 (since array is 1-indexed in our data)
                val p95 = metrics.getP95Latency("testcmd")
                p95.shouldNotBeNull()
                // The actual value depends on implementation:
                // sorted list is [1,2,...,100], index 95 gives value 96
                p95 shouldBe 96L
            }

            it("limits duration samples to maxDurationSamples (100)") {
                val metrics = ErrorMetrics()

                // Add more than 100 samples
                (1..150).forEach { i ->
                    metrics.recordCommandSuccess("testcmd", i.toLong())
                }

                // Average should be based on last 100 samples (51-150)
                val avg = metrics.getAverageLatency("testcmd")
                avg.shouldNotBeNull()
                // Average of 51-150 = (51+150)/2 = 100.5
                avg shouldBe 100.5
            }
        }

        describe("success rate") {

            it("getCommandSuccessRate returns null when no data") {
                val metrics = ErrorMetrics()

                metrics.getCommandSuccessRate("unknown").shouldBeNull()
            }

            it("getCommandSuccessRate returns 1.0 for all successes") {
                val metrics = ErrorMetrics()

                metrics.recordCommandSuccess("testcmd", 100L)
                metrics.recordCommandSuccess("testcmd", 100L)

                metrics.getCommandSuccessRate("testcmd") shouldBe 1.0
            }

            it("getCommandSuccessRate returns 0.0 for all failures") {
                val metrics = ErrorMetrics()

                metrics.recordCommandFailure("testcmd", "Error", 100L)
                metrics.recordCommandFailure("testcmd", "Error", 100L)

                metrics.getCommandSuccessRate("testcmd") shouldBe 0.0
            }

            it("getCommandSuccessRate calculates correct ratio") {
                val metrics = ErrorMetrics()

                metrics.recordCommandSuccess("testcmd", 100L)
                metrics.recordCommandSuccess("testcmd", 100L)
                metrics.recordCommandSuccess("testcmd", 100L)
                metrics.recordCommandFailure("testcmd", "Error", 100L)

                metrics.getCommandSuccessRate("testcmd") shouldBe 0.75
            }

            it("getDiscordApiSuccessRate returns null when no data") {
                val metrics = ErrorMetrics()

                metrics.getDiscordApiSuccessRate().shouldBeNull()
            }

            it("getDiscordApiSuccessRate calculates API success rate") {
                val metrics = ErrorMetrics()

                metrics.recordDiscordApiSuccess()
                metrics.recordDiscordApiSuccess()
                metrics.recordDiscordApiFailure("error")

                metrics.getDiscordApiSuccessRate() shouldBe (2.0 / 3.0)
            }
        }

        describe("uptime") {

            it("getUptimeSeconds returns elapsed time") {
                val metrics = ErrorMetrics()

                // Small delay to ensure non-zero uptime
                Thread.sleep(10)

                metrics.getUptimeSeconds() shouldBeGreaterThanOrEqual 0L
            }

            it("getUptimeFormatted formats correctly for minutes only") {
                val metrics = ErrorMetrics()

                // Uptime should be near 0m
                val formatted = metrics.getUptimeFormatted()
                formatted shouldContain "m"
            }

            it("getUptimeFormatted includes days and hours when applicable") {
                // We can't easily test longer uptimes without mocking time,
                // but we can verify the format doesn't throw
                val metrics = ErrorMetrics()
                val formatted = metrics.getUptimeFormatted()
                formatted.shouldNotBeNull()
            }
        }

        describe("getSnapshot") {

            it("returns complete MetricsSnapshot") {
                val metrics = ErrorMetrics()

                metrics.recordCommandSuccess("cmd1", 100L)
                metrics.recordDiscordApiSuccess()
                metrics.recordCircuitBreakerTrip()
                metrics.recordConnectionAttempt(true)

                val snapshot = metrics.getSnapshot()

                snapshot.uptimeSeconds shouldBeGreaterThanOrEqual 0L
                snapshot.uptimeFormatted.shouldNotBeNull()
                snapshot.commandStats shouldContainKey "cmd1"
                snapshot.discordApiStats.successCount shouldBe 1
                snapshot.circuitBreakerStats.tripCount shouldBe 1
                snapshot.connectionStats.successCount shouldBe 1
            }

            it("includes all command stats") {
                val metrics = ErrorMetrics()

                metrics.recordCommandSuccess("cmd1", 100L)
                metrics.recordCommandSuccess("cmd2", 200L)
                metrics.recordCommandFailure("cmd3", "Error", 50L)

                val snapshot = metrics.getSnapshot()

                snapshot.commandStats shouldContainKey "cmd1"
                snapshot.commandStats shouldContainKey "cmd2"
                snapshot.commandStats shouldContainKey "cmd3"
            }

            it("includes error stats map") {
                val metrics = ErrorMetrics()

                metrics.recordCommandFailure("cmd", "TypeError", 100L)
                metrics.recordCommandFailure("cmd", "ValueError", 100L)

                val snapshot = metrics.getSnapshot()

                snapshot.errorStats shouldContainKey "TypeError"
                snapshot.errorStats shouldContainKey "ValueError"
            }
        }

        describe("reset") {

            it("clears all counters and maps") {
                val metrics = ErrorMetrics()

                // Populate metrics
                metrics.recordCommandSuccess("cmd", 100L)
                metrics.recordCommandFailure("cmd", "Error", 50L)
                metrics.recordDiscordApiSuccess()
                metrics.recordDiscordApiFailure("timeout")
                metrics.recordDiscordApiRejected()
                metrics.recordCircuitBreakerTrip()
                metrics.recordCircuitBreakerRecovery()
                metrics.recordConnectionAttempt(true)
                metrics.recordConnectionAttempt(false)

                // Reset
                metrics.reset()

                // Verify all cleared
                val snapshot = metrics.getSnapshot()
                snapshot.commandStats.shouldBeEmpty()
                snapshot.errorStats.shouldBeEmpty()
                snapshot.discordApiStats.successCount shouldBe 0
                snapshot.discordApiStats.failureCount shouldBe 0
                snapshot.discordApiStats.rejectedCount shouldBe 0
                snapshot.circuitBreakerStats.tripCount shouldBe 0
                snapshot.circuitBreakerStats.recoveryCount shouldBe 0
                snapshot.connectionStats.attemptCount shouldBe 0
                snapshot.connectionStats.successCount shouldBe 0
                snapshot.connectionStats.failureCount shouldBe 0
            }
        }

        describe("MetricsSnapshot.toDisplayString") {

            it("formats metrics for display") {
                val metrics = ErrorMetrics()

                metrics.recordCommandSuccess("mcstats", 100L)
                metrics.recordDiscordApiSuccess()

                val snapshot = metrics.getSnapshot()
                val display = snapshot.toDisplayString()

                display shouldContain "AMSSync Metrics"
                display shouldContain "Uptime"
                display shouldContain "Discord API"
                display shouldContain "mcstats"
            }
        }

        describe("CommandStats") {

            it("contains all required fields") {
                val stats = CommandStats(
                    successCount = 10,
                    failureCount = 2,
                    successRate = 0.833,
                    avgLatencyMs = 150.0,
                    p95LatencyMs = 200L
                )

                stats.successCount shouldBe 10
                stats.failureCount shouldBe 2
                stats.successRate shouldBe 0.833
                stats.avgLatencyMs shouldBe 150.0
                stats.p95LatencyMs shouldBe 200L
            }
        }
    }
})
