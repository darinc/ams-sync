package io.github.darinc.amssync.events

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.bukkit.entity.EntityType
import org.bukkit.event.entity.EntityDamageEvent.DamageCause

class DeathCauseClassifierTest : DescribeSpec({

    describe("DeathCauseClassifier") {

        describe("classifyByDamageCause") {

            it("classifies FALL as Environmental.Fall") {
                DeathCauseClassifier.classifyByDamageCause(DamageCause.FALL) shouldBe DeathCategory.Environmental.Fall
            }

            it("classifies DROWNING as Environmental.Drowning") {
                DeathCauseClassifier.classifyByDamageCause(DamageCause.DROWNING) shouldBe DeathCategory.Environmental.Drowning
            }

            it("classifies FIRE as Environmental.Fire") {
                DeathCauseClassifier.classifyByDamageCause(DamageCause.FIRE) shouldBe DeathCategory.Environmental.Fire
            }

            it("classifies FIRE_TICK as Environmental.Fire") {
                DeathCauseClassifier.classifyByDamageCause(DamageCause.FIRE_TICK) shouldBe DeathCategory.Environmental.Fire
            }

            it("classifies LAVA as Environmental.Lava") {
                DeathCauseClassifier.classifyByDamageCause(DamageCause.LAVA) shouldBe DeathCategory.Environmental.Lava
            }

            it("classifies VOID as Environmental.Void") {
                DeathCauseClassifier.classifyByDamageCause(DamageCause.VOID) shouldBe DeathCategory.Environmental.Void
            }

            it("classifies LIGHTNING as Environmental.Lightning") {
                DeathCauseClassifier.classifyByDamageCause(DamageCause.LIGHTNING) shouldBe DeathCategory.Environmental.Lightning
            }

            it("classifies SUFFOCATION as Environmental.Suffocation") {
                DeathCauseClassifier.classifyByDamageCause(DamageCause.SUFFOCATION) shouldBe DeathCategory.Environmental.Suffocation
            }

            it("classifies STARVATION as Environmental.Starvation") {
                DeathCauseClassifier.classifyByDamageCause(DamageCause.STARVATION) shouldBe DeathCategory.Environmental.Starvation
            }

            it("classifies CONTACT as Environmental.Cactus") {
                DeathCauseClassifier.classifyByDamageCause(DamageCause.CONTACT) shouldBe DeathCategory.Environmental.Cactus
            }

            it("classifies HOT_FLOOR as Environmental.Magma") {
                DeathCauseClassifier.classifyByDamageCause(DamageCause.HOT_FLOOR) shouldBe DeathCategory.Environmental.Magma
            }

            it("classifies FALLING_BLOCK as Environmental.FallingBlock") {
                DeathCauseClassifier.classifyByDamageCause(DamageCause.FALLING_BLOCK) shouldBe DeathCategory.Environmental.FallingBlock
            }

            it("classifies FREEZE as Environmental.Freeze") {
                DeathCauseClassifier.classifyByDamageCause(DamageCause.FREEZE) shouldBe DeathCategory.Environmental.Freeze
            }

            it("classifies BLOCK_EXPLOSION as Environmental.Explosion") {
                DeathCauseClassifier.classifyByDamageCause(DamageCause.BLOCK_EXPLOSION) shouldBe DeathCategory.Environmental.Explosion
            }

            it("classifies ENTITY_EXPLOSION as Mob.Creeper") {
                DeathCauseClassifier.classifyByDamageCause(DamageCause.ENTITY_EXPLOSION) shouldBe DeathCategory.Mob.Creeper
            }

            it("classifies THORNS as Environmental.Thorns") {
                DeathCauseClassifier.classifyByDamageCause(DamageCause.THORNS) shouldBe DeathCategory.Environmental.Thorns
            }

            it("classifies WITHER as Mob.Wither") {
                DeathCauseClassifier.classifyByDamageCause(DamageCause.WITHER) shouldBe DeathCategory.Mob.Wither
            }

            it("returns null for ENTITY_ATTACK (needs entity type)") {
                DeathCauseClassifier.classifyByDamageCause(DamageCause.ENTITY_ATTACK) shouldBe null
            }

            it("returns null for PROJECTILE (needs entity type)") {
                DeathCauseClassifier.classifyByDamageCause(DamageCause.PROJECTILE) shouldBe null
            }
        }

        describe("classifyByEntityType") {

            it("classifies CREEPER as Mob.Creeper") {
                DeathCauseClassifier.classifyByEntityType(EntityType.CREEPER) shouldBe DeathCategory.Mob.Creeper
            }

            it("classifies SKELETON as Mob.Skeleton") {
                DeathCauseClassifier.classifyByEntityType(EntityType.SKELETON) shouldBe DeathCategory.Mob.Skeleton
            }

            it("classifies STRAY as Mob.Skeleton") {
                DeathCauseClassifier.classifyByEntityType(EntityType.STRAY) shouldBe DeathCategory.Mob.Skeleton
            }

            it("classifies WITHER_SKELETON as Mob.Skeleton") {
                DeathCauseClassifier.classifyByEntityType(EntityType.WITHER_SKELETON) shouldBe DeathCategory.Mob.Skeleton
            }

            it("classifies ZOMBIE as Mob.Zombie") {
                DeathCauseClassifier.classifyByEntityType(EntityType.ZOMBIE) shouldBe DeathCategory.Mob.Zombie
            }

            it("classifies ZOMBIE_VILLAGER as Mob.Zombie") {
                DeathCauseClassifier.classifyByEntityType(EntityType.ZOMBIE_VILLAGER) shouldBe DeathCategory.Mob.Zombie
            }

            it("classifies HUSK as Mob.Zombie") {
                DeathCauseClassifier.classifyByEntityType(EntityType.HUSK) shouldBe DeathCategory.Mob.Zombie
            }

            it("classifies DROWNED as Mob.Zombie") {
                DeathCauseClassifier.classifyByEntityType(EntityType.DROWNED) shouldBe DeathCategory.Mob.Zombie
            }

            it("classifies SPIDER as Mob.Spider") {
                DeathCauseClassifier.classifyByEntityType(EntityType.SPIDER) shouldBe DeathCategory.Mob.Spider
            }

            it("classifies CAVE_SPIDER as Mob.Spider") {
                DeathCauseClassifier.classifyByEntityType(EntityType.CAVE_SPIDER) shouldBe DeathCategory.Mob.Spider
            }

            it("classifies ENDERMAN as Mob.Enderman") {
                DeathCauseClassifier.classifyByEntityType(EntityType.ENDERMAN) shouldBe DeathCategory.Mob.Enderman
            }

            it("classifies WITHER as Mob.Wither") {
                DeathCauseClassifier.classifyByEntityType(EntityType.WITHER) shouldBe DeathCategory.Mob.Wither
            }

            it("classifies ENDER_DRAGON as Mob.EnderDragon") {
                DeathCauseClassifier.classifyByEntityType(EntityType.ENDER_DRAGON) shouldBe DeathCategory.Mob.EnderDragon
            }

            it("classifies BLAZE as Mob.Blaze") {
                DeathCauseClassifier.classifyByEntityType(EntityType.BLAZE) shouldBe DeathCategory.Mob.Blaze
            }

            it("classifies GHAST as Mob.Ghast") {
                DeathCauseClassifier.classifyByEntityType(EntityType.GHAST) shouldBe DeathCategory.Mob.Ghast
            }

            it("classifies WARDEN as Mob.Warden") {
                DeathCauseClassifier.classifyByEntityType(EntityType.WARDEN) shouldBe DeathCategory.Mob.Warden
            }

            it("classifies WITCH as Mob.Witch") {
                DeathCauseClassifier.classifyByEntityType(EntityType.WITCH) shouldBe DeathCategory.Mob.Witch
            }

            it("classifies PHANTOM as Mob.Phantom") {
                DeathCauseClassifier.classifyByEntityType(EntityType.PHANTOM) shouldBe DeathCategory.Mob.Phantom
            }

            it("classifies GUARDIAN as Mob.Guardian") {
                DeathCauseClassifier.classifyByEntityType(EntityType.GUARDIAN) shouldBe DeathCategory.Mob.Guardian
            }

            it("classifies ELDER_GUARDIAN as Mob.Guardian") {
                DeathCauseClassifier.classifyByEntityType(EntityType.ELDER_GUARDIAN) shouldBe DeathCategory.Mob.Guardian
            }

            it("classifies SLIME as Mob.Slime") {
                DeathCauseClassifier.classifyByEntityType(EntityType.SLIME) shouldBe DeathCategory.Mob.Slime
            }

            it("classifies MAGMA_CUBE as Mob.Slime") {
                DeathCauseClassifier.classifyByEntityType(EntityType.MAGMA_CUBE) shouldBe DeathCategory.Mob.Slime
            }

            it("classifies WOLF as Mob.Wolf") {
                DeathCauseClassifier.classifyByEntityType(EntityType.WOLF) shouldBe DeathCategory.Mob.Wolf
            }

            it("classifies BEE as Mob.Bee") {
                DeathCauseClassifier.classifyByEntityType(EntityType.BEE) shouldBe DeathCategory.Mob.Bee
            }

            it("classifies IRON_GOLEM as Mob.Golem") {
                DeathCauseClassifier.classifyByEntityType(EntityType.IRON_GOLEM) shouldBe DeathCategory.Mob.Golem
            }

            it("classifies PLAYER as PvP") {
                DeathCauseClassifier.classifyByEntityType(EntityType.PLAYER) shouldBe DeathCategory.PvP
            }

            it("classifies unknown mob as Mob.GenericMob") {
                DeathCauseClassifier.classifyByEntityType(EntityType.SILVERFISH) shouldBe DeathCategory.Mob.GenericMob
            }
        }

        describe("parseDeathMessage") {

            // Mob patterns
            it("parses creeper explosion message") {
                DeathCauseClassifier.parseDeathMessage("Steve was blown up by Creeper") shouldBe DeathCategory.Mob.Creeper
            }

            it("parses skeleton shot message") {
                DeathCauseClassifier.parseDeathMessage("Steve was shot by Skeleton") shouldBe DeathCategory.Mob.Skeleton
            }

            it("parses zombie slain message") {
                DeathCauseClassifier.parseDeathMessage("Steve was slain by Zombie") shouldBe DeathCategory.Mob.Zombie
            }

            it("parses spider slain message") {
                DeathCauseClassifier.parseDeathMessage("Steve was slain by Spider") shouldBe DeathCategory.Mob.Spider
            }

            it("parses enderman slain message") {
                DeathCauseClassifier.parseDeathMessage("Steve was slain by Enderman") shouldBe DeathCategory.Mob.Enderman
            }

            it("parses wither kill message") {
                DeathCauseClassifier.parseDeathMessage("Steve was killed by Wither") shouldBe DeathCategory.Mob.Wither
            }

            it("parses wither effect message") {
                DeathCauseClassifier.parseDeathMessage("Steve withered away") shouldBe DeathCategory.Mob.Wither
            }

            it("parses dragon slain message") {
                DeathCauseClassifier.parseDeathMessage("Steve was slain by Ender Dragon") shouldBe DeathCategory.Mob.EnderDragon
            }

            it("parses blaze fireball message") {
                DeathCauseClassifier.parseDeathMessage("Steve was fireballed by Blaze") shouldBe DeathCategory.Mob.Blaze
            }

            it("parses ghast fireball message") {
                DeathCauseClassifier.parseDeathMessage("Steve was fireballed by Ghast") shouldBe DeathCategory.Mob.Ghast
            }

            it("parses bee sting message") {
                DeathCauseClassifier.parseDeathMessage("Steve was stung to death") shouldBe DeathCategory.Mob.Bee
            }

            it("parses iron golem pummel message") {
                DeathCauseClassifier.parseDeathMessage("Steve was pummeled by Iron Golem") shouldBe DeathCategory.Mob.Golem
            }

            // Environmental patterns
            it("parses fall message - hit ground") {
                DeathCauseClassifier.parseDeathMessage("Steve hit the ground too hard") shouldBe DeathCategory.Environmental.Fall
            }

            it("parses fall message - fell from high place") {
                DeathCauseClassifier.parseDeathMessage("Steve fell from a high place") shouldBe DeathCategory.Environmental.Fall
            }

            it("parses fall message - fell off") {
                DeathCauseClassifier.parseDeathMessage("Steve fell off a ladder") shouldBe DeathCategory.Environmental.Fall
            }

            it("parses void message - fell out") {
                DeathCauseClassifier.parseDeathMessage("Steve fell out of the world") shouldBe DeathCategory.Environmental.Void
            }

            it("parses void message - didn't want to live") {
                DeathCauseClassifier.parseDeathMessage("Steve didn't want to live in the same world as Zombie") shouldBe DeathCategory.Environmental.Void
            }

            it("parses drowning message") {
                DeathCauseClassifier.parseDeathMessage("Steve drowned") shouldBe DeathCategory.Environmental.Drowning
            }

            it("parses lava message") {
                DeathCauseClassifier.parseDeathMessage("Steve tried to swim in lava") shouldBe DeathCategory.Environmental.Lava
            }

            it("parses fire message - burned") {
                DeathCauseClassifier.parseDeathMessage("Steve burned to death") shouldBe DeathCategory.Environmental.Fire
            }

            it("parses fire message - flames") {
                DeathCauseClassifier.parseDeathMessage("Steve went up in flames") shouldBe DeathCategory.Environmental.Fire
            }

            it("parses fire message - walked into") {
                DeathCauseClassifier.parseDeathMessage("Steve walked into fire whilst fighting Zombie") shouldBe DeathCategory.Environmental.Fire
            }

            it("parses lightning message") {
                DeathCauseClassifier.parseDeathMessage("Steve was struck by lightning") shouldBe DeathCategory.Environmental.Lightning
            }

            it("parses cactus message - pricked") {
                DeathCauseClassifier.parseDeathMessage("Steve was pricked to death") shouldBe DeathCategory.Environmental.Cactus
            }

            it("parses cactus message - hugged") {
                DeathCauseClassifier.parseDeathMessage("Steve hugged a cactus") shouldBe DeathCategory.Environmental.Cactus
            }

            it("parses starvation message") {
                DeathCauseClassifier.parseDeathMessage("Steve starved to death") shouldBe DeathCategory.Environmental.Starvation
            }

            it("parses suffocation message") {
                DeathCauseClassifier.parseDeathMessage("Steve suffocated in a wall") shouldBe DeathCategory.Environmental.Suffocation
            }

            it("parses falling block message - squashed") {
                DeathCauseClassifier.parseDeathMessage("Steve was squashed by a falling anvil") shouldBe DeathCategory.Environmental.FallingBlock
            }

            it("parses freeze message") {
                DeathCauseClassifier.parseDeathMessage("Steve froze to death") shouldBe DeathCategory.Environmental.Freeze
            }

            it("parses explosion message") {
                DeathCauseClassifier.parseDeathMessage("Steve blew up") shouldBe DeathCategory.Environmental.Explosion
            }

            it("parses berry bush message") {
                DeathCauseClassifier.parseDeathMessage("Steve was poked to death by a sweet berry bush") shouldBe DeathCategory.Environmental.BerryBush
            }

            it("parses magma message") {
                DeathCauseClassifier.parseDeathMessage("Steve discovered the floor was lava") shouldBe DeathCategory.Environmental.Magma
            }

            // Generic fallback
            it("parses generic slain by message as GenericMob") {
                DeathCauseClassifier.parseDeathMessage("Steve was slain by Vindicator") shouldBe DeathCategory.Mob.GenericMob
            }

            it("returns Unknown for unrecognized message") {
                DeathCauseClassifier.parseDeathMessage("Steve died mysteriously") shouldBe DeathCategory.Unknown
            }

            // Case insensitivity
            it("handles uppercase messages") {
                DeathCauseClassifier.parseDeathMessage("STEVE WAS SLAIN BY ZOMBIE") shouldBe DeathCategory.Mob.Zombie
            }

            it("handles mixed case messages") {
                DeathCauseClassifier.parseDeathMessage("Steve DROWNED in the ocean") shouldBe DeathCategory.Environmental.Drowning
            }
        }
    }
})
