package io.github.darinc.amssync.events

import kotlin.random.Random

/**
 * Represents categories of death for commentary selection.
 * Uses sealed class hierarchy for exhaustive matching.
 */
sealed class DeathCategory {

    /**
     * Mob-specific deaths.
     */
    sealed class Mob : DeathCategory() {
        data object Creeper : Mob()
        data object Skeleton : Mob()
        data object Zombie : Mob()
        data object Spider : Mob()
        data object Enderman : Mob()
        data object Wither : Mob()
        data object EnderDragon : Mob()
        data object Blaze : Mob()
        data object Ghast : Mob()
        data object Piglin : Mob()
        data object Warden : Mob()
        data object Witch : Mob()
        data object Phantom : Mob()
        data object Guardian : Mob()
        data object Slime : Mob()
        data object Wolf : Mob()
        data object Bee : Mob()
        data object Golem : Mob()
        data object GenericMob : Mob()
    }

    /**
     * Environmental deaths.
     */
    sealed class Environmental : DeathCategory() {
        data object Fall : Environmental()
        data object Lava : Environmental()
        data object Fire : Environmental()
        data object Drowning : Environmental()
        data object Void : Environmental()
        data object Lightning : Environmental()
        data object Cactus : Environmental()
        data object Suffocation : Environmental()
        data object Starvation : Environmental()
        data object FallingBlock : Environmental()
        data object Magma : Environmental()
        data object BerryBush : Environmental()
        data object Freeze : Environmental()
        data object Explosion : Environmental()
        data object Thorns : Environmental()
    }

    /**
     * Player-caused deaths.
     */
    data object PvP : DeathCategory()

    /**
     * Catch-all for unclassified deaths.
     */
    data object Unknown : DeathCategory()

    /**
     * Returns true if this death type is considered "embarrassing"
     * for MCMMO roast purposes.
     */
    fun isEmbarrassing(): Boolean = when (this) {
        is Environmental.Starvation -> true
        is Environmental.Cactus -> true
        is Environmental.Fall -> true
        is Environmental.Drowning -> true
        is Environmental.BerryBush -> true
        is Environmental.Freeze -> true
        is Mob.Bee -> true
        is Mob.Slime -> true
        else -> false
    }
}

/**
 * Repository of funny commentary messages for death announcements.
 * All messages are embedded in code (not configurable).
 */
@Suppress("MaxLineLength")
object DeathCommentaryRepository {

    private val random = Random.Default

    /** MCMMO power level threshold for "elite" status */
    const val DEFAULT_ELITE_POWER_THRESHOLD = 3000

    // ============================================================================
    // MOB DEATH MESSAGES
    // ============================================================================

    private val CREEPER_MESSAGES = listOf(
        "That's a nice everything you had there...",
        "Sssssssorry about that.",
        "Surprise party! You were the pinata.",
        "The creeper just wanted a hug. A final hug.",
        "Tick, tick, BOOM!",
        "Personal space? Never heard of it.",
        "Should've invested in Blast Protection.",
        "That creeper was clearly a cat person.",
        "Green means go... to the respawn screen.",
        "Creeper? Aw man..."
    )

    private val SKELETON_MESSAGES = listOf(
        "360 no-scoped!",
        "That skeleton has been practicing.",
        "Bones aren't supposed to have that good of aim.",
        "Robin Hood called, he wants his archer back.",
        "The skeleton union sends their regards.",
        "Death by calcium. Ironic.",
        "Maybe carry a shield next time?",
        "Hawkeye's bony cousin strikes again.",
        "You got boned."
    )

    private val ZOMBIE_MESSAGES = listOf(
        "Braaaaains! Or lack thereof.",
        "The walking dead sends their regards.",
        "Couldn't outrun something that walks?",
        "Zombies are attracted to your intelligence. Oh wait...",
        "The undead welcome their newest member.",
        "Slow and steady wins the race, apparently.",
        "Night of the Living Dead: Minecraft Edition.",
        "They just wanted to give you a hug. With their teeth."
    )

    private val SPIDER_MESSAGES = listOf(
        "Eight legs of NOPE.",
        "Charlotte's web of destruction.",
        "Spider-Man's evil cousin strikes again.",
        "Australia called, they want their spider back.",
        "Time to burn down the whole server.",
        "The itsy bitsy spider climbed up and murdered you.",
        "Arachnophobia justified.",
        "Web developer? More like web destroyer."
    )

