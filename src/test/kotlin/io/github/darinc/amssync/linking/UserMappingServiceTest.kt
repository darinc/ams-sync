package io.github.darinc.amssync.linking

import io.github.darinc.amssync.AMSSyncPlugin
import io.github.darinc.amssync.exceptions.DuplicateMappingException
import io.github.darinc.amssync.exceptions.InvalidDiscordIdException
import io.github.darinc.amssync.exceptions.MappingNotFoundException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.FileConfiguration
import java.util.logging.Logger

class UserMappingServiceTest : DescribeSpec({

    // Valid Discord snowflake IDs for testing
    val validDiscordId1 = "123456789012345678"
    val validDiscordId2 = "987654321098765432"
    val validDiscordId3 = "111222333444555666"

    fun createMockedPlugin(): AMSSyncPlugin {
        val plugin = mockk<AMSSyncPlugin>(relaxed = true)
        val config = mockk<FileConfiguration>(relaxed = true)
        val logger = mockk<Logger>(relaxed = true)

        every { plugin.config } returns config
        every { plugin.logger } returns logger

        // Default: no existing mappings section
        every { config.getConfigurationSection("user-mappings") } returns null

        return plugin
    }

    describe("UserMappingService") {

        describe("addMapping") {

            it("adds a valid mapping to both maps") {
                val plugin = createMockedPlugin()
                val service = UserMappingService(plugin)

                service.addMapping(validDiscordId1, "Player1")

                service.getMinecraftUsername(validDiscordId1) shouldBe "Player1"
                service.getDiscordId("Player1") shouldBe validDiscordId1
            }

            it("throws InvalidDiscordIdException for invalid Discord ID") {
                val plugin = createMockedPlugin()
                val service = UserMappingService(plugin)

                val exception = shouldThrow<InvalidDiscordIdException> {
                    service.addMapping("invalid", "Player1")
                }
                exception.discordId shouldBe "invalid"
            }

            it("throws InvalidDiscordIdException for too short Discord ID") {
                val plugin = createMockedPlugin()
                val service = UserMappingService(plugin)

                shouldThrow<InvalidDiscordIdException> {
                    service.addMapping("1234567890123456", "Player1") // 16 digits
                }
            }

            it("throws InvalidDiscordIdException for too long Discord ID") {
                val plugin = createMockedPlugin()
                val service = UserMappingService(plugin)

                shouldThrow<InvalidDiscordIdException> {
                    service.addMapping("12345678901234567890", "Player1") // 20 digits
                }
            }

            it("replaces existing mapping for same Discord ID when allowReplace is true") {
                val plugin = createMockedPlugin()
                val service = UserMappingService(plugin)

                service.addMapping(validDiscordId1, "Player1")
                service.addMapping(validDiscordId1, "Player2") // Replace

                service.getMinecraftUsername(validDiscordId1) shouldBe "Player2"
                service.getDiscordId("Player1").shouldBeNull() // Old mapping removed
                service.getDiscordId("Player2") shouldBe validDiscordId1
            }

            it("throws DuplicateMappingException when allowReplace is false") {
                val plugin = createMockedPlugin()
                val service = UserMappingService(plugin)

                service.addMapping(validDiscordId1, "Player1")

                val exception = shouldThrow<DuplicateMappingException> {
                    service.addMapping(validDiscordId1, "Player2", allowReplace = false)
                }
                exception.discordId shouldBe validDiscordId1
                exception.existingMapping shouldBe "Player1"
            }

            it("removes old Discord ID when Minecraft username is reassigned") {
                val plugin = createMockedPlugin()
                val service = UserMappingService(plugin)

                service.addMapping(validDiscordId1, "Player1")
                service.addMapping(validDiscordId2, "Player1") // Same MC name, different Discord

                // Old Discord ID should be unmapped
                service.getMinecraftUsername(validDiscordId1).shouldBeNull()
                service.getMinecraftUsername(validDiscordId2) shouldBe "Player1"
                service.getDiscordId("Player1") shouldBe validDiscordId2
            }

            it("accepts 17-digit Discord ID") {
                val plugin = createMockedPlugin()
                val service = UserMappingService(plugin)

                service.addMapping("12345678901234567", "Player1")
                service.getMinecraftUsername("12345678901234567") shouldBe "Player1"
            }

            it("accepts 19-digit Discord ID") {
                val plugin = createMockedPlugin()
                val service = UserMappingService(plugin)

                service.addMapping("1234567890123456789", "Player1")
                service.getMinecraftUsername("1234567890123456789") shouldBe "Player1"
            }
        }

        describe("removeMappingByDiscordId") {

            it("removes existing mapping from both maps") {
                val plugin = createMockedPlugin()
                val service = UserMappingService(plugin)

                service.addMapping(validDiscordId1, "Player1")
                val removed = service.removeMappingByDiscordId(validDiscordId1)

                removed shouldBe true
                service.getMinecraftUsername(validDiscordId1).shouldBeNull()
                service.getDiscordId("Player1").shouldBeNull()
            }

            it("returns false when mapping does not exist") {
                val plugin = createMockedPlugin()
                val service = UserMappingService(plugin)

                val removed = service.removeMappingByDiscordId(validDiscordId1)
                removed shouldBe false
            }

            it("throws MappingNotFoundException when throwIfNotFound is true") {
                val plugin = createMockedPlugin()
                val service = UserMappingService(plugin)

                val exception = shouldThrow<MappingNotFoundException> {
                    service.removeMappingByDiscordId(validDiscordId1, throwIfNotFound = true)
                }
                exception.identifier shouldBe validDiscordId1
            }

            it("throws InvalidDiscordIdException for invalid ID even when removing") {
                val plugin = createMockedPlugin()
                val service = UserMappingService(plugin)

                shouldThrow<InvalidDiscordIdException> {
                    service.removeMappingByDiscordId("invalid")
                }
            }
        }

        describe("removeMappingByMinecraftUsername") {

            it("removes existing mapping from both maps") {
                val plugin = createMockedPlugin()
                val service = UserMappingService(plugin)

                service.addMapping(validDiscordId1, "Player1")
                val removed = service.removeMappingByMinecraftUsername("Player1")

                removed shouldBe true
                service.getMinecraftUsername(validDiscordId1).shouldBeNull()
                service.getDiscordId("Player1").shouldBeNull()
            }

            it("returns false when mapping does not exist") {
                val plugin = createMockedPlugin()
                val service = UserMappingService(plugin)

                val removed = service.removeMappingByMinecraftUsername("Unknown")
                removed shouldBe false
            }

            it("throws MappingNotFoundException when throwIfNotFound is true") {
                val plugin = createMockedPlugin()
                val service = UserMappingService(plugin)

                val exception = shouldThrow<MappingNotFoundException> {
                    service.removeMappingByMinecraftUsername("Unknown", throwIfNotFound = true)
                }
                exception.identifier shouldBe "Unknown"
            }
        }

        describe("getMinecraftUsername / getDiscordId") {

            it("returns null for non-existent mapping") {
                val plugin = createMockedPlugin()
                val service = UserMappingService(plugin)

                service.getMinecraftUsername(validDiscordId1).shouldBeNull()
                service.getDiscordId("Unknown").shouldBeNull()
            }

            it("returns correct values for existing mapping") {
                val plugin = createMockedPlugin()
                val service = UserMappingService(plugin)

                service.addMapping(validDiscordId1, "Player1")

                service.getMinecraftUsername(validDiscordId1) shouldBe "Player1"
                service.getDiscordId("Player1") shouldBe validDiscordId1
            }
        }

        describe("getMinecraftUsernameOrThrow / getDiscordIdOrThrow") {

            it("throws MappingNotFoundException when not found") {
                val plugin = createMockedPlugin()
                val service = UserMappingService(plugin)

                shouldThrow<MappingNotFoundException> {
                    service.getMinecraftUsernameOrThrow(validDiscordId1)
                }

                shouldThrow<MappingNotFoundException> {
                    service.getDiscordIdOrThrow("Unknown")
                }
            }

            it("returns value when found") {
                val plugin = createMockedPlugin()
                val service = UserMappingService(plugin)

                service.addMapping(validDiscordId1, "Player1")

                service.getMinecraftUsernameOrThrow(validDiscordId1) shouldBe "Player1"
                service.getDiscordIdOrThrow("Player1") shouldBe validDiscordId1
            }

            it("validates Discord ID format before lookup") {
                val plugin = createMockedPlugin()
                val service = UserMappingService(plugin)

                shouldThrow<InvalidDiscordIdException> {
                    service.getMinecraftUsernameOrThrow("invalid")
                }
            }
        }

        describe("isDiscordLinked / isMinecraftLinked") {

            it("returns false when not linked") {
                val plugin = createMockedPlugin()
                val service = UserMappingService(plugin)

                service.isDiscordLinked(validDiscordId1) shouldBe false
                service.isMinecraftLinked("Player1") shouldBe false
            }

            it("returns true when linked") {
                val plugin = createMockedPlugin()
                val service = UserMappingService(plugin)

                service.addMapping(validDiscordId1, "Player1")

                service.isDiscordLinked(validDiscordId1) shouldBe true
                service.isMinecraftLinked("Player1") shouldBe true
            }
        }

        describe("getMappingCount") {

            it("returns 0 for empty service") {
                val plugin = createMockedPlugin()
                val service = UserMappingService(plugin)

                service.getMappingCount() shouldBe 0
            }

            it("returns correct count after adding mappings") {
                val plugin = createMockedPlugin()
                val service = UserMappingService(plugin)

                service.addMapping(validDiscordId1, "Player1")
                service.addMapping(validDiscordId2, "Player2")
                service.addMapping(validDiscordId3, "Player3")

                service.getMappingCount() shouldBe 3
            }

            it("decreases count after removal") {
                val plugin = createMockedPlugin()
                val service = UserMappingService(plugin)

                service.addMapping(validDiscordId1, "Player1")
                service.addMapping(validDiscordId2, "Player2")
                service.removeMappingByDiscordId(validDiscordId1)

                service.getMappingCount() shouldBe 1
            }
        }

        describe("getAllMappings") {

            it("returns empty map for empty service") {
                val plugin = createMockedPlugin()
                val service = UserMappingService(plugin)

                service.getAllMappings() shouldBe emptyMap()
            }

            it("returns all mappings as immutable copy") {
                val plugin = createMockedPlugin()
                val service = UserMappingService(plugin)

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

            it("loads mappings from config section") {
                val plugin = createMockedPlugin()
                val config = plugin.config
                val mappingsSection = mockk<ConfigurationSection>()

                every { config.getConfigurationSection("user-mappings") } returns mappingsSection
                every { mappingsSection.getKeys(false) } returns setOf(validDiscordId1, validDiscordId2)
                every { mappingsSection.getString(validDiscordId1) } returns "Player1"
                every { mappingsSection.getString(validDiscordId2) } returns "Player2"

                val service = UserMappingService(plugin)
                service.loadMappings()

                service.getMinecraftUsername(validDiscordId1) shouldBe "Player1"
                service.getMinecraftUsername(validDiscordId2) shouldBe "Player2"
                service.getMappingCount() shouldBe 2
            }

            it("clears existing mappings before loading") {
                val plugin = createMockedPlugin()
                val service = UserMappingService(plugin)

                // Add a mapping first
                service.addMapping(validDiscordId1, "OldPlayer")

                // Load empty config
                service.loadMappings()

                service.getMappingCount() shouldBe 0
            }

            it("handles null config section gracefully") {
                val plugin = createMockedPlugin()
                every { plugin.config.getConfigurationSection("user-mappings") } returns null

                val service = UserMappingService(plugin)
                service.loadMappings()

                service.getMappingCount() shouldBe 0
            }
        }

        describe("saveMappings") {

            it("saves all mappings to config") {
                val plugin = createMockedPlugin()
                val config = plugin.config
                val service = UserMappingService(plugin)

                service.addMapping(validDiscordId1, "Player1")
                service.addMapping(validDiscordId2, "Player2")

                service.saveMappings()

                verify { plugin.reloadConfig() }
                verify { config.set("user-mappings", null) }
                verify { config.set("user-mappings.$validDiscordId1", "Player1") }
                verify { config.set("user-mappings.$validDiscordId2", "Player2") }
                verify { plugin.saveConfig() }
            }

            it("clears user-mappings section before saving") {
                val plugin = createMockedPlugin()
                val config = plugin.config
                val service = UserMappingService(plugin)

                service.saveMappings()

                verify { config.set("user-mappings", null) }
            }
        }
    }
})
