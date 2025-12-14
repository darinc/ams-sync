package io.github.darinc.amssync.features

/**
 * Interface for feature coordinators that manage related services.
 * Each feature can be enabled/disabled via configuration and provides
 * lifecycle hooks for initialization and shutdown.
 */
interface Feature {
    /**
     * Whether this feature is enabled based on configuration.
     */
    val isEnabled: Boolean

    /**
     * Initialize the feature and its dependencies.
     * Called during plugin startup when the feature is enabled.
     */
    fun initialize()

    /**
     * Shutdown the feature and clean up resources.
     * Called during plugin shutdown.
     */
    fun shutdown()
}