    private val ENDERMAN_MESSAGES = listOf(
        "Never look them in the eyes. NEVER.",
        "*Teleports behind you* Nothing personnel, kid.",
        "The end is nigh. Literally.",
        "Staring contest champion: Enderman.",
        "Blinked at the wrong moment.",
        "That's what you get for making eye contact.",
        "Should've worn a pumpkin.",
        "Tall, dark, and deadly."
    )

    private val WITHER_MESSAGES = listOf(
        "Withered away like your hopes and dreams.",
        "Three heads are better than one, apparently.",
        "You've been withered away from existence.",
        "The Wither sends its regards.",
        "Maybe don't summon ancient evils next time?",
        "Congratulations on the achievement... wait, wrong kind.",
        "The skulls were a warning."
    )

    private val DRAGON_MESSAGES = listOf(
        "The dragon always wins.",
        "Game of Thrones called, they want their death scene back.",
        "Dracarys!",
        "Not all who wander into The End survive.",
        "The dragon sends its regards from the void.",
        "Achievement Unlocked: Dragon Food",
        "Breath of the Wild? More like Breath of Death.",
        "The End... is your end."
    )

    private val BLAZE_MESSAGES = listOf(
        "Too hot to handle!",
        "Fire beats player. Always.",
        "Blaze it... wait, not like that.",
        "The Nether sends its hottest regards.",
        "Roasted. Literally.",
        "That's one spicy meatball.",
        "Fire Resistance? Never heard of her."
    )

    private val GHAST_MESSAGES = listOf(
        "Ghastly performance out there.",
        "Crying won't help now.",
        "The ghost of gameplay past.",
        "Should've brought a tennis racket.",
        "Return to sender failed.",
        "The Nether's ugliest fireball launcher.",
        "That's one angry flying tissue."
    )

    private val WARDEN_MESSAGES = listOf(
        "You felt that one in your bones. All of them.",
        "The Warden heard you talking trash.",
        "Stealth: 0/10",
        "Deaf to your pleas, not to your footsteps.",
        "The deep dark claims another victim.",
        "Maybe try being quieter?",
        "Sneak level: Not enough.",
        "The ancient cities demand silence."
    )

    private val PHANTOM_MESSAGES = listOf(
        "Should've gone to bed.",
        "Sleep is important. Now you know.",
        "The consequences of insomnia.",
        "Night owl? More like night target.",
        "The sky is not safe anymore.",
        "Sweet dreams... NOT."
    )

    private val GUARDIAN_MESSAGES = listOf(
        "Laser tag champion: Guardian.",
        "Mining fatigue was just the appetizer.",
        "The ocean temple's security system is effective.",
        "Nemo's angry cousin says hello.",
        "Should've brought milk."
    )

    private val WITCH_MESSAGES = listOf(
        "Double, double, toil and you're dead.",
        "Potion master? More like potion disaster.",
        "The witch cackles in the distance.",
        "Chemical warfare is no joke.",
        "Should've brought Golden Apples."
    )

    private val BEE_MESSAGES = listOf(
        "According to all known laws of aviation... oops, wrong script.",
        "Bee-trayed by nature.",
        "That bee chose violence.",
        "Stung to the grave.",
        "Not the bees! NOT THE BEES!",
        "Ya like jazz? They didn't."
    )

    private val SLIME_MESSAGES = listOf(
        "Bounced to death.",
        "Jello has claimed another victim.",
        "The slime sends its jiggly regards.",
        "Death by cube. Humiliating.",
        "Slime time is all the time.",
        "The cube of doom strikes again."
    )

    private val WOLF_MESSAGES = listOf(
        "Man's best friend? Not this one.",
        "Who's a good boy? Not you, apparently.",
        "Should've brought bones.",
        "The pack shows no mercy.",
        "Domestication attempt: Failed."
    )

    private val GOLEM_MESSAGES = listOf(
        "Iron fist of justice!",
        "The village protector protects... from you.",
        "Villager HR has dealt with the complaint.",
        "Should've been nicer to villagers.",
        "Metal and murder don't mix."
    )

    private val GENERIC_MOB_MESSAGES = listOf(
        "The monsters are winning.",
        "Another one bites the dust!",
        "The hostile mobs send their regards.",
        "Nature is brutal. So is Minecraft.",
        "At least it was quick. Maybe.",
        "RIP. Press F to pay respects.",
        "The food chain has spoken.",
        "Survival of the fittest. You weren't."
    )

    // ============================================================================
    // ENVIRONMENTAL DEATH MESSAGES
    // ============================================================================

