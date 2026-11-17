package io.github.darinc.amssync.events

import kotlin.random.Random

/**
 * Builds PvP-specific commentary from kill context (revenge, streaks, weapon).
 *
 * Names and weapon are inserted verbatim; callers escape them for Discord markdown.
 */
object PvpCommentary {

    /** Streak length at which a kill streak gets called out. */
    const val STREAK_CALLOUT = 3

    /** Minimum streak a victim must have had for its end to be called out. */
    const val ENDED_STREAK_CALLOUT = 3

    private val REVENGE = listOf(
        "{killer} gets payback on {victim}.",
        "Revenge is a dish best served cold. {killer} served it.",
        "{victim} had that coming. Score settled.",
        "{killer} remembers. {killer} always remembers.",
        "Uno reverse. {killer} evens the score."
    )

    private val ENDED_STREAK = listOf(
        "{killer} ends {victim}'s {ended}-kill streak.",
        "{victim}'s {ended}-kill rampage is over, courtesy of {killer}.",
        "Streak broken! {victim} was on {ended} kills."
    )

    private val STREAK = listOf(
        "{killer} is on a {streak}-kill streak!",
        "{streak} kills in a row. Somebody stop {killer}.",
        "{killer} is unstoppable: {streak} kills and counting.",
        "Kill streak: {streak}. {killer} is just getting warmed up."
    )

    private val WEAPON = listOf(
        "Death by {weapon}. Truly humbling.",
        "{killer} and their trusty {weapon} strike again.",
        "Taken out by {weapon}. {victim} will never live this down.",
        "The {weapon} claims another victim."
    )

    /**
     * Pick a commentary line, preferring the most notable thing about the kill.
     *
     * Priority: revenge, ended streak, killer streak, named weapon, then [fallback].
     *
     * @param killer Killer's display name
     * @param victim Victim's display name
     * @param context Streak/revenge context from [PvpTracker]
     * @param weaponName Custom name of the killer's weapon, if it has one
     * @param fallback Generic commentary used when nothing notable happened
     */
    fun generate(
        killer: String,
        victim: String,
        context: PvpKillContext,
        weaponName: String?,
        fallback: () -> String,
        random: Random = Random.Default
    ): String {
        val templates = when {
            context.isRevenge -> REVENGE
            context.endedStreak >= ENDED_STREAK_CALLOUT -> ENDED_STREAK
            context.killerStreak >= STREAK_CALLOUT -> STREAK
            weaponName != null -> WEAPON
            else -> return fallback()
        }
        return templates.random(random)
            .replace("{killer}", killer)
            .replace("{victim}", victim)
            .replace("{streak}", context.killerStreak.toString())
            .replace("{ended}", context.endedStreak.toString())
            .replace("{weapon}", weaponName ?: "")
    }
}
