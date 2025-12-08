package io.github.darinc.amssync.discord

import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

/**
 * Rate limiter with per-user cooldown and burst protection.
 *
 * @param cooldownMs Minimum time between requests per user (default: 3 seconds)
 * @param maxRequests Maximum requests allowed per time window (default: 60)
 * @param windowMs Time window for burst limiting in milliseconds (default: 60 seconds)
 */
class RateLimiter(
    private val cooldownMs: Long = 3000L,
    private val maxRequests: Int = 60,
    private val windowMs: Long = 60000L,
    private val logger: Logger? = null
) {
    private val lastRequest = ConcurrentHashMap<String, Long>()
    private val requestTimestamps = ConcurrentHashMap<String, MutableList<Long>>()

    /**
     * Check if a user is allowed to make a request.
     *
     * @param userId The unique identifier for the user (Discord ID or Minecraft UUID)
     * @return RateLimitResult indicating if the request is allowed or why it was denied
     */
    fun checkRateLimit(userId: String): RateLimitResult {
        val now = System.currentTimeMillis()

        // Check cooldown (minimum time between requests)
        val lastTime = lastRequest[userId]
        if (lastTime != null) {
            val elapsed = now - lastTime
            if (elapsed < cooldownMs) {
                val remainingMs = cooldownMs - elapsed
                logger?.fine("Rate limit: $userId cooldown active, ${remainingMs}ms remaining")
                return RateLimitResult.Cooldown(remainingMs)
            }
        }

        // Check burst limit (max requests per window)
        val timestamps = requestTimestamps.getOrPut(userId) { mutableListOf() }

        // Remove expired timestamps outside the window
        synchronized(timestamps) {
            val windowStart = now - windowMs
            timestamps.removeIf { it < windowStart }

            if (timestamps.size >= maxRequests) {
                // Find when the oldest request will expire
                val oldestTimestamp = timestamps.minOrNull() ?: now
                val retryAfterMs = (oldestTimestamp + windowMs) - now
                logger?.fine("Rate limit: $userId burst limited, retry after ${retryAfterMs}ms")
                return RateLimitResult.BurstLimited(retryAfterMs.coerceAtLeast(0))
            }

            // Record this request
            timestamps.add(now)
        }

        // Update last request time
        lastRequest[userId] = now

        return RateLimitResult.Allowed
    }

    /**
     * Clean up expired entries to prevent memory leaks.
     * Should be called periodically (e.g., every minute).
     */
    fun cleanup() {
        val now = System.currentTimeMillis()
        val windowStart = now - windowMs
        val cooldownStart = now - cooldownMs

        // Remove users with no recent activity
        lastRequest.entries.removeIf { it.value < cooldownStart }

        // Clean up timestamp lists
        requestTimestamps.entries.removeIf { (_, timestamps) ->
            synchronized(timestamps) {
                timestamps.removeIf { it < windowStart }
                timestamps.isEmpty()
            }
        }
    }

    /**
     * Get the current request count for a user in the current window.
     */
    fun getRequestCount(userId: String): Int {
        val now = System.currentTimeMillis()
        val windowStart = now - windowMs
        val timestamps = requestTimestamps[userId] ?: return 0

        synchronized(timestamps) {
            return timestamps.count { it >= windowStart }
        }
    }

    /**
     * Reset rate limit for a specific user (useful for testing or admin override).
     */
    fun reset(userId: String) {
        lastRequest.remove(userId)
        requestTimestamps.remove(userId)
    }

    /**
     * Reset all rate limits.
     */
    fun resetAll() {
        lastRequest.clear()
        requestTimestamps.clear()
    }
}

/**
 * Result of a rate limit check.
 */
sealed class RateLimitResult {
    /** Request is allowed to proceed */
    object Allowed : RateLimitResult()

    /** Request denied due to cooldown - must wait before next request */
    data class Cooldown(val remainingMs: Long) : RateLimitResult() {
        val remainingSeconds: Double get() = remainingMs / 1000.0
    }

    /** Request denied due to burst limit - too many requests in time window */
    data class BurstLimited(val retryAfterMs: Long) : RateLimitResult() {
        val retryAfterSeconds: Double get() = retryAfterMs / 1000.0
    }
}

/**
 * Configuration for rate limiting, loadable from plugin config.
 */
data class RateLimiterConfig(
    val enabled: Boolean = true,
    val cooldownMs: Long = 3000L,
    val maxRequestsPerMinute: Int = 60
) {
    fun toRateLimiter(logger: Logger? = null): RateLimiter {
        return RateLimiter(
            cooldownMs = cooldownMs,
            maxRequests = maxRequestsPerMinute,
            windowMs = 60000L,
            logger = logger
        )
    }
}
