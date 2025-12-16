package io.github.darinc.amssync.image

import io.github.darinc.amssync.events.DeathCategory
import java.awt.Color

/**
 * Maps death categories to visual themes for death cards.
 *
 * Each theme defines:
 * - Background asset filename
 * - Tint color for overlay effects
 * - Associated icon asset path
 */
object DeathThemes {

    /**
     * Visual theme for death card backgrounds.
     *
     * @property backgroundAsset Filename in assets/death/backgrounds/
     * @property tintColor Color tint applied over background
     * @property description Human-readable description
     */
    enum class Theme(
        val backgroundAsset: String,
        val tintColor: Color,
        val description: String
    ) {
        /** Deep Dark / Ancient City theme for Warden */
        DEEP_DARK("deep_dark.png", Color(0, 70, 90), "Deep Dark"),

        /** Nether theme for fire, lava, blazes, ghasts */
        NETHER("nether.png", Color(180, 60, 0), "Nether"),

        /** Ocean theme for drowning, guardians */
        OCEAN("ocean.png", Color(0, 80, 140), "Ocean"),

        /** End dimension theme for dragon, enderman, void */
        END("end.png", Color(80, 0, 100), "The End"),

        /** Overworld theme for most surface mobs */
        OVERWORLD("overworld.png", Color(40, 70, 40), "Overworld"),

        /** Frozen/snowy theme for freeze deaths */
        FROZEN("frozen.png", Color(140, 180, 220), "Frozen"),

        /** Desert theme for cactus deaths */
        DESERT("desert.png", Color(180, 150, 80), "Desert"),

        /** Cave/underground theme for falls, suffocation */
        CAVE("cave.png", Color(60, 50, 50), "Cave"),

        /** PvP arena theme for player kills */
        PVP_ARENA("pvp_arena.png", Color(140, 20, 20), "PvP Arena")
    }

    /**
     * Get the appropriate theme for a death category.
     */
    fun getTheme(category: DeathCategory): Theme {
        return when (category) {
            // Deep Dark theme
            is DeathCategory.Mob.Warden -> Theme.DEEP_DARK

            // Nether theme
            is DeathCategory.Mob.Blaze,
            is DeathCategory.Mob.Ghast,
            is DeathCategory.Mob.Piglin,
            is DeathCategory.Environmental.Lava,
            is DeathCategory.Environmental.Fire,
            is DeathCategory.Environmental.Magma -> Theme.NETHER

            // Ocean theme
            is DeathCategory.Environmental.Drowning,
            is DeathCategory.Mob.Guardian -> Theme.OCEAN

            // End theme
            is DeathCategory.Mob.EnderDragon,
            is DeathCategory.Mob.Enderman,
            is DeathCategory.Environmental.Void -> Theme.END

            // Frozen theme
            is DeathCategory.Environmental.Freeze -> Theme.FROZEN

            // Desert theme
            is DeathCategory.Environmental.Cactus -> Theme.DESERT

            // Cave theme
            is DeathCategory.Environmental.Fall,
            is DeathCategory.Environmental.Suffocation,
            is DeathCategory.Environmental.FallingBlock -> Theme.CAVE

            // PvP theme
            is DeathCategory.PvP -> Theme.PVP_ARENA

            // Default overworld theme for everything else
            else -> Theme.OVERWORLD
        }
    }

    /**
     * Get the mob icon asset filename for a mob death category.
     * Returns null for non-mob deaths.
     */
    fun getMobIconAsset(category: DeathCategory): String? {
        return when (category) {
            is DeathCategory.Mob.Warden -> "warden.png"
            is DeathCategory.Mob.Creeper -> "creeper.png"
            is DeathCategory.Mob.Skeleton -> "skeleton.png"
            is DeathCategory.Mob.Zombie -> "zombie.png"
            is DeathCategory.Mob.Spider -> "spider.png"
            is DeathCategory.Mob.Enderman -> "enderman.png"
            is DeathCategory.Mob.Blaze -> "blaze.png"
            is DeathCategory.Mob.Ghast -> "ghast.png"
            is DeathCategory.Mob.EnderDragon -> "ender_dragon.png"
            is DeathCategory.Mob.Wither -> "wither.png"
            is DeathCategory.Mob.Guardian -> "guardian.png"
            is DeathCategory.Mob.Phantom -> "phantom.png"
            is DeathCategory.Mob.Witch -> "witch.png"
            is DeathCategory.Mob.Bee -> "bee.png"
            is DeathCategory.Mob.Slime -> "slime.png"
            is DeathCategory.Mob.Wolf -> "wolf.png"
            is DeathCategory.Mob.Golem -> "golem.png"
            is DeathCategory.Mob.Piglin -> "piglin.png"
            is DeathCategory.Mob.GenericMob -> "generic_mob.png"
            else -> null
        }
    }

