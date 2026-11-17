package io.github.darinc.amssync.events

import io.github.darinc.amssync.AMSSyncPlugin
import io.github.darinc.amssync.discord.WebhookManager
import io.github.darinc.amssync.exceptions.PlayerDataNotFoundException
import io.github.darinc.amssync.image.AvatarFetcher
import io.github.darinc.amssync.image.DeathCardRenderer
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.utils.MarkdownSanitizer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.entity.Player
import org.bukkit.profile.PlayerTextures
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.time.Instant
import javax.imageio.ImageIO

/**
 * Listens for player death events and announces them to Discord.
 *
 * @property plugin The parent plugin instance
 * @property config Event announcement configuration
 * @property webhookManager Manager for sending messages (webhook or bot)
 * @property deathCardRenderer Renderer for visual death cards (nullable if disabled)
 * @property avatarFetcher Avatar fetcher for body renders (nullable if disabled)
 */
class PlayerDeathListener(
    private val plugin: AMSSyncPlugin,
    private val config: EventAnnouncementConfig,
    private val webhookManager: WebhookManager,
    private val deathCardRenderer: DeathCardRenderer? = null,
    private val avatarFetcher: AvatarFetcher? = null
) : Listener {

    private val pvpTracker = PvpTracker()

    /**
     * Killer details for a death caused by another player.
     */
    private data class PvpKill(
        val killer: Player,
        val context: PvpKillContext,
        val weaponName: String?,
        val weaponSkill: String?
    )

    /**
     * Handle player death event and announce to Discord.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        if (!config.playerDeaths.enabled) return

        val player = event.entity
        val deathMessage = event.deathMessage() ?: return

        // Convert adventure Component to plain text
        val plainMessage = PlainTextComponentSerializer.plainText().serialize(deathMessage)

        plugin.logger.fine("Announcing player death: $plainMessage")

        val category = DeathCauseClassifier.classify(event)
        val pvpKill = recordPvp(player)

        // Check if death cards are enabled and available
        if (config.playerDeaths.deathCards.enabled && deathCardRenderer != null && avatarFetcher != null) {
            sendDeathCard(player, category, pvpKill)
        } else {
            sendDeathEmbed(player, plainMessage, category, pvpKill)
        }
    }

    /**
     * Update kill streak tracking and return killer details if another player caused this death.
     */
    private fun recordPvp(victim: Player): PvpKill? {
        val killer = victim.killer?.takeIf { it.uniqueId != victim.uniqueId }
        if (killer == null) {
            pvpTracker.recordDeath(victim.uniqueId)
            return null
        }
        val context = pvpTracker.recordKill(killer.uniqueId, victim.uniqueId)
        val weaponSkill = DeathCardCommentary.weaponSkillFor(killer.inventory.itemInMainHand.type.name)
        return PvpKill(killer, context, weaponName(killer), weaponSkill)
    }

    /**
     * The custom name of the killer's held item, in the same `[Name]` form vanilla uses.
     */
    private fun weaponName(killer: Player): String? {
        val name = killer.inventory.itemInMainHand.itemMeta?.customName() ?: return null
        val plain = PlainTextComponentSerializer.plainText().serialize(name)
        return plain.takeIf { it.isNotBlank() }?.let { "[$it]" }
    }

    /**
     * Send a visual death card image to Discord.
     */
    private fun sendDeathCard(player: Player, category: DeathCategory, pvpKill: PvpKill?) {
        // Read the skin from the player's own profile while still on the main thread
        val textures = player.playerProfile.textures
        val skinUrl = textures.skin?.toString()
        val slimSkin = if (skinUrl != null) textures.skinModel == PlayerTextures.SkinModel.SLIM else null

        // Run async to avoid blocking the main thread
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                // Get player stats from MCMMO
                val stats = try {
                    plugin.mcmmoApi.getPlayerStats(player.name)
                } catch (e: Exception) {
                    plugin.logger.fine("No MCMMO stats for ${player.name}: ${e.message}")
                    null
                }

                val powerLevel = stats?.values?.sum()

                val skin = avatarFetcher!!.fetchSkin(player.name, skinUrl)

                val pvpInfo = pvpKill?.let { kill ->
                    PvpCardInfo(
                        killerName = kill.killer.name,
                        weaponName = kill.weaponName,
                        weaponSkill = kill.weaponSkill,
                        killerStats = runCatching { plugin.mcmmoApi.getPlayerStats(kill.killer.name) }.getOrNull(),
                        context = kill.context
                    )
                }

                // Generate the headline and epitaph
                val (sarcasticMessage, commentary) = if (config.playerDeaths.deathCards.skillSpecificRoasts) {
                    DeathCardCommentary.generateMessages(category, stats, powerLevel, player.name, pvpInfo)
                } else {
                    // Fall back to basic commentary
                    val basic = DeathCommentaryRepository.getCommentary(category)
                    "Slain by ${io.github.darinc.amssync.image.DeathThemes.getDeathCauseName(category)}." to basic
                }

                // Render the death card
                val cardImage = deathCardRenderer!!.renderDeathCard(
                    playerName = player.name,
                    powerLevel = if (config.playerDeaths.deathCards.showPowerLevel) powerLevel else null,
                    deathCategory = category,
                    sarcasticMessage = sarcasticMessage,
                    commentary = commentary,
                    skin = skin,
                    slimSkin = slimSkin
                )

                // Convert to bytes and send
                val imageBytes = imageToBytes(cardImage)
                val filename = "${player.name.lowercase()}_death.png"

                webhookManager.sendImage(imageBytes, filename, player.name, null)
                plugin.logger.fine("Sent death card for ${player.name}")

            } catch (e: Exception) {
                plugin.logger.warning("Failed to generate death card for ${player.name}: ${e.message}")
                // Fall back to embed
                val category2 = category
                plugin.server.scheduler.runTask(plugin, Runnable {
                    val commentary = if (config.playerDeaths.commentaryEnabled) {
                        generateCommentary(player.name, category2)
                    } else null
                    val avatarUrl = if (config.showAvatars) {
                        EventAnnouncementConfig.getAvatarUrl(player.name, player.uniqueId, config.avatarProvider)
                    } else null
                    sendEmbedWithCommentary(
                        "${escapeForDiscord(player.name)} died", commentary, player.name, avatarUrl, avatarUrl
                    )
                })
            }
        })
    }

    /**
     * Send a traditional embed-based death announcement.
     */
    private fun sendDeathEmbed(player: Player, plainMessage: String, category: DeathCategory, pvpKill: PvpKill?) {
        // Player names like Silver_Parallax would otherwise render as Discord italics, and
        // renamed items could otherwise inject masked links
        val safeMessage = escapeForDiscord(plainMessage)

        val commentary = if (config.playerDeaths.commentaryEnabled) {
            if (pvpKill != null) {
                PvpCommentary.generate(
                    killer = escapeForDiscord(pvpKill.killer.name),
                    victim = escapeForDiscord(player.name),
                    context = pvpKill.context,
                    weaponName = pvpKill.weaponName?.let { escapeForDiscord(it) },
                    fallback = { generateCommentary(player.name, category) }
                )
            } else {
                generateCommentary(player.name, category)
            }
        } else null

        val avatarUrl = if (config.showAvatars) {
            EventAnnouncementConfig.getAvatarUrl(
                player.name,
                player.uniqueId,
                config.avatarProvider
            )
        } else null

        // For PvP, the thumbnail shows the killer; the webhook avatar already shows the victim
        val thumbnailUrl = if (config.showAvatars && pvpKill != null) {
            EventAnnouncementConfig.getAvatarUrl(pvpKill.killer.name, pvpKill.killer.uniqueId, config.avatarProvider)
        } else {
            avatarUrl
        }

        if (config.useEmbeds) {
            sendEmbedWithCommentary(safeMessage, commentary, player.name, avatarUrl, thumbnailUrl)
        } else {
            val fullMessage = if (commentary != null) {
                "$safeMessage\n*$commentary*"
            } else {
                safeMessage
            }
            webhookManager.sendMessage(fullMessage, player.name, avatarUrl)
        }
    }

    /**
     * Convert a BufferedImage to PNG bytes.
     */
    private fun imageToBytes(image: BufferedImage): ByteArray {
        val baos = ByteArrayOutputStream()
        ImageIO.write(image, "PNG", baos)
        return baos.toByteArray()
    }

    /**
     * Generate commentary for a death.
     * Tries MCMMO roast first (if enabled and applicable), then falls back to standard commentary.
     */
    private fun generateCommentary(playerName: String, category: DeathCategory): String {
        // Try MCMMO roast first if enabled
        if (config.playerDeaths.mcmmoRoastsEnabled) {
            val roast = tryGetMcmmoRoast(playerName, category)
            if (roast != null) return roast
        }

        // Fall back to standard commentary
        return DeathCommentaryRepository.getCommentary(category)
    }

    /**
     * Try to get an MCMMO elite roast for a high-level player.
     * Returns null if player isn't high enough level, death isn't embarrassing, or error occurs.
     */
    private fun tryGetMcmmoRoast(playerName: String, category: DeathCategory): String? {
        if (!category.isEmbarrassing()) return null

        return try {
            val powerLevel = plugin.mcmmoApi.getPowerLevel(playerName)
            DeathCommentaryRepository.getEliteRoast(
                category,
                powerLevel,
                config.playerDeaths.mcmmoRoastThreshold
            )
        } catch (e: PlayerDataNotFoundException) {
            plugin.logger.fine("No MCMMO data for $playerName, skipping roast")
            null
        } catch (e: Exception) {
            plugin.logger.warning("Error getting MCMMO power level for roast: ${e.message}")
            null
        }
    }

    /**
     * Send embed with vanilla message and optional commentary.
     */
    private fun sendEmbedWithCommentary(
        deathMessage: String,
        commentary: String?,
        playerName: String,
        avatarUrl: String?,
        thumbnailUrl: String?
    ) {
        val description = if (commentary != null) {
            "$deathMessage\n\n*$commentary*"
        } else {
            deathMessage
        }

        val embed = EmbedBuilder()
            .setColor(Color(237, 66, 69)) // Discord red
            .setDescription(description)
            .apply {
                if (thumbnailUrl != null) {
                    setThumbnail(thumbnailUrl)
                }
            }
            .setTimestamp(Instant.now())
            .build()

        webhookManager.sendEmbed(embed, playerName, avatarUrl)
    }

    companion object {
        /**
         * Escape player-controlled text (names, renamed items) for Discord markdown.
         * Brackets are escaped too so `[text](url)` cannot become a masked link.
         */
        internal fun escapeForDiscord(text: String): String =
            MarkdownSanitizer.escape(text).replace(Regex("""[\[\]()]"""), """\\$0""")
    }
}
