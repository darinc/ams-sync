package io.github.darinc.amssync.image

import java.awt.Color
import java.awt.image.BufferedImage
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger
import javax.imageio.ImageIO

/**
 * Fetches and caches player avatar images from external APIs.
 *
 * Supports both mc-heads.net and crafatar.com as avatar providers.
 * Implements LRU-style caching with TTL to prevent excessive API calls.
 *
 * @property logger Logger for debugging and warnings
 * @property cacheMaxSize Maximum number of cached avatars
 * @property cacheTtlMs Time-to-live for cached avatars in milliseconds
 */
class AvatarFetcher(
    private val logger: Logger,
    private val cacheMaxSize: Int = 100,
    private val cacheTtlMs: Long = 300_000L  // 5 minutes default
) {
    private val avatarCache = ConcurrentHashMap<String, CachedAvatar>()

    companion object {
        private const val CONNECT_TIMEOUT_MS = 3000
        private const val READ_TIMEOUT_MS = 5000

        // Avatar size constants
        const val BODY_SIZE = 128
        const val HEAD_SIZE = 64
        const val PODIUM_HEAD_SIZE = 48

        /** Largest image accepted from an avatar or skin service */
        private const val MAX_IMAGE_BYTES = 2 * 1024 * 1024
        private const val MAX_IMAGE_DIMENSION = 1024

        private const val MOJANG_TEXTURE_HOST = "textures.minecraft.net"

        /**
         * Whether a skin URL points at Mojang's texture server (exact host match, http or https).
         */
        fun isMojangTextureUrl(url: String): Boolean = try {
            val uri = URI(url)
            uri.scheme?.lowercase() in setOf("http", "https") &&
                uri.host.equals(MOJANG_TEXTURE_HOST, ignoreCase = true) &&
                uri.userInfo == null && (uri.port == -1 || uri.port == 80 || uri.port == 443)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Fetch a full body skin render for a player.
     *
     * @param playerName Minecraft username
     * @param uuid Player UUID (used for crafatar)
     * @param provider Avatar provider ("mc-heads" or "crafatar")
     * @return BufferedImage of the body render, or placeholder on failure
     */
    fun fetchBodyRender(playerName: String, uuid: UUID?, provider: String): BufferedImage {
        val cacheKey = "body:$playerName:$provider"

        // Check cache first
        avatarCache[cacheKey]?.let { cached ->
            if (!cached.isExpired(cacheTtlMs)) {
                logger.fine("Avatar cache hit for body: $playerName")
                return cached.image
            }
        }

        val url = when (provider.lowercase()) {
            "crafatar" -> {
                val id = uuid?.toString()?.replace("-", "") ?: playerName
                "https://crafatar.com/renders/body/$id?size=$BODY_SIZE&overlay"
            }
            else -> "https://mc-heads.net/body/$playerName/$BODY_SIZE"
        }

        return fetchAndCache(url, cacheKey) ?: createPlaceholderBody()
    }

    /**
     * Fetch a head/face avatar for a player.
     *
     * @param playerName Minecraft username
     * @param uuid Player UUID (used for crafatar)
     * @param provider Avatar provider ("mc-heads" or "crafatar")
     * @param size Avatar size in pixels
     * @return BufferedImage of the head avatar, or placeholder on failure
     */
    fun fetchHeadAvatar(
        playerName: String,
        uuid: UUID?,
        provider: String,
        size: Int = HEAD_SIZE
    ): BufferedImage {
        val cacheKey = "head:$playerName:$provider:$size"

        // Check cache first
        avatarCache[cacheKey]?.let { cached ->
            if (!cached.isExpired(cacheTtlMs)) {
                logger.fine("Avatar cache hit for head: $playerName")
                return cached.image
            }
        }

        val url = when (provider.lowercase()) {
            "crafatar" -> {
                val id = uuid?.toString()?.replace("-", "") ?: playerName
                "https://crafatar.com/avatars/$id?size=$size&overlay"
            }
            else -> "https://mc-heads.net/avatar/$playerName/$size"
        }

        return fetchAndCache(url, cacheKey) ?: createPlaceholderHead(size)
    }

    /**
     * Fetch a player's raw skin texture.
     *
     * Tries the texture URL from the player's own profile first (served by Mojang), then
     * mc-heads.net by name. Returns null if neither yields a valid skin.
     *
     * @param playerName Minecraft username
     * @param textureUrl Skin URL from the player's profile, if known
     * @return 64x64 (or legacy 64x32) skin texture, or null
     */
    fun fetchSkin(playerName: String, textureUrl: String?): BufferedImage? {
        val cacheKey = "skin:$playerName:${textureUrl.orEmpty()}"
        avatarCache[cacheKey]?.let { cached ->
            if (!cached.isExpired(cacheTtlMs) && isSkinTexture(cached.image)) return cached.image
        }

        // Profile textures can be set by skin plugins or offline-mode servers, so only trust Mojang's host
        val trustedTexture = textureUrl?.takeIf { isMojangTextureUrl(it) }
        val candidates = listOfNotNull(trustedTexture, "https://mc-heads.net/skin/$playerName")
        return candidates.firstNotNullOfOrNull { url ->
            fetchAndCache(url, cacheKey)?.takeIf { isSkinTexture(it) }
        }
    }

    private fun isSkinTexture(image: BufferedImage): Boolean =
        image.width >= 64 && image.width % 64 == 0 &&
            (image.height == image.width || image.height * 2 == image.width)

    /**
     * Fetch body render asynchronously.
     */
    fun fetchBodyRenderAsync(
        playerName: String,
        uuid: UUID?,
        provider: String
    ): CompletableFuture<BufferedImage> {
        return CompletableFuture.supplyAsync {
            fetchBodyRender(playerName, uuid, provider)
        }
    }

    /**
     * Fetch head avatar asynchronously.
     */
    fun fetchHeadAvatarAsync(
        playerName: String,
        uuid: UUID?,
        provider: String,
        size: Int = HEAD_SIZE
    ): CompletableFuture<BufferedImage> {
        return CompletableFuture.supplyAsync {
            fetchHeadAvatar(playerName, uuid, provider, size)
        }
    }

    /**
     * Fetch multiple head avatars in parallel.
     *
     * @param players List of (playerName, uuid) pairs
     * @param provider Avatar provider
     * @param size Avatar size
     * @return Map of playerName to BufferedImage
     */
    fun fetchHeadAvatarsBatch(
        players: List<Pair<String, UUID?>>,
        provider: String,
        size: Int = HEAD_SIZE
    ): Map<String, BufferedImage> {
        val futures = players.map { (name, uuid) ->
            name to fetchHeadAvatarAsync(name, uuid, provider, size)
        }

        return futures.associate { (name, future) ->
            name to future.join()
        }
    }

    /**
     * Fetch and cache an image from a URL.
     */
    private fun fetchAndCache(urlString: String, cacheKey: String): BufferedImage? {
        return try {
            logger.fine("Fetching avatar from: $urlString")

            val url = URI.create(urlString).toURL()
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "AMSSync-Minecraft-Plugin")

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                logger.warning("Avatar fetch failed with status ${connection.responseCode}: $urlString")
                return null
            }

            val bytes = connection.inputStream.use { it.readNBytes(MAX_IMAGE_BYTES + 1) }
            connection.disconnect()
            if (bytes.size > MAX_IMAGE_BYTES) {
                logger.warning("Avatar response too large: $urlString")
                return null
            }
            val image = decodeBounded(bytes) ?: return null

            if (image != null) {
                // Add to cache, evict old entries if needed
                cleanupCacheIfNeeded()
                avatarCache[cacheKey] = CachedAvatar(image, System.currentTimeMillis())
                logger.fine("Avatar fetched and cached: $cacheKey")
            }

            image
        } catch (e: Exception) {
            logger.warning("Failed to fetch avatar from $urlString: ${e.message}")
            null
        }
    }

    /**
     * Decode an image only if its declared size is reasonable, before allocating pixels.
     */
    private fun decodeBounded(bytes: ByteArray): BufferedImage? {
        ImageIO.createImageInputStream(bytes.inputStream()).use { input ->
            val reader = ImageIO.getImageReaders(input).asSequence().firstOrNull() ?: return null
            try {
                reader.input = input
                if (reader.getWidth(0) > MAX_IMAGE_DIMENSION || reader.getHeight(0) > MAX_IMAGE_DIMENSION) {
                    logger.warning("Avatar image dimensions too large")
                    return null
                }
                return reader.read(0)
            } finally {
                reader.dispose()
            }
        }
    }

    /**
     * Clean up expired cache entries and evict oldest if over capacity.
     */
    private fun cleanupCacheIfNeeded() {
        // Remove expired entries
        avatarCache.entries.removeIf { (_, cached) ->
            cached.isExpired(cacheTtlMs)
        }

        // Evict oldest entries if still over capacity
        while (avatarCache.size >= cacheMaxSize) {
            val oldest = avatarCache.entries.minByOrNull { it.value.timestamp }
            oldest?.let { avatarCache.remove(it.key) }
        }
    }

    /**
     * Create a placeholder body silhouette image.
     */
    private fun createPlaceholderBody(): BufferedImage {
        val width = 64
        val height = BODY_SIZE
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g2d = image.createGraphics()
        GraphicsUtils.enableAntialiasing(g2d)

        // Gray silhouette
        g2d.color = Color(100, 100, 100)

        // Head
        g2d.fillRect(20, 0, 24, 24)

        // Body
        g2d.fillRect(16, 24, 32, 32)

        // Arms
        g2d.fillRect(4, 24, 12, 32)
        g2d.fillRect(48, 24, 12, 32)

        // Legs
        g2d.fillRect(16, 56, 14, 32)
        g2d.fillRect(34, 56, 14, 32)

        g2d.dispose()
        return image
    }

    /**
     * Create a placeholder head image (gray square with question mark).
     */
    private fun createPlaceholderHead(size: Int): BufferedImage {
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g2d = image.createGraphics()
        GraphicsUtils.enableAntialiasing(g2d)

        // Gray background
        g2d.color = Color(80, 80, 80)
        g2d.fillRect(0, 0, size, size)

        // Question mark
        g2d.color = Color(150, 150, 150)
        g2d.font = CardStyles.FONT_TITLE
        val metrics = g2d.fontMetrics
        val text = "?"
        val x = (size - metrics.stringWidth(text)) / 2
        val y = (size - metrics.height) / 2 + metrics.ascent
        g2d.drawString(text, x, y)

        g2d.dispose()
        return image
    }

    /**
     * Clear the entire avatar cache.
     */
    fun clearCache() {
        avatarCache.clear()
        logger.info("Avatar cache cleared")
    }

    /**
     * Get current cache size.
     */
    fun getCacheSize(): Int = avatarCache.size

    /**
     * Cached avatar with timestamp for TTL checking.
     */
    private data class CachedAvatar(
        val image: BufferedImage,
        val timestamp: Long
    ) {
        fun isExpired(ttlMs: Long): Boolean {
            return System.currentTimeMillis() - timestamp > ttlMs
        }
    }
}
