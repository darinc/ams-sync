package io.github.darinc.amssync.discord.commands

import com.destroystokyo.paper.profile.PlayerProfile
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.bukkit.OfflinePlayer
import org.bukkit.Server
import java.io.IOException
import java.util.UUID

class WhitelistServiceTest : DescribeSpec({

    val uuid = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5")
    val server = mockk<Server>()
    val service = WhitelistService(server)

    beforeEach { clearAllMocks() }

    describe("lookupProfile") {

        it("resolves a player who has never joined via Mojang, using canonical name") {
            val profile = mockk<PlayerProfile>()
            every { server.onlineMode } returns true
            every { server.createProfile("notch") } returns profile
            every { profile.complete(false) } returns true
            every { profile.id } returns uuid
            every { profile.name } returns "Notch"

            service.lookupProfile("notch") shouldBe ProfileLookup.Found("Notch", uuid)
        }

        it("returns NotFound when Mojang has no such account") {
            val profile = mockk<PlayerProfile>()
            every { server.onlineMode } returns true
            every { server.createProfile("nobody_xyz") } returns profile
            every { profile.complete(false) } returns false
            every { profile.id } returns null
            every { profile.name } returns "nobody_xyz"

            service.lookupProfile("nobody_xyz") shouldBe ProfileLookup.NotFound
        }

        it("returns Failed when the lookup throws") {
            val profile = mockk<PlayerProfile>()
            every { server.onlineMode } returns true
            every { server.createProfile("Steve") } returns profile
            every { profile.complete(false) } throws IOException("timed out")

            service.lookupProfile("Steve") shouldBe ProfileLookup.Failed("timed out")
        }

        it("skips the Mojang lookup on offline-mode servers") {
            every { server.onlineMode } returns false

            service.lookupProfile("Steve") shouldBe ProfileLookup.Found("Steve", null)
            verify(exactly = 0) { server.createProfile(any<String>()) }
        }
    }

    describe("addToWhitelist") {

        it("whitelists a player who has never joined") {
            val player = mockk<OfflinePlayer>(relaxed = true)
            every { server.getOfflinePlayer("Notch") } returns player
            every { player.uniqueId } returns uuid
            every { player.name } returns "Notch"
            every { player.isWhitelisted } returns false
            every { player.hasPlayedBefore() } returns false

            service.addToWhitelist(ProfileLookup.Found("Notch", uuid)) shouldBe
                WhitelistAddResult.Added("Notch", uuid)
            verify { player.isWhitelisted = true }
        }

        it("reports players already on the whitelist without changing them") {
            val player = mockk<OfflinePlayer>(relaxed = true)
            every { server.getOfflinePlayer("Notch") } returns player
            every { player.uniqueId } returns uuid
            every { player.name } returns "Notch"
            every { player.isWhitelisted } returns true

            service.addToWhitelist(ProfileLookup.Found("Notch", uuid)) shouldBe
                WhitelistAddResult.AlreadyWhitelisted("Notch")
            verify(exactly = 0) { player.isWhitelisted = any() }
        }

        it("refuses when the server's UUID disagrees with Mojang") {
            val player = mockk<OfflinePlayer>(relaxed = true)
            every { server.getOfflinePlayer("Notch") } returns player
            every { player.uniqueId } returns UUID.randomUUID()

            service.addToWhitelist(ProfileLookup.Found("Notch", uuid))
                .shouldBeInstanceOf<WhitelistAddResult.Failed>()
            verify(exactly = 0) { player.isWhitelisted = any() }
        }

        it("accepts the server's UUID when no Mojang UUID is known (offline mode)") {
            val offlineUuid = UUID.nameUUIDFromBytes("OfflinePlayer:Steve".toByteArray())
            val player = mockk<OfflinePlayer>(relaxed = true)
            every { server.getOfflinePlayer("Steve") } returns player
            every { player.uniqueId } returns offlineUuid
            every { player.name } returns "Steve"
            every { player.isWhitelisted } returns false

            service.addToWhitelist(ProfileLookup.Found("Steve", null)) shouldBe
                WhitelistAddResult.Added("Steve", offlineUuid)
        }
    }
})
