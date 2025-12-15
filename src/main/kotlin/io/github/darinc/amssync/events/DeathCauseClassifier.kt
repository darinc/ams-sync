package io.github.darinc.amssync.events

import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent.DamageCause
import org.bukkit.event.entity.PlayerDeathEvent

/**
 * Classifies death events into categories for commentary selection.
 *
 * Uses a hybrid approach:
 * 1. Primary: DamageCause enum from lastDamageCause
 * 2. Secondary: Entity type from EntityDamageByEntityEvent
 * 3. Fallback: Death message text parsing for edge cases
 */
object DeathCauseClassifier {

    /**
     * Classify a death event into a category.
     *
     * @param event The player death event
     * @return The classified death category
     */
    fun classify(event: PlayerDeathEvent): DeathCategory {
        val player = event.entity
        val lastDamage = player.lastDamageCause

        // Check for entity damage (mob or player) first - most specific
        if (lastDamage is EntityDamageByEntityEvent) {
            val damager = lastDamage.damager

            // Check for PvP
            if (damager is Player) {
                return DeathCategory.PvP
            }

            // Check for specific mob types
            val mobCategory = classifyByEntityType(damager.type)
            if (mobCategory != DeathCategory.Unknown) {
                return mobCategory
            }
        }

        // Primary: Use DamageCause enum
        val causeCategory = lastDamage?.cause?.let { classifyByDamageCause(it) }

        // If we got a category from DamageCause, use it
        if (causeCategory != null && causeCategory != DeathCategory.Unknown) {
            return causeCategory
        }

        // Fallback: Parse death message text
        val deathMessage = event.deathMessage()?.let { message ->
            net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText()
                .serialize(message)
        } ?: return DeathCategory.Unknown

        return parseDeathMessage(deathMessage)
    }

    /**
     * Classify by Bukkit's DamageCause enum.
     * Returns null for causes that need more context (like ENTITY_ATTACK).
     */
    internal fun classifyByDamageCause(cause: DamageCause): DeathCategory? {
        return when (cause) {
            DamageCause.FALL -> DeathCategory.Environmental.Fall
            DamageCause.DROWNING -> DeathCategory.Environmental.Drowning
            DamageCause.FIRE, DamageCause.FIRE_TICK -> DeathCategory.Environmental.Fire
            DamageCause.LAVA -> DeathCategory.Environmental.Lava
            DamageCause.VOID -> DeathCategory.Environmental.Void
            DamageCause.LIGHTNING -> DeathCategory.Environmental.Lightning
            DamageCause.SUFFOCATION -> DeathCategory.Environmental.Suffocation
            DamageCause.STARVATION -> DeathCategory.Environmental.Starvation
            DamageCause.CONTACT -> DeathCategory.Environmental.Cactus
            DamageCause.HOT_FLOOR -> DeathCategory.Environmental.Magma
            DamageCause.FALLING_BLOCK -> DeathCategory.Environmental.FallingBlock
            DamageCause.FREEZE -> DeathCategory.Environmental.Freeze
            DamageCause.BLOCK_EXPLOSION -> DeathCategory.Environmental.Explosion
            DamageCause.ENTITY_EXPLOSION -> DeathCategory.Mob.Creeper
            DamageCause.THORNS -> DeathCategory.Environmental.Thorns
            DamageCause.WITHER -> DeathCategory.Mob.Wither
            DamageCause.ENTITY_ATTACK, DamageCause.ENTITY_SWEEP_ATTACK -> null
            DamageCause.PROJECTILE -> null
            else -> null
        }
    }

    /**
     * Classify by Bukkit's EntityType enum for mob deaths.
     */
    internal fun classifyByEntityType(entityType: EntityType): DeathCategory {
        return when (entityType) {
            EntityType.CREEPER -> DeathCategory.Mob.Creeper
            EntityType.SKELETON, EntityType.STRAY, EntityType.WITHER_SKELETON -> DeathCategory.Mob.Skeleton
            EntityType.ZOMBIE, EntityType.ZOMBIE_VILLAGER, EntityType.HUSK, EntityType.DROWNED -> DeathCategory.Mob.Zombie
            EntityType.SPIDER, EntityType.CAVE_SPIDER -> DeathCategory.Mob.Spider
            EntityType.ENDERMAN -> DeathCategory.Mob.Enderman
            EntityType.WITHER -> DeathCategory.Mob.Wither
            EntityType.ENDER_DRAGON -> DeathCategory.Mob.EnderDragon
            EntityType.BLAZE -> DeathCategory.Mob.Blaze
            EntityType.GHAST -> DeathCategory.Mob.Ghast
            EntityType.PIGLIN, EntityType.PIGLIN_BRUTE, EntityType.ZOMBIFIED_PIGLIN -> DeathCategory.Mob.Piglin
            EntityType.WARDEN -> DeathCategory.Mob.Warden
            EntityType.WITCH -> DeathCategory.Mob.Witch
            EntityType.PHANTOM -> DeathCategory.Mob.Phantom
            EntityType.GUARDIAN, EntityType.ELDER_GUARDIAN -> DeathCategory.Mob.Guardian
            EntityType.SLIME, EntityType.MAGMA_CUBE -> DeathCategory.Mob.Slime
            EntityType.WOLF -> DeathCategory.Mob.Wolf
            EntityType.BEE -> DeathCategory.Mob.Bee
            EntityType.IRON_GOLEM -> DeathCategory.Mob.Golem
            EntityType.PLAYER -> DeathCategory.PvP
            else -> DeathCategory.Mob.GenericMob
        }
    }

