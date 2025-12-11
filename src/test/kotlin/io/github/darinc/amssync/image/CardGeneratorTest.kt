package io.github.darinc.amssync.image

import io.kotest.core.spec.style.DescribeSpec
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.imageio.ImageIO

/**
 * Test that generates example card images for documentation.
 *
 * Run with: ./gradlew test --tests "CardGeneratorTest"
 *
 * Generated images are saved to docs/images/
 *
 * Uses mallowqueen's Minecraft skin for realistic avatars but displays
 * made-up player names in the cards.
 */
class CardGeneratorTest : DescribeSpec({

    val outputDir = File("docs/images")
    val renderer = PlayerCardRenderer("Awesome Minecraft Server")
    val milestoneRenderer = MilestoneCardRenderer("Awesome Minecraft Server")

    // Player skins for card examples
    val COMMON_PLAYER = "silver_parallax"      // Common tier stats card
    val RARE_PLAYER = "gyarados"               // Rare tier stats card
    val EPIC_PLAYER = "Noaln_"                 // Epic tier stats card
    val LEGENDARY_PLAYER = "mallowqueen"       // Legendary tier & milestones
    val FIRST_PLACE = "NothingTV"              // 1st place on leaderboards

    // Fetch body render from mc-heads.net for a specific player
    fun fetchBodyImage(player: String): BufferedImage {
        return try {
            val url = URL("https://mc-heads.net/body/$player/128")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "AMSSync-Test")

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val image = ImageIO.read(connection.inputStream)
                connection.disconnect()
                image ?: createPlaceholderBody()
            } else {
                println("Warning: Failed to fetch body image for $player (${connection.responseCode}), using placeholder")
                createPlaceholderBody()
            }
        } catch (e: Exception) {
            println("Warning: Failed to fetch body image for $player (${e.message}), using placeholder")
            createPlaceholderBody()
        }
    }

    // Fetch head avatar from mc-heads.net for a specific player
    fun fetchHeadImage(player: String, size: Int = 64): BufferedImage {
        return try {
            val url = URL("https://mc-heads.net/avatar/$player/$size")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "AMSSync-Test")

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val image = ImageIO.read(connection.inputStream)
                connection.disconnect()
                image ?: createPlaceholderAvatar(size)
            } else {
                println("Warning: Failed to fetch head image for $player (${connection.responseCode}), using placeholder")
                createPlaceholderAvatar(size)
            }
        } catch (e: Exception) {
            println("Warning: Failed to fetch head image for $player (${e.message}), using placeholder")
            createPlaceholderAvatar(size)
        }
    }

    beforeSpec {
        outputDir.mkdirs()
    }

    describe("Example Card Generation") {

        it("generates a COMMON tier stats card (Steel Gray gradient) - silver_parallax") {
            // Power level < 1000
            val stats = mapOf(
                "MINING" to 120,
                "WOODCUTTING" to 95,
                "HERBALISM" to 80,
                "EXCAVATION" to 65,
                "FISHING" to 45,
                "SWORDS" to 110,
                "AXES" to 85,
                "ARCHERY" to 70,
                "UNARMED" to 55,
                "TAMING" to 30,
                "REPAIR" to 75,
                "ACROBATICS" to 60,
                "ALCHEMY" to 25
            )
            val powerLevel = stats.values.sum() // ~915

            val bodyImage = fetchBodyImage(COMMON_PLAYER)

            val card = renderer.renderStatsCard(
                playerName = COMMON_PLAYER,
                stats = stats,
                powerLevel = powerLevel,
                bodyImage = bodyImage
            )

            val outputFile = File(outputDir, "stats-card-common.png")
            ImageIO.write(card, "PNG", outputFile)
            println("Generated: ${outputFile.absolutePath} (Power: $powerLevel) - ${COMMON_PLAYER}")
        }

        it("generates a RARE tier stats card (Ocean Blue gradient) - gyarados") {
            // Power level 1000-4999
            val stats = mapOf(
                "MINING" to 350,
                "WOODCUTTING" to 280,
                "HERBALISM" to 220,
                "EXCAVATION" to 180,
                "FISHING" to 120,
                "SWORDS" to 320,
                "AXES" to 250,
                "ARCHERY" to 200,
                "UNARMED" to 150,
                "TAMING" to 90,
                "REPAIR" to 240,
                "ACROBATICS" to 180,
                "ALCHEMY" to 70
            )
            val powerLevel = stats.values.sum() // ~2650

            val bodyImage = fetchBodyImage(RARE_PLAYER)

            val card = renderer.renderStatsCard(
                playerName = RARE_PLAYER,
                stats = stats,
                powerLevel = powerLevel,
                bodyImage = bodyImage
            )

            val outputFile = File(outputDir, "stats-card-rare.png")
            ImageIO.write(card, "PNG", outputFile)
            println("Generated: ${outputFile.absolutePath} (Power: $powerLevel) - ${RARE_PLAYER}")
        }

        it("generates an EPIC tier stats card (Royal Purple gradient) - Noaln_") {
            // Power level 5000-9999
            val stats = mapOf(
                "MINING" to 750,
                "WOODCUTTING" to 680,
                "HERBALISM" to 520,
                "EXCAVATION" to 450,
                "FISHING" to 320,
                "SWORDS" to 700,
                "AXES" to 580,
                "ARCHERY" to 490,
                "UNARMED" to 380,
                "TAMING" to 250,
                "REPAIR" to 600,
                "ACROBATICS" to 450,
                "ALCHEMY" to 180
            )
            val powerLevel = stats.values.sum() // ~6350

            val bodyImage = fetchBodyImage(EPIC_PLAYER)

            val card = renderer.renderStatsCard(
                playerName = EPIC_PLAYER,
                stats = stats,
                powerLevel = powerLevel,
                bodyImage = bodyImage
            )

            val outputFile = File(outputDir, "stats-card-epic.png")
            ImageIO.write(card, "PNG", outputFile)
            println("Generated: ${outputFile.absolutePath} (Power: $powerLevel) - ${EPIC_PLAYER}")
        }

        it("generates a LEGENDARY tier stats card (Infernal gradient) - mallowqueen") {
            // Power level 10000+
            val stats = mapOf(
                "MINING" to 1000,
                "WOODCUTTING" to 1000,
                "HERBALISM" to 945,
                "EXCAVATION" to 878,
                "FISHING" to 756,
                "SWORDS" to 1000,
                "AXES" to 892,
                "ARCHERY" to 834,
                "UNARMED" to 701,
                "TAMING" to 623,
                "REPAIR" to 1000,
                "ACROBATICS" to 889,
                "ALCHEMY" to 534
            )
            val powerLevel = stats.values.sum() // ~11052

            val bodyImage = fetchBodyImage(LEGENDARY_PLAYER)

            val card = renderer.renderStatsCard(
                playerName = LEGENDARY_PLAYER,
                stats = stats,
                powerLevel = powerLevel,
                bodyImage = bodyImage
            )

            val outputFile = File(outputDir, "stats-card-legendary.png")
            ImageIO.write(card, "PNG", outputFile)
            println("Generated: ${outputFile.absolutePath} (Power: $powerLevel) - ${LEGENDARY_PLAYER}")
        }

        it("generates a leaderboard card example - NothingTV, mallowqueen, Noaln_ podium") {
            // Top 3: NothingTV (1st), mallowqueen (2nd), Noaln_ (3rd)
            val leaderboard = listOf(
                FIRST_PLACE to 1000,
                LEGENDARY_PLAYER to 945,
                EPIC_PLAYER to 887,
                "CaveDweller" to 812,
                "PickaxePro" to 756,
                "OreMaster" to 701,
                "DeepMiner" to 654,
                "RockSmasher" to 598,
                "GoldRush" to 543,
                "IronWill" to 489
            )

            // Fetch real avatars for top 3
            val avatars = mapOf(
                FIRST_PLACE to fetchHeadImage(FIRST_PLACE, CardStyles.PODIUM_HEAD_SIZE),
                LEGENDARY_PLAYER to fetchHeadImage(LEGENDARY_PLAYER, CardStyles.PODIUM_HEAD_SIZE),
                EPIC_PLAYER to fetchHeadImage(EPIC_PLAYER, CardStyles.PODIUM_HEAD_SIZE)
            )

            val card = renderer.renderLeaderboardCard(
                title = "Mining Leaderboard",
                leaderboard = leaderboard,
                avatarImages = avatars
            )

            val outputFile = File(outputDir, "leaderboard-card-example.png")
            ImageIO.write(card, "PNG", outputFile)
            println("Generated: ${outputFile.absolutePath} - Podium: ${FIRST_PLACE}, ${LEGENDARY_PLAYER}, ${EPIC_PLAYER}")
        }

        it("generates a power level leaderboard example") {
            // Top 3: NothingTV (1st), mallowqueen (2nd), Noaln_ (3rd)
            val leaderboard = listOf(
                FIRST_PLACE to 11052,
                LEGENDARY_PLAYER to 9845,
                EPIC_PLAYER to 8721,
                "VeteranMC" to 7654,
                "ElitePlayer" to 6543,
                "Dedicated" to 5432,
                "Experienced" to 4321,
                "Regular" to 3210,
                "Casual" to 2109,
                "Newbie" to 1098
            )

            val avatars = mapOf(
                FIRST_PLACE to fetchHeadImage(FIRST_PLACE, CardStyles.PODIUM_HEAD_SIZE),
                LEGENDARY_PLAYER to fetchHeadImage(LEGENDARY_PLAYER, CardStyles.PODIUM_HEAD_SIZE),
                EPIC_PLAYER to fetchHeadImage(EPIC_PLAYER, CardStyles.PODIUM_HEAD_SIZE)
            )

            val card = renderer.renderLeaderboardCard(
                title = "Power Level",
                leaderboard = leaderboard,
                avatarImages = avatars
            )

            val outputFile = File(outputDir, "leaderboard-power-example.png")
            ImageIO.write(card, "PNG", outputFile)
            println("Generated: ${outputFile.absolutePath}")
        }
    }

    describe("Milestone Card Generation - mallowqueen") {

        it("generates a skill milestone card (Beginner tier - level 100)") {
            val headImage = fetchHeadImage(LEGENDARY_PLAYER, MilestoneStyles.HEAD_SIZE)

            val card = milestoneRenderer.renderSkillMilestoneCard(
                playerName = LEGENDARY_PLAYER,
                skill = com.gmail.nossr50.datatypes.skills.PrimarySkillType.MINING,
                level = 100,
                headImage = headImage
            )

            val outputFile = File(outputDir, "milestone-skill-beginner.png")
            ImageIO.write(card, "PNG", outputFile)
            println("Generated: ${outputFile.absolutePath} - ${LEGENDARY_PLAYER}")
        }

        it("generates a skill milestone card (Expert tier - level 500)") {
            val headImage = fetchHeadImage(LEGENDARY_PLAYER, MilestoneStyles.HEAD_SIZE)

            val card = milestoneRenderer.renderSkillMilestoneCard(
                playerName = LEGENDARY_PLAYER,
                skill = com.gmail.nossr50.datatypes.skills.PrimarySkillType.SWORDS,
                level = 500,
                headImage = headImage
            )

            val outputFile = File(outputDir, "milestone-skill-expert.png")
            ImageIO.write(card, "PNG", outputFile)
            println("Generated: ${outputFile.absolutePath} - ${LEGENDARY_PLAYER}")
        }

        it("generates a skill milestone card (Legendary tier - level 1000)") {
            val headImage = fetchHeadImage(LEGENDARY_PLAYER, MilestoneStyles.HEAD_SIZE)

            val card = milestoneRenderer.renderSkillMilestoneCard(
                playerName = LEGENDARY_PLAYER,
                skill = com.gmail.nossr50.datatypes.skills.PrimarySkillType.SWORDS,
                level = 1000,
                headImage = headImage
            )

            val outputFile = File(outputDir, "milestone-skill-legendary.png")
            ImageIO.write(card, "PNG", outputFile)
            println("Generated: ${outputFile.absolutePath} - ${LEGENDARY_PLAYER}")
        }

        it("generates a power level milestone card (Warrior tier - 1000)") {
            val headImage = fetchHeadImage(LEGENDARY_PLAYER, MilestoneStyles.HEAD_SIZE)

            val card = milestoneRenderer.renderPowerMilestoneCard(
                playerName = LEGENDARY_PLAYER,
                powerLevel = 1000,
                headImage = headImage
            )

            val outputFile = File(outputDir, "milestone-power-warrior.png")
            ImageIO.write(card, "PNG", outputFile)
            println("Generated: ${outputFile.absolutePath} - ${LEGENDARY_PLAYER}")
        }

        it("generates a power level milestone card (Mythic tier - 20000)") {
            val headImage = fetchHeadImage(LEGENDARY_PLAYER, MilestoneStyles.HEAD_SIZE)

            val card = milestoneRenderer.renderPowerMilestoneCard(
                playerName = LEGENDARY_PLAYER,
                powerLevel = 20000,
                headImage = headImage
            )

            val outputFile = File(outputDir, "milestone-power-mythic.png")
            ImageIO.write(card, "PNG", outputFile)
            println("Generated: ${outputFile.absolutePath} - ${LEGENDARY_PLAYER}")
        }
    }
})