    /**
     * Get the environmental icon asset filename for an environmental death.
     * Returns null for non-environmental deaths.
     */
    fun getEnvironmentalIconAsset(category: DeathCategory): String? {
        return when (category) {
            is DeathCategory.Environmental.Fall -> "fall.png"
            is DeathCategory.Environmental.Lava -> "lava.png"
            is DeathCategory.Environmental.Fire -> "fire.png"
            is DeathCategory.Environmental.Drowning -> "water.png"
            is DeathCategory.Environmental.Void -> "void.png"
            is DeathCategory.Environmental.Lightning -> "lightning.png"
            is DeathCategory.Environmental.Cactus -> "cactus.png"
            is DeathCategory.Environmental.Suffocation -> "suffocation.png"
            is DeathCategory.Environmental.Starvation -> "starvation.png"
            is DeathCategory.Environmental.FallingBlock -> "falling_block.png"
            is DeathCategory.Environmental.Magma -> "lava.png"
            is DeathCategory.Environmental.BerryBush -> "berry_bush.png"
            is DeathCategory.Environmental.Freeze -> "freeze.png"
            is DeathCategory.Environmental.Explosion -> "explosion.png"
            is DeathCategory.Environmental.Thorns -> "thorns.png"
            else -> null
        }
    }

    /**
     * Get the icon asset path for any death category.
     * Returns the appropriate mob, environmental, or generic icon.
     */
    fun getIconAsset(category: DeathCategory): String {
        return when {
            category is DeathCategory.Mob -> getMobIconAsset(category) ?: "generic_mob.png"
            category is DeathCategory.Environmental -> getEnvironmentalIconAsset(category) ?: "skull.png"
            category is DeathCategory.PvP -> "pvp.png"
            else -> "skull.png"
        }
    }

    /**
     * Get the icon subdirectory for a death category.
     */
    fun getIconDirectory(category: DeathCategory): String {
        return when (category) {
            is DeathCategory.Mob -> "mobs"
            is DeathCategory.Environmental -> "environmental"
            is DeathCategory.PvP -> "environmental"
            else -> "environmental"
        }
    }

    /**
     * Get the display name for a death cause.
     * Used in the death message like "Slain by a WARDEN".
     */
    fun getDeathCauseName(category: DeathCategory): String {
        return when (category) {
            is DeathCategory.Mob.Warden -> "WARDEN"
            is DeathCategory.Mob.Creeper -> "CREEPER"
            is DeathCategory.Mob.Skeleton -> "SKELETON"
            is DeathCategory.Mob.Zombie -> "ZOMBIE"
            is DeathCategory.Mob.Spider -> "SPIDER"
            is DeathCategory.Mob.Enderman -> "ENDERMAN"
            is DeathCategory.Mob.Blaze -> "BLAZE"
            is DeathCategory.Mob.Ghast -> "GHAST"
            is DeathCategory.Mob.EnderDragon -> "ENDER DRAGON"
            is DeathCategory.Mob.Wither -> "WITHER"
            is DeathCategory.Mob.Guardian -> "GUARDIAN"
            is DeathCategory.Mob.Phantom -> "PHANTOM"
            is DeathCategory.Mob.Witch -> "WITCH"
            is DeathCategory.Mob.Bee -> "BEE"
            is DeathCategory.Mob.Slime -> "SLIME"
            is DeathCategory.Mob.Wolf -> "WOLF"
            is DeathCategory.Mob.Golem -> "IRON GOLEM"
            is DeathCategory.Mob.Piglin -> "PIGLIN"
            is DeathCategory.Mob.GenericMob -> "MOB"
            is DeathCategory.Environmental.Fall -> "GRAVITY"
            is DeathCategory.Environmental.Lava -> "LAVA"
            is DeathCategory.Environmental.Fire -> "FIRE"
            is DeathCategory.Environmental.Drowning -> "WATER"
            is DeathCategory.Environmental.Void -> "THE VOID"
            is DeathCategory.Environmental.Lightning -> "LIGHTNING"
            is DeathCategory.Environmental.Cactus -> "CACTUS"
            is DeathCategory.Environmental.Suffocation -> "SUFFOCATION"
            is DeathCategory.Environmental.Starvation -> "STARVATION"
            is DeathCategory.Environmental.FallingBlock -> "FALLING BLOCK"
            is DeathCategory.Environmental.Magma -> "MAGMA"
            is DeathCategory.Environmental.BerryBush -> "BERRY BUSH"
            is DeathCategory.Environmental.Freeze -> "FREEZING"
            is DeathCategory.Environmental.Explosion -> "EXPLOSION"
            is DeathCategory.Environmental.Thorns -> "THORNS"
            is DeathCategory.PvP -> "ANOTHER PLAYER"
            is DeathCategory.Unknown -> "UNKNOWN CAUSE"
        }
    }
}