    /**
     * Parse death message text as a fallback for classification.
     */
    @Suppress("CyclomaticComplexMethod") // Flat pattern matching, not complex logic
    internal fun parseDeathMessage(message: String): DeathCategory {
        val lowerMessage = message.lowercase()

        return when {
            // Specific mob patterns
            "was blown up by creeper" in lowerMessage -> DeathCategory.Mob.Creeper
            "was shot by skeleton" in lowerMessage -> DeathCategory.Mob.Skeleton
            "was shot by stray" in lowerMessage -> DeathCategory.Mob.Skeleton
            "was slain by zombie" in lowerMessage -> DeathCategory.Mob.Zombie
            "was slain by spider" in lowerMessage -> DeathCategory.Mob.Spider
            "was slain by enderman" in lowerMessage -> DeathCategory.Mob.Enderman
            "was killed by wither" in lowerMessage -> DeathCategory.Mob.Wither
            "withered away" in lowerMessage -> DeathCategory.Mob.Wither
            "was slain by ender dragon" in lowerMessage -> DeathCategory.Mob.EnderDragon
            "was fireballed by blaze" in lowerMessage -> DeathCategory.Mob.Blaze
            "was fireballed by ghast" in lowerMessage -> DeathCategory.Mob.Ghast
            "was slain by warden" in lowerMessage -> DeathCategory.Mob.Warden
            "was stung to death" in lowerMessage -> DeathCategory.Mob.Bee
            "was pummeled by" in lowerMessage -> DeathCategory.Mob.Golem
            "was slain by phantom" in lowerMessage -> DeathCategory.Mob.Phantom
            "was slain by guardian" in lowerMessage -> DeathCategory.Mob.Guardian
            "was slain by elder guardian" in lowerMessage -> DeathCategory.Mob.Guardian
            "was killed by witch" in lowerMessage -> DeathCategory.Mob.Witch

            // Environmental patterns
            "hit the ground too hard" in lowerMessage -> DeathCategory.Environmental.Fall
            "fell from a high place" in lowerMessage -> DeathCategory.Environmental.Fall
            "fell off" in lowerMessage -> DeathCategory.Environmental.Fall
            "fell out of the world" in lowerMessage -> DeathCategory.Environmental.Void
            "didn't want to live in the same world as" in lowerMessage -> DeathCategory.Environmental.Void
            "drowned" in lowerMessage -> DeathCategory.Environmental.Drowning
            "tried to swim in lava" in lowerMessage -> DeathCategory.Environmental.Lava
            "burned to death" in lowerMessage -> DeathCategory.Environmental.Fire
            "went up in flames" in lowerMessage -> DeathCategory.Environmental.Fire
            "walked into fire" in lowerMessage -> DeathCategory.Environmental.Fire
            "was struck by lightning" in lowerMessage -> DeathCategory.Environmental.Lightning
            "was pricked to death" in lowerMessage -> DeathCategory.Environmental.Cactus
            "hugged a cactus" in lowerMessage -> DeathCategory.Environmental.Cactus
            "starved to death" in lowerMessage -> DeathCategory.Environmental.Starvation
            "suffocated in a wall" in lowerMessage -> DeathCategory.Environmental.Suffocation
            "was squashed" in lowerMessage -> DeathCategory.Environmental.FallingBlock
            "was squished" in lowerMessage -> DeathCategory.Environmental.FallingBlock
            "froze to death" in lowerMessage -> DeathCategory.Environmental.Freeze
            "blew up" in lowerMessage -> DeathCategory.Environmental.Explosion
            "was poked to death by a sweet berry bush" in lowerMessage -> DeathCategory.Environmental.BerryBush
            "was killed by thorns" in lowerMessage -> DeathCategory.Environmental.Thorns
            "discovered the floor was lava" in lowerMessage -> DeathCategory.Environmental.Magma

            // Generic mob patterns
            "was slain by" in lowerMessage -> DeathCategory.Mob.GenericMob
            "was killed by" in lowerMessage -> DeathCategory.Mob.GenericMob
            "was shot by" in lowerMessage -> DeathCategory.Mob.Skeleton

            // PvP fallback
            "was slain by" in lowerMessage && !containsMobName(lowerMessage) -> DeathCategory.PvP

            else -> DeathCategory.Unknown
        }
    }

    /**
     * Check if the death message contains known mob names.
     */
    private fun containsMobName(message: String): Boolean {
        val mobNames = listOf(
            "zombie", "skeleton", "spider", "creeper", "enderman",
            "wither", "dragon", "blaze", "ghast", "piglin", "warden",
            "witch", "phantom", "guardian", "slime", "wolf", "bee",
            "golem", "vindicator", "pillager", "ravager", "vex",
            "evoker", "shulker", "silverfish", "hoglin", "zoglin"
        )
        return mobNames.any { it in message }
    }
}