/**
 * Create a placeholder full body skin render (Steve-like silhouette).
 */
private fun createPlaceholderBody(): BufferedImage {
    val width = 64
    val height = 128
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val g2d = image.createGraphics()

    // Simple Steve-like placeholder
    val skinColor = Color(200, 150, 100)
    val shirtColor = Color(0, 170, 170)
    val pantsColor = Color(60, 60, 140)
    val shoeColor = Color(80, 80, 80)

    // Head
    g2d.color = skinColor
    g2d.fillRect(16, 0, 32, 32)

    // Eyes
    g2d.color = Color.WHITE
    g2d.fillRect(20, 12, 8, 4)
    g2d.fillRect(36, 12, 8, 4)
    g2d.color = Color(60, 30, 10)
    g2d.fillRect(22, 12, 4, 4)
    g2d.fillRect(38, 12, 4, 4)

    // Body/shirt
    g2d.color = shirtColor
    g2d.fillRect(12, 32, 40, 40)

    // Arms
    g2d.color = skinColor
    g2d.fillRect(0, 32, 12, 40)
    g2d.fillRect(52, 32, 12, 40)

    // Legs/pants
    g2d.color = pantsColor
    g2d.fillRect(16, 72, 16, 48)
    g2d.fillRect(32, 72, 16, 48)

    // Shoes
    g2d.color = shoeColor
    g2d.fillRect(16, 116, 16, 12)
    g2d.fillRect(32, 116, 16, 12)

    g2d.dispose()
    return image
}

/**
 * Create a placeholder avatar (simple head).
 */
private fun createPlaceholderAvatar(size: Int = CardStyles.PODIUM_HEAD_SIZE): BufferedImage {
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g2d = image.createGraphics()

    // Simple face
    g2d.color = Color(200, 150, 100)
    g2d.fillRect(0, 0, size, size)

    // Eyes
    val eyeSize = size / 8
    g2d.color = Color.WHITE
    g2d.fillRect(size / 4 - eyeSize / 2, size / 3, eyeSize * 2, eyeSize)
    g2d.fillRect(size * 3 / 4 - eyeSize * 3 / 2, size / 3, eyeSize * 2, eyeSize)

    g2d.color = Color(60, 30, 10)
    g2d.fillRect(size / 4, size / 3, eyeSize, eyeSize)
    g2d.fillRect(size * 3 / 4 - eyeSize, size / 3, eyeSize, eyeSize)

    // Hair
    g2d.color = Color(60, 40, 20)
    g2d.fillRect(0, 0, size, size / 6)

    g2d.dispose()
    return image
}
