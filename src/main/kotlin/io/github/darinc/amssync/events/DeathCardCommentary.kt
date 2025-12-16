package io.github.darinc.amssync.events

import io.github.darinc.amssync.image.DeathThemes
import kotlin.random.Random

/**
 * Enhanced sarcastic commentary system for death cards.
 *
 * Incorporates MCMMO skill data for personalized, skill-specific roasts.
 * Generates two types of messages:
 * - Main sarcastic message (appears in death message box)
 * - Commentary (appears below the coffin)
 */
@Suppress("MaxLineLength")
object DeathCardCommentary {

    private val random = Random.Default

    /** High skill threshold for ironic roasts */
    const val HIGH_SKILL_THRESHOLD = 400

    /** Low skill threshold for "needs training" roasts */
    const val LOW_SKILL_THRESHOLD = 100

    /** Elite power level threshold */
    const val ELITE_POWER_THRESHOLD = 3000

    // ========== PUBLIC API ==========

    /**
     * Generate both the main sarcastic message and commentary.
     *
     * @param category Death category
     * @param playerStats Map of skill name to level (nullable)
     * @param powerLevel Player's power level (nullable)
     * @return Pair of (sarcasticMessage, commentary)
     */
    fun generateMessages(
        category: DeathCategory,
        playerStats: Map<String, Int>?,
        powerLevel: Int?
    ): Pair<String, String> {
        val causeName = DeathThemes.getDeathCauseName(category)

        // Generate main sarcastic message
        val sarcasticMessage = generateSarcasticMessage(category, causeName, playerStats, powerLevel)

        // Generate commentary
        val commentary = generateCommentary(category, playerStats, powerLevel)

        return sarcasticMessage to commentary
    }

    /**
     * Generate the main sarcastic death message.
     * Format: "Slain by a WARDEN. How EPIC. Your Herbalism won't help you now."
     */
    private fun generateSarcasticMessage(
        category: DeathCategory,
        causeName: String,
        playerStats: Map<String, Int>?,
        powerLevel: Int?
    ): String {
        val slainBy = getSlainByPhrase(category, causeName)
        val tierComment = if (powerLevel != null) getTierComment(powerLevel) else ""
        val skillComment = getSkillComment(category, playerStats)

        return buildString {
            append(slainBy)
            if (tierComment.isNotEmpty()) {
                append(" ")
                append(tierComment)
            }
            if (skillComment.isNotEmpty()) {
                append(" ")
                append(skillComment)
            }
        }
    }

    private fun getSlainByPhrase(category: DeathCategory, causeName: String): String {
        return when (category) {
            is DeathCategory.Mob -> {
                val phrases = listOf(
                    "Slain by a $causeName.",
                    "Killed by a $causeName.",
                    "Defeated by a $causeName.",
                    "Eliminated by a $causeName."
                )
                phrases.random()
            }
            is DeathCategory.Environmental -> {
                val phrases = when (category) {
                    DeathCategory.Environmental.Fall -> listOf("Gravity wins again.", "The floor said hello.", "Splat.")
                    DeathCategory.Environmental.Lava -> listOf("Took a lava bath.", "Became extra crispy.", "Swimming in lava.")
                    DeathCategory.Environmental.Drowning -> listOf("Forgot to breathe.", "Went swimming forever.", "Glub glub.")
                    DeathCategory.Environmental.Fire -> listOf("Caught fire.", "Became a torch.", "Too hot to handle.")
                    DeathCategory.Environmental.Void -> listOf("Fell into the void.", "Embraced the abyss.", "Gone forever.")
                    DeathCategory.Environmental.Starvation -> listOf("Forgot to eat.", "Starved to death.", "Died hungry.")
                    DeathCategory.Environmental.Cactus -> listOf("Hugged a cactus.", "Prickly end.", "Death by plant.")
                    DeathCategory.Environmental.Freeze -> listOf("Froze to death.", "Became an ice cube.", "Cold ending.")
                    else -> listOf("Died to $causeName.", "Killed by $causeName.")
                }
                phrases.random()
            }
            is DeathCategory.PvP -> {
                listOf("Got owned.", "Skill issue.", "Outplayed.", "GG.").random()
            }
            else -> "Died mysteriously."
        }
    }

