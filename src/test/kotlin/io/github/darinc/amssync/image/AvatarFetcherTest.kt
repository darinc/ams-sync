package io.github.darinc.amssync.image

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import java.util.logging.Logger

class AvatarFetcherTest : DescribeSpec({

    describe("AvatarFetcher") {

        describe("cache management") {

            it("starts with empty cache") {
                val logger = mockk<Logger>(relaxed = true)
                val fetcher = AvatarFetcher(logger)

                fetcher.getCacheSize() shouldBe 0
            }

            it("clearCache empties the cache") {
                val logger = mockk<Logger>(relaxed = true)
                val fetcher = AvatarFetcher(logger)

                // Get a placeholder to populate cache indirectly
                fetcher.fetchHeadAvatar("TestPlayer", null, "mc-heads", 64)

                fetcher.clearCache()

                fetcher.getCacheSize() shouldBe 0
            }
        }

        describe("placeholder generation") {

            it("creates placeholder head with correct dimensions") {
                val logger = mockk<Logger>(relaxed = true)
                val fetcher = AvatarFetcher(
                    logger = logger,
                    cacheMaxSize = 100,
                    cacheTtlMs = 1L // Very short TTL to force placeholder
                )

                // Use an invalid player name to trigger placeholder
                val placeholder = fetcher.fetchHeadAvatar("_invalid_player_that_does_not_exist_", null, "mc-heads", 64)

                placeholder.width shouldBe 64
                placeholder.height shouldBe 64
            }

            it("creates placeholder head with custom size") {
                val logger = mockk<Logger>(relaxed = true)
                val fetcher = AvatarFetcher(logger)

                val placeholder = fetcher.fetchHeadAvatar("_invalid_player_", null, "mc-heads", 128)

                placeholder.width shouldBe 128
                placeholder.height shouldBe 128
            }

            it("returns an image for body render") {
                val logger = mockk<Logger>(relaxed = true)
                val fetcher = AvatarFetcher(logger)

                val image = fetcher.fetchBodyRender("TestPlayer", null, "mc-heads")

                // Should return a valid image (either from API or placeholder)
                image.width shouldBeGreaterThan 0
                image.height shouldBeGreaterThan 0
            }
        }

        describe("cache key generation") {

            it("uses different cache keys for different sizes") {
                val logger = mockk<Logger>(relaxed = true)
                val fetcher = AvatarFetcher(logger, cacheMaxSize = 100, cacheTtlMs = 60000L)

                // Fetch same player at different sizes
                fetcher.fetchHeadAvatar("TestPlayer", null, "mc-heads", 64)
                fetcher.fetchHeadAvatar("TestPlayer", null, "mc-heads", 128)

                // Both should be cached separately
                fetcher.getCacheSize() shouldBe 2
            }

            it("uses different cache keys for different providers") {
                val logger = mockk<Logger>(relaxed = true)
                val fetcher = AvatarFetcher(logger, cacheMaxSize = 100, cacheTtlMs = 60000L)

                // Fetch same player with different providers
                // Note: If one provider fails (returns placeholder), only successful fetches are cached
                fetcher.fetchHeadAvatar("TestPlayer", null, "mc-heads", 64)
                fetcher.fetchHeadAvatar("TestPlayer", null, "crafatar", 64)

                // At least one should be cached (may be 1 or 2 depending on API availability)
                fetcher.getCacheSize() shouldBeGreaterThan 0
            }

            it("uses different cache keys for head vs body") {
                val logger = mockk<Logger>(relaxed = true)
                val fetcher = AvatarFetcher(logger, cacheMaxSize = 100, cacheTtlMs = 60000L)

                // Fetch head and body for same player
                fetcher.fetchHeadAvatar("TestPlayer", null, "mc-heads", 64)
                fetcher.fetchBodyRender("TestPlayer", null, "mc-heads")

                // Both should be cached separately
                fetcher.getCacheSize() shouldBe 2
            }
        }

        describe("batch fetching") {

            it("returns map with all requested players") {
                val logger = mockk<Logger>(relaxed = true)
                val fetcher = AvatarFetcher(logger)

                val players = listOf(
                    "Player1" to null,
                    "Player2" to null,
                    "Player3" to null
                )

                val results = fetcher.fetchHeadAvatarsBatch(players, "mc-heads", 64)

                results.size shouldBe 3
                results.keys shouldBe setOf("Player1", "Player2", "Player3")
            }

            it("returns images for each player in batch") {
                val logger = mockk<Logger>(relaxed = true)
                val fetcher = AvatarFetcher(logger)

                val players = listOf(
                    "Player1" to null,
                    "Player2" to null
                )

                val results = fetcher.fetchHeadAvatarsBatch(players, "mc-heads", 48)

                results["Player1"]!!.width shouldBe 48
                results["Player2"]!!.width shouldBe 48
            }
        }

        describe("constants") {

            it("has correct body size constant") {
                AvatarFetcher.BODY_SIZE shouldBe 128
            }

            it("has correct head size constant") {
                AvatarFetcher.HEAD_SIZE shouldBe 64
            }

            it("has correct podium head size constant") {
                AvatarFetcher.PODIUM_HEAD_SIZE shouldBe 48
            }
        }

        describe("configuration") {

            it("respects custom cache max size") {
                val logger = mockk<Logger>(relaxed = true)
                val fetcher = AvatarFetcher(
                    logger = logger,
                    cacheMaxSize = 2,
                    cacheTtlMs = 60000L
                )

                // Fill cache beyond limit
                fetcher.fetchHeadAvatar("Player1", null, "mc-heads", 64)
                fetcher.fetchHeadAvatar("Player2", null, "mc-heads", 64)
                fetcher.fetchHeadAvatar("Player3", null, "mc-heads", 64)

                // Cache should be limited
                fetcher.getCacheSize() shouldBe 2
            }

            it("allows configuration of cache TTL") {
                val logger = mockk<Logger>(relaxed = true)
                val shortTtlFetcher = AvatarFetcher(
                    logger = logger,
                    cacheMaxSize = 100,
                    cacheTtlMs = 1L // 1ms TTL
                )

                val longTtlFetcher = AvatarFetcher(
                    logger = logger,
                    cacheMaxSize = 100,
                    cacheTtlMs = 300000L // 5 min TTL
                )

                // Both should be configurable without error
                shortTtlFetcher.getCacheSize() shouldBe 0
                longTtlFetcher.getCacheSize() shouldBe 0
            }
        }
    }
})
