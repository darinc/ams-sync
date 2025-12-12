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

            // Metadata table for tracking operational state
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS metadata (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                )
                """.trimIndent()
            )

            // Config history table for tracking retention config changes
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS config_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    effective_from TEXT NOT NULL,
                    raw_days INTEGER NOT NULL,
                    hourly_days INTEGER NOT NULL,
                    daily_days INTEGER NOT NULL,
                    weekly_years INTEGER NOT NULL
                )
                """.trimIndent()
            )
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_config_effective ON config_history(effective_from)")
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

    // ========== Trend Queries for Charts ==========

    /**
     * Get power level trend from raw snapshots (for timeframes <= 7 days).
     */
    @Synchronized
    fun getPowerLevelFromSnapshots(uuid: UUID, after: Instant): List<TrendPoint> {
        val conn = connection ?: return emptyList()
        val threshold = timestampFormatter.format(after)
        val results = mutableListOf<TrendPoint>()

        try {
            conn.prepareStatement(
                """
                SELECT timestamp, power_level
                FROM snapshots
                WHERE uuid = ? AND timestamp >= ?
                ORDER BY timestamp ASC
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, uuid.toString())
                stmt.setString(2, threshold)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    val ts = Instant.parse(rs.getString("timestamp"))
                    results.add(TrendPoint(ts, rs.getInt("power_level")))
                }
            }
        } catch (e: Exception) {
            logger.warning("Failed to query power level from snapshots: ${e.message}")
        }
        return results
    }

    /**
     * Get power level trend from hourly summaries (for timeframes 7-30 days).
     */
    @Synchronized
    fun getPowerLevelFromHourly(uuid: UUID, afterHour: String): List<TrendPoint> {
        val conn = connection ?: return emptyList()
        val results = mutableListOf<TrendPoint>()

        try {
            conn.prepareStatement(
                """
                SELECT hour, end_power_level
                FROM hourly_summaries
                WHERE uuid = ? AND hour >= ?
                ORDER BY hour ASC
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, uuid.toString())
                stmt.setString(2, afterHour)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    val hour = rs.getString("hour")
                    // Parse hour format "2025-01-15T14" to Instant
                    val ts = java.time.LocalDateTime.parse(hour + ":00:00")
                        .atZone(java.time.ZoneId.systemDefault())
                        .toInstant()
                    results.add(TrendPoint(ts, rs.getInt("end_power_level")))
                }
            }
        } catch (e: Exception) {
            logger.warning("Failed to query power level from hourly: ${e.message}")
        }
        return results
    }

    /**
     * Get power level trend from daily summaries (for timeframes 30-180 days).
     */
    @Synchronized
    fun getPowerLevelFromDaily(uuid: UUID, afterDate: String): List<TrendPoint> {
        val conn = connection ?: return emptyList()
        val results = mutableListOf<TrendPoint>()

        try {
            conn.prepareStatement(
                """
                SELECT date, end_power_level
                FROM daily_summaries
                WHERE uuid = ? AND date >= ?
                ORDER BY date ASC
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, uuid.toString())
                stmt.setString(2, afterDate)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    val date = rs.getString("date")
                    val ts = java.time.LocalDate.parse(date)
                        .atStartOfDay(java.time.ZoneId.systemDefault())
                        .toInstant()
                    results.add(TrendPoint(ts, rs.getInt("end_power_level")))
                }
            }
        } catch (e: Exception) {
            logger.warning("Failed to query power level from daily: ${e.message}")
        }
        return results
    }

    /**
     * Get power level trend from weekly summaries (for timeframes > 180 days).
     */
    @Synchronized
    fun getPowerLevelFromWeekly(uuid: UUID, afterWeek: String): List<TrendPoint> {
        val conn = connection ?: return emptyList()
        val results = mutableListOf<TrendPoint>()

        try {
            conn.prepareStatement(
                """
                SELECT week, end_power_level
                FROM weekly_summaries
                WHERE uuid = ? AND week >= ?
                ORDER BY week ASC
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, uuid.toString())
                stmt.setString(2, afterWeek)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    val week = rs.getString("week")
                    // Parse week format "2025-W02" to Instant (start of that week)
                    val weekFields = java.time.temporal.WeekFields.ISO
                    val yearWeek = week.split("-W")
                    val year = yearWeek[0].toInt()
                    val weekNum = yearWeek[1].toInt()
                    val ts = java.time.LocalDate.of(year, 1, 1)
                        .with(weekFields.weekOfYear(), weekNum.toLong())
                        .with(weekFields.dayOfWeek(), 1)
                        .atStartOfDay(java.time.ZoneId.systemDefault())
                        .toInstant()
                    results.add(TrendPoint(ts, rs.getInt("end_power_level")))
                }
            }
        } catch (e: Exception) {
            logger.warning("Failed to query power level from weekly: ${e.message}")
        }
        return results
    }

    /**
     * Get specific skill level trend from raw snapshots.
     */
    @Synchronized
    fun getSkillLevelFromSnapshots(uuid: UUID, skill: String, after: Instant): List<TrendPoint> {
        val conn = connection ?: return emptyList()
        val threshold = timestampFormatter.format(after)
        val results = mutableListOf<TrendPoint>()

        try {
            conn.prepareStatement(
                """
                SELECT timestamp, skills_json
                FROM snapshots
                WHERE uuid = ? AND timestamp >= ?
                ORDER BY timestamp ASC
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, uuid.toString())
                stmt.setString(2, threshold)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    val ts = Instant.parse(rs.getString("timestamp"))
                    val skillsJson = rs.getString("skills_json")
                    val skills = parseSkillsJson(skillsJson)
                    val level = skills[skill] ?: 0
                    results.add(TrendPoint(ts, level))
                }
            }
        } catch (e: Exception) {
            logger.warning("Failed to query skill level from snapshots: ${e.message}")
        }
        return results
    }

    /**
     * Get specific skill level trend from hourly summaries.
     */
    @Synchronized
    fun getSkillLevelFromHourly(uuid: UUID, skill: String, afterHour: String): List<TrendPoint> {
        val conn = connection ?: return emptyList()
        val results = mutableListOf<TrendPoint>()

        try {
            conn.prepareStatement(
                """
                SELECT hour, skills_json
                FROM hourly_summaries
                WHERE uuid = ? AND hour >= ?
                ORDER BY hour ASC
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, uuid.toString())
                stmt.setString(2, afterHour)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    val hour = rs.getString("hour")
                    val ts = java.time.LocalDateTime.parse(hour + ":00:00")
                        .atZone(java.time.ZoneId.systemDefault())
                        .toInstant()
                    val skillsJson = rs.getString("skills_json")
                    val level = extractSkillEndLevel(skillsJson, skill)
                    results.add(TrendPoint(ts, level))
                }
            }
        } catch (e: Exception) {
            logger.warning("Failed to query skill level from hourly: ${e.message}")
        }
        return results
    }

    /**
     * Get specific skill level trend from daily summaries.
     */
    @Synchronized
    fun getSkillLevelFromDaily(uuid: UUID, skill: String, afterDate: String): List<TrendPoint> {
        val conn = connection ?: return emptyList()
        val results = mutableListOf<TrendPoint>()

        try {
            conn.prepareStatement(
                """
                SELECT date, skills_json
                FROM daily_summaries
                WHERE uuid = ? AND date >= ?
                ORDER BY date ASC
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, uuid.toString())
                stmt.setString(2, afterDate)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    val date = rs.getString("date")
                    val ts = java.time.LocalDate.parse(date)
                        .atStartOfDay(java.time.ZoneId.systemDefault())
                        .toInstant()
                    val skillsJson = rs.getString("skills_json")
                    val level = extractSkillEndLevel(skillsJson, skill)
                    results.add(TrendPoint(ts, level))
                }
            }
        } catch (e: Exception) {
            logger.warning("Failed to query skill level from daily: ${e.message}")
        }
        return results
    }

    /**
     * Get specific skill level trend from weekly summaries.
     */
    @Synchronized
    fun getSkillLevelFromWeekly(uuid: UUID, skill: String, afterWeek: String): List<TrendPoint> {
        val conn = connection ?: return emptyList()
        val results = mutableListOf<TrendPoint>()

        try {
            conn.prepareStatement(
                """
                SELECT week, skills_json
                FROM weekly_summaries
                WHERE uuid = ? AND week >= ?
                ORDER BY week ASC
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, uuid.toString())
                stmt.setString(2, afterWeek)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    val week = rs.getString("week")
                    val weekFields = java.time.temporal.WeekFields.ISO
                    val yearWeek = week.split("-W")
                    val year = yearWeek[0].toInt()
                    val weekNum = yearWeek[1].toInt()
                    val ts = java.time.LocalDate.of(year, 1, 1)
                        .with(weekFields.weekOfYear(), weekNum.toLong())
                        .with(weekFields.dayOfWeek(), 1)
                        .atStartOfDay(java.time.ZoneId.systemDefault())
                        .toInstant()
                    val skillsJson = rs.getString("skills_json")
                    val level = extractSkillEndLevel(skillsJson, skill)
                    results.add(TrendPoint(ts, level))
                }
            }
        } catch (e: Exception) {
            logger.warning("Failed to query skill level from weekly: ${e.message}")
        }
        return results
    }

    // ========== Bounded Trend Queries (for hybrid multi-tier queries) ==========

    /**
     * Get power level trend from raw snapshots between two timestamps.
     */
    @Synchronized
    fun getPowerLevelFromSnapshotsBetween(uuid: UUID, after: Instant, before: Instant): List<TrendPoint> {
        val conn = connection ?: return emptyList()
        val afterStr = timestampFormatter.format(after)
        val beforeStr = timestampFormatter.format(before)
        val results = mutableListOf<TrendPoint>()

        try {
            conn.prepareStatement(
                """
                SELECT timestamp, power_level
                FROM snapshots
                WHERE uuid = ? AND timestamp >= ? AND timestamp < ?
                ORDER BY timestamp ASC
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, uuid.toString())
                stmt.setString(2, afterStr)
                stmt.setString(3, beforeStr)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    val ts = Instant.parse(rs.getString("timestamp"))
                    results.add(TrendPoint(ts, rs.getInt("power_level")))
                }
            }
        } catch (e: Exception) {
            logger.warning("Failed to query power level from snapshots (bounded): ${e.message}")
        }
        return results
    }

    /**
     * Get power level trend from hourly summaries between two hours.
     */
    @Synchronized
    fun getPowerLevelFromHourlyBetween(uuid: UUID, afterHour: String, beforeHour: String): List<TrendPoint> {
        val conn = connection ?: return emptyList()
        val results = mutableListOf<TrendPoint>()

        try {
            conn.prepareStatement(
                """
                SELECT hour, end_power_level
                FROM hourly_summaries
                WHERE uuid = ? AND hour >= ? AND hour < ?
                ORDER BY hour ASC
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, uuid.toString())
                stmt.setString(2, afterHour)
                stmt.setString(3, beforeHour)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    val hour = rs.getString("hour")
                    val ts = java.time.LocalDateTime.parse(hour + ":00:00")
                        .atZone(java.time.ZoneId.systemDefault())
                        .toInstant()
                    results.add(TrendPoint(ts, rs.getInt("end_power_level")))
                }
            }
        } catch (e: Exception) {
            logger.warning("Failed to query power level from hourly (bounded): ${e.message}")
        }
        return results
    }

    /**
     * Get power level trend from daily summaries between two dates.
     */
    @Synchronized
    fun getPowerLevelFromDailyBetween(uuid: UUID, afterDate: String, beforeDate: String): List<TrendPoint> {
        val conn = connection ?: return emptyList()
        val results = mutableListOf<TrendPoint>()

        try {
            conn.prepareStatement(
                """
                SELECT date, end_power_level
                FROM daily_summaries
                WHERE uuid = ? AND date >= ? AND date < ?
                ORDER BY date ASC
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, uuid.toString())
                stmt.setString(2, afterDate)
                stmt.setString(3, beforeDate)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    val date = rs.getString("date")
                    val ts = java.time.LocalDate.parse(date)
                        .atStartOfDay(java.time.ZoneId.systemDefault())
                        .toInstant()
                    results.add(TrendPoint(ts, rs.getInt("end_power_level")))
                }
            }
        } catch (e: Exception) {
            logger.warning("Failed to query power level from daily (bounded): ${e.message}")
        }
        return results
    }

    /**
     * Get specific skill level trend from raw snapshots between two timestamps.
     */
    @Synchronized
    fun getSkillLevelFromSnapshotsBetween(uuid: UUID, skill: String, after: Instant, before: Instant): List<TrendPoint> {
        val conn = connection ?: return emptyList()
        val afterStr = timestampFormatter.format(after)
        val beforeStr = timestampFormatter.format(before)
        val results = mutableListOf<TrendPoint>()

        try {
            conn.prepareStatement(
                """
                SELECT timestamp, skills_json
                FROM snapshots
                WHERE uuid = ? AND timestamp >= ? AND timestamp < ?
                ORDER BY timestamp ASC
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, uuid.toString())
                stmt.setString(2, afterStr)
                stmt.setString(3, beforeStr)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    val ts = Instant.parse(rs.getString("timestamp"))
                    val skillsJson = rs.getString("skills_json")
                    val skills = parseSkillsJson(skillsJson)
                    val level = skills[skill] ?: 0
                    results.add(TrendPoint(ts, level))
                }
            }
        } catch (e: Exception) {
            logger.warning("Failed to query skill level from snapshots (bounded): ${e.message}")
        }
        return results
    }

    /**
     * Get specific skill level trend from hourly summaries between two hours.
     */
    @Synchronized
    fun getSkillLevelFromHourlyBetween(uuid: UUID, skill: String, afterHour: String, beforeHour: String): List<TrendPoint> {
        val conn = connection ?: return emptyList()
        val results = mutableListOf<TrendPoint>()

        try {
            conn.prepareStatement(
                """
                SELECT hour, skills_json
                FROM hourly_summaries
                WHERE uuid = ? AND hour >= ? AND hour < ?
                ORDER BY hour ASC
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, uuid.toString())
                stmt.setString(2, afterHour)
                stmt.setString(3, beforeHour)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    val hour = rs.getString("hour")
                    val ts = java.time.LocalDateTime.parse(hour + ":00:00")
                        .atZone(java.time.ZoneId.systemDefault())
                        .toInstant()
                    val skillsJson = rs.getString("skills_json")
                    val level = extractSkillEndLevel(skillsJson, skill)
                    results.add(TrendPoint(ts, level))
                }
            }
        } catch (e: Exception) {
            logger.warning("Failed to query skill level from hourly (bounded): ${e.message}")
        }
        return results
    }

    /**
     * Get specific skill level trend from daily summaries between two dates.
     */
    @Synchronized
    fun getSkillLevelFromDailyBetween(uuid: UUID, skill: String, afterDate: String, beforeDate: String): List<TrendPoint> {
        val conn = connection ?: return emptyList()
        val results = mutableListOf<TrendPoint>()

        try {
            conn.prepareStatement(
                """
                SELECT date, skills_json
                FROM daily_summaries
                WHERE uuid = ? AND date >= ? AND date < ?
                ORDER BY date ASC
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, uuid.toString())
                stmt.setString(2, afterDate)
                stmt.setString(3, beforeDate)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    val date = rs.getString("date")
                    val ts = java.time.LocalDate.parse(date)
                        .atStartOfDay(java.time.ZoneId.systemDefault())
                        .toInstant()
                    val skillsJson = rs.getString("skills_json")
                    val level = extractSkillEndLevel(skillsJson, skill)
                    results.add(TrendPoint(ts, level))
                }
            }
        } catch (e: Exception) {
            logger.warning("Failed to query skill level from daily (bounded): ${e.message}")
        }
        return results
    }

    // ========== Helper Methods ==========

    /**
     * Extract the end level for a specific skill from aggregated skills JSON.
     * Format: {"SKILL":{"start":X,"end":Y,"gain":Z}}
     */
    private fun extractSkillEndLevel(skillsJson: String, skill: String): Int {
        if (skillsJson.isBlank() || skillsJson == "{}") return 0

        return try {
            // Find the skill entry and extract "end" value
            val skillPattern = """"$skill":\{"start":\d+,"end":(\d+),"gain":-?\d+\}"""
            val regex = Regex(skillPattern)
            val match = regex.find(skillsJson)
            match?.groupValues?.get(1)?.toInt() ?: 0
        } catch (e: Exception) {
            logger.warning("Failed to extract skill end level: ${e.message}")
            0
        }
    }

    // ========== Metadata ==========

    /**
     * Get a metadata value by key.
     *
     * @param key The metadata key
     * @return The value, or null if not found
     */
    @Synchronized
    fun getMetadata(key: String): String? {
        val conn = connection ?: return null

        return try {
            conn.prepareStatement("SELECT value FROM metadata WHERE key = ?").use { stmt ->
                stmt.setString(1, key)
                val rs = stmt.executeQuery()
                if (rs.next()) rs.getString("value") else null
            }
        } catch (e: Exception) {
            logger.warning("Failed to get metadata '$key': ${e.message}")
            null
        }
    }

    /**
     * Set a metadata value (upsert).
     *
     * @param key The metadata key
     * @param value The value to store
     */
    @Synchronized
    fun setMetadata(key: String, value: String) {
        val conn = connection ?: return

        try {
            conn.prepareStatement(
                "INSERT OR REPLACE INTO metadata (key, value) VALUES (?, ?)"
            ).use { stmt ->
                stmt.setString(1, key)
                stmt.setString(2, value)
                stmt.executeUpdate()
            }
        } catch (e: Exception) {
            logger.warning("Failed to set metadata '$key': ${e.message}")
        }
    }

    // ========== Config History ==========

    /**
     * Get the current (most recent) config from history.
     *
     * @return The most recent config snapshot, or null if none recorded
     */
    @Synchronized
    fun getCurrentConfig(): ConfigSnapshot? {
        val conn = connection ?: return null

        return try {
            conn.prepareStatement(
                """
                SELECT effective_from, raw_days, hourly_days, daily_days, weekly_years
                FROM config_history
                ORDER BY effective_from DESC
                LIMIT 1
                """.trimIndent()
            ).use { stmt ->
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    ConfigSnapshot(
                        effectiveFrom = Instant.parse(rs.getString("effective_from")),
                        rawDays = rs.getInt("raw_days"),
                        hourlyDays = rs.getInt("hourly_days"),
                        dailyDays = rs.getInt("daily_days"),
                        weeklyYears = rs.getInt("weekly_years")
                    )
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            logger.warning("Failed to get current config: ${e.message}")
            null
        }
    }

    /**
     * Record config in history if it differs from the current config.
     *
     * @param rawDays Raw snapshot retention days
     * @param hourlyDays Hourly summary retention days
     * @param dailyDays Daily summary retention days
     * @param weeklyYears Weekly summary retention years
     * @return true if new config was recorded, false if unchanged
     */
    @Synchronized
    fun recordConfigIfChanged(
        rawDays: Int,
        hourlyDays: Int,
        dailyDays: Int,
        weeklyYears: Int
    ): Boolean {
        val current = getCurrentConfig()

        // Check if config has changed
        if (current != null &&
            current.rawDays == rawDays &&
            current.hourlyDays == hourlyDays &&
            current.dailyDays == dailyDays &&
            current.weeklyYears == weeklyYears
        ) {
            return false
        }

        val conn = connection ?: return false
        val timestamp = timestampFormatter.format(Instant.now())

        return try {
            conn.prepareStatement(
                """
                INSERT INTO config_history (effective_from, raw_days, hourly_days, daily_days, weekly_years)
                VALUES (?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, timestamp)
                stmt.setInt(2, rawDays)
                stmt.setInt(3, hourlyDays)
                stmt.setInt(4, dailyDays)
                stmt.setInt(5, weeklyYears)
                stmt.executeUpdate()
            }
            logger.info("Recorded new config: raw=$rawDays, hourly=$hourlyDays, daily=$dailyDays, weekly=$weeklyYears years")
            true
        } catch (e: Exception) {
            logger.warning("Failed to record config: ${e.message}")
            false
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

/**
 * Data class for config history snapshot.
 */
data class ConfigSnapshot(
    val effectiveFrom: Instant,
    val rawDays: Int,
    val hourlyDays: Int,
    val dailyDays: Int,
    val weeklyYears: Int
)