    private fun getTierComment(powerLevel: Int): String {
        return when {
            powerLevel >= 10000 -> listOf("How LEGENDARY.", "Legends CAN fall.", "Even legends die.").random()
            powerLevel >= 5000 -> listOf("How EPIC.", "An EPIC failure.", "Not so EPIC now.").random()
            powerLevel >= 1000 -> listOf("How RARE.", "RARE but dead.", "That's not very RARE.").random()
            else -> ""
        }
    }

    private fun getSkillComment(category: DeathCategory, stats: Map<String, Int>?): String {
        if (stats == null) return ""

        val relevantSkill = getRelevantSkill(category)
        val topSkill = stats.maxByOrNull { it.value }

        // Try relevant skill first
        if (relevantSkill != null) {
            val skillLevel = stats[relevantSkill] ?: 0
            if (skillLevel > HIGH_SKILL_THRESHOLD) {
                return getIronicSkillComment(relevantSkill, skillLevel)
            }
        }

        // Fall back to top skill
        if (topSkill != null && topSkill.value > 200) {
            return getTopSkillComment(topSkill.key, topSkill.value)
        }

        return ""
    }

    private fun getIronicSkillComment(skill: String, level: Int): String {
        val formattedSkill = formatSkillName(skill)
        return listOf(
            "Your $level $formattedSkill won't help you now.",
            "$formattedSkill $level didn't save you.",
            "All that $formattedSkill training, wasted.",
            "$level in $formattedSkill and THIS happens?"
        ).random()
    }

    private fun getTopSkillComment(skill: String, level: Int): String {
        val formattedSkill = formatSkillName(skill)
        return listOf(
            "Your $formattedSkill won't help you now.",
            "$formattedSkill master, still died.",
            "Maybe train something else besides $formattedSkill?"
        ).random()
    }

    // ========== COMMENTARY GENERATION ==========

    /**
     * Generate the commentary that appears below the coffin.
     */
    private fun generateCommentary(
        category: DeathCategory,
        stats: Map<String, Int>?,
        powerLevel: Int?
    ): String {
        // Try skill-specific commentary first
        if (stats != null && powerLevel != null) {
            val skillCommentary = trySkillSpecificCommentary(category, stats, powerLevel)
            if (skillCommentary != null) return skillCommentary
        }

        // Try elite roast if applicable
        if (powerLevel != null && powerLevel >= ELITE_POWER_THRESHOLD && category.isEmbarrassing()) {
            val eliteRoast = DeathCommentaryRepository.getEliteRoast(category, powerLevel)
            if (eliteRoast != null) return eliteRoast
        }

        // Fall back to standard commentary
        return DeathCommentaryRepository.getCommentary(category)
    }

    private fun trySkillSpecificCommentary(
        category: DeathCategory,
        stats: Map<String, Int>,
        powerLevel: Int
    ): String? {
        val relevantSkill = getRelevantSkill(category) ?: return null
        val skillLevel = stats[relevantSkill] ?: return null

        // High skill + relevant death = maximum irony
        if (skillLevel >= HIGH_SKILL_THRESHOLD) {
            return getIronicCommentary(relevantSkill, skillLevel)
        }

        // Low skill + relevant death = "needed more training"
        if (skillLevel < LOW_SKILL_THRESHOLD) {
            return getLowSkillCommentary(relevantSkill, skillLevel)
        }

        // Medium skill - random chance of skill-specific or generic
        return if (random.nextFloat() < 0.5f) {
            getMidSkillCommentary(relevantSkill, skillLevel)
        } else {
            null
        }
    }