    private val FALL_MESSAGES = listOf(
        "Gravity always wins.",
        "The ground broke their fall. And everything else.",
        "Icarus flew too close to the sun... wait, wrong story.",
        "Trust fall gone wrong.",
        "Free falling, but not Tom Petty style.",
        "Parkour! ...Parkour?",
        "What goes up must come down. Hard.",
        "The floor is lava... actually no, the floor is just hard.",
        "Newton sends his regards.",
        "Legs are overrated anyway.",
        "Feather Falling? Never heard of her."
    )

    private val LAVA_MESSAGES = listOf(
        "Floor is lava: Expert difficulty.",
        "Forbidden hot tub.",
        "Orange juice... THE BAD KIND.",
        "Minecraft lava: Nature's garbage disposal.",
        "At least they're warm now. Very warm.",
        "Spicy water claims another victim.",
        "Fire resistance potions exist, just saying.",
        "The forbidden swimming pool.",
        "Cremation: Instant delivery.",
        "That's one way to get a tan."
    )

    private val FIRE_MESSAGES = listOf(
        "Playing with fire has consequences.",
        "This is fine. Everything is fine.",
        "Stop, drop, and... too late.",
        "Smokey the Bear is disappointed.",
        "Hot take: fire bad.",
        "Spontaneous combustion is a lifestyle choice.",
        "Flame on! ...and stay on.",
        "Well done. Literally."
    )

    private val DROWNING_MESSAGES = listOf(
        "Should've brought a water breathing potion.",
        "Aquaman would be disappointed.",
        "Swimming lessons needed.",
        "The ocean claims another victim.",
        "Forgot that breathing was important.",
        "Fish are laughing somewhere.",
        "Water you thinking?!",
        "Glub glub... that's all folks.",
        "The sea was angry that day, my friends.",
        "Depth Strider doesn't help with breathing."
    )

    private val VOID_MESSAGES = listOf(
        "Into the void they go!",
        "The void stared back. And won.",
        "Gone, reduced to atoms.",
        "To infinity and beyond! ...mostly beyond.",
        "Yeeted into the abyss.",
        "The void is hungry.",
        "Achievement Unlocked: Free Falling Forever",
        "The end of the world. Literally.",
        "Beyond the edge of reason."
    )

    private val LIGHTNING_MESSAGES = listOf(
        "Thor says hello!",
        "Shocking turn of events.",
        "Zeus was NOT happy today.",
        "Thunder, thunder, thunderstruck!",
        "Static electricity's angry cousin.",
        "The storm chose violence.",
        "Conductor of electricity. Briefly.",
        "Weather forecast: 100% chance of death."
    )

    private val CACTUS_MESSAGES = listOf(
        "Death by succulent. Embarrassing.",
        "The cactus sends its prickly regards.",
        "Hugged a cactus. It did not hug back.",
        "Sharp lesson learned.",
        "Desert plant: 1, Player: 0",
        "That cactus was just defending itself. Aggressively.",
        "Green and mean.",
        "Nature's passive-aggressive roommate.",
        "Poke. Poke. Dead."
    )

    private val SUFFOCATION_MESSAGES = listOf(
        "Buried alive. Classic.",
        "Personal space violated by blocks.",
        "The walls are closing in!",
        "Sand/gravel is not your friend.",
        "Mining without looking up: a cautionary tale.",
        "Claustrophobic yet?",
        "Block party gone wrong."
    )

    private val STARVATION_MESSAGES = listOf(
        "Should've packed a lunch.",
        "Hunger games: You lost.",
        "Forgot that eating was a thing?",
        "The snack bar was RIGHT THERE.",
        "Hangry to the grave.",
        "Diet went too far.",
        "Food? What's food?",
        "The most preventable death award goes to...",
        "Even zombies eat. Just saying."
    )

    private val BERRY_BUSH_MESSAGES = listOf(
        "Death by berry bush. Really?",
        "The forbidden fruit strikes back.",
        "Nature's tiny murder bush.",
        "Should've watched where you walked.",
        "Berries: Delicious AND deadly."
    )

    private val FREEZE_MESSAGES = listOf(
        "Let it go... of your life.",
        "Ice ice baby... ice ice... dead.",
        "Winter is coming. Winter is here. You're frozen.",
        "Should've packed a jacket.",
        "Cold never bothered them? It did now."
    )

