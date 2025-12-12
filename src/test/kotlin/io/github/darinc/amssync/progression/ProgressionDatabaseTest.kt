package io.github.darinc.amssync.progression

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain as stringContain
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.sql.DriverManager
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.logging.Logger
import kotlin.io.path.createTempDirectory

class ProgressionDatabaseTest : DescribeSpec({

    // Helper to create a database in a temp directory
    fun createTestDatabase(): Pair<ProgressionDatabase, File> {
        val tempDir = createTempDirectory("progression-test").toFile()
        val logger = mockk<Logger>(relaxed = true)
        val database = ProgressionDatabase(tempDir, "test.db", logger)
        database.initialize()
        return Pair(database, tempDir)
    }

    describe("ProgressionDatabase") {

        describe("initialize") {

            it("creates database file and tables") {
                val tempDir = createTempDirectory("progression-test").toFile()
                try {
                    val logger = mockk<Logger>(relaxed = true)
                    val database = ProgressionDatabase(tempDir, "test.db", logger)

                    val result = database.initialize()

                    result shouldBe true
                    File(tempDir, "test.db").exists() shouldBe true
                    database.close()
                } finally {
                    tempDir.deleteRecursively()
                }
            }

            it("creates all required tables") {
                val tempDir = createTempDirectory("progression-test").toFile()
                try {
                    val logger = mockk<Logger>(relaxed = true)
                    val database = ProgressionDatabase(tempDir, "test.db", logger)
                    database.initialize()

                    // Verify tables exist by querying sqlite_master
                    val dbFile = File(tempDir, "test.db")
                    DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
                        val tables = mutableListOf<String>()
                        conn.createStatement().use { stmt ->
                            val rs = stmt.executeQuery(
                                "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name"
                            )
                            while (rs.next()) {
                                tables.add(rs.getString("name"))
                            }
                        }
                        tables.contains("level_ups").shouldBeTrue()
                        tables.contains("snapshots").shouldBeTrue()
                        tables.contains("hourly_summaries").shouldBeTrue()
                        tables.contains("daily_summaries").shouldBeTrue()
                        tables.contains("weekly_summaries").shouldBeTrue()
                    }
                    database.close()
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        }

        describe("insertLevelUp") {

            it("inserts a level-up event") {
                val (database, tempDir) = createTestDatabase()
                try {
                    val uuid = UUID.randomUUID()

                    database.insertLevelUp(uuid, "TestPlayer", "MINING", 99, 100)

                    // Verify by direct query
                    val dbFile = File(tempDir, "test.db")
                    DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
                        conn.createStatement().use { stmt ->
                            val rs = stmt.executeQuery("SELECT * FROM level_ups")
                            rs.next() shouldBe true
                            rs.getString("uuid") shouldBe uuid.toString()
                            rs.getString("player_name") shouldBe "TestPlayer"
                            rs.getString("skill") shouldBe "MINING"
                            rs.getInt("old_level") shouldBe 99
                            rs.getInt("new_level") shouldBe 100
                        }
                    }
                    database.close()
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        }

        describe("insertSnapshot") {

            it("inserts a skill snapshot") {
                val (database, tempDir) = createTestDatabase()
                try {
                    val uuid = UUID.randomUUID()
                    val skills = mapOf("MINING" to 100, "WOODCUTTING" to 50, "FISHING" to 25)

                    database.insertSnapshot(uuid, "TestPlayer", 175, skills)

                    // Verify by direct query
                    val dbFile = File(tempDir, "test.db")
                    DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
                        conn.createStatement().use { stmt ->
                            val rs = stmt.executeQuery("SELECT * FROM snapshots")
                            rs.next() shouldBe true
                            rs.getString("uuid") shouldBe uuid.toString()
                            rs.getString("player_name") shouldBe "TestPlayer"
                            rs.getInt("power_level") shouldBe 175
                            val skillsJson = rs.getString("skills_json")
                            skillsJson stringContain "MINING"
                            skillsJson stringContain "100"
                        }
                    }
                    database.close()
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        }

        describe("getSnapshotsForHourlyCompaction") {

            it("returns empty list when no snapshots older than threshold") {
                val (database, tempDir) = createTestDatabase()
                try {
                    val uuid = UUID.randomUUID()
                    database.insertSnapshot(uuid, "TestPlayer", 100, mapOf("MINING" to 100))

                    // Threshold in the past - snapshot is newer
                    val threshold = Instant.now().minus(1, ChronoUnit.DAYS)
                    val results = database.getSnapshotsForHourlyCompaction(threshold)

                    results.shouldBeEmpty()
                    database.close()
                } finally {
                    tempDir.deleteRecursively()
                }
            }

            it("returns snapshots older than threshold grouped by hour") {
                val (database, tempDir) = createTestDatabase()
                try {
                    val uuid = UUID.randomUUID()

                    // Insert snapshot directly with old timestamp
                    val dbFile = File(tempDir, "test.db")
                    val oldTimestamp = Instant.now().minus(10, ChronoUnit.DAYS)
                    DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
                        conn.prepareStatement(
                            """INSERT INTO snapshots (timestamp, uuid, player_name, power_level, skills_json)
                               VALUES (?, ?, ?, ?, ?)"""
                        ).use { stmt ->
                            stmt.setString(1, oldTimestamp.toString())
                            stmt.setString(2, uuid.toString())
                            stmt.setString(3, "TestPlayer")
                            stmt.setInt(4, 100)
                            stmt.setString(5, """{"MINING":100}""")
                            stmt.executeUpdate()
                        }
                    }

                    val threshold = Instant.now().minus(7, ChronoUnit.DAYS)
                    val results = database.getSnapshotsForHourlyCompaction(threshold)

                    results shouldHaveSize 1
                    results[0].uuid shouldBe uuid
                    results[0].playerName shouldBe "TestPlayer"
                    database.close()
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        }

        describe("insertHourlySummary") {

            it("inserts an hourly summary") {
                val (database, tempDir) = createTestDatabase()
                try {
                    val uuid = UUID.randomUUID()

                    database.insertHourlySummary(
                        hour = "2025-01-15T14",
                        uuid = uuid,
                        playerName = "TestPlayer",
                        startPowerLevel = 100,
                        endPowerLevel = 110,
                        skillsJson = """{"MINING":{"start":50,"end":60,"gain":10}}"""
                    )

                    // Verify by direct query
                    val dbFile = File(tempDir, "test.db")
                    DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
                        conn.createStatement().use { stmt ->
                            val rs = stmt.executeQuery("SELECT * FROM hourly_summaries")
                            rs.next() shouldBe true
                            rs.getString("hour") shouldBe "2025-01-15T14"
                            rs.getString("uuid") shouldBe uuid.toString()
                            rs.getInt("start_power_level") shouldBe 100
                            rs.getInt("end_power_level") shouldBe 110
                        }
                    }
                    database.close()
                } finally {
                    tempDir.deleteRecursively()
                }
            }

            it("upserts on conflict (same hour and uuid)") {
                val (database, tempDir) = createTestDatabase()
                try {
                    val uuid = UUID.randomUUID()

                    database.insertHourlySummary(
                        hour = "2025-01-15T14",
                        uuid = uuid,
                        playerName = "TestPlayer",
                        startPowerLevel = 100,
                        endPowerLevel = 110,
                        skillsJson = "{}"
                    )

                    database.insertHourlySummary(
                        hour = "2025-01-15T14",
                        uuid = uuid,
                        playerName = "TestPlayer",
                        startPowerLevel = 100,
                        endPowerLevel = 120, // Updated value
                        skillsJson = "{}"
                    )

                    // Should only have one record
                    val dbFile = File(tempDir, "test.db")
                    DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
                        conn.createStatement().use { stmt ->
                            val rs = stmt.executeQuery("SELECT COUNT(*) as count FROM hourly_summaries")
                            rs.next()
                            rs.getInt("count") shouldBe 1
                        }
                        conn.createStatement().use { stmt ->
                            val rs = stmt.executeQuery("SELECT end_power_level FROM hourly_summaries")
                            rs.next()
                            rs.getInt("end_power_level") shouldBe 120
                        }
                    }
                    database.close()
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        }

        describe("deleteSnapshotsOlderThan") {

            it("deletes old snapshots and returns count") {
                val (database, tempDir) = createTestDatabase()
                try {
                    val uuid = UUID.randomUUID()
                    val dbFile = File(tempDir, "test.db")
                    val oldTimestamp = Instant.now().minus(10, ChronoUnit.DAYS)
                    val newTimestamp = Instant.now()

                    // Insert one old and one new snapshot
                    DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
                        conn.prepareStatement(
                            """INSERT INTO snapshots (timestamp, uuid, player_name, power_level, skills_json)
                               VALUES (?, ?, ?, ?, ?)"""
                        ).use { stmt ->
                            stmt.setString(1, oldTimestamp.toString())
                            stmt.setString(2, uuid.toString())
                            stmt.setString(3, "Player1")
                            stmt.setInt(4, 100)
                            stmt.setString(5, "{}")
                            stmt.executeUpdate()
                        }
                        conn.prepareStatement(
                            """INSERT INTO snapshots (timestamp, uuid, player_name, power_level, skills_json)
                               VALUES (?, ?, ?, ?, ?)"""
                        ).use { stmt ->
                            stmt.setString(1, newTimestamp.toString())
                            stmt.setString(2, uuid.toString())
                            stmt.setString(3, "Player1")
                            stmt.setInt(4, 110)
                            stmt.setString(5, "{}")
                            stmt.executeUpdate()
                        }
                    }

                    val threshold = Instant.now().minus(7, ChronoUnit.DAYS)
                    val deleted = database.deleteSnapshotsOlderThan(threshold)

                    deleted shouldBe 1

                    // Verify only new snapshot remains
                    DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
                        conn.createStatement().use { stmt ->
                            val rs = stmt.executeQuery("SELECT COUNT(*) as count FROM snapshots")
                            rs.next()
                            rs.getInt("count") shouldBe 1
                        }
                    }
                    database.close()
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        }

        describe("getHourlySummariesForDailyCompaction") {

            it("returns hourly summaries older than threshold grouped by date") {
                val (database, tempDir) = createTestDatabase()
                try {
                    val uuid = UUID.randomUUID()

                    // Insert hourly summaries
                    database.insertHourlySummary("2025-01-10T10", uuid, "Player", 100, 105, "{}")
                    database.insertHourlySummary("2025-01-10T11", uuid, "Player", 105, 110, "{}")
                    database.insertHourlySummary("2025-01-10T12", uuid, "Player", 110, 115, "{}")

                    val results = database.getHourlySummariesForDailyCompaction("2025-01-15T00")

                    results shouldHaveSize 1
                    results[0].date shouldBe "2025-01-10"
                    results[0].startPowerLevel shouldBe 100
                    results[0].endPowerLevel shouldBe 115
                    database.close()
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        }

        describe("insertDailySummary") {

            it("inserts a daily summary") {
                val (database, tempDir) = createTestDatabase()
                try {
                    val uuid = UUID.randomUUID()

                    database.insertDailySummary(
                        date = "2025-01-15",
                        uuid = uuid,
                        playerName = "TestPlayer",
                        startPowerLevel = 100,
                        endPowerLevel = 150,
                        skillsJson = """{"MINING":{"start":50,"end":100,"gain":50}}"""
                    )

                    val dbFile = File(tempDir, "test.db")
                    DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
                        conn.createStatement().use { stmt ->
                            val rs = stmt.executeQuery("SELECT * FROM daily_summaries")
                            rs.next() shouldBe true
                            rs.getString("date") shouldBe "2025-01-15"
                            rs.getInt("end_power_level") shouldBe 150
                        }
                    }
                    database.close()
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        }

        describe("deleteHourlySummariesOlderThan") {

            it("deletes old hourly summaries") {
                val (database, tempDir) = createTestDatabase()
                try {
                    val uuid = UUID.randomUUID()
                    database.insertHourlySummary("2025-01-10T10", uuid, "Player", 100, 110, "{}")
                    database.insertHourlySummary("2025-01-20T10", uuid, "Player", 150, 160, "{}")

                    val deleted = database.deleteHourlySummariesOlderThan("2025-01-15T00")

                    deleted shouldBe 1

                    val dbFile = File(tempDir, "test.db")
                    DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
                        conn.createStatement().use { stmt ->
                            val rs = stmt.executeQuery("SELECT COUNT(*) as count FROM hourly_summaries")
                            rs.next()
                            rs.getInt("count") shouldBe 1
                        }
                    }
                    database.close()
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        }

        describe("getDailySummariesForWeeklyCompaction") {

            it("returns daily summaries older than threshold grouped by week") {
                val (database, tempDir) = createTestDatabase()
                try {
                    val uuid = UUID.randomUUID()

                    // Insert daily summaries for the same week
                    database.insertDailySummary("2025-01-06", uuid, "Player", 100, 110, "{}") // Monday
                    database.insertDailySummary("2025-01-07", uuid, "Player", 110, 120, "{}") // Tuesday
                    database.insertDailySummary("2025-01-08", uuid, "Player", 120, 130, "{}") // Wednesday

                    val results = database.getDailySummariesForWeeklyCompaction("2025-01-15")

                    results shouldHaveSize 1
                    results[0].startPowerLevel shouldBe 100
                    results[0].endPowerLevel shouldBe 130
                    database.close()
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        }

        describe("insertWeeklySummary") {

            it("inserts a weekly summary") {
                val (database, tempDir) = createTestDatabase()
                try {
                    val uuid = UUID.randomUUID()

                    database.insertWeeklySummary(
                        week = "2025-W02",
                        uuid = uuid,
                        playerName = "TestPlayer",
                        startPowerLevel = 100,
                        endPowerLevel = 200,
                        skillsJson = "{}"
                    )

                    val dbFile = File(tempDir, "test.db")
                    DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
                        conn.createStatement().use { stmt ->
                            val rs = stmt.executeQuery("SELECT * FROM weekly_summaries")
                            rs.next() shouldBe true
                            rs.getString("week") shouldBe "2025-W02"
                            rs.getInt("end_power_level") shouldBe 200
                        }
                    }
                    database.close()
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        }

        describe("deleteDailySummariesOlderThan") {

            it("deletes old daily summaries") {
                val (database, tempDir) = createTestDatabase()
                try {
                    val uuid = UUID.randomUUID()
                    database.insertDailySummary("2025-01-01", uuid, "Player", 100, 110, "{}")
                    database.insertDailySummary("2025-06-01", uuid, "Player", 200, 210, "{}")

                    val deleted = database.deleteDailySummariesOlderThan(LocalDate.of(2025, 3, 1))

                    deleted shouldBe 1

                    val dbFile = File(tempDir, "test.db")
                    DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
                        conn.createStatement().use { stmt ->
                            val rs = stmt.executeQuery("SELECT date FROM daily_summaries")
                            rs.next() shouldBe true
                            rs.getString("date") shouldBe "2025-06-01"
                        }
                    }
                    database.close()
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        }

        describe("deleteWeeklySummariesOlderThan") {

            it("deletes old weekly summaries") {
                val (database, tempDir) = createTestDatabase()
                try {
                    val uuid = UUID.randomUUID()
                    database.insertWeeklySummary("2020-W01", uuid, "Player", 100, 110, "{}")
                    database.insertWeeklySummary("2025-W01", uuid, "Player", 200, 210, "{}")

                    val deleted = database.deleteWeeklySummariesOlderThan("2023-W01")

                    deleted shouldBe 1

                    val dbFile = File(tempDir, "test.db")
                    DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
                        conn.createStatement().use { stmt ->
                            val rs = stmt.executeQuery("SELECT week FROM weekly_summaries")
                            rs.next() shouldBe true
                            rs.getString("week") shouldBe "2025-W01"
                        }
                    }
                    database.close()
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        }

        describe("deleteLevelUpsOlderThan") {

            it("deletes old level-up events") {
                val (database, tempDir) = createTestDatabase()
                try {
                    val uuid = UUID.randomUUID()
                    val dbFile = File(tempDir, "test.db")
                    val oldTimestamp = Instant.now().minus(100, ChronoUnit.DAYS)
                    val newTimestamp = Instant.now()

                    // Insert one old and one new level-up
                    DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
                        conn.prepareStatement(
                            """INSERT INTO level_ups (timestamp, uuid, player_name, skill, old_level, new_level)
                               VALUES (?, ?, ?, ?, ?, ?)"""
                        ).use { stmt ->
                            stmt.setString(1, oldTimestamp.toString())
                            stmt.setString(2, uuid.toString())
                            stmt.setString(3, "Player1")
                            stmt.setString(4, "MINING")
                            stmt.setInt(5, 49)
                            stmt.setInt(6, 50)
                            stmt.executeUpdate()
                        }
                        conn.prepareStatement(
                            """INSERT INTO level_ups (timestamp, uuid, player_name, skill, old_level, new_level)
                               VALUES (?, ?, ?, ?, ?, ?)"""
                        ).use { stmt ->
                            stmt.setString(1, newTimestamp.toString())
                            stmt.setString(2, uuid.toString())
                            stmt.setString(3, "Player1")
                            stmt.setString(4, "MINING")
                            stmt.setInt(5, 99)
                            stmt.setInt(6, 100)
                            stmt.executeUpdate()
                        }
                    }

                    val threshold = Instant.now().minus(30, ChronoUnit.DAYS)
                    val deleted = database.deleteLevelUpsOlderThan(threshold)

                    deleted shouldBe 1

                    DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
                        conn.createStatement().use { stmt ->
                            val rs = stmt.executeQuery("SELECT new_level FROM level_ups")
                            rs.next() shouldBe true
                            rs.getInt("new_level") shouldBe 100
                        }
                    }
                    database.close()
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        }

        describe("buildAggregatedSkillsJson") {

            it("builds aggregated skills JSON from start and end") {
                val (database, tempDir) = createTestDatabase()
                try {
                    val startJson = """{"MINING":50,"WOODCUTTING":30}"""
                    val endJson = """{"MINING":60,"WOODCUTTING":35}"""

                    val result = database.buildAggregatedSkillsJson(startJson, endJson)

                    result stringContain "MINING"
                    result stringContain "\"start\":50"
                    result stringContain "\"end\":60"
                    result stringContain "\"gain\":10"
                    result stringContain "WOODCUTTING"
                    database.close()
                } finally {
                    tempDir.deleteRecursively()
                }
            }

            it("handles empty JSON") {
                val (database, tempDir) = createTestDatabase()
                try {
                    val result = database.buildAggregatedSkillsJson("{}", "{}")

                    result shouldBe "{}"
                    database.close()
                } finally {
                    tempDir.deleteRecursively()
                }
            }

            it("handles skills that appear only in end") {
                val (database, tempDir) = createTestDatabase()
                try {
                    val startJson = """{"MINING":50}"""
                    val endJson = """{"MINING":60,"FISHING":10}"""

                    val result = database.buildAggregatedSkillsJson(startJson, endJson)

                    result stringContain "FISHING"
                    result stringContain "\"start\":0"
                    result stringContain "\"end\":10"
                    database.close()
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        }

        describe("close") {

            it("closes the database connection") {
                val (database, tempDir) = createTestDatabase()
                try {
                    database.close()

                    // After close, operations should fail gracefully (return 0 or empty)
                    val deleted = database.deleteSnapshotsOlderThan(Instant.now())
                    deleted shouldBe 0
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        }

        describe("getPowerLevelFromSnapshots") {

            it("returns empty list when no data") {
                val (database, tempDir) = createTestDatabase()
                try {
                    val uuid = UUID.randomUUID()
                    val after = Instant.now().minus(7, ChronoUnit.DAYS)

                    val results = database.getPowerLevelFromSnapshots(uuid, after)

                    results.shouldBeEmpty()
                    database.close()
                } finally {
                    tempDir.deleteRecursively()
                }
            }

            it("returns trend points from snapshots") {
                val (database, tempDir) = createTestDatabase()
                try {
                    val uuid = UUID.randomUUID()
                    val dbFile = File(tempDir, "test.db")

                    // Insert snapshots at different times
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

                    val after = Instant.now().minus(7, ChronoUnit.DAYS)
                    val results = database.getPowerLevelFromSnapshots(uuid, after)

                    results shouldHaveSize 3
                    results[0].level shouldBe 100
                    results[1].level shouldBe 150
                    results[2].level shouldBe 200
                    database.close()
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        }

        describe("getSkillLevelFromSnapshots") {

            it("returns skill level trend from snapshots") {
                val (database, tempDir) = createTestDatabase()
                try {
                    val uuid = UUID.randomUUID()
                    val dbFile = File(tempDir, "test.db")

                    val now = Instant.now()
                    val times = listOf(
                        now.minus(2, ChronoUnit.DAYS),
                        now.minus(1, ChronoUnit.DAYS)
                    )

                    DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
                        conn.prepareStatement(
                            """INSERT INTO snapshots (timestamp, uuid, player_name, power_level, skills_json)
                               VALUES (?, ?, ?, ?, ?)"""
                        ).use { stmt ->
                            stmt.setString(1, times[0].toString())
                            stmt.setString(2, uuid.toString())
                            stmt.setString(3, "TestPlayer")
                            stmt.setInt(4, 100)
                            stmt.setString(5, """{"MINING":50,"WOODCUTTING":50}""")
                            stmt.executeUpdate()
                        }
                        conn.prepareStatement(
                            """INSERT INTO snapshots (timestamp, uuid, player_name, power_level, skills_json)
                               VALUES (?, ?, ?, ?, ?)"""
                        ).use { stmt ->
                            stmt.setString(1, times[1].toString())
                            stmt.setString(2, uuid.toString())
                            stmt.setString(3, "TestPlayer")
                            stmt.setInt(4, 120)
                            stmt.setString(5, """{"MINING":70,"WOODCUTTING":50}""")
                            stmt.executeUpdate()
                        }
                    }

                    val after = Instant.now().minus(7, ChronoUnit.DAYS)
                    val results = database.getSkillLevelFromSnapshots(uuid, "MINING", after)

                    results shouldHaveSize 2
                    results[0].level shouldBe 50
                    results[1].level shouldBe 70
                    database.close()
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        }

        describe("getPowerLevelFromHourly") {

            it("returns power level trend from hourly summaries") {
                val (database, tempDir) = createTestDatabase()
                try {
                    val uuid = UUID.randomUUID()

                    database.insertHourlySummary("2025-01-10T10", uuid, "Player", 100, 110, "{}")
                    database.insertHourlySummary("2025-01-10T11", uuid, "Player", 110, 120, "{}")
                    database.insertHourlySummary("2025-01-10T12", uuid, "Player", 120, 130, "{}")

                    val results = database.getPowerLevelFromHourly(uuid, "2025-01-01T00")

                    results shouldHaveSize 3
                    results[0].level shouldBe 110
                    results[1].level shouldBe 120
                    results[2].level shouldBe 130
                    database.close()
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        }

        describe("getPowerLevelFromDaily") {

            it("returns power level trend from daily summaries") {
                val (database, tempDir) = createTestDatabase()
                try {
                    val uuid = UUID.randomUUID()

                    database.insertDailySummary("2025-01-10", uuid, "Player", 100, 150, "{}")
                    database.insertDailySummary("2025-01-11", uuid, "Player", 150, 200, "{}")

                    val results = database.getPowerLevelFromDaily(uuid, "2025-01-01")

                    results shouldHaveSize 2
                    results[0].level shouldBe 150
                    results[1].level shouldBe 200
                    database.close()
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        }

        describe("getPowerLevelFromWeekly") {

            it("returns power level trend from weekly summaries") {
                val (database, tempDir) = createTestDatabase()
                try {
                    val uuid = UUID.randomUUID()

                    database.insertWeeklySummary("2025-W01", uuid, "Player", 100, 200, "{}")
                    database.insertWeeklySummary("2025-W02", uuid, "Player", 200, 350, "{}")

                    val results = database.getPowerLevelFromWeekly(uuid, "2024-W50")

                    results shouldHaveSize 2
                    results[0].level shouldBe 200
                    results[1].level shouldBe 350
                    database.close()
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        }

        describe("getSkillLevelFromHourly") {

            it("returns skill level trend from hourly summaries") {
                val (database, tempDir) = createTestDatabase()
                try {
                    val uuid = UUID.randomUUID()
                    val skillsJson1 = """{"MINING":{"start":50,"end":60,"gain":10}}"""
                    val skillsJson2 = """{"MINING":{"start":60,"end":75,"gain":15}}"""

                    database.insertHourlySummary("2025-01-10T10", uuid, "Player", 100, 110, skillsJson1)
                    database.insertHourlySummary("2025-01-10T11", uuid, "Player", 110, 125, skillsJson2)

                    val results = database.getSkillLevelFromHourly(uuid, "MINING", "2025-01-01T00")

                    results shouldHaveSize 2
                    results[0].level shouldBe 60
                    results[1].level shouldBe 75
                    database.close()
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        }
    }
})
