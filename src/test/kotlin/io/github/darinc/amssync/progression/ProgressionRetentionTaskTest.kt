package io.github.darinc.amssync.progression

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.slot
import org.bukkit.Server
import org.bukkit.scheduler.BukkitScheduler
import org.bukkit.scheduler.BukkitTask
import io.github.darinc.amssync.AMSSyncPlugin
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.logging.Logger

class ProgressionRetentionTaskTest : DescribeSpec({

    // Helper to create mocked plugin and database
    fun createMocks(): Triple<AMSSyncPlugin, ProgressionDatabase, Logger> {
        val logger = mockk<Logger>(relaxed = true)
        val plugin = mockk<AMSSyncPlugin>(relaxed = true)
        val database = mockk<ProgressionDatabase>(relaxed = true)
        val server = mockk<Server>(relaxed = true)
        val scheduler = mockk<BukkitScheduler>(relaxed = true)
        val task = mockk<BukkitTask>(relaxed = true)

        every { plugin.logger } returns logger
        every { plugin.server } returns server
        every { server.scheduler } returns scheduler
        every { scheduler.runTaskTimerAsynchronously(any(), any<Runnable>(), any(), any()) } returns task

        return Triple(plugin, database, logger)
    }

    describe("ProgressionRetentionTask") {

        describe("start") {

            it("does not start task when retention is disabled") {
                val (plugin, database, logger) = createMocks()
                val config = RetentionConfig(
                    enabled = false,
                    cleanupIntervalHours = 24,
                    tiers = RetentionTiers(7, 30, 180, 5)
                )
                val task = ProgressionRetentionTask(plugin, config, database)

                task.start()

                verify { logger.info("Progression retention disabled in config") }
                verify(exactly = 0) { plugin.server.scheduler.runTaskTimerAsynchronously(any(), any<Runnable>(), any(), any()) }
            }

            it("starts periodic task when retention is enabled") {
                val (plugin, database, _) = createMocks()
                val delaySlot = slot<Long>()
                val intervalSlot = slot<Long>()
                every {
                    plugin.server.scheduler.runTaskTimerAsynchronously(
                        any(),
                        any<Runnable>(),
                        capture(delaySlot),
                        capture(intervalSlot)
                    )
                } returns mockk(relaxed = true)

                val config = RetentionConfig(
                    enabled = true,
                    cleanupIntervalHours = 24,
                    tiers = RetentionTiers(7, 30, 180, 5)
                )
                val retentionTask = ProgressionRetentionTask(plugin, config, database)

                retentionTask.start()

                delaySlot.captured shouldBe 1200L // 1 minute = 60 seconds * 20 ticks
            }

            it("uses correct cleanup interval from config") {
                val (plugin, database, _) = createMocks()
                val delaySlot = slot<Long>()
                val intervalSlot = slot<Long>()
                every {
                    plugin.server.scheduler.runTaskTimerAsynchronously(
                        any(),
                        any<Runnable>(),
                        capture(delaySlot),
                        capture(intervalSlot)
                    )
                } returns mockk(relaxed = true)

                val config = RetentionConfig(
                    enabled = true,
                    cleanupIntervalHours = 12,
                    tiers = RetentionTiers(7, 30, 180, 5)
                )
                val retentionTask = ProgressionRetentionTask(plugin, config, database)

                retentionTask.start()

                // 12 hours * 60 min * 60 sec * 20 ticks = 864000 ticks
                intervalSlot.captured shouldBe 864000L
            }

            it("logs retention configuration on start") {
                val (plugin, database, logger) = createMocks()
                val config = RetentionConfig(
                    enabled = true,
                    cleanupIntervalHours = 24,
                    tiers = RetentionTiers(7, 30, 180, 5)
                )
                val messageSlot = slot<String>()
                every { logger.info(capture(messageSlot)) } returns Unit

                val retentionTask = ProgressionRetentionTask(plugin, config, database)
                retentionTask.start()

                messageSlot.captured shouldBe "Progression retention task started - raw: 7d, hourly: 30d, daily: 180d, weekly: 5y"
            }
        }

        describe("stop") {

            it("cancels the scheduled task") {
                val (plugin, database, _) = createMocks()
                val bukkitTask = mockk<BukkitTask>(relaxed = true)
                every { plugin.server.scheduler.runTaskTimerAsynchronously(any(), any<Runnable>(), any(), any()) } returns bukkitTask

                val config = RetentionConfig(
                    enabled = true,
                    cleanupIntervalHours = 24,
                    tiers = RetentionTiers(7, 30, 180, 5)
                )
                val retentionTask = ProgressionRetentionTask(plugin, config, database)

                retentionTask.start()
                retentionTask.stop()

                verify { bukkitTask.cancel() }
            }
        }

        describe("retention execution") {

            it("calls all compaction methods in order when task runs") {
                val (plugin, database, logger) = createMocks()
                val runnableSlot = slot<Runnable>()
                val server = plugin.server
                val scheduler = server.scheduler

                every { scheduler.runTaskTimerAsynchronously(any(), capture(runnableSlot), any(), any()) } returns mockk(relaxed = true)

                // Setup database responses
                every { database.getSnapshotsForHourlyCompaction(any()) } returns emptyList()
                every { database.getHourlySummariesForDailyCompaction(any()) } returns emptyList()
                every { database.getDailySummariesForWeeklyCompaction(any()) } returns emptyList()
                every { database.deleteWeeklySummariesOlderThan(any()) } returns 0
                every { database.deleteLevelUpsOlderThan(any()) } returns 0

                val config = RetentionConfig(
                    enabled = true,
                    cleanupIntervalHours = 24,
                    tiers = RetentionTiers(7, 30, 180, 5)
                )
                val retentionTask = ProgressionRetentionTask(plugin, config, database)
                retentionTask.start()

                // Execute the captured runnable
                runnableSlot.captured.run()

                // Verify all compaction methods were called
                verify { database.getSnapshotsForHourlyCompaction(any()) }
                verify { database.getHourlySummariesForDailyCompaction(any()) }
                verify { database.getDailySummariesForWeeklyCompaction(any()) }
                verify { database.deleteWeeklySummariesOlderThan(any()) }
                verify { database.deleteLevelUpsOlderThan(any()) }
            }

            it("compacts snapshots to hourly summaries") {
                val (plugin, database, _) = createMocks()
                val runnableSlot = slot<Runnable>()
                val server = plugin.server
                val scheduler = server.scheduler

                every { scheduler.runTaskTimerAsynchronously(any(), capture(runnableSlot), any(), any()) } returns mockk(relaxed = true)

                val uuid = UUID.randomUUID()
                val compactionData = listOf(
                    HourlyCompactionData(
                        hour = "2025-01-10T14",
                        uuid = uuid,
                        playerName = "TestPlayer",
                        startPowerLevel = 100,
                        endPowerLevel = 110,
                        startSkillsJson = """{"MINING":50}""",
                        endSkillsJson = """{"MINING":60}"""
                    )
                )
                every { database.getSnapshotsForHourlyCompaction(any()) } returns compactionData
                every { database.buildAggregatedSkillsJson(any(), any()) } returns """{"MINING":{"start":50,"end":60,"gain":10}}"""
                every { database.getHourlySummariesForDailyCompaction(any()) } returns emptyList()
                every { database.getDailySummariesForWeeklyCompaction(any()) } returns emptyList()
                every { database.deleteWeeklySummariesOlderThan(any()) } returns 0
                every { database.deleteLevelUpsOlderThan(any()) } returns 0

                val config = RetentionConfig(
                    enabled = true,
                    cleanupIntervalHours = 24,
                    tiers = RetentionTiers(7, 30, 180, 5)
                )
                val retentionTask = ProgressionRetentionTask(plugin, config, database)
                retentionTask.start()

                runnableSlot.captured.run()

                verify {
                    database.insertHourlySummary(
                        hour = "2025-01-10T14",
                        uuid = uuid,
                        playerName = "TestPlayer",
                        startPowerLevel = 100,
                        endPowerLevel = 110,
                        skillsJson = any()
                    )
                }
                verify { database.deleteSnapshotsOlderThan(any()) }
            }

            it("compacts hourly summaries to daily summaries") {
                val (plugin, database, _) = createMocks()
                val runnableSlot = slot<Runnable>()
                val server = plugin.server
                val scheduler = server.scheduler

                every { scheduler.runTaskTimerAsynchronously(any(), capture(runnableSlot), any(), any()) } returns mockk(relaxed = true)

                val uuid = UUID.randomUUID()
                val compactionData = listOf(
                    DailyCompactionData(
                        date = "2025-01-10",
                        uuid = uuid,
                        playerName = "TestPlayer",
                        startPowerLevel = 100,
                        endPowerLevel = 150,
                        startSkillsJson = """{"MINING":50}""",
                        endSkillsJson = """{"MINING":100}"""
                    )
                )
                every { database.getSnapshotsForHourlyCompaction(any()) } returns emptyList()
                every { database.getHourlySummariesForDailyCompaction(any()) } returns compactionData
                every { database.buildAggregatedSkillsJson(any(), any()) } returns "{}"
                every { database.getDailySummariesForWeeklyCompaction(any()) } returns emptyList()
                every { database.deleteWeeklySummariesOlderThan(any()) } returns 0
                every { database.deleteLevelUpsOlderThan(any()) } returns 0

                val config = RetentionConfig(
                    enabled = true,
                    cleanupIntervalHours = 24,
                    tiers = RetentionTiers(7, 30, 180, 5)
                )
                val retentionTask = ProgressionRetentionTask(plugin, config, database)
                retentionTask.start()

                runnableSlot.captured.run()

                verify {
                    database.insertDailySummary(
                        date = "2025-01-10",
                        uuid = uuid,
                        playerName = "TestPlayer",
                        startPowerLevel = 100,
                        endPowerLevel = 150,
                        skillsJson = any()
                    )
                }
                verify { database.deleteHourlySummariesOlderThan(any()) }
            }

            it("compacts daily summaries to weekly summaries") {
                val (plugin, database, _) = createMocks()
                val runnableSlot = slot<Runnable>()
                val server = plugin.server
                val scheduler = server.scheduler

                every { scheduler.runTaskTimerAsynchronously(any(), capture(runnableSlot), any(), any()) } returns mockk(relaxed = true)

                val uuid = UUID.randomUUID()
                val compactionData = listOf(
                    WeeklyCompactionData(
                        week = "2025-W02",
                        uuid = uuid,
                        playerName = "TestPlayer",
                        startPowerLevel = 100,
                        endPowerLevel = 200,
                        startSkillsJson = """{"MINING":50}""",
                        endSkillsJson = """{"MINING":150}"""
                    )
                )
                every { database.getSnapshotsForHourlyCompaction(any()) } returns emptyList()
                every { database.getHourlySummariesForDailyCompaction(any()) } returns emptyList()
                every { database.getDailySummariesForWeeklyCompaction(any()) } returns compactionData
                every { database.buildAggregatedSkillsJson(any(), any()) } returns "{}"
                every { database.deleteWeeklySummariesOlderThan(any()) } returns 0
                every { database.deleteLevelUpsOlderThan(any()) } returns 0

                val config = RetentionConfig(
                    enabled = true,
                    cleanupIntervalHours = 24,
                    tiers = RetentionTiers(7, 30, 180, 5)
                )
                val retentionTask = ProgressionRetentionTask(plugin, config, database)
                retentionTask.start()

                runnableSlot.captured.run()

                verify {
                    database.insertWeeklySummary(
                        week = "2025-W02",
                        uuid = uuid,
                        playerName = "TestPlayer",
                        startPowerLevel = 100,
                        endPowerLevel = 200,
                        skillsJson = any()
                    )
                }
                verify { database.deleteDailySummariesOlderThan(any<LocalDate>()) }
            }

            it("logs summary when activity occurs") {
                val (plugin, database, logger) = createMocks()
                val runnableSlot = slot<Runnable>()
                val server = plugin.server
                val scheduler = server.scheduler
                val logSlot = slot<String>()

                every { scheduler.runTaskTimerAsynchronously(any(), capture(runnableSlot), any(), any()) } returns mockk(relaxed = true)
                every { logger.info(capture(logSlot)) } returns Unit

                every { database.getSnapshotsForHourlyCompaction(any()) } returns emptyList()
                every { database.getHourlySummariesForDailyCompaction(any()) } returns emptyList()
                every { database.getDailySummariesForWeeklyCompaction(any()) } returns emptyList()
                every { database.deleteWeeklySummariesOlderThan(any()) } returns 5
                every { database.deleteLevelUpsOlderThan(any()) } returns 10

                val config = RetentionConfig(
                    enabled = true,
                    cleanupIntervalHours = 24,
                    tiers = RetentionTiers(7, 30, 180, 5)
                )
                val retentionTask = ProgressionRetentionTask(plugin, config, database)
                retentionTask.start()

                runnableSlot.captured.run()

                // Check that activity was logged (timing varies, so check pattern)
                logSlot.captured shouldMatch Regex(
                    "Progression retention \\(\\d+ms\\): hourly\\+0, daily\\+0, weekly\\+0, weekly-5, events-10"
                )
            }

            it("handles exceptions gracefully") {
                val (plugin, database, logger) = createMocks()
                val runnableSlot = slot<Runnable>()
                val warningSlot = slot<String>()
                val server = plugin.server
                val scheduler = server.scheduler

                every { scheduler.runTaskTimerAsynchronously(any(), capture(runnableSlot), any(), any()) } returns mockk(relaxed = true)
                every { database.getSnapshotsForHourlyCompaction(any()) } throws RuntimeException("Test error")
                every { logger.warning(capture(warningSlot)) } returns Unit

                val config = RetentionConfig(
                    enabled = true,
                    cleanupIntervalHours = 24,
                    tiers = RetentionTiers(7, 30, 180, 5)
                )
                val retentionTask = ProgressionRetentionTask(plugin, config, database)
                retentionTask.start()

                // Should not throw
                runnableSlot.captured.run()

                warningSlot.captured shouldMatch Regex("Progression retention task failed after \\d+ms: Test error")
            }
        }
    }
})
