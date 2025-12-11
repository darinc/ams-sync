package io.github.darinc.amssync.services

import io.github.darinc.amssync.audit.AuditLogger
import io.github.darinc.amssync.discord.RateLimiter
import io.github.darinc.amssync.linking.UserMappingService
import io.github.darinc.amssync.mcmmo.McmmoApiWrapper
import io.github.darinc.amssync.metrics.ErrorMetrics

/**
 * Central registry for all plugin services.
 * Groups related services together for better organization and reduced coupling.
 */
class ServiceRegistry {
    // Core services (always required)
    lateinit var userMappingService: UserMappingService
        internal set

    lateinit var mcmmoApi: McmmoApiWrapper
        internal set

    lateinit var errorMetrics: ErrorMetrics
        internal set

    lateinit var auditLogger: AuditLogger
        internal set

    var rateLimiter: RateLimiter? = null
        internal set

    // Grouped services
    lateinit var resilience: ResilienceServices
        internal set

    lateinit var discord: DiscordServices
        internal set

    var image: ImageServices = ImageServices.disabled()
        internal set

    var events: EventServices = EventServices.empty()
        internal set

    var progression: ProgressionServices = ProgressionServices.disabled()
        internal set

    /**
     * Shutdown all services in proper order.
     */
    fun shutdown() {
        // Announce server stop before disconnecting
        events.announceServerStop()

        // Shutdown event services
        events.shutdown()

        // Shutdown Discord services
        if (::discord.isInitialized) {
            discord.shutdown()
        }

        // Shutdown resilience services
        if (::resilience.isInitialized) {
            resilience.shutdown()
        }

        // Shutdown progression services
        progression.shutdown()

        // Save user mappings
        if (::userMappingService.isInitialized) {
            userMappingService.saveMappings()
        }
    }

    /**
     * Check if core services are initialized.
     */
    fun isCoreInitialized(): Boolean =
        ::userMappingService.isInitialized &&
            ::mcmmoApi.isInitialized &&
            ::errorMetrics.isInitialized &&
            ::auditLogger.isInitialized

    /**
     * Check if Discord services are initialized.
     */
    fun isDiscordInitialized(): Boolean = ::discord.isInitialized
}