    private val EXPLOSION_MESSAGES = listOf(
        "Boom goes the dynamite!",
        "Explosive personality, explosive ending.",
        "TNT: Trust No Thing.",
        "Should've read the warning label.",
        "Michael Bay would be proud."
    )

    private val THORNS_MESSAGES = listOf(
        "Karma is wearing enchanted armor.",
        "Attacked the wrong person.",
        "Violence begets violence.",
        "Every rose has its thorns.",
        "Return to sender: Damage."
    )

    // ============================================================================
    // PVP MESSAGES
    // ============================================================================

    private val PVP_MESSAGES = listOf(
        "Get rekt!",
        "Skill issue.",
        "Outplayed!",
        "The PvP gods have spoken.",
        "Maybe try creative mode?",
        "GG... just GG.",
        "Better luck next time, champ.",
        "That's gonna leave a mark on the K/D ratio.",
        "They chose violence. Violence won.",
        "1v1 me bro... oh wait, they did.",
        "Deleted.",
        "Sent to the shadow realm.",
        "You've been served... a respawn screen."
    )

    // ============================================================================
    // UNKNOWN/GENERIC MESSAGES
    // ============================================================================

    private val GENERIC_MESSAGES = listOf(
        "And they were never seen again...",
        "Another one bites the dust!",
        "RIP. Gone but not forgotten.",
        "F in the chat.",
        "Life comes at you fast.",
        "Well, that happened.",
        "Task failed successfully.",
        "Press F to pay respects.",
        "That's rough, buddy.",
        "The universe has a sense of humor.",
        "Today was not their day."
    )

    // ============================================================================
    // MCMMO ELITE ROAST MESSAGES (for high-level players dying to embarrassing things)
    // ============================================================================

    private val ELITE_ROAST_MESSAGES = mapOf(
        DeathCategory.Environmental.Starvation to listOf(
            "Power level {level} and forgot to eat? MCMMO can't teach common sense.",
            "All those skills and none of them are 'remembering to eat'.",
            "{level} power level, zero survival instincts.",
            "Maxed out skills but couldn't find the hunger bar?",
            "The grind was so real they forgot to eat. Literally.",
            "Training montage didn't include a meal break.",
            "{level} power and defeated by... hunger."
        ),
        DeathCategory.Environmental.Cactus to listOf(
            "Power level {level} vs a plant. The plant won.",
            "Survived dragons, defeated withers, killed by... a cactus.",
            "{level} power level can't protect against pointy green things.",
            "All those combat skills and THIS is how it ends?",
            "The desert's most dangerous predator claims another elite.",
            "Who needs enemies when you have cacti?",
            "{level} levels of training didn't cover 'avoid spiky plants'."
        ),
        DeathCategory.Environmental.Fall to listOf(
            "Power level {level}. Acrobatics level: clearly not enough.",
            "Maxed MCMMO but forgot about gravity.",
            "{level} power level couldn't stick the landing.",
            "All those levels and they still can't fly.",
            "Impressive skills, terrible depth perception.",
            "Ground floor reached. Permanently.",
            "{level} power and still couldn't roll with it."
        ),
        DeathCategory.Environmental.Drowning to listOf(
            "Power level {level} and drowned? Even the fish are laughing.",
            "Mastered every skill except swimming, apparently.",
            "{level} power level vs water. Water wins.",
            "MCMMO doesn't have a 'breathing underwater' skill. Tragic.",
            "Elite player, amateur swimmer.",
            "Trained for combat, forgot about cardio.",
            "{level} levels and still can't hold their breath."
        ),
        DeathCategory.Environmental.BerryBush to listOf(
            "Power level {level}... killed by a bush.",
            "Defeated the Ender Dragon, fell to a berry bush.",
            "All that grinding, ended by foliage.",
            "{level} power level means nothing to the mighty berry bush.",
            "The most embarrassing death in server history."
        ),
        DeathCategory.Environmental.Freeze to listOf(
            "Power level {level} and couldn't find warmth?",
            "All that training and still got cold feet. Literally.",
            "{level} levels but no cold resistance.",
            "The ice age claims another legend."
        ),
        DeathCategory.Mob.Bee to listOf(
            "Power level {level}, taken down by a bee. Buzz buzz.",
            "Survived the Wither but not a bee? Really?",
            "{level} power level and allergic to bees, apparently.",
            "The bee movie sequel got dark.",
            "All those skills and defeated by something you can fit in a bottle."
        ),
        DeathCategory.Mob.Slime to listOf(
            "Power level {level} and killed by Jello.",
            "Bounced to death. {level} levels well spent.",
            "The slime sends its jiggly regards to the {level} power level legend.",
            "Elite player meets elite slime. Slime wins.",
            "Death by cube. {level} power. Maximum embarrassment."
        )
    )

