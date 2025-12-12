package io.github.darinc.amssync.services

import io.github.darinc.amssync.discord.CircuitBreaker
import io.github.darinc.amssync.discord.TimeoutManager

/**
 * Groups resilience-related services: timeout protection, circuit breaker.
 * These services protect against cascading failures during Discord outages.
 */
data class ResilienceServices(
    val timeoutManager: TimeoutManager?,
    val circuitBreaker: CircuitBreaker?
) {
    /**
     * Shutdown all resilience services.
     */
    fun shutdown() {
        timeoutManager?.shutdown()
    }
}
