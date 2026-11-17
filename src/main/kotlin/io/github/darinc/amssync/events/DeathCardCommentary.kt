package io.github.darinc.amssync.events

import io.github.darinc.amssync.image.DeathThemes
import kotlin.random.Random

/**
 * Details about the killer for a PvP death card.
 *
 * @property killerName Killer's username
 * @property weaponName Custom name of the killer's weapon in `[Name]` form, if renamed
 * @property weaponSkill mcMMO skill matching the killer's weapon (e.g. SWORDS), if known
 * @property killerStats Killer's mcMMO skill levels, if available
 * @property context Streak and revenge context from [PvpTracker]
 */
data class PvpCardInfo(
    val killerName: String,
    val weaponName: String?,
    val weaponSkill: String?,
    val killerStats: Map<String, Int>?,
    val context: PvpKillContext
)

/**
 * Text for death cards: a short headline in the box above the coffin and an epitaph below it.
 *
 * Headlines are kept short so they fit on one line. The epitaph prefers something specific
 * to the death (PvP context, a skill matchup, an mcMMO roast) and falls back to the
 * standard per-cause commentary.
 */
@Suppress("MaxLineLength", "TooManyFunctions")
object DeathCardCommentary {

    /** Relevant skill at or above this level gets an ironic roast */
    const val HIGH_SKILL_THRESHOLD = 400

    /** Relevant skill below this level gets a "needs training" roast */
    const val LOW_SKILL_THRESHOLD = 100

    /** Elite power level threshold for embarrassing-death roasts */
    const val ELITE_POWER_THRESHOLD = 3000

    /** Longest headline the card shows without shrinking the font much */
    const val MAX_HEADLINE_LENGTH = 48

    private const val SKILL_ROAST_CHANCE = 0.6f

    private val COMBAT_SKILLS = listOf("SWORDS", "AXES", "ARCHERY", "UNARMED", "MACES", "TRIDENTS", "CROSSBOWS", "SPEARS")

    // ========== PUBLIC API ==========

    /**
     * Generate the headline and epitaph for a death card.
     *
     * @param category Death category
     * @param playerStats Victim's mcMMO skill levels (nullable)
     * @param powerLevel Victim's power level (nullable)
     * @param playerName Victim's username, used in PvP lines
     * @param pvp Killer details when another player caused the death
     * @return Pair of (headline, epitaph)
     */
    fun generateMessages(
        category: DeathCategory,
        playerStats: Map<String, Int>?,
        powerLevel: Int?,
        playerName: String = "They",
        pvp: PvpCardInfo? = null,
        picker: NoRepeatPicker = NoRepeatPicker.shared,
        random: Random = Random.Default
    ): Pair<String, String> {
        val headline = if (pvp != null) pvpHeadline(pvp, picker) else headline(category, picker)
        val epitaph = if (pvp != null) {
            pvpEpitaph(playerName, playerStats, pvp, picker) ?: DeathCommentaryRepository.getCommentary(DeathCategory.PvP)
        } else {
            epitaph(category, playerStats, powerLevel, picker, random)
        }
        return headline to epitaph
    }

    /**
     * The mcMMO skill trained by a weapon, from its Bukkit material name.
     */
    fun weaponSkillFor(materialName: String): String? = when {
        materialName == "AIR" -> "UNARMED"
        materialName.endsWith("_SWORD") -> "SWORDS"
        materialName.endsWith("_AXE") -> "AXES"
        materialName.endsWith("_SPEAR") -> "SPEARS"
        materialName == "BOW" -> "ARCHERY"
        materialName == "CROSSBOW" -> "CROSSBOWS"
        materialName == "TRIDENT" -> "TRIDENTS"
        materialName == "MACE" -> "MACES"
        else -> null
    }

    // ========== HEADLINES ==========

    private fun headline(category: DeathCategory, picker: NoRepeatPicker): String {
        val cause = causeName(category)
        return picker.pick("card-headline:$category", headlinesFor(category)).replace("{cause}", cause)
    }

    private fun pvpHeadline(pvp: PvpCardInfo, picker: NoRepeatPicker): String {
        val weapon = pvp.weaponName
        if (weapon != null) {
            val withWeapon = picker.pick("card-headline:pvp-weapon", PVP_WEAPON_HEADLINES)
                .replace("{killer}", pvp.killerName)
                .replace("{weapon}", weapon)
            if (withWeapon.length <= MAX_HEADLINE_LENGTH) return withWeapon
        }
        return picker.pick("card-headline:pvp", PVP_HEADLINES).replace("{killer}", pvp.killerName)
    }

