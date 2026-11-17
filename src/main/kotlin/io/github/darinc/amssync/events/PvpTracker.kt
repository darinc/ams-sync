package io.github.darinc.amssync.events

import java.util.UUID

/**
 * What a single PvP kill means given recent history.
 *
 * @property killerStreak Kills the killer has made since they last died, including this one
 * @property isRevenge The victim killed the killer recently and the killer has now evened the score
 * @property endedStreak The streak the victim had going before this death (0 if none)
 */
data class PvpKillContext(
    val killerStreak: Int,
    val isRevenge: Boolean,
    val endedStreak: Int
)

/**
 * Tracks recent PvP kills in memory to detect kill streaks and revenge kills.
 *
 * A streak counts kills since the player last died (from any cause). A revenge kill is
 * when a player kills the person who killed them within [revengeWindowMillis].
 * State is lost on restart or reload, which is acceptable for flavor text.
 */
class PvpTracker(
    private val revengeWindowMillis: Long = DEFAULT_REVENGE_WINDOW_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val streaks = mutableMapOf<UUID, Int>()

    /** Victim UUID -> (killer UUID, time of kill) for the victim's most recent PvP death. */
    private val lastKilledBy = mutableMapOf<UUID, Pair<UUID, Long>>()

    /**
     * Record a PvP kill and return its context.
     */
    @Synchronized
    fun recordKill(killer: UUID, victim: UUID): PvpKillContext {
        val now = clock()

        val revengeEntry = lastKilledBy[killer]
        val isRevenge = revengeEntry != null &&
            revengeEntry.first == victim &&
            now - revengeEntry.second <= revengeWindowMillis
        if (isRevenge) lastKilledBy.remove(killer)

        val endedStreak = streaks.remove(victim) ?: 0
        val killerStreak = (streaks[killer] ?: 0) + 1
        streaks[killer] = killerStreak
        lastKilledBy[victim] = killer to now

        return PvpKillContext(killerStreak, isRevenge, endedStreak)
    }

    /**
     * Record a non-PvP death, which ends the player's streak.
     */
    @Synchronized
    fun recordDeath(victim: UUID) {
        streaks.remove(victim)
    }

    companion object {
        const val DEFAULT_REVENGE_WINDOW_MILLIS = 10 * 60 * 1000L
    }
}
