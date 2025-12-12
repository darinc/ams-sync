package io.github.darinc.amssync.progression

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import java.io.File
import java.sql.DriverManager
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.logging.Logger
import kotlin.io.path.createTempDirectory

class ProgressionQueryServiceTest : DescribeSpec({

    // Helper to create a database in a temp directory
    fun createTestDatabase(): Pair<ProgressionDatabase, File> {
        val tempDir = createTempDirectory("progression-query-test").toFile()
        val logger = mockk<Logger>(relaxed = true)
        val database = ProgressionDatabase(tempDir, "test.db", logger)
        database.initialize()
        return Pair(database, tempDir)
    }

    describe("ProgressionQueryService") {

        describe("getTrend") {

            it("returns NoData when no progression data exists") {
                val (database, tempDir) = createTestDatabase()
                try {
                    val logger = mockk<Logger>(relaxed = true)
                    val service = ProgressionQueryService(database, logger)
                    val uuid = UUID.randomUUID()

                    val result = service.getTrend(uuid, "TestPlayer", "POWER", Timeframe.SEVEN_DAYS)

                    result.shouldBeInstanceOf<TrendResult.NoData>()
                    database.close()
                } finally {
                    tempDir.deleteRecursively()
                }
            }

            it("returns Success with power level data from snapshots for 7-day timeframe") {
                val (database, tempDir) = createTestDatabase()
                try {
                    val logger = mockk<Logger>(relaxed = true)
                    val service = ProgressionQueryService(database, logger)
                    val uuid = UUID.randomUUID()
                    val dbFile = File(tempDir, "test.db")

                    // Insert recent snapshots (within 7 days)
                    val now = Instant.now()
                    val times = listOf(
                        now.minus(5, ChronoUnit.DAYS),
                        now.minus(3, ChronoUnit.DAYS),
                        now.minus(1, ChronoUnit.DAYS)
                    )
                    val powerLevels = listOf(100, 150, 200)

                    DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
                        times.forEachIndexed { i, timestamp ->
                            conn.prepareStatement(
                                """INSERT INTO snapshots (timestamp, uuid, player_name, power_level, skills_json)
                                   VALUES (?, ?, ?, ?, ?)"""
                            ).use { stmt ->
                                stmt.setString(1, timestamp.toString())
                                stmt.setString(2, uuid.toString())
                                stmt.setString(3, "TestPlayer")
                                stmt.setInt(4, powerLevels[i])
                                stmt.setString(5, "{}")
                                stmt.executeUpdate()
                            }
                        }
                    }

                    val result = service.getTrend(uuid, "TestPlayer", ProgressionQueryService.POWER_SKILL, Timeframe.SEVEN_DAYS)

                    result.shouldBeInstanceOf<TrendResult.Success>()
                    val success = result as TrendResult.Success
                    success.playerName shouldBe "TestPlayer"
                    success.skill shouldBe "POWER"
                    success.points.size shouldBe 3
                    success.points[0].level shouldBe 100
                    success.points[2].level shouldBe 200
                    database.close()
                } finally {
                    tempDir.deleteRecursively()
                }
            }

            it("returns Success with skill data from hourly summaries for 30-day timeframe") {
                val (database, tempDir) = createTestDatabase()
                try {
                    val logger = mockk<Logger>(relaxed = true)
                    val service = ProgressionQueryService(database, logger)
                    val uuid = UUID.randomUUID()

                    // Use recent dates (within 30 days)
                    val now = Instant.now()
                    val hourFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH")
                        .withZone(ZoneId.systemDefault())
                    val hour1 = hourFormatter.format(now.minus(10, ChronoUnit.DAYS))
                    val hour2 = hourFormatter.format(now.minus(10, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS))

                    // Insert hourly summaries
                    val skillsJson1 = """{"MINING":{"start":50,"end":60,"gain":10}}"""
                    val skillsJson2 = """{"MINING":{"start":60,"end":75,"gain":15}}"""
                    database.insertHourlySummary(hour1, uuid, "TestPlayer", 100, 110, skillsJson1)
                    database.insertHourlySummary(hour2, uuid, "TestPlayer", 110, 125, skillsJson2)

                    val result = service.getTrend(uuid, "TestPlayer", "MINING", Timeframe.THIRTY_DAYS)

                    result.shouldBeInstanceOf<TrendResult.Success>()
                    val success = result as TrendResult.Success
                    success.skill shouldBe "MINING"
                    success.points.size shouldBe 2
                    success.points[0].level shouldBe 60
                    success.points[1].level shouldBe 75
                    database.close()
                } finally {
                    tempDir.deleteRecursively()
                }
            }

            it("returns Success from daily summaries for 90-day timeframe") {
                val (database, tempDir) = createTestDatabase()
                try {
                    val logger = mockk<Logger>(relaxed = true)
                    val service = ProgressionQueryService(database, logger)
                    val uuid = UUID.randomUUID()

                    // Use recent dates (within 90 days)
                    val today = LocalDate.now(ZoneId.systemDefault())
                    val date1 = today.minusDays(50).toString()
                    val date2 = today.minusDays(49).toString()

                    database.insertDailySummary(date1, uuid, "TestPlayer", 100, 150, "{}")
                    database.insertDailySummary(date2, uuid, "TestPlayer", 150, 200, "{}")

                    val result = service.getTrend(uuid, "TestPlayer", ProgressionQueryService.POWER_SKILL, Timeframe.NINETY_DAYS)

                    result.shouldBeInstanceOf<TrendResult.Success>()
                    val success = result as TrendResult.Success
                    success.points.size shouldBe 2
                    database.close()
                } finally {
                    tempDir.deleteRecursively()
                }
            }

            it("returns Success from weekly summaries for ALL_TIME timeframe") {
                val (database, tempDir) = createTestDatabase()
                try {
                    val logger = mockk<Logger>(relaxed = true)
                    val service = ProgressionQueryService(database, logger)
                    val uuid = UUID.randomUUID()

                    database.insertWeeklySummary("2025-W01", uuid, "TestPlayer", 100, 200, "{}")
                    database.insertWeeklySummary("2025-W02", uuid, "TestPlayer", 200, 350, "{}")
                    database.insertWeeklySummary("2025-W03", uuid, "TestPlayer", 350, 500, "{}")

                    val result = service.getTrend(uuid, "TestPlayer", ProgressionQueryService.POWER_SKILL, Timeframe.ALL_TIME)

                    result.shouldBeInstanceOf<TrendResult.Success>()
                    val success = result as TrendResult.Success
                    success.points.size shouldBe 3
                    success.points[0].level shouldBe 200
                    success.points[2].level shouldBe 500
                    database.close()
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        }

        describe("POWER_SKILL constant") {

            it("has correct value") {
                ProgressionQueryService.POWER_SKILL shouldBe "POWER"
            }
        }
    }
})
