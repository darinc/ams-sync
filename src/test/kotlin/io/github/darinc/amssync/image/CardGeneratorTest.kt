package io.github.darinc.amssync.image

import io.kotest.core.spec.style.DescribeSpec
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Test that generates example card images for documentation.
 *
 * Run with: ./gradlew test --tests "CardGeneratorTest"
 *
 * Generated images are saved to docs/images/
 */
class CardGeneratorTest : DescribeSpec({

    val outputDir = File("docs/images")
    val renderer = PlayerCardRenderer("play.example.com")

    beforeSpec {
        outputDir.mkdirs()
    }

    describe("Example Card Generation") {

        it("generates a stats card example") {
            // Create sample stats for a mid-level player
            val stats = mapOf(
                "MINING" to 847,
                "WOODCUTTING" to 623,
                "HERBALISM" to 412,
                "EXCAVATION" to 289,
                "FISHING" to 156,
                "SWORDS" to 534,
                "AXES" to 421,
                "ARCHERY" to 367,
                "UNARMED" to 198,
                "TAMING" to 89,
                "REPAIR" to 445,
                "ACROBATICS" to 312,
                "ALCHEMY" to 67
            )
            val powerLevel = stats.values.sum()

            // Create a placeholder body image (full body skin render)
            val bodyImage = createPlaceholderBody()

            val card = renderer.renderStatsCard(
                playerName = "ExamplePlayer",
                stats = stats,
                powerLevel = powerLevel,
                bodyImage = bodyImage
            )

            val outputFile = File(outputDir, "stats-card-example.png")
            ImageIO.write(card, "PNG", outputFile)
            println("Generated: ${outputFile.absolutePath}")
        }

        it("generates a legendary stats card example") {
            // Create sample stats for a high-level player with mastery
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
            val powerLevel = stats.values.sum()

            val bodyImage = createPlaceholderBody()

            val card = renderer.renderStatsCard(
                playerName = "LegendPlayer",
                stats = stats,
                powerLevel = powerLevel,
                bodyImage = bodyImage
            )

            val outputFile = File(outputDir, "stats-card-legendary.png")
            ImageIO.write(card, "PNG", outputFile)
            println("Generated: ${outputFile.absolutePath}")
        }

        it("generates a leaderboard card example") {
            val leaderboard = listOf(
                "MiningKing" to 1000,
                "DiamondDig" to 945,
                "StoneBreaker" to 887,
                "CaveDweller" to 812,
                "PickaxePro" to 756,
                "OreMaster" to 701,
                "DeepMiner" to 654,
                "RockSmasher" to 598,
                "GoldRush" to 543,
                "IronWill" to 489
            )

            // Create placeholder avatar images
            val avatars = leaderboard.take(3).associate { (name, _) ->
                name to createPlaceholderAvatar()
            }

            val card = renderer.renderLeaderboardCard(
                title = "Mining Leaderboard",
                leaderboard = leaderboard,
                avatarImages = avatars
            )

            val outputFile = File(outputDir, "leaderboard-card-example.png")
            ImageIO.write(card, "PNG", outputFile)
            println("Generated: ${outputFile.absolutePath}")
        }

        it("generates a power level leaderboard example") {
            val leaderboard = listOf(
                "LegendPlayer" to 11052,
                "ProGamer" to 9845,
                "SkillMaster" to 8721,
                "VeteranMC" to 7654,
                "ElitePlayer" to 6543,
                "Dedicated" to 5432,
                "Experienced" to 4321,
                "Regular" to 3210,
                "Casual" to 2109,
                "Newbie" to 1098
            )

            val avatars = leaderboard.take(3).associate { (name, _) ->
                name to createPlaceholderAvatar()
            }

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
private fun createPlaceholderAvatar(): BufferedImage {
    val size = CardStyles.PODIUM_HEAD_SIZE
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
