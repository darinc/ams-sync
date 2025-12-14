package io.github.darinc.amssync.audit

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.io.File
import java.util.logging.Logger

class AuditLoggerTest : DescribeSpec({

    // Helper to create a test setup with temp directory and mock logger
    fun createTestSetup(): Pair<File, Logger> {
        val tempDir = File.createTempFile("audit-test", "").parentFile
            .resolve("audit-test-${System.currentTimeMillis()}")
        tempDir.mkdirs()
        val logger = mockk<Logger>(relaxed = true)
        return Pair(tempDir, logger)
    }

    describe("AuditLogger") {

        describe("logAdminAction") {

            it("logs to plugin logger at INFO level") {
                val (tempDir, logger) = createTestSetup()
                try {
                    val auditLogger = AuditLogger(tempDir, logger)

                    auditLogger.logAdminAction(
                        action = AuditAction.LINK_USER,
                        actor = "TestAdmin",
                        actorType = ActorType.CONSOLE
                    )

                    verify { logger.info(any<String>()) }
                } finally {
                    tempDir.deleteRecursively()
                }
            }

            it("includes action display name in message") {
                val (tempDir, logger) = createTestSetup()
                try {
                    val messageSlot = slot<String>()
                    every { logger.info(capture(messageSlot)) } returns Unit

                    val auditLogger = AuditLogger(tempDir, logger)
                    auditLogger.logAdminAction(
                        action = AuditAction.LINK_USER,
                        actor = "Admin",
                        actorType = ActorType.CONSOLE
                    )

                    messageSlot.captured shouldContain "Link User"
                } finally {
                    tempDir.deleteRecursively()
                }
            }

            it("includes actor and actor type") {
                val (tempDir, logger) = createTestSetup()
                try {
                    val messageSlot = slot<String>()
                    every { logger.info(capture(messageSlot)) } returns Unit

                    val auditLogger = AuditLogger(tempDir, logger)
                    auditLogger.logAdminAction(
                        action = AuditAction.LINK_USER,
                        actor = "TestAdmin#1234",
                        actorType = ActorType.DISCORD_USER
                    )

                    messageSlot.captured shouldContain "TestAdmin#1234"
                    messageSlot.captured shouldContain "DISCORD_USER"
                } finally {
                    tempDir.deleteRecursively()
                }
            }

            it("includes target when provided") {
                val (tempDir, logger) = createTestSetup()
                try {
                    val messageSlot = slot<String>()
                    every { logger.info(capture(messageSlot)) } returns Unit

                    val auditLogger = AuditLogger(tempDir, logger)
                    auditLogger.logAdminAction(
                        action = AuditAction.LINK_USER,
                        actor = "Admin",
                        actorType = ActorType.CONSOLE,
                        target = "Steve"
                    )

                    messageSlot.captured shouldContain "Steve"
                } finally {
                    tempDir.deleteRecursively()
                }
            }

            it("includes details when provided") {
                val (tempDir, logger) = createTestSetup()
                try {
                    val messageSlot = slot<String>()
                    every { logger.info(capture(messageSlot)) } returns Unit

                    val auditLogger = AuditLogger(tempDir, logger)
                    auditLogger.logAdminAction(
                        action = AuditAction.LINK_USER,
                        actor = "Admin",
                        actorType = ActorType.CONSOLE,
                        details = mapOf("discordId" to "123456789", "reason" to "test")
                    )

                    messageSlot.captured shouldContain "discordId=123456789"
                    messageSlot.captured shouldContain "reason=test"
                } finally {
                    tempDir.deleteRecursively()
                }
            }

            it("uses + icon for success") {
                val (tempDir, logger) = createTestSetup()
                try {
                    val messageSlot = slot<String>()
                    every { logger.info(capture(messageSlot)) } returns Unit

                    val auditLogger = AuditLogger(tempDir, logger)
                    auditLogger.logAdminAction(
                        action = AuditAction.LINK_USER,
                        actor = "Admin",
                        actorType = ActorType.CONSOLE,
                        success = true
                    )

                    messageSlot.captured shouldContain "[+]"
                } finally {
                    tempDir.deleteRecursively()
                }
            }

            it("uses - icon for failure") {
                val (tempDir, logger) = createTestSetup()
                try {
                    val messageSlot = slot<String>()
                    every { logger.info(capture(messageSlot)) } returns Unit

                    val auditLogger = AuditLogger(tempDir, logger)
                    auditLogger.logAdminAction(
                        action = AuditAction.PERMISSION_DENIED,
                        actor = "BadActor",
                        actorType = ActorType.MINECRAFT_PLAYER,
                        success = false
                    )

                    messageSlot.captured shouldContain "[-]"
                } finally {
                    tempDir.deleteRecursively()
                }
            }

            it("writes JSON line to audit file") {
                val (tempDir, logger) = createTestSetup()
                try {
                    val auditLogger = AuditLogger(tempDir, logger)

                    auditLogger.logAdminAction(
                        action = AuditAction.LINK_USER,
                        actor = "TestAdmin",
                        actorType = ActorType.CONSOLE,
                        target = "Steve"
                    )

                    val auditFile = File(tempDir, "audit.log")
                    auditFile.exists() shouldBe true

                    val content = auditFile.readText()
                    content shouldContain "\"action\":\"LINK_USER\""
                    content shouldContain "\"actor\":\"TestAdmin\""
                    content shouldContain "\"target\":\"Steve\""
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        }

        describe("logSecurityEvent") {

            it("converts SecurityEvent to AuditAction") {
                val (tempDir, logger) = createTestSetup()
                try {
                    val messageSlot = slot<String>()
                    every { logger.info(capture(messageSlot)) } returns Unit

                    val auditLogger = AuditLogger(tempDir, logger)
                    auditLogger.logSecurityEvent(
                        event = SecurityEvent.PERMISSION_DENIED,
                        actor = "UnauthorizedUser",
                        actorType = ActorType.DISCORD_USER
                    )

                    messageSlot.captured shouldContain "Permission Denied"
                } finally {
                    tempDir.deleteRecursively()
                }
            }

            it("logs with success=false") {
                val (tempDir, logger) = createTestSetup()
                try {
                    val messageSlot = slot<String>()
                    every { logger.info(capture(messageSlot)) } returns Unit

                    val auditLogger = AuditLogger(tempDir, logger)
                    auditLogger.logSecurityEvent(
                        event = SecurityEvent.RATE_LIMITED,
                        actor = "SpamUser",
                        actorType = ActorType.DISCORD_USER
                    )

                    messageSlot.captured shouldContain "[-]"

                    val auditFile = File(tempDir, "audit.log")
                    val content = auditFile.readText()
                    content shouldContain "\"success\":false"
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        }

        describe("JSON formatting") {

            it("produces valid JSON structure") {
                val (tempDir, logger) = createTestSetup()
                try {
                    val auditLogger = AuditLogger(tempDir, logger)

                    auditLogger.logAdminAction(
                        action = AuditAction.LINK_USER,
                        actor = "Admin",
                        actorType = ActorType.CONSOLE
                    )

                    val auditFile = File(tempDir, "audit.log")
                    val content = auditFile.readText().trim()

                    // Should start with { and end with }
                    content.first() shouldBe '{'
                    content.last() shouldBe '}'
                } finally {
                    tempDir.deleteRecursively()
                }
            }

            it("includes timestamp in ISO format") {
                val (tempDir, logger) = createTestSetup()
                try {
                    val auditLogger = AuditLogger(tempDir, logger)

                    auditLogger.logAdminAction(
                        action = AuditAction.LINK_USER,
                        actor = "Admin",
                        actorType = ActorType.CONSOLE
                    )

                    val auditFile = File(tempDir, "audit.log")
                    val content = auditFile.readText()

                    // ISO 8601 format includes T separator and timezone
                    content shouldContain "\"timestamp\":\""
                    content shouldContain "T"
                } finally {
                    tempDir.deleteRecursively()
                }
            }

            it("escapes quotes in strings") {
                val (tempDir, logger) = createTestSetup()
                try {
                    val auditLogger = AuditLogger(tempDir, logger)

                    auditLogger.logAdminAction(
                        action = AuditAction.LINK_USER,
                        actor = "User\"WithQuotes",
                        actorType = ActorType.CONSOLE
                    )

                    val auditFile = File(tempDir, "audit.log")
                    val content = auditFile.readText()

                    content shouldContain "User\\\"WithQuotes"
                } finally {
                    tempDir.deleteRecursively()
                }
            }

            it("escapes newlines in strings") {
                val (tempDir, logger) = createTestSetup()
                try {
                    val auditLogger = AuditLogger(tempDir, logger)

                    auditLogger.logAdminAction(
                        action = AuditAction.LINK_USER,
                        actor = "User\nWithNewline",
                        actorType = ActorType.CONSOLE
                    )

                    val auditFile = File(tempDir, "audit.log")
                    val content = auditFile.readText()

                    content shouldContain "User\\nWithNewline"
                    content shouldNotContain "User\nWith"
                } finally {
                    tempDir.deleteRecursively()
                }
            }

            it("escapes backslashes in strings") {
                val (tempDir, logger) = createTestSetup()
                try {
                    val auditLogger = AuditLogger(tempDir, logger)

                    auditLogger.logAdminAction(
                        action = AuditAction.LINK_USER,
                        actor = "User\\WithBackslash",
                        actorType = ActorType.CONSOLE
                    )

                    val auditFile = File(tempDir, "audit.log")
                    val content = auditFile.readText()

                    content shouldContain "User\\\\WithBackslash"
                } finally {
                    tempDir.deleteRecursively()
                }
            }

            it("handles string, number, boolean detail values") {
                val (tempDir, logger) = createTestSetup()
                try {
                    val auditLogger = AuditLogger(tempDir, logger)

                    auditLogger.logAdminAction(
                        action = AuditAction.LINK_USER,
                        actor = "Admin",
                        actorType = ActorType.CONSOLE,
                        details = mapOf(
                            "stringVal" to "test",
                            "intVal" to 42,
                            "boolVal" to true,
                            "doubleVal" to 3.14
                        )
                    )

                    val auditFile = File(tempDir, "audit.log")
                    val content = auditFile.readText()

                    content shouldContain "\"stringVal\":\"test\""
                    content shouldContain "\"intVal\":42"
                    content shouldContain "\"boolVal\":true"
                    content shouldContain "\"doubleVal\":3.14"
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        }

        describe("AuditAction enum") {

            it("has correct display names") {
                AuditAction.LINK_USER.displayName shouldBe "Link User"
                AuditAction.UNLINK_USER.displayName shouldBe "Unlink User"
                AuditAction.LIST_MAPPINGS.displayName shouldBe "List Mappings"
                AuditAction.CHECK_USER.displayName shouldBe "Check User"
                AuditAction.PERMISSION_DENIED.displayName shouldBe "Permission Denied"
                AuditAction.RATE_LIMITED.displayName shouldBe "Rate Limited"
                AuditAction.INVALID_INPUT.displayName shouldBe "Invalid Input"
                AuditAction.WHITELIST_ADD.displayName shouldBe "Whitelist Add"
                AuditAction.WHITELIST_REMOVE.displayName shouldBe "Whitelist Remove"
            }
        }

        describe("ActorType enum") {

            it("has all expected values") {
                ActorType.values().size shouldBe 3
                ActorType.valueOf("DISCORD_USER") shouldBe ActorType.DISCORD_USER
                ActorType.valueOf("MINECRAFT_PLAYER") shouldBe ActorType.MINECRAFT_PLAYER
                ActorType.valueOf("CONSOLE") shouldBe ActorType.CONSOLE
            }
        }

        describe("SecurityEvent enum") {

            it("toAuditAction maps correctly") {
                SecurityEvent.PERMISSION_DENIED.toAuditAction() shouldBe AuditAction.PERMISSION_DENIED
                SecurityEvent.RATE_LIMITED.toAuditAction() shouldBe AuditAction.RATE_LIMITED
                SecurityEvent.INVALID_INPUT.toAuditAction() shouldBe AuditAction.INVALID_INPUT
            }

            it("has all expected values") {
                SecurityEvent.values().size shouldBe 3
            }
        }
    }
})