    // ============================================================================
    // PUBLIC API
    // ============================================================================

    /**
     * Get a random commentary message for the given death category.
     *
     * @param category The death category
     * @return A randomly selected commentary string
     */
    fun getCommentary(category: DeathCategory): String {
        val messages = getMessagesForCategory(category)
        return messages[random.nextInt(messages.size)]
    }

    /**
     * Get MCMMO elite roast message if applicable.
     * Returns null if the death type isn't embarrassing or no roast exists.
     *
     * @param category The death category
     * @param powerLevel The player's MCMMO power level
     * @param threshold Power level threshold for roasts
     * @return A roast message with {level} replaced, or null
     */
    fun getEliteRoast(category: DeathCategory, powerLevel: Int, threshold: Int = DEFAULT_ELITE_POWER_THRESHOLD): String? {
        if (powerLevel < threshold) return null
        if (!category.isEmbarrassing()) return null

        val roasts = ELITE_ROAST_MESSAGES[category] ?: return null
        val message = roasts[random.nextInt(roasts.size)]
        return message.replace("{level}", powerLevel.toString())
    }

    @Suppress("CyclomaticComplexMethod") // Flat lookup table, not complex logic
    private fun getMessagesForCategory(category: DeathCategory): List<String> {
        return when (category) {
            is DeathCategory.Mob.Creeper -> CREEPER_MESSAGES
            is DeathCategory.Mob.Skeleton -> SKELETON_MESSAGES
            is DeathCategory.Mob.Zombie -> ZOMBIE_MESSAGES
            is DeathCategory.Mob.Spider -> SPIDER_MESSAGES
            is DeathCategory.Mob.Enderman -> ENDERMAN_MESSAGES
            is DeathCategory.Mob.Wither -> WITHER_MESSAGES
            is DeathCategory.Mob.EnderDragon -> DRAGON_MESSAGES
            is DeathCategory.Mob.Blaze -> BLAZE_MESSAGES
            is DeathCategory.Mob.Ghast -> GHAST_MESSAGES
            is DeathCategory.Mob.Warden -> WARDEN_MESSAGES
            is DeathCategory.Mob.Witch -> WITCH_MESSAGES
            is DeathCategory.Mob.Phantom -> PHANTOM_MESSAGES
            is DeathCategory.Mob.Guardian -> GUARDIAN_MESSAGES
            is DeathCategory.Mob.Piglin -> GENERIC_MOB_MESSAGES
            is DeathCategory.Mob.Slime -> SLIME_MESSAGES
            is DeathCategory.Mob.Wolf -> WOLF_MESSAGES
            is DeathCategory.Mob.Bee -> BEE_MESSAGES
            is DeathCategory.Mob.Golem -> GOLEM_MESSAGES
            is DeathCategory.Mob.GenericMob -> GENERIC_MOB_MESSAGES
            is DeathCategory.Environmental.Fall -> FALL_MESSAGES
            is DeathCategory.Environmental.Lava -> LAVA_MESSAGES
            is DeathCategory.Environmental.Fire -> FIRE_MESSAGES
            is DeathCategory.Environmental.Drowning -> DROWNING_MESSAGES
            is DeathCategory.Environmental.Void -> VOID_MESSAGES
            is DeathCategory.Environmental.Lightning -> LIGHTNING_MESSAGES
            is DeathCategory.Environmental.Cactus -> CACTUS_MESSAGES
            is DeathCategory.Environmental.Suffocation -> SUFFOCATION_MESSAGES
            is DeathCategory.Environmental.Starvation -> STARVATION_MESSAGES
            is DeathCategory.Environmental.FallingBlock -> SUFFOCATION_MESSAGES
            is DeathCategory.Environmental.Magma -> LAVA_MESSAGES
            is DeathCategory.Environmental.BerryBush -> BERRY_BUSH_MESSAGES
            is DeathCategory.Environmental.Freeze -> FREEZE_MESSAGES
            is DeathCategory.Environmental.Explosion -> EXPLOSION_MESSAGES
            is DeathCategory.Environmental.Thorns -> THORNS_MESSAGES
            DeathCategory.PvP -> PVP_MESSAGES
            DeathCategory.Unknown -> GENERIC_MESSAGES
        }
    }
}