    @Suppress("CyclomaticComplexMethod") // Flat lookup table
    internal fun headlinesFor(category: DeathCategory): List<String> = when (category) {
        DeathCategory.Mob.Creeper -> listOf("Hugged by a creeper. Briefly.", "Went out with a bang.", "Ssssssorry for your loss.", "Now available in several pieces.", "Exploded into the afterlife.")
        DeathCategory.Mob.Skeleton -> listOf("Turned into a pincushion.", "Shot by a guy with no lungs.", "Outranged by a calcium deposit.", "Died to a walking xylophone.")
        DeathCategory.Mob.Zombie -> listOf("Outrun by a zombie. Somehow.", "Lunch for the undead.", "Caught by the slowest mob in the game.", "Became a zombie snack.")
        DeathCategory.Mob.Spider -> listOf("Wrapped up for later.", "Eight legs beat two.", "Spider: 1. Arachnophobia: justified.")
        DeathCategory.Mob.Enderman -> listOf("Made eye contact. Big mistake.", "Stared at the wrong tall guy.", "Teleported to the afterlife.")
        DeathCategory.Mob.Wither -> listOf("Withered away.", "Summoned their own doom.", "Three heads beat one.")
        DeathCategory.Mob.EnderDragon -> listOf("Lost to the final boss.", "Flung into orbit by a dragon.", "The dragon sends its regards.")
        DeathCategory.Mob.Blaze -> listOf("Roasted by a flying fire stick.", "Blazed and confused.", "Fireballed into ash.")
        DeathCategory.Mob.Ghast -> listOf("Lost at fireball tennis.", "Ghasted.", "Killed by a crying jellyfish.")
        DeathCategory.Mob.Piglin -> listOf("Should've worn gold.", "Bartered their life away.", "Piglins don't do refunds.")
        DeathCategory.Mob.Warden -> listOf("Woke the Warden.", "Sneaking: failed.", "Heard. Found. Flattened.")
        DeathCategory.Mob.Witch -> listOf("Lost a potion fight to a witch.", "Hexed and wrecked.", "Cackled to death.")
        DeathCategory.Mob.Phantom -> listOf("Should have slept.", "Bedtime, enforced by phantoms.", "Insomnia: fatal.")
        DeathCategory.Mob.Guardian -> listOf("Lasered by an angry fish.", "Stared down by a guardian.", "Ocean monument: 1.")
        DeathCategory.Mob.Slime -> listOf("Bounced to death.", "Defeated by gelatin.", "Absorbed by a cube.")
        DeathCategory.Mob.Wolf -> listOf("Not a good boy.", "Picked a fight with the pack.", "Mauled by a floof.")
        DeathCategory.Mob.Bee -> listOf("Bee'd to death.", "Stung by a pollen courier.", "Messed with the hive.")
        DeathCategory.Mob.Golem -> listOf("The Iron Golem said no.", "Thrown into orbit by a golem.", "Punched by village security.")
        DeathCategory.Mob.GenericMob -> listOf("Slain by a {cause}.", "Lost to a {cause}.", "A {cause} sends its regards.")
        DeathCategory.Environmental.Fall -> listOf("Forgot gravity was on.", "Took the express way down.", "Floor: 1. Player: 0.", "Missed the water bucket clutch.", "Splat.")
        DeathCategory.Environmental.Lava -> listOf("Took a lava bath.", "Swam in the spicy water.", "Became one with the magma.", "Cooked medium well.")
        DeathCategory.Environmental.Fire -> listOf("Became a torch.", "Too hot to handle.", "Stopped, dropped, forgot to roll.")
        DeathCategory.Environmental.Drowning -> listOf("Forgot how to breathe.", "Glub glub.", "Sleeps with the fishes now.", "Ran out of bubbles.")
        DeathCategory.Environmental.Void -> listOf("Fell out of the world.", "Lost to the void. No refunds.", "Gone. Items too.")
        DeathCategory.Environmental.Lightning -> listOf("Struck by lightning.", "Smitten by the sky.", "Zapped. Unlucky.")
        DeathCategory.Environmental.Cactus -> listOf("Hugged a cactus.", "Lost a fight to a plant.", "A prickly ending.")
        DeathCategory.Environmental.Suffocation -> listOf("Became part of the wall.", "Stuck in a block. Forever.", "Needed more air, less dirt.")
        DeathCategory.Environmental.Starvation -> listOf("Forgot to eat.", "Starved with a full inventory.", "Hunger bar: empty. Life: also.")
        DeathCategory.Environmental.FallingBlock -> listOf("Flattened by a falling block.", "An anvil, cartoon style.", "Looked up too late.")
        DeathCategory.Environmental.Magma -> listOf("The floor was lava. Sort of.", "Should have sneaked on magma.", "Toasted from the feet up.")
        DeathCategory.Environmental.BerryBush -> listOf("Murdered by berries.", "The bush fought back.", "Death by fruit salad.")
        DeathCategory.Environmental.Freeze -> listOf("Froze solid.", "Powder snow claims another.", "Chilled to the bone.")
        DeathCategory.Environmental.Explosion -> listOf("Blown to bits.", "Exploded. Unclear why.", "Kaboom.")
        DeathCategory.Environmental.Thorns -> listOf("Hurt themselves in confusion.", "Hit something spiky.", "Thorns did the rest.")
        DeathCategory.PvP -> PVP_HEADLINES.map { it.replace("{killer}", "a player") }
        DeathCategory.Unknown -> listOf("Died mysteriously.", "Cause of death: unclear.", "It just happened.")
    }

