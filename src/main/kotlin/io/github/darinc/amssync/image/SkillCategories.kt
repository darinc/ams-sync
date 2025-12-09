package io.github.darinc.amssync.image

/**
 * Categorizes MCMMO skills into Combat, Gathering, and Misc groups.
 */
object SkillCategories {

    /**
     * Combat skills - fighting and defense related.
     */
    val COMBAT = setOf(
        "SWORDS",
        "AXES",
        "ARCHERY",
        "UNARMED",
        "TAMING"
    )

    /**
     * Gathering skills - resource collection.
     */
    val GATHERING = setOf(
        "MINING",
        "WOODCUTTING",
        "HERBALISM",
        "EXCAVATION",
        "FISHING"
    )

    /**
     * Miscellaneous skills - crafting and utility.
     */
    val MISC = setOf(
        "REPAIR",
        "ACROBATICS",
        "ALCHEMY"
    )

    /**
     * Display names for skills (proper capitalization).
     */
    private val DISPLAY_NAMES = mapOf(
        "SWORDS" to "Swords",
        "AXES" to "Axes",
        "ARCHERY" to "Archery",
        "UNARMED" to "Unarmed",
        "TAMING" to "Taming",
        "MINING" to "Mining",
        "WOODCUTTING" to "Woodcut",
        "HERBALISM" to "Herbalism",
        "EXCAVATION" to "Excavation",
        "FISHING" to "Fishing",
        "REPAIR" to "Repair",
        "ACROBATICS" to "Acrobatics",
        "ALCHEMY" to "Alchemy"
    )

    /**
     * Get the category for a skill.
     */
    fun getCategory(skill: String): SkillCategory {
        val upperSkill = skill.uppercase()
        return when {
            COMBAT.contains(upperSkill) -> SkillCategory.COMBAT
            GATHERING.contains(upperSkill) -> SkillCategory.GATHERING
            else -> SkillCategory.MISC
        }
    }

    /**
     * Get display name for a skill.
     */
    fun getDisplayName(skill: String): String {
        return DISPLAY_NAMES[skill.uppercase()] ?: skill.lowercase()
            .replaceFirstChar { it.uppercase() }
    }

    /**
     * Categorize all skills from a stats map into their respective categories.
     *
     * @param stats Map of skill name (uppercase) to level
     * @return Triple of (combat skills, gathering skills, misc skills) as sorted maps
     */
    fun categorize(stats: Map<String, Int>): Triple<Map<String, Int>, Map<String, Int>, Map<String, Int>> {
        val combat = mutableMapOf<String, Int>()
        val gathering = mutableMapOf<String, Int>()
        val misc = mutableMapOf<String, Int>()

        for ((skill, level) in stats) {
            val upperSkill = skill.uppercase()
            when {
                COMBAT.contains(upperSkill) -> combat[upperSkill] = level
                GATHERING.contains(upperSkill) -> gathering[upperSkill] = level
                MISC.contains(upperSkill) -> misc[upperSkill] = level
                // Skip unknown skills (like child skills)
            }
        }

        // Sort each category by the defined order
        return Triple(
            sortByOrder(combat, COMBAT),
            sortByOrder(gathering, GATHERING),
            sortByOrder(misc, MISC)
        )
    }

    /**
     * Sort skills by their defined order in the category set.
     */
    private fun sortByOrder(skills: Map<String, Int>, order: Set<String>): Map<String, Int> {
        val orderList = order.toList()
        return skills.toSortedMap(compareBy { orderList.indexOf(it) })
    }

    /**
     * Get skills in a single category from a stats map.
     */
    fun getSkillsInCategory(stats: Map<String, Int>, category: SkillCategory): Map<String, Int> {
        val categorySkills = when (category) {
            SkillCategory.COMBAT -> COMBAT
            SkillCategory.GATHERING -> GATHERING
            SkillCategory.MISC -> MISC
        }

        return stats.filter { (skill, _) ->
            categorySkills.contains(skill.uppercase())
        }
    }

    /**
     * Check if a skill level qualifies for mastery (1000+).
     */
    fun isMastered(level: Int): Boolean = level >= 1000

    /**
     * Get the max level for calculating progress bars.
     */
    const val MAX_SKILL_LEVEL = 1000
}
