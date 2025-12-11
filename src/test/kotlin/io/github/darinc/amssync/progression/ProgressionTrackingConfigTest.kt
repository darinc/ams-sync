package io.github.darinc.amssync.progression

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.bukkit.configuration.file.FileConfiguration

class ProgressionTrackingConfigTest : DescribeSpec({

    describe("ProgressionTrackingConfig") {

        describe("fromConfig") {

            it("loads all values from config") {
                val config = mockk<FileConfiguration>()
                every { config.getBoolean("progression-tracking.enabled", false) } returns true
                every { config.getString("progression-tracking.database-file", "progression.db") } returns "custom.db"
                every { config.getBoolean("progression-tracking.events.enabled", true) } returns true
                every { config.getBoolean("progression-tracking.snapshots.enabled", true) } returns true
                every { config.getInt("progression-tracking.snapshots.interval-minutes", 5) } returns 10
                every { config.getBoolean("progression-tracking.snapshots.online-only", true) } returns false
                every { config.getBoolean("progression-tracking.retention.enabled", true) } returns true
                every { config.getInt("progression-tracking.retention.cleanup-interval-hours", 24) } returns 12
                every { config.getInt("progression-tracking.retention.tiers.raw-days", 7) } returns 14
                every { config.getInt("progression-tracking.retention.tiers.hourly-days", 30) } returns 60
                every { config.getInt("progression-tracking.retention.tiers.daily-days", 180) } returns 365
                every { config.getInt("progression-tracking.retention.tiers.weekly-years", 5) } returns 10

                val progressionConfig = ProgressionTrackingConfig.fromConfig(config)

                progressionConfig.enabled shouldBe true
                progressionConfig.databaseFile shouldBe "custom.db"
                progressionConfig.events.enabled shouldBe true
                progressionConfig.snapshots.enabled shouldBe true
                progressionConfig.snapshots.intervalMinutes shouldBe 10
                progressionConfig.snapshots.onlineOnly shouldBe false
                progressionConfig.retention.enabled shouldBe true
                progressionConfig.retention.cleanupIntervalHours shouldBe 12
                progressionConfig.retention.tiers.rawDays shouldBe 14
                progressionConfig.retention.tiers.hourlyDays shouldBe 60
                progressionConfig.retention.tiers.dailyDays shouldBe 365
                progressionConfig.retention.tiers.weeklyYears shouldBe 10
            }

            it("uses default values when config keys missing") {
                val config = mockk<FileConfiguration>()
                every { config.getBoolean("progression-tracking.enabled", false) } returns false
                every { config.getString("progression-tracking.database-file", "progression.db") } returns "progression.db"
                every { config.getBoolean("progression-tracking.events.enabled", true) } returns true
                every { config.getBoolean("progression-tracking.snapshots.enabled", true) } returns true
                every { config.getInt("progression-tracking.snapshots.interval-minutes", 5) } returns 5
                every { config.getBoolean("progression-tracking.snapshots.online-only", true) } returns true
                every { config.getBoolean("progression-tracking.retention.enabled", true) } returns true
                every { config.getInt("progression-tracking.retention.cleanup-interval-hours", 24) } returns 24
                every { config.getInt("progression-tracking.retention.tiers.raw-days", 7) } returns 7
                every { config.getInt("progression-tracking.retention.tiers.hourly-days", 30) } returns 30
                every { config.getInt("progression-tracking.retention.tiers.daily-days", 180) } returns 180
                every { config.getInt("progression-tracking.retention.tiers.weekly-years", 5) } returns 5

                val progressionConfig = ProgressionTrackingConfig.fromConfig(config)

                progressionConfig.enabled shouldBe false
                progressionConfig.databaseFile shouldBe "progression.db"
                progressionConfig.snapshots.intervalMinutes shouldBe 5
                progressionConfig.retention.tiers.rawDays shouldBe 7
                progressionConfig.retention.tiers.hourlyDays shouldBe 30
                progressionConfig.retention.tiers.dailyDays shouldBe 180
                progressionConfig.retention.tiers.weeklyYears shouldBe 5
            }

            it("handles null string values with defaults") {
                val config = mockk<FileConfiguration>()
                every { config.getBoolean("progression-tracking.enabled", false) } returns true
                every { config.getString("progression-tracking.database-file", "progression.db") } returns null
                every { config.getBoolean("progression-tracking.events.enabled", true) } returns true
                every { config.getBoolean("progression-tracking.snapshots.enabled", true) } returns true
                every { config.getInt("progression-tracking.snapshots.interval-minutes", 5) } returns 5
                every { config.getBoolean("progression-tracking.snapshots.online-only", true) } returns true
                every { config.getBoolean("progression-tracking.retention.enabled", true) } returns true
                every { config.getInt("progression-tracking.retention.cleanup-interval-hours", 24) } returns 24
                every { config.getInt("progression-tracking.retention.tiers.raw-days", 7) } returns 7
                every { config.getInt("progression-tracking.retention.tiers.hourly-days", 30) } returns 30
                every { config.getInt("progression-tracking.retention.tiers.daily-days", 180) } returns 180
                every { config.getInt("progression-tracking.retention.tiers.weekly-years", 5) } returns 5

                val progressionConfig = ProgressionTrackingConfig.fromConfig(config)

                progressionConfig.databaseFile shouldBe "progression.db"
            }
        }
    }

    describe("SnapshotConfig") {

        describe("getIntervalTicks") {

            it("converts minutes to ticks (20 ticks per second)") {
                val snapshotConfig = SnapshotConfig(
                    enabled = true,
                    intervalMinutes = 5,
                    onlineOnly = true
                )

                // 5 minutes * 60 seconds * 20 ticks = 6000 ticks
                snapshotConfig.getIntervalTicks() shouldBe 6000L
            }

            it("handles larger intervals") {
                val snapshotConfig = SnapshotConfig(
                    enabled = true,
                    intervalMinutes = 15,
                    onlineOnly = true
                )

                // 15 minutes * 60 seconds * 20 ticks = 18000 ticks
                snapshotConfig.getIntervalTicks() shouldBe 18000L
            }

            it("handles 1 minute interval") {
                val snapshotConfig = SnapshotConfig(
                    enabled = true,
                    intervalMinutes = 1,
                    onlineOnly = true
                )

                // 1 minute * 60 seconds * 20 ticks = 1200 ticks
                snapshotConfig.getIntervalTicks() shouldBe 1200L
            }
        }
    }

    describe("RetentionConfig") {

        describe("getCleanupIntervalTicks") {

            it("converts hours to ticks") {
                val retentionConfig = RetentionConfig(
                    enabled = true,
                    cleanupIntervalHours = 24,
                    tiers = RetentionTiers(7, 30, 180, 5)
                )

                // 24 hours * 60 minutes * 60 seconds * 20 ticks = 1,728,000 ticks
                retentionConfig.getCleanupIntervalTicks() shouldBe 1728000L
            }

            it("handles shorter intervals") {
                val retentionConfig = RetentionConfig(
                    enabled = true,
                    cleanupIntervalHours = 6,
                    tiers = RetentionTiers(7, 30, 180, 5)
                )

                // 6 hours * 60 * 60 * 20 = 432,000 ticks
                retentionConfig.getCleanupIntervalTicks() shouldBe 432000L
            }
        }
    }

    describe("RetentionTiers") {

        describe("getTotalRetentionDays") {

            it("calculates total retention from daily days and weekly years") {
                val tiers = RetentionTiers(
                    rawDays = 7,
                    hourlyDays = 30,
                    dailyDays = 180,
                    weeklyYears = 5
                )

                // dailyDays + (weeklyYears * 365) = 180 + (5 * 365) = 180 + 1825 = 2005
                tiers.getTotalRetentionDays() shouldBe 2005
            }

            it("handles different tier configurations") {
                val tiers = RetentionTiers(
                    rawDays = 14,
                    hourlyDays = 60,
                    dailyDays = 365,
                    weeklyYears = 10
                )

                // 365 + (10 * 365) = 365 + 3650 = 4015
                tiers.getTotalRetentionDays() shouldBe 4015
            }

            it("handles minimal configuration") {
                val tiers = RetentionTiers(
                    rawDays = 1,
                    hourlyDays = 1,
                    dailyDays = 1,
                    weeklyYears = 1
                )

                // 1 + (1 * 365) = 366
                tiers.getTotalRetentionDays() shouldBe 366
            }
        }

        describe("fromConfig") {

            it("loads tiered retention settings") {
                val config = mockk<FileConfiguration>()
                every { config.getInt("progression-tracking.retention.tiers.raw-days", 7) } returns 7
                every { config.getInt("progression-tracking.retention.tiers.hourly-days", 30) } returns 30
                every { config.getInt("progression-tracking.retention.tiers.daily-days", 180) } returns 180
                every { config.getInt("progression-tracking.retention.tiers.weekly-years", 5) } returns 5

                val tiers = RetentionTiers.fromConfig(config)

                tiers.rawDays shouldBe 7
                tiers.hourlyDays shouldBe 30
                tiers.dailyDays shouldBe 180
                tiers.weeklyYears shouldBe 5
            }
        }
    }
})