    // ========== SKILL-SPECIFIC ROASTS ==========

    private val IRONIC_COMMENTARY = mapOf(
        "ACROBATICS" to listOf(
            "But don't worry, your {level} levels of Acrobatics make great fertilizer.",
            "{level} Acrobatics and THAT'S your landing technique?",
            "All that Acrobatics training. Zero style points.",
            "Acrobatics {level}. Graceful in life, splat in death."
        ),
        "ARCHERY" to listOf(
            "Your {level} Archery skill couldn't outshoot that one.",
            "{level} in Archery and still got outshot. Ironic.",
            "All those arrows trained, none of them helped.",
            "Hawkeye would be disappointed. Archery {level}."
        ),
        "SWORDS" to listOf(
            "Your {level} Swords skill couldn't cut it.",
            "All that sword training at level {level}, wasted.",
            "{level} in Swords. The mob didn't care.",
            "Swords master. Mob victim."
        ),
        "AXES" to listOf(
            "Your {level} Axes skill chopped nothing useful.",
            "{level} Axes training couldn't axe your way out.",
            "Maybe next time use those {level} levels of Axes."
        ),
        "UNARMED" to listOf(
            "Your {level} Unarmed skill punched nothing today.",
            "{level} in Unarmed. Fists of fury, feet of clay.",
            "All that hand-to-hand training at {level}. Still dead."
        ),
        "TAMING" to listOf(
            "Your {level} Taming couldn't tame THAT.",
            "{level} in Taming and the animals still hate you.",
            "Maybe try taming something that WON'T kill you."
        ),
        "MINING" to listOf(
            "Your {level} Mining skill couldn't dig you out of this one.",
            "{level} in Mining. Should've mined your own business.",
            "All those hours mining at level {level}. Still got buried."
        ),
        "WOODCUTTING" to listOf(
            "Your {level} Woodcutting skill couldn't chop through THAT.",
            "{level} in Woodcutting. Log off, permanently.",
            "Should've stuck to trees. Woodcutting {level}."
        ),
        "HERBALISM" to listOf(
            "But don't worry, your {level} levels of Herbalism will make excellent fertilizer.",
            "{level} in Herbalism. At least you'll decompose nicely.",
            "All those plants at level {level}. None of them could save you.",
            "Herbalism master at {level}. Plant food now."
        ),
        "EXCAVATION" to listOf(
            "Your {level} Excavation skill couldn't dig you out of THAT.",
            "{level} in Excavation. Time to dig your own grave.",
            "All that digging at level {level}. Now someone digs for YOU."
        ),
        "FISHING" to listOf(
            "Your {level} Fishing skill is sleeping with the fishes.",
            "{level} in Fishing. Fish: 1, You: 0.",
            "Master angler at {level}. Got hooked instead.",
            "All those fish at level {level}. Still drowned."
        ),
        "ALCHEMY" to listOf(
            "Your {level} Alchemy skill brewed nothing useful.",
            "{level} in Alchemy. No potion for THAT.",
            "Should've brewed a survival potion. Alchemy {level}.",
            "All those potions at level {level}. Forgot the important one."
        ),
        "REPAIR" to listOf(
            "Your {level} Repair skill couldn't fix THAT.",
            "{level} in Repair. Some things can't be fixed.",
            "Master repairer at {level}. Body: unfixable."
        )
    )

