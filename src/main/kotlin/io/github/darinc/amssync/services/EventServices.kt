package io.github.darinc.amssync.services

import io.github.darinc.amssync.events.AchievementListener
import io.github.darinc.amssync.events.PlayerDeathListener
import io.github.darinc.amssync.events.ServerEventListener
import io.github.darinc.amssync.mcmmo.McMMOEventListener

/**
 * Groups event listener services: MCMMO milestones, server events, deaths, achievements.
 */
data class EventServices(
    val mcmmoListener: McMMOEventListener?,
    val serverListener: ServerEventListener?,
    val deathListener: PlayerDeathListener?,
    val achievementListener: AchievementListener?
) {
    /**
     * Shutdown all event services.
     */
    fun shutdown() {
        mcmmoListener?.shutdown()
    }

    /**
     * Announce server stop event.
     */
    fun announceServerStop() {
        serverListener?.announceServerStop()
    }

    companion object {
        /**
         * Create empty event services.
         */
        fun empty(): EventServices = EventServices(null, null, null, null)
    }
}
