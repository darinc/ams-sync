package io.github.darinc.amssync.config

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import org.bukkit.plugin.java.JavaPlugin
import java.io.ByteArrayInputStream
import java.io.File
import java.util.logging.Logger

class ConfigMigratorTest : DescribeSpec({

    // Helper to create a mock plugin with temp directory and config resource
    fun createMockPlugin(tempDir: File, defaultConfigContent: String): JavaPlugin {
        val logger = mockk<Logger>(relaxed = true)
        val plugin = mockk<JavaPlugin>(relaxed = true)

        every { plugin.dataFolder } returns tempDir
        every { plugin.logger } returns logger
        every { plugin.getResource("config.yml") } answers {
            ByteArrayInputStream(defaultConfigContent.toByteArray())
        }

        return plugin
    }

    val minimalDefaultConfig = """
        # Configuration version
        config-version: 1

        # Settings
        discord:
          token: "YOUR_BOT_TOKEN_HERE"
          guild-id: "YOUR_GUILD_ID_HERE"
          retry:
            enabled: true
            max-attempts: 5

        # User mappings
        user-mappings:
    """.trimIndent()

    describe("ConfigMigrator") {

        describe("migrateIfNeeded") {

            it("returns FreshInstall when config.yml does not exist") {
                val tempDir = createTempDir()
                try {
                    val plugin = createMockPlugin(tempDir, minimalDefaultConfig)
                    val migrator = ConfigMigrator(plugin, plugin.logger)

                    val result = migrator.migrateIfNeeded()

                    result.shouldBeInstanceOf<ConfigMigrator.MigrationResult.FreshInstall>()
                } finally {
                    tempDir.deleteRecursively()
                }
            }

            it("returns UpToDate when config-version matches current") {
                val tempDir = createTempDir()
                try {
                    // Create config with current version (1)
                    val configFile = File(tempDir, "config.yml")
                    configFile.writeText("""
                        config-version: 1
                        discord:
                          token: "my-token"
                    """.trimIndent())

                    val plugin = createMockPlugin(tempDir, minimalDefaultConfig)
                    val migrator = ConfigMigrator(plugin, plugin.logger)

                    val result = migrator.migrateIfNeeded()

                    result.shouldBeInstanceOf<ConfigMigrator.MigrationResult.UpToDate>()
                } finally {
                    tempDir.deleteRecursively()
                }
            }

            it("returns Migrated with details when version is older") {
                val tempDir = createTempDir()
                try {
                    // Create config with old version (0)
                    val configFile = File(tempDir, "config.yml")
                    configFile.writeText("""
                        config-version: 0
                        discord:
                          token: "user-token"
                          guild-id: "user-guild"
                    """.trimIndent())

                    val plugin = createMockPlugin(tempDir, minimalDefaultConfig)
                    val migrator = ConfigMigrator(plugin, plugin.logger)

                    val result = migrator.migrateIfNeeded()

                    result.shouldBeInstanceOf<ConfigMigrator.MigrationResult.Migrated>()
                    val migrated = result as ConfigMigrator.MigrationResult.Migrated
                    migrated.fromVersion shouldBe 0
                    migrated.toVersion shouldBe 1
                } finally {
                    tempDir.deleteRecursively()
                }
            }

            it("returns Failed when config file cannot be loaded") {
                val tempDir = createTempDir()
                try {
                    // Create an invalid YAML file
                    val configFile = File(tempDir, "config.yml")
                    configFile.writeText("invalid: yaml: content: [")

                    val plugin = createMockPlugin(tempDir, minimalDefaultConfig)
                    val migrator = ConfigMigrator(plugin, plugin.logger)

                    val result = migrator.migrateIfNeeded()

                    result.shouldBeInstanceOf<ConfigMigrator.MigrationResult.Failed>()
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        }

        describe("migration process") {

            it("creates timestamped backup file") {
                val tempDir = createTempDir()
                try {
                    val configFile = File(tempDir, "config.yml")
                    configFile.writeText("""
                        config-version: 0
                        discord:
                          token: "old-token"
                    """.trimIndent())

                    val plugin = createMockPlugin(tempDir, minimalDefaultConfig)
                    val migrator = ConfigMigrator(plugin, plugin.logger)

                    val result = migrator.migrateIfNeeded()

                    result.shouldBeInstanceOf<ConfigMigrator.MigrationResult.Migrated>()

                    // Check for backup file
                    val backupFiles = tempDir.listFiles { _, name -> name.startsWith("config-backup-") }
                    backupFiles.shouldNotBeNull()
                    backupFiles.shouldNotBeEmpty()
                } finally {
                    tempDir.deleteRecursively()
                }
            }

            it("preserves user values for existing keys") {
                val tempDir = createTempDir()
                try {
                    val configFile = File(tempDir, "config.yml")
                    configFile.writeText("""
                        config-version: 0
                        discord:
                          token: "my-custom-token"
                          guild-id: "123456789012345678"
                    """.trimIndent())

                    val plugin = createMockPlugin(tempDir, minimalDefaultConfig)
                    val migrator = ConfigMigrator(plugin, plugin.logger)

                    migrator.migrateIfNeeded()

                    // Read the migrated config
                    val migratedContent = configFile.readText()

                    // User values should be preserved
                    migratedContent shouldContain "my-custom-token"
                    migratedContent shouldContain "123456789012345678"
                } finally {
                    tempDir.deleteRecursively()
                }
            }

            it("adds new keys from default config") {
                val tempDir = createTempDir()
                try {
                    // User config missing retry settings
                    val configFile = File(tempDir, "config.yml")
                    configFile.writeText("""
                        config-version: 0
                        discord:
                          token: "user-token"
                    """.trimIndent())

                    val plugin = createMockPlugin(tempDir, minimalDefaultConfig)
                    val migrator = ConfigMigrator(plugin, plugin.logger)

                    val result = migrator.migrateIfNeeded()

                    result.shouldBeInstanceOf<ConfigMigrator.MigrationResult.Migrated>()
                    val migrated = result as ConfigMigrator.MigrationResult.Migrated

                    // Should have added new keys
                    migrated.addedKeys.shouldNotBeEmpty()
                } finally {
                    tempDir.deleteRecursively()
                }
            }

            it("preserves user-mappings section") {
                val tempDir = createTempDir()
                try {
                    val configFile = File(tempDir, "config.yml")
                    configFile.writeText("""
                        config-version: 0
                        discord:
                          token: "user-token"
                        user-mappings:
                          "123456789012345678": "Steve"
                          "987654321098765432": "Alex"
                    """.trimIndent())

                    val plugin = createMockPlugin(tempDir, minimalDefaultConfig)
                    val migrator = ConfigMigrator(plugin, plugin.logger)

                    migrator.migrateIfNeeded()

                    val migratedContent = configFile.readText()

                    migratedContent shouldContain "user-mappings:"
                    migratedContent shouldContain "123456789012345678"
                    migratedContent shouldContain "Steve"
                    migratedContent shouldContain "987654321098765432"
                    migratedContent shouldContain "Alex"
                } finally {
                    tempDir.deleteRecursively()
                }
            }

            it("updates config-version to current") {
                val tempDir = createTempDir()
                try {
                    val configFile = File(tempDir, "config.yml")
                    configFile.writeText("""
                        config-version: 0
                        discord:
                          token: "user-token"
                    """.trimIndent())

                    val plugin = createMockPlugin(tempDir, minimalDefaultConfig)
                    val migrator = ConfigMigrator(plugin, plugin.logger)

                    migrator.migrateIfNeeded()

                    val migratedContent = configFile.readText()
                    migratedContent shouldContain "config-version: 1"
                } finally {
                    tempDir.deleteRecursively()
                }
            }

            it("identifies added keys in result") {
                val tempDir = createTempDir()
                try {
                    // User config missing most settings
                    val configFile = File(tempDir, "config.yml")
                    configFile.writeText("""
                        config-version: 0
                        discord:
                          token: "user-token"
                    """.trimIndent())

                    val plugin = createMockPlugin(tempDir, minimalDefaultConfig)
                    val migrator = ConfigMigrator(plugin, plugin.logger)

                    val result = migrator.migrateIfNeeded()

                    result.shouldBeInstanceOf<ConfigMigrator.MigrationResult.Migrated>()
                    val migrated = result as ConfigMigrator.MigrationResult.Migrated

                    // Should report added keys
                    migrated.addedKeys shouldContain "discord.retry.enabled"
                    migrated.addedKeys shouldContain "discord.retry.max-attempts"
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        }

        describe("MigrationResult sealed class") {

            it("FreshInstall is singleton object") {
                val result1 = ConfigMigrator.MigrationResult.FreshInstall
                val result2 = ConfigMigrator.MigrationResult.FreshInstall

                (result1 === result2) shouldBe true
            }

            it("UpToDate is singleton object") {
                val result1 = ConfigMigrator.MigrationResult.UpToDate
                val result2 = ConfigMigrator.MigrationResult.UpToDate

                (result1 === result2) shouldBe true
            }

            it("Migrated contains fromVersion, toVersion, addedKeys, backupPath") {
                val migrated = ConfigMigrator.MigrationResult.Migrated(
                    fromVersion = 0,
                    toVersion = 1,
                    addedKeys = listOf("key1", "key2"),
                    backupPath = "config-backup-20231201-120000.yml"
                )

                migrated.fromVersion shouldBe 0
                migrated.toVersion shouldBe 1
                migrated.addedKeys shouldBe listOf("key1", "key2")
                migrated.backupPath shouldBe "config-backup-20231201-120000.yml"
            }

            it("Failed contains reason and optional exception") {
                val exception = RuntimeException("test error")
                val failedWithException = ConfigMigrator.MigrationResult.Failed(
                    reason = "Migration error",
                    exception = exception
                )
                val failedWithoutException = ConfigMigrator.MigrationResult.Failed(
                    reason = "Some reason"
                )

                failedWithException.reason shouldBe "Migration error"
                failedWithException.exception shouldBe exception
                failedWithoutException.reason shouldBe "Some reason"
                failedWithoutException.exception.shouldBeNull()
            }
        }

        describe("YAML value formatting") {

            it("preserves boolean values correctly") {
                val tempDir = createTempDir()
                try {
                    val configFile = File(tempDir, "config.yml")
                    configFile.writeText("""
                        config-version: 0
                        discord:
                          token: "test"
                          retry:
                            enabled: false
                    """.trimIndent())

                    val defaultConfig = """
                        config-version: 1
                        discord:
                          token: "YOUR_BOT_TOKEN_HERE"
                          retry:
                            enabled: true
                        user-mappings:
                    """.trimIndent()

                    val plugin = createMockPlugin(tempDir, defaultConfig)
                    val migrator = ConfigMigrator(plugin, plugin.logger)

                    migrator.migrateIfNeeded()

                    val migratedContent = configFile.readText()
                    // User value (false) should be preserved, not default (true)
                    migratedContent shouldContain "enabled: false"
                } finally {
                    tempDir.deleteRecursively()
                }
            }

            it("preserves numeric values correctly") {
                val tempDir = createTempDir()
                try {
                    val configFile = File(tempDir, "config.yml")
                    configFile.writeText("""
                        config-version: 0
                        discord:
                          token: "test"
                          retry:
                            max-attempts: 10
                    """.trimIndent())

                    val defaultConfig = """
                        config-version: 1
                        discord:
                          token: "YOUR_BOT_TOKEN_HERE"
                          retry:
                            max-attempts: 5
                        user-mappings:
                    """.trimIndent()

                    val plugin = createMockPlugin(tempDir, defaultConfig)
                    val migrator = ConfigMigrator(plugin, plugin.logger)

                    migrator.migrateIfNeeded()

                    val migratedContent = configFile.readText()
                    // User value (10) should be preserved, not default (5)
                    migratedContent shouldContain "max-attempts: 10"
                } finally {
                    tempDir.deleteRecursively()
                }
            }

            it("handles empty user-mappings section") {
                val tempDir = createTempDir()
                try {
                    val configFile = File(tempDir, "config.yml")
                    configFile.writeText("""
                        config-version: 0
                        discord:
                          token: "test"
                    """.trimIndent())

                    val plugin = createMockPlugin(tempDir, minimalDefaultConfig)
                    val migrator = ConfigMigrator(plugin, plugin.logger)

                    migrator.migrateIfNeeded()

                    val migratedContent = configFile.readText()
                    // Should have user-mappings section
                    migratedContent shouldContain "user-mappings:"
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        }

        describe("edge cases") {

            it("handles config with no config-version key") {
                val tempDir = createTempDir()
                try {
                    val configFile = File(tempDir, "config.yml")
                    configFile.writeText("""
                        discord:
                          token: "test"
                    """.trimIndent())

                    val plugin = createMockPlugin(tempDir, minimalDefaultConfig)
                    val migrator = ConfigMigrator(plugin, plugin.logger)

                    val result = migrator.migrateIfNeeded()

                    // Should treat missing version as 0 and migrate
                    result.shouldBeInstanceOf<ConfigMigrator.MigrationResult.Migrated>()
                } finally {
                    tempDir.deleteRecursively()
                }
            }

            it("handles config with future version") {
                val tempDir = createTempDir()
                try {
                    val configFile = File(tempDir, "config.yml")
                    configFile.writeText("""
                        config-version: 999
                        discord:
                          token: "test"
                    """.trimIndent())

                    val plugin = createMockPlugin(tempDir, minimalDefaultConfig)
                    val migrator = ConfigMigrator(plugin, plugin.logger)

                    val result = migrator.migrateIfNeeded()

                    // Future version should be treated as up-to-date
                    result.shouldBeInstanceOf<ConfigMigrator.MigrationResult.UpToDate>()
                } finally {
                    tempDir.deleteRecursively()
                }
            }

            it("handles empty config file") {
                val tempDir = createTempDir()
                try {
                    val configFile = File(tempDir, "config.yml")
                    configFile.writeText("")

                    val plugin = createMockPlugin(tempDir, minimalDefaultConfig)
                    val migrator = ConfigMigrator(plugin, plugin.logger)

                    val result = migrator.migrateIfNeeded()

                    // Empty config should trigger migration (version 0)
                    result.shouldBeInstanceOf<ConfigMigrator.MigrationResult.Migrated>()
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        }
    }
})