    private val LOW_SKILL_COMMENTARY = mapOf(
        "ACROBATICS" to listOf(
            "Maybe train Acrobatics above {level}?",
            "Acrobatics {level}. That explains the splat.",
            "Pro tip: Acrobatics helps with falling. Yours is {level}."
        ),
        "FISHING" to listOf(
            "Fishing {level}. Try breathing lessons first.",
            "Maybe train Fishing. Currently: {level}.",
            "Water training 101: don't drown. Fishing: {level}."
        ),
        "SWORDS" to listOf(
            "Swords {level}. That mob felt zero resistance.",
            "With Swords at {level}, what did you expect?",
            "Maybe try leveling Swords past {level}?"
        ),
        "ACROBATICS" to listOf(
            "Acrobatics {level}. Explains the gravity issue.",
            "Perhaps invest in Acrobatics training? Currently: {level}."
        ),
        "ALCHEMY" to listOf(
            "Alchemy {level}. Fire Resistance exists, you know.",
            "Pro tip: Alchemy makes survival potions. Yours: {level}."
        ),
        "TAMING" to listOf(
            "Taming {level}. The animals are NOT impressed.",
            "Maybe level Taming before approaching wildlife? Current: {level}."
        )
    )

    private val MID_SKILL_COMMENTARY = mapOf(
        "ACROBATICS" to listOf(
            "Acrobatics {level}. Not bad, but not enough.",
            "Keep training Acrobatics. {level} almost saved you."
        ),
        "SWORDS" to listOf(
            "Swords {level}. You'll get there eventually.",
            "Close, but Swords {level} wasn't enough today."
        ),
        "FISHING" to listOf(
            "Fishing {level}. Almost pro, still drowning.",
            "Keep at it. Fishing {level} is getting there."
        )
    )

    private fun getIronicCommentary(skill: String, level: Int): String {
        val messages = IRONIC_COMMENTARY[skill] ?: IRONIC_COMMENTARY["SWORDS"]!!
        return messages.random().replace("{level}", level.toString())
    }

    private fun getLowSkillCommentary(skill: String, level: Int): String {
        val messages = LOW_SKILL_COMMENTARY[skill] ?: listOf(
            "Maybe train ${formatSkillName(skill)}? Currently: {level}.",
            "${formatSkillName(skill)} {level}. That explains a lot."
        )
        return messages.random().replace("{level}", level.toString())
    }

    private fun getMidSkillCommentary(skill: String, level: Int): String {
        val messages = MID_SKILL_COMMENTARY[skill] ?: listOf(
            "${formatSkillName(skill)} {level}. Getting there.",
            "Keep training ${formatSkillName(skill)}. {level} is respectable."
        )
        return messages.random().replace("{level}", level.toString())
    }

    // ========== UTILITY ==========

    /**
     * Get the relevant skill for a death category.
     */
    private fun getRelevantSkill(category: DeathCategory): String? {
        return when (category) {
            is DeathCategory.Environmental.Fall -> "ACROBATICS"
            is DeathCategory.Environmental.Drowning -> "FISHING"
            is DeathCategory.Environmental.Lava,
            is DeathCategory.Environmental.Fire,
            is DeathCategory.Environmental.Magma -> "ALCHEMY"
            is DeathCategory.Mob.Skeleton -> "ARCHERY"
            is DeathCategory.Mob.Zombie -> "SWORDS"
            is DeathCategory.Mob.Spider -> "ARCHERY"
            is DeathCategory.Mob.Creeper -> "MINING"
            is DeathCategory.Mob.Wolf,
            is DeathCategory.Mob.Bee -> "TAMING"
            is DeathCategory.Environmental.Starvation,
            is DeathCategory.Environmental.BerryBush -> "HERBALISM"
            is DeathCategory.Environmental.Suffocation,
            is DeathCategory.Environmental.FallingBlock -> "MINING"
            is DeathCategory.PvP -> "UNARMED"
            is DeathCategory.Mob.Wither,
            is DeathCategory.Mob.EnderDragon,
            is DeathCategory.Mob.Warden -> "AXES"
            is DeathCategory.Mob.Blaze,
            is DeathCategory.Mob.Ghast -> "ARCHERY"
            else -> null
        }
    }

    /**
     * Format skill name for display.
     */
    private fun formatSkillName(skill: String): String {
        return skill.lowercase().replaceFirstChar { it.uppercase() }
    }
}