    private val PVP_HEADLINES = listOf(
        "Slain by {killer}.",
        "Sent home by {killer}.",
        "{killer} wins this round.",
        "Deleted by {killer}.",
        "Outplayed by {killer}."
    )

    private val PVP_WEAPON_HEADLINES = listOf(
        "Slain by {killer}'s {weapon}.",
        "Taken out by {killer} and {weapon}.",
        "{killer} brought {weapon}."
    )

    // ========== EPITAPHS ==========

    private fun epitaph(
        category: DeathCategory,
        stats: Map<String, Int>?,
        powerLevel: Int?,
        picker: NoRepeatPicker,
        random: Random
    ): String {
        if (powerLevel != null && powerLevel >= ELITE_POWER_THRESHOLD && category.isEmbarrassing()) {
            DeathCommentaryRepository.getEliteRoast(category, powerLevel, ELITE_POWER_THRESHOLD)?.let { return it }
        }
        if (stats != null && random.nextFloat() < SKILL_ROAST_CHANCE) {
            skillRoast(category, stats, picker)?.let { return it }
        }
        return DeathCommentaryRepository.getCommentary(category)
    }

    private fun skillRoast(category: DeathCategory, stats: Map<String, Int>, picker: NoRepeatPicker): String? {
        val skill = relevantSkill(category, stats) ?: return null
        val level = stats[skill] ?: return null
        val isMob = category is DeathCategory.Mob

        val (key, lines) = when {
            level >= HIGH_SKILL_THRESHOLD -> "high:$skill" to (HIGH_SKILL[skill].orEmpty() + HIGH_GENERIC + if (isMob) HIGH_VS_MOB else emptyList())
            level < LOW_SKILL_THRESHOLD -> "low:$skill" to (LOW_SKILL[skill].orEmpty() + LOW_GENERIC)
            else -> return null
        }
        return picker.pick("card-skill:$key", lines)
            .replace("{skill}", formatSkillName(skill))
            .replace("{level}", level.toString())
            .replace("{cause}", causeName(category))
    }

    private fun pvpEpitaph(victimName: String, victimStats: Map<String, Int>?, pvp: PvpCardInfo, picker: NoRepeatPicker): String? {
        val context = pvp.context
        val notable = context.isRevenge ||
            context.endedStreak >= PvpCommentary.ENDED_STREAK_CALLOUT ||
            context.killerStreak >= PvpCommentary.STREAK_CALLOUT
        if (notable) {
            return PvpCommentary.generate(pvp.killerName, victimName, context, null, fallback = { "" }, picker = picker)
        }

        val skill = pvp.weaponSkill ?: return null
        val killerLevel = pvp.killerStats?.get(skill) ?: return null
        val victimLevel = victimStats?.get(skill) ?: return null
        val lines = if (killerLevel >= victimLevel) PVP_SKILL_GAP else PVP_UPSET
        return picker.pick("card-pvp-skill:${killerLevel >= victimLevel}", lines)
            .replace("{killer}", pvp.killerName)
            .replace("{victim}", victimName)
            .replace("{skill}", formatSkillName(skill))
            .replace("{k}", killerLevel.toString())
            .replace("{v}", victimLevel.toString())
    }

