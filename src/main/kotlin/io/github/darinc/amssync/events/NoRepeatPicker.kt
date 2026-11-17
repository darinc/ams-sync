package io.github.darinc.amssync.events

import kotlin.random.Random

/**
 * Picks random lines from a pool while avoiding lines used recently from the same pool.
 *
 * For a pool of n lines, the last `min(n / 2, maxMemory)` picks are skipped, so short
 * pools still vary and long pools don't repeat for a while.
 *
 * @property maxMemory Upper bound on how many recent picks are remembered per pool
 * @property random Source of randomness (overridable for tests)
 */
class NoRepeatPicker(
    private val maxMemory: Int = DEFAULT_MAX_MEMORY,
    private val random: Random = Random.Default
) {
    private val recent = mutableMapOf<String, ArrayDeque<String>>()

    /**
     * Pick a line from [options], avoiding recent picks for the pool named [key].
     */
    @Synchronized
    fun pick(key: String, options: List<String>): String {
        require(options.isNotEmpty()) { "No options for '$key'" }
        val history = recent.getOrPut(key) { ArrayDeque() }
        val fresh = options.filter { it !in history }.ifEmpty { options }
        val choice = fresh[random.nextInt(fresh.size)]

        val memory = minOf(options.size / 2, maxMemory)
        history.addLast(choice)
        while (history.size > memory) history.removeFirst()
        return choice
    }

    companion object {
        const val DEFAULT_MAX_MEMORY = 6

        /** Shared picker so every announcement path draws from the same history. */
        val shared = NoRepeatPicker()
    }
}
