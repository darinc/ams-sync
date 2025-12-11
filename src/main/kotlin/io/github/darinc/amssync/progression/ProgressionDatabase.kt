package io.github.darinc.amssync.progression

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.logging.Logger

/**
 * SQLite database for storing skill progression data.
 *
 * Uses Paper 1.21's bundled SQLite JDBC driver.
 *
 * Supports tiered data storage:
 * - Raw snapshots (every N minutes)
 * - Hourly aggregates
 * - Daily aggregates
 * - Weekly aggregates
 *
 * @property dataFolder Plugin data folder for database file location
 * @property databaseFile Database filename
 * @property logger Logger for database operations
 */
class ProgressionDatabase(
    private val dataFolder: File,
    private val databaseFile: String,
    private val logger: Logger
) {
    private var connection: Connection? = null
    private val timestampFormatter = DateTimeFormatter.ISO_INSTANT

    /**
     * Initialize the database connection and schema.
     *
     * @return true if initialization succeeded, false otherwise
     */
    fun initialize(): Boolean {
        return try {
            // Paper 1.21 bundles SQLite JDBC
            Class.forName("org.sqlite.JDBC")

            val dbFile = File(dataFolder, databaseFile)
            val url = "jdbc:sqlite:${dbFile.absolutePath}"

            connection = DriverManager.getConnection(url)
            connection?.autoCommit = true

            initSchema()
            logger.info("Progression database initialized: ${dbFile.name}")
            true
        } catch (e: Exception) {
            logger.severe("Failed to initialize progression database: ${e.message}")
            false
        }
    }

    /**
     * Create database tables if they don't exist.
     */
    private fun initSchema() {
        val conn = connection ?: return

        conn.createStatement().use { stmt ->
            // Level-up events table
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS level_ups (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    timestamp TEXT NOT NULL,
                    uuid TEXT NOT NULL,
                    player_name TEXT NOT NULL,
                    skill TEXT NOT NULL,
                    old_level INTEGER NOT NULL,
                    new_level INTEGER NOT NULL
                )
                """.trimIndent()
            )
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_level_ups_uuid ON level_ups(uuid)")
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_level_ups_timestamp ON level_ups(timestamp)")

            // Raw snapshots table (every N minutes)
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS snapshots (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    timestamp TEXT NOT NULL,
                    uuid TEXT NOT NULL,
                    player_name TEXT NOT NULL,
                    power_level INTEGER NOT NULL,
                    skills_json TEXT NOT NULL
                )
                """.trimIndent()
            )
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_snapshots_uuid ON snapshots(uuid)")
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_snapshots_timestamp ON snapshots(timestamp)")

            // Hourly aggregates table
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS hourly_summaries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    hour TEXT NOT NULL,
                    uuid TEXT NOT NULL,
                    player_name TEXT NOT NULL,
                    start_power_level INTEGER NOT NULL,
                    end_power_level INTEGER NOT NULL,
                    skills_json TEXT NOT NULL,
                    UNIQUE(hour, uuid)
                )
                """.trimIndent()
            )
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_hourly_uuid ON hourly_summaries(uuid)")
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_hourly_hour ON hourly_summaries(hour)")

            // Daily aggregates table
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS daily_summaries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    date TEXT NOT NULL,
                    uuid TEXT NOT NULL,
                    player_name TEXT NOT NULL,
                    start_power_level INTEGER NOT NULL,
                    end_power_level INTEGER NOT NULL,
                    skills_json TEXT NOT NULL,
                    UNIQUE(date, uuid)
                )
                """.trimIndent()
            )
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_daily_uuid ON daily_summaries(uuid)")
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_daily_date ON daily_summaries(date)")

            // Weekly aggregates table
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS weekly_summaries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    week TEXT NOT NULL,
                    uuid TEXT NOT NULL,
                    player_name TEXT NOT NULL,
                    start_power_level INTEGER NOT NULL,
                    end_power_level INTEGER NOT NULL,
                    skills_json TEXT NOT NULL,
                    UNIQUE(week, uuid)
                )
                """.trimIndent()
            )
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_weekly_uuid ON weekly_summaries(uuid)")
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_weekly_week ON weekly_summaries(week)")
        }
    }

    /**
     * Insert a level-up event.
     */
    @Synchronized
    fun insertLevelUp(
        uuid: UUID,
        playerName: String,
        skill: String,
        oldLevel: Int,
        newLevel: Int
    ) {
        val conn = connection ?: return
        val timestamp = timestampFormatter.format(Instant.now())

        try {
            conn.prepareStatement(
                """
                INSERT INTO level_ups (timestamp, uuid, player_name, skill, old_level, new_level)
                VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, timestamp)
                stmt.setString(2, uuid.toString())
                stmt.setString(3, playerName)
                stmt.setString(4, skill)
                stmt.setInt(5, oldLevel)
                stmt.setInt(6, newLevel)
                stmt.executeUpdate()
            }
        } catch (e: Exception) {
            logger.warning("Failed to insert level-up event: ${e.message}")
        }
    }

    /**
     * Insert a skill snapshot.
     */
    @Synchronized
    fun insertSnapshot(
        uuid: UUID,
        playerName: String,
        powerLevel: Int,
        skills: Map<String, Int>
    ) {
        val conn = connection ?: return
        val timestamp = timestampFormatter.format(Instant.now())
        val skillsJson = buildSkillsJson(skills)

        try {
            conn.prepareStatement(
                """
                INSERT INTO snapshots (timestamp, uuid, player_name, power_level, skills_json)
                VALUES (?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, timestamp)
                stmt.setString(2, uuid.toString())
                stmt.setString(3, playerName)
                stmt.setInt(4, powerLevel)
                stmt.setString(5, skillsJson)
                stmt.executeUpdate()
            }
        } catch (e: Exception) {
            logger.warning("Failed to insert snapshot: ${e.message}")
        }
    }

    // ========== Raw -> Hourly Compaction ==========

    /**
     * Get raw snapshots for hourly compaction.
     * Groups by hour (YYYY-MM-DDTHH) and player.
     */
    @Synchronized
    fun getSnapshotsForHourlyCompaction(olderThan: Instant): List<HourlyCompactionData> {
        val conn = connection ?: return emptyList()
        val threshold = timestampFormatter.format(olderThan)
        val results = mutableListOf<HourlyCompactionData>()

        try {
            conn.prepareStatement(
                """
                SELECT
                    strftime('%Y-%m-%dT%H', timestamp) as hour,
                    uuid,
                    player_name,
                    MIN(power_level) as min_power,
                    MAX(power_level) as max_power,
                    (SELECT skills_json FROM snapshots s2
                     WHERE s2.uuid = s.uuid
                       AND strftime('%Y-%m-%dT%H', s2.timestamp) = strftime('%Y-%m-%dT%H', s.timestamp)
                     ORDER BY s2.timestamp ASC LIMIT 1) as start_skills,
                    (SELECT skills_json FROM snapshots s2
                     WHERE s2.uuid = s.uuid
                       AND strftime('%Y-%m-%dT%H', s2.timestamp) = strftime('%Y-%m-%dT%H', s.timestamp)
                     ORDER BY s2.timestamp DESC LIMIT 1) as end_skills
                FROM snapshots s
                WHERE timestamp < ?
                GROUP BY strftime('%Y-%m-%dT%H', timestamp), uuid
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, threshold)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    results.add(
                        HourlyCompactionData(
                            hour = rs.getString("hour"),
                            uuid = UUID.fromString(rs.getString("uuid")),
                            playerName = rs.getString("player_name"),
                            startPowerLevel = rs.getInt("min_power"),
                            endPowerLevel = rs.getInt("max_power"),
                            startSkillsJson = rs.getString("start_skills") ?: "{}",
                            endSkillsJson = rs.getString("end_skills") ?: "{}"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            logger.warning("Failed to get snapshots for hourly compaction: ${e.message}")
        }

        return results
    }

    /**
     * Insert an hourly summary (upsert).
     */
    @Synchronized
    fun insertHourlySummary(
        hour: String,
        uuid: UUID,
        playerName: String,
        startPowerLevel: Int,
        endPowerLevel: Int,
        skillsJson: String
    ) {
        val conn = connection ?: return

        try {
            conn.prepareStatement(
                """
                INSERT OR REPLACE INTO hourly_summaries
                (hour, uuid, player_name, start_power_level, end_power_level, skills_json)
                VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, hour)
                stmt.setString(2, uuid.toString())
                stmt.setString(3, playerName)
                stmt.setInt(4, startPowerLevel)
                stmt.setInt(5, endPowerLevel)
                stmt.setString(6, skillsJson)
                stmt.executeUpdate()
            }
        } catch (e: Exception) {
            logger.warning("Failed to insert hourly summary: ${e.message}")
        }
    }

    /**
     * Delete raw snapshots older than the specified timestamp.
     */
    @Synchronized
    fun deleteSnapshotsOlderThan(olderThan: Instant): Int {
        val conn = connection ?: return 0
        val threshold = timestampFormatter.format(olderThan)

        return try {
            conn.prepareStatement("DELETE FROM snapshots WHERE timestamp < ?").use { stmt ->
                stmt.setString(1, threshold)
                stmt.executeUpdate()
            }
        } catch (e: Exception) {
            logger.warning("Failed to delete old snapshots: ${e.message}")
            0
        }
    }

    // ========== Hourly -> Daily Compaction ==========

    /**
     * Get hourly summaries for daily compaction.
     * Groups by date and player.
     */
    @Synchronized
    fun getHourlySummariesForDailyCompaction(olderThan: String): List<DailyCompactionData> {
        val conn = connection ?: return emptyList()
        val results = mutableListOf<DailyCompactionData>()

        try {
            conn.prepareStatement(
                """
                SELECT
                    substr(hour, 1, 10) as date,
                    uuid,
                    player_name,
                    MIN(start_power_level) as min_power,
                    MAX(end_power_level) as max_power,
                    (SELECT skills_json FROM hourly_summaries h2
                     WHERE h2.uuid = h.uuid
                       AND substr(h2.hour, 1, 10) = substr(h.hour, 1, 10)
                     ORDER BY h2.hour ASC LIMIT 1) as start_skills,
                    (SELECT skills_json FROM hourly_summaries h2
                     WHERE h2.uuid = h.uuid
                       AND substr(h2.hour, 1, 10) = substr(h.hour, 1, 10)
                     ORDER BY h2.hour DESC LIMIT 1) as end_skills
                FROM hourly_summaries h
                WHERE hour < ?
                GROUP BY substr(hour, 1, 10), uuid
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, olderThan)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    results.add(
                        DailyCompactionData(
                            date = rs.getString("date"),
                            uuid = UUID.fromString(rs.getString("uuid")),
                            playerName = rs.getString("player_name"),
                            startPowerLevel = rs.getInt("min_power"),
                            endPowerLevel = rs.getInt("max_power"),
                            startSkillsJson = rs.getString("start_skills") ?: "{}",
                            endSkillsJson = rs.getString("end_skills") ?: "{}"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            logger.warning("Failed to get hourly summaries for daily compaction: ${e.message}")
        }

        return results
    }

    /**
     * Insert a daily summary (upsert).
     */
    @Synchronized
    fun insertDailySummary(
        date: String,
        uuid: UUID,
        playerName: String,
        startPowerLevel: Int,
        endPowerLevel: Int,
        skillsJson: String
    ) {
        val conn = connection ?: return

        try {
            conn.prepareStatement(
                """
                INSERT OR REPLACE INTO daily_summaries
                (date, uuid, player_name, start_power_level, end_power_level, skills_json)
                VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, date)
                stmt.setString(2, uuid.toString())
                stmt.setString(3, playerName)
                stmt.setInt(4, startPowerLevel)
                stmt.setInt(5, endPowerLevel)
                stmt.setString(6, skillsJson)
                stmt.executeUpdate()
            }
        } catch (e: Exception) {
            logger.warning("Failed to insert daily summary: ${e.message}")
        }
    }

    /**
     * Delete hourly summaries older than the specified hour.
     */
    @Synchronized
    fun deleteHourlySummariesOlderThan(olderThan: String): Int {
        val conn = connection ?: return 0

        return try {
            conn.prepareStatement("DELETE FROM hourly_summaries WHERE hour < ?").use { stmt ->
                stmt.setString(1, olderThan)
                stmt.executeUpdate()
            }
        } catch (e: Exception) {
            logger.warning("Failed to delete old hourly summaries: ${e.message}")
            0
        }
    }

    // ========== Daily -> Weekly Compaction ==========

    /**
     * Get daily summaries for weekly compaction.
     * Groups by ISO week (YYYY-Www) and player.
     */
    @Synchronized
    fun getDailySummariesForWeeklyCompaction(olderThan: String): List<WeeklyCompactionData> {
        val conn = connection ?: return emptyList()
        val results = mutableListOf<WeeklyCompactionData>()

        try {
            conn.prepareStatement(
                """
                SELECT
                    strftime('%Y-W%W', date) as week,
                    uuid,
                    player_name,
                    MIN(start_power_level) as min_power,
                    MAX(end_power_level) as max_power,
                    (SELECT skills_json FROM daily_summaries d2
                     WHERE d2.uuid = d.uuid
                       AND strftime('%Y-W%W', d2.date) = strftime('%Y-W%W', d.date)
                     ORDER BY d2.date ASC LIMIT 1) as start_skills,
                    (SELECT skills_json FROM daily_summaries d2
                     WHERE d2.uuid = d.uuid
                       AND strftime('%Y-W%W', d2.date) = strftime('%Y-W%W', d.date)
                     ORDER BY d2.date DESC LIMIT 1) as end_skills
                FROM daily_summaries d
                WHERE date < ?
                GROUP BY strftime('%Y-W%W', date), uuid
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, olderThan)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    results.add(
                        WeeklyCompactionData(
                            week = rs.getString("week"),
                            uuid = UUID.fromString(rs.getString("uuid")),
                            playerName = rs.getString("player_name"),
                            startPowerLevel = rs.getInt("min_power"),
                            endPowerLevel = rs.getInt("max_power"),
                            startSkillsJson = rs.getString("start_skills") ?: "{}",
                            endSkillsJson = rs.getString("end_skills") ?: "{}"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            logger.warning("Failed to get daily summaries for weekly compaction: ${e.message}")
        }

        return results
    }

    /**
     * Insert a weekly summary (upsert).
     */
    @Synchronized
    fun insertWeeklySummary(
        week: String,
        uuid: UUID,
        playerName: String,
        startPowerLevel: Int,
        endPowerLevel: Int,
        skillsJson: String
    ) {
        val conn = connection ?: return

        try {
            conn.prepareStatement(
                """
                INSERT OR REPLACE INTO weekly_summaries
                (week, uuid, player_name, start_power_level, end_power_level, skills_json)
                VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, week)
                stmt.setString(2, uuid.toString())
                stmt.setString(3, playerName)
                stmt.setInt(4, startPowerLevel)
                stmt.setInt(5, endPowerLevel)
                stmt.setString(6, skillsJson)
                stmt.executeUpdate()
            }
        } catch (e: Exception) {
            logger.warning("Failed to insert weekly summary: ${e.message}")
        }
    }

    /**
     * Delete daily summaries older than the specified date.
     */
    @Synchronized
    fun deleteDailySummariesOlderThan(olderThan: LocalDate): Int {
        val conn = connection ?: return 0
        val threshold = olderThan.toString()

        return try {
            conn.prepareStatement("DELETE FROM daily_summaries WHERE date < ?").use { stmt ->
                stmt.setString(1, threshold)
                stmt.executeUpdate()
            }
        } catch (e: Exception) {
            logger.warning("Failed to delete old daily summaries: ${e.message}")
            0
        }
    }

    /**
     * Delete weekly summaries older than the specified week.
     */
    @Synchronized
    fun deleteWeeklySummariesOlderThan(olderThan: String): Int {
        val conn = connection ?: return 0

        return try {
            conn.prepareStatement("DELETE FROM weekly_summaries WHERE week < ?").use { stmt ->
                stmt.setString(1, olderThan)
                stmt.executeUpdate()
            }
        } catch (e: Exception) {
            logger.warning("Failed to delete old weekly summaries: ${e.message}")
            0
        }
    }

    // ========== Level-up Event Cleanup ==========

    /**
     * Delete level-up events older than the specified timestamp.
     */
    @Synchronized
    fun deleteLevelUpsOlderThan(olderThan: Instant): Int {
        val conn = connection ?: return 0
        val threshold = timestampFormatter.format(olderThan)

        return try {
            conn.prepareStatement("DELETE FROM level_ups WHERE timestamp < ?").use { stmt ->
                stmt.setString(1, threshold)
                stmt.executeUpdate()
            }
        } catch (e: Exception) {
            logger.warning("Failed to delete old level-ups: ${e.message}")
            0
        }
    }

    // ========== Utilities ==========

    /**
     * Close the database connection.
     */
    fun close() {
        try {
            connection?.close()
            connection = null
            logger.info("Progression database connection closed")
        } catch (e: Exception) {
            logger.warning("Error closing progression database: ${e.message}")
        }
    }

    /**
     * Build a JSON object string from a skills map.
     */
    private fun buildSkillsJson(skills: Map<String, Int>): String {
        val entries = skills.entries.joinToString(",") { (skill, level) ->
            """"$skill":$level"""
        }
        return "{$entries}"
    }

    /**
     * Build a JSON object for aggregated skill summary.
     * Takes start and end skills JSON and computes gains.
     */
    fun buildAggregatedSkillsJson(startSkillsJson: String, endSkillsJson: String): String {
        val startSkills = parseSkillsJson(startSkillsJson)
        val endSkills = parseSkillsJson(endSkillsJson)

        val allSkills = (startSkills.keys + endSkills.keys).distinct()
        val entries = allSkills.map { skill ->
            val start = startSkills[skill] ?: 0
            val end = endSkills[skill] ?: 0
            val gain = end - start
            """"$skill":{"start":$start,"end":$end,"gain":$gain}"""
        }
        return "{${entries.joinToString(",")}}"
    }

    /**
     * Parse a simple skills JSON string ({"SKILL":123}) back into a map.
     */
    private fun parseSkillsJson(json: String): Map<String, Int> {
        if (json.isBlank() || json == "{}") return emptyMap()

        return try {
            // Simple JSON parsing for {"SKILL":123,"SKILL2":456}
            json.trim()
                .removePrefix("{")
                .removeSuffix("}")
                .split(",")
                .filter { it.isNotBlank() && !it.contains("{") }
                .associate { entry ->
                    val parts = entry.split(":")
                    val skill = parts[0].trim().removeSurrounding("\"")
                    val level = parts[1].trim().toInt()
                    skill to level
                }
        } catch (e: Exception) {
            logger.warning("Failed to parse skills JSON: ${e.message}")
            emptyMap()
        }
    }
}

/**
 * Data class for hourly compaction aggregation.
 */
data class HourlyCompactionData(
    val hour: String,  // "2025-01-15T14"
    val uuid: UUID,
    val playerName: String,
    val startPowerLevel: Int,
    val endPowerLevel: Int,
    val startSkillsJson: String,
    val endSkillsJson: String
)

/**
 * Data class for daily compaction aggregation.
 */
data class DailyCompactionData(
    val date: String,  // "2025-01-15"
    val uuid: UUID,
    val playerName: String,
    val startPowerLevel: Int,
    val endPowerLevel: Int,
    val startSkillsJson: String,
    val endSkillsJson: String
)

/**
 * Data class for weekly compaction aggregation.
 */
data class WeeklyCompactionData(
    val week: String,  // "2025-W03"
    val uuid: UUID,
    val playerName: String,
    val startPowerLevel: Int,
    val endPowerLevel: Int,
    val startSkillsJson: String,
    val endSkillsJson: String
)
