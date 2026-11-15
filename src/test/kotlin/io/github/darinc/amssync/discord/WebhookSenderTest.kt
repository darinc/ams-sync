package io.github.darinc.amssync.discord

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.IncomingWebhookClient
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction
import net.dv8tion.jda.api.utils.FileUpload
import java.util.logging.Logger

class WebhookSenderTest : DescribeSpec({

    val validUrl = "https://discord.com/api/webhooks/123456789012345678/abc-DEF_token"

    lateinit var logger: Logger
    lateinit var jda: JDA
    lateinit var client: IncomingWebhookClient
    lateinit var action: WebhookMessageCreateAction<Message>
    var createdWith: Pair<JDA, String>? = null

    fun sender(
        jdaProvider: () -> JDA? = { jda },
        circuitBreaker: CircuitBreaker? = null
    ) = WebhookSender(validUrl, jdaProvider, { circuitBreaker }, logger) { j, url ->
        createdWith = j to url
        client
    }

    beforeEach {
        logger = mockk(relaxed = true)
        jda = mockk { every { status } returns JDA.Status.CONNECTED }
        action = mockk(relaxed = true)
        client = mockk {
            every { sendMessage(any<String>()) } returns action
            every { sendMessageEmbeds(any<MessageEmbed>(), *anyVararg()) } returns action
            every { sendFiles(any<FileUpload>()) } returns action
        }
        createdWith = null
    }

    describe("URL validation") {
        it("accepts discord.com and discordapp.com webhook URLs") {
            WebhookSender.isValidUrl(validUrl) shouldBe true
            WebhookSender.isValidUrl("https://discordapp.com/api/webhooks/123456789012345678/tok") shouldBe true
            WebhookSender.isValidUrl("https://ptb.discord.com/api/v10/webhooks/123456789012345678/tok") shouldBe true
        }

        it("rejects null, blank and non-webhook URLs") {
            WebhookSender.isValidUrl(null) shouldBe false
            WebhookSender.isValidUrl("  ") shouldBe false
            WebhookSender.isValidUrl("https://example.com/api/webhooks/1/tok") shouldBe false
            WebhookSender.isValidUrl("https://discord.com/channels/1/2") shouldBe false
        }

        it("constructor rejects an invalid URL") {
            shouldThrow<IllegalArgumentException> {
                WebhookSender("not a url", { jda }, { null }, logger)
            }
        }
    }

    describe("sending") {
        it("sends text with username and avatar through the current JDA") {
            sender().sendMessage("hello", "Steve", "https://mc-heads.net/avatar/Steve") shouldBe true

            createdWith shouldBe (jda to validUrl)
            verify { client.sendMessage("hello") }
            verify { action.setUsername("Steve") }
            verify { action.setAvatarUrl("https://mc-heads.net/avatar/Steve") }
            verify { action.queue(any(), any()) }
        }

        it("does not override username or avatar when not given") {
            sender().sendMessage("hello") shouldBe true

            verify(exactly = 0) { action.setUsername(any()) }
            verify(exactly = 0) { action.setAvatarUrl(any()) }
        }

        it("sends JDA embeds directly") {
            val embed = mockk<MessageEmbed>()
            sender().sendEmbed(embed, "Server") shouldBe true

            verify { client.sendMessageEmbeds(embed) }
            verify { action.setUsername("Server") }
        }

        it("sends file bytes as an upload with the given filename") {
            val upload = slot<FileUpload>()
            every { client.sendFiles(capture(upload)) } returns action

            sender().sendFile(byteArrayOf(1, 2, 3), "card.png", username = "MCMMO Milestones") shouldBe true

            upload.captured.name shouldBe "card.png"
            verify { action.setUsername("MCMMO Milestones") }
        }

        it("logs failures reported by the request callback") {
            val onFailure = slot<java.util.function.Consumer<in Throwable>>()
            every { action.queue(any(), capture(onFailure)) } returns Unit

            sender().sendMessage("hello") shouldBe true
            onFailure.captured.accept(RuntimeException("Unknown Webhook"))

            verify { logger.warning(match<String> { it.contains("Unknown Webhook") }) }
        }
    }

    describe("when Discord is unavailable") {
        it("skips when there is no JDA instance") {
            sender(jdaProvider = { null }).sendMessage("hello") shouldBe false
            createdWith shouldBe null
        }

        it("skips when JDA is not connected") {
            every { jda.status } returns JDA.Status.RECONNECT_QUEUED
            sender().sendMessage("hello") shouldBe false
            createdWith shouldBe null
        }

        it("returns false instead of throwing when building the request fails") {
            every { client.sendMessage(any<String>()) } throws IllegalArgumentException("Content too long")
            sender().sendMessage("x".repeat(5000)) shouldBe false
            verify { logger.warning(match<String> { it.contains("Content too long") }) }
        }
    }

    describe("circuit breaker") {
        it("sends when the circuit is closed") {
            val breaker = CircuitBreaker(logger = logger)
            sender(circuitBreaker = breaker).sendMessage("hello") shouldBe true
            verify { action.queue(any(), any()) }
        }

        it("does not send when the circuit rejects the request") {
            val breaker = mockk<CircuitBreaker> {
                every { execute<Unit>(any(), any()) } returns
                    CircuitBreaker.CircuitResult.Rejected(CircuitBreaker.State.OPEN, "open")
            }
            sender(circuitBreaker = breaker).sendMessage("hello") shouldBe false
            verify(exactly = 0) { client.sendMessage(any<String>()) }
        }
    }
})