    private val PVP_SKILL_GAP = listOf(
        "{skill} {k} vs. {skill} {v}. The math checks out.",
        "{killer} has {k} {skill}. {victim} has {v}. Any questions?",
        "Brought {skill} {v} to a {skill} {k} fight."
    )

    private val PVP_UPSET = listOf(
        "Higher {skill} than {killer} and still lost. Upset of the year.",
        "{victim} had {skill} {v}. {killer} had {k}. Skill isn't everything.",
        "{skill} {v} vs. {skill} {k}. The underdog bites."
    )

    // ========== SKILL ROASTS ==========

    private val HIGH_GENERIC = listOf(
        "{skill} {level}, and this is how it ends.",
        "{level} levels of {skill}. Zero levels of survival.",
        "{skill} {level}. Somewhere, a trainer is crying.",
        "{skill} {level} looked great on paper."
    )

    private val HIGH_VS_MOB = listOf(
        "{skill} {level} vs. one {cause}. The {cause} won.",
        "All that {skill}, and a {cause} still got the last hit."
    )

    private val HIGH_SKILL = mapOf(
        "ACROBATICS" to listOf("Acrobatics {level}. Didn't roll. Did fall.", "{level} Acrobatics and that was the landing?"),
        "ALCHEMY" to listOf("Alchemy {level} and no Fire Resistance on hand?", "Brewed {level} levels of potions. Forgot the useful one."),
        "FISHING" to listOf("Fishing {level}. The fish finally got even.", "Caught thousands of fish. Became fish food."),
        "HERBALISM" to listOf("Herbalism {level}. Will make excellent compost.", "{level} Herbalism. The plants have unionized."),
        "MINING" to listOf("Mining {level}. Finally hit rock bottom.", "{level} Mining. Dug their own grave, professionally."),
        "TAMING" to listOf("Taming {level}. The animals did not agree.", "{level} Taming, and nature still said no.")
    )

    private val LOW_GENERIC = listOf(
        "{skill} {level}. That explains a lot.",
        "Maybe level {skill} past {level}?",
        "{skill} {level}. Training starts at respawn."
    )

    private val LOW_SKILL = mapOf(
        "ACROBATICS" to listOf("Acrobatics {level}. Gravity noticed.", "Pro tip: Acrobatics helps with falling. Yours is {level}."),
        "ALCHEMY" to listOf("Alchemy {level}. Fire Resistance is a potion, by the way.", "Alchemy {level}. Never met a cauldron."),
        "FISHING" to listOf("Fishing {level}. Never really learned water.", "Fishing {level}. Swims like a rock."),
        "HERBALISM" to listOf("Herbalism {level}. Could not identify food.", "Herbalism {level}. Wheat remains a mystery.")
    )

    /**
     * The skill that should have prevented this death.
     * Mob deaths use the victim's best combat skill.
     */
    @Suppress("CyclomaticComplexMethod") // Flat lookup table
    internal fun relevantSkill(category: DeathCategory, stats: Map<String, Int>): String? = when (category) {
        DeathCategory.Environmental.Fall -> "ACROBATICS"
        DeathCategory.Environmental.Drowning -> "FISHING"
        DeathCategory.Environmental.Lava,
        DeathCategory.Environmental.Fire,
        DeathCategory.Environmental.Magma -> "ALCHEMY"
        DeathCategory.Environmental.Starvation,
        DeathCategory.Environmental.BerryBush,
        DeathCategory.Environmental.Cactus -> "HERBALISM"
        DeathCategory.Environmental.Suffocation,
        DeathCategory.Environmental.FallingBlock -> "MINING"
        DeathCategory.Mob.Wolf,
        DeathCategory.Mob.Bee -> "TAMING"
        is DeathCategory.Mob -> COMBAT_SKILLS.filter { it in stats }.maxByOrNull { stats.getValue(it) }
        else -> null
    }

    // ========== UTILITY ==========

    private fun causeName(category: DeathCategory): String =
        DeathThemes.getDeathCauseName(category).lowercase()
            .split(" ").joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }

    private fun formatSkillName(skill: String): String = skill.lowercase().replaceFirstChar { it.uppercase() }
}
