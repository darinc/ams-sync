package io.github.darinc.amssync.linking

import io.github.darinc.amssync.exceptions.DuplicateMappingException
import io.github.darinc.amssync.exceptions.InvalidDiscordIdException
import io.github.darinc.amssync.exceptions.MappingNotFoundException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.mockk.mockk
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.logging.Logger

class UserMappingServiceTest : DescribeSpec({

    // Valid Discord snowflake IDs for testing
    val validDiscordId1 = "123456789012345678"
    val validDiscordId2 = "987654321098765432"
    val validDiscordId3 = "111222333444555666"

    // Create a service with a temporary config file
    fun createService(): Pair<UserMappingService, File> {
        val tempFile = File.createTempFile("test-config", ".yml")
        tempFile.deleteOnExit()
        val logger = mockk<Logger>(relaxed = true)
        return UserMappingService(tempFile, logger) to tempFile
    }

    // Create a service with just a mock logger (temp file auto-created)
    fun createServiceSimple(): UserMappingService {
        val (service, _) = createService()
        return service
    }

    describe("UserMappingService") {

        describe("addMapping") {

            it("adds a valid mapping to both maps") {
                val service = createServiceSimple()

                service.addMapping(validDiscordId1, "Player1")

                service.getMinecraftUsername(validDiscordId1) shouldBe "Player1"
                service.getDiscordId("Player1") shouldBe validDiscordId1
            }

            it("throws InvalidDiscordIdException for invalid Discord ID") {
                val service = createServiceSimple()

                val exception = shouldThrow<InvalidDiscordIdException> {
                    service.addMapping("invalid", "Player1")
                }
                exception.discordId shouldBe "invalid"
            }

            it("throws InvalidDiscordIdException for too short Discord ID") {
                val service = createServiceSimple()

                shouldThrow<InvalidDiscordIdException> {
                    service.addMapping("1234567890123456", "Player1") // 16 digits
                }
            }

            it("throws InvalidDiscordIdException for too long Discord ID") {
                val service = createServiceSimple()

                shouldThrow<InvalidDiscordIdException> {
                    service.addMapping("12345678901234567890", "Player1") // 20 digits
                }
            }

            it("replaces existing mapping for same Discord ID when allowReplace is true") {
                val service = createServiceSimple()

                service.addMapping(validDiscordId1, "Player1")
                service.addMapping(validDiscordId1, "Player2") // Replace

                service.getMinecraftUsername(validDiscordId1) shouldBe "Player2"
                service.getDiscordId("Player1").shouldBeNull() // Old mapping removed
                service.getDiscordId("Player2") shouldBe validDiscordId1
            }

            it("throws DuplicateMappingException when allowReplace is false") {
                val service = createServiceSimple()

                service.addMapping(validDiscordId1, "Player1")

                val exception = shouldThrow<DuplicateMappingException> {
                    service.addMapping(validDiscordId1, "Player2", allowReplace = false)
                }
                exception.discordId shouldBe validDiscordId1
                exception.existingMapping shouldBe "Player1"
            }

            it("removes old Discord ID when Minecraft username is reassigned") {
                val service = createServiceSimple()

                service.addMapping(validDiscordId1, "Player1")
                service.addMapping(validDiscordId2, "Player1") // Same MC name, different Discord

                // Old Discord ID should be unmapped
                service.getMinecraftUsername(validDiscordId1).shouldBeNull()
                service.getMinecraftUsername(validDiscordId2) shouldBe "Player1"
                service.getDiscordId("Player1") shouldBe validDiscordId2
            }

            it("accepts 17-digit Discord ID") {
                val service = createServiceSimple()

                service.addMapping("12345678901234567", "Player1")
                service.getMinecraftUsername("12345678901234567") shouldBe "Player1"
            }

            it("accepts 19-digit Discord ID") {
                val service = createServiceSimple()

                service.addMapping("1234567890123456789", "Player1")
                service.getMinecraftUsername("1234567890123456789") shouldBe "Player1"
            }
        }

        describe("removeMappingByDiscordId") {

            it("removes existing mapping from both maps") {
                val service = createServiceSimple()

                service.addMapping(validDiscordId1, "Player1")
                val removed = service.removeMappingByDiscordId(validDiscordId1)

                removed shouldBe true
                service.getMinecraftUsername(validDiscordId1).shouldBeNull()
                service.getDiscordId("Player1").shouldBeNull()
            }

            it("returns false when mapping does not exist") {
                val service = createServiceSimple()

                val removed = service.removeMappingByDiscordId(validDiscordId1)
                removed shouldBe false
            }

            it("throws MappingNotFoundException when throwIfNotFound is true") {
                val service = createServiceSimple()

                val exception = shouldThrow<MappingNotFoundException> {
                    service.removeMappingByDiscordId(validDiscordId1, throwIfNotFound = true)
                }
                exception.identifier shouldBe validDiscordId1
            }

            it("throws InvalidDiscordIdException for invalid ID even when removing") {
                val service = createServiceSimple()

                shouldThrow<InvalidDiscordIdException> {
                    service.removeMappingByDiscordId("invalid")
                }
            }
        }

        describe("removeMappingByMinecraftUsername") {

            it("removes existing mapping from both maps") {
                val service = createServiceSimple()

                service.addMapping(validDiscordId1, "Player1")
                val removed = service.removeMappingByMinecraftUsername("Player1")

                removed shouldBe true
                service.getMinecraftUsername(validDiscordId1).shouldBeNull()
                service.getDiscordId("Player1").shouldBeNull()
            }

            it("returns false when mapping does not exist") {
                val service = createServiceSimple()

                val removed = service.removeMappingByMinecraftUsername("Unknown")
                removed shouldBe false
            }

            it("throws MappingNotFoundException when throwIfNotFound is true") {
                val service = createServiceSimple()

                val exception = shouldThrow<MappingNotFoundException> {
                    service.removeMappingByMinecraftUsername("Unknown", throwIfNotFound = true)
                }
                exception.identifier shouldBe "Unknown"
            }
        }

        describe("getMinecraftUsername / getDiscordId") {

            it("returns null for non-existent mapping") {
                val service = createServiceSimple()

                service.getMinecraftUsername(validDiscordId1).shouldBeNull()
                service.getDiscordId("Unknown").shouldBeNull()
            }

            it("returns correct values for existing mapping") {
                val service = createServiceSimple()

                service.addMapping(validDiscordId1, "Player1")

                service.getMinecraftUsername(validDiscordId1) shouldBe "Player1"
                service.getDiscordId("Player1") shouldBe validDiscordId1
            }
        }

        describe("getMinecraftUsernameOrThrow / getDiscordIdOrThrow") {

            it("throws MappingNotFoundException when not found") {
                val service = createServiceSimple()

                shouldThrow<MappingNotFoundException> {
                    service.getMinecraftUsernameOrThrow(validDiscordId1)
                }

                shouldThrow<MappingNotFoundException> {
                    service.getDiscordIdOrThrow("Unknown")
                }
            }

            it("returns value when found") {
                val service = createServiceSimple()

                service.addMapping(validDiscordId1, "Player1")

                service.getMinecraftUsernameOrThrow(validDiscordId1) shouldBe "Player1"
                service.getDiscordIdOrThrow("Player1") shouldBe validDiscordId1
            }

            it("validates Discord ID format before lookup") {
                val service = createServiceSimple()

                shouldThrow<InvalidDiscordIdException> {
                    service.getMinecraftUsernameOrThrow("invalid")
                }
            }
        }

        describe("isDiscordLinked / isMinecraftLinked") {

            it("returns false when not linked") {
                val service = createServiceSimple()

                service.isDiscordLinked(validDiscordId1) shouldBe false
                service.isMinecraftLinked("Player1") shouldBe false
            }

            it("returns true when linked") {
                val service = createServiceSimple()

                service.addMapping(validDiscordId1, "Player1")

                service.isDiscordLinked(validDiscordId1) shouldBe true
                service.isMinecraftLinked("Player1") shouldBe true
            }
        }

        describe("getMappingCount") {

            it("returns 0 for empty service") {
                val service = createServiceSimple()

                service.getMappingCount() shouldBe 0
            }

            it("returns correct count after adding mappings") {
                val service = createServiceSimple()

                service.addMapping(validDiscordId1, "Player1")
                service.addMapping(validDiscordId2, "Player2")
                service.addMapping(validDiscordId3, "Player3")

                service.getMappingCount() shouldBe 3
            }

            it("decreases count after removal") {
                val service = createServiceSimple()

                service.addMapping(validDiscordId1, "Player1")
                service.addMapping(validDiscordId2, "Player2")
                service.removeMappingByDiscordId(validDiscordId1)

                service.getMappingCount() shouldBe 1
            }
        }

        describe("getAllMappings") {

            it("returns empty map for empty service") {
                val service = createServiceSimple()

                service.getAllMappings() shouldBe emptyMap()
            }

            it("returns all mappings as immutable copy") {
                val service = createServiceSimple()

                service.addMapping(validDiscordId1, "Player1")
                service.addMapping(validDiscordId2, "Player2")

                val mappings = service.getAllMappings()
                mappings shouldBe mapOf(
                    validDiscordId1 to "Player1",
                    validDiscordId2 to "Player2"
                )
            }
        }

        describe("loadMappings") {

            it("loads mappings from config file") {
                val (service, configFile) = createService()

                // Write test config to file
                val config = YamlConfiguration()
                config.set("user-mappings.$validDiscordId1", "Player1")
                config.set("user-mappings.$validDiscordId2", "Player2")
                config.save(configFile)

                service.loadMappings()

                service.getMinecraftUsername(validDiscordId1) shouldBe "Player1"
                service.getMinecraftUsername(validDiscordId2) shouldBe "Player2"
                service.getMappingCount() shouldBe 2
            }

            it("clears existing mappings before loading") {
                val (service, _) = createService()

                // Add a mapping first
                service.addMapping(validDiscordId1, "OldPlayer")

                // Load empty config (file has no user-mappings section)
                service.loadMappings()

                service.getMappingCount() shouldBe 0
            }

            it("handles missing config file gracefully") {
                val tempFile = File.createTempFile("test-config", ".yml")
                tempFile.delete() // Delete the file so it doesn't exist
                val logger = mockk<Logger>(relaxed = true)
                val service = UserMappingService(tempFile, logger)

                service.loadMappings()

                service.getMappingCount() shouldBe 0
            }

            it("handles missing user-mappings section gracefully") {
                val (service, configFile) = createService()

                // Write config without user-mappings section
                val config = YamlConfiguration()
                config.set("some-other-key", "value")
                config.save(configFile)

                service.loadMappings()

                service.getMappingCount() shouldBe 0
            }
        }

        describe("saveMappings") {

            it("saves all mappings to config file") {
                val (service, configFile) = createService()

                service.addMapping(validDiscordId1, "Player1")
                service.addMapping(validDiscordId2, "Player2")

                service.saveMappings()

                // Verify file contents
                val savedConfig = YamlConfiguration.loadConfiguration(configFile)
                savedConfig.getString("user-mappings.$validDiscordId1") shouldBe "Player1"
                savedConfig.getString("user-mappings.$validDiscordId2") shouldBe "Player2"
            }

            it("clears user-mappings section before saving") {
                val (service, configFile) = createService()

                // Write existing mapping to file
                val config = YamlConfiguration()
                config.set("user-mappings.$validDiscordId1", "OldPlayer")
                config.save(configFile)

                // Add different mapping and save
                service.addMapping(validDiscordId2, "NewPlayer")
                service.saveMappings()

                // Verify old mapping is gone
                val savedConfig = YamlConfiguration.loadConfiguration(configFile)
                savedConfig.getString("user-mappings.$validDiscordId1").shouldBeNull()
                savedConfig.getString("user-mappings.$validDiscordId2") shouldBe "NewPlayer"
            }

            it("preserves other config sections when saving") {
                val (service, configFile) = createService()

                // Write config with other sections
                val config = YamlConfiguration()
                config.set("other-section.key", "value")
                config.set("user-mappings.$validDiscordId1", "OldPlayer")
                config.save(configFile)

                // Add mapping and save
                service.addMapping(validDiscordId2, "NewPlayer")
                service.saveMappings()

                // Verify other section is preserved
                val savedConfig = YamlConfiguration.loadConfiguration(configFile)
                savedConfig.getString("other-section.key") shouldBe "value"
            }
        }
    }
})
