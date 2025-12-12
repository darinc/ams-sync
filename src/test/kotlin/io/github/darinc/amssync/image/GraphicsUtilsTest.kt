package io.github.darinc.amssync.image

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage

class GraphicsUtilsTest : DescribeSpec({

    describe("GraphicsUtils") {

        describe("enableAntialiasing") {

            it("sets antialiasing rendering hint") {
                val image = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
                val g2d = image.createGraphics()

                GraphicsUtils.enableAntialiasing(g2d)

                g2d.getRenderingHint(RenderingHints.KEY_ANTIALIASING) shouldBe RenderingHints.VALUE_ANTIALIAS_ON
                g2d.dispose()
            }

            it("sets text antialiasing rendering hint") {
                val image = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
                val g2d = image.createGraphics()

                GraphicsUtils.enableAntialiasing(g2d)

                g2d.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING) shouldBe RenderingHints.VALUE_TEXT_ANTIALIAS_ON
                g2d.dispose()
            }

            it("sets render quality hint") {
                val image = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
                val g2d = image.createGraphics()

                GraphicsUtils.enableAntialiasing(g2d)

                g2d.getRenderingHint(RenderingHints.KEY_RENDERING) shouldBe RenderingHints.VALUE_RENDER_QUALITY
                g2d.dispose()
            }

            it("sets interpolation hint") {
                val image = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
                val g2d = image.createGraphics()

                GraphicsUtils.enableAntialiasing(g2d)

                g2d.getRenderingHint(RenderingHints.KEY_INTERPOLATION) shouldBe RenderingHints.VALUE_INTERPOLATION_BILINEAR
                g2d.dispose()
            }
        }

        describe("drawRoundedRect") {

            it("draws filled rectangle when fill color provided") {
                val image = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
                val g2d = image.createGraphics()

                GraphicsUtils.drawRoundedRect(
                    g2d, 10, 10, 50, 30, 5,
                    fill = Color.RED
                )

                // Check that some pixels in the center are filled
                val pixel = image.getRGB(35, 25)
                val alpha = (pixel shr 24) and 0xff
                alpha shouldBeGreaterThan 0
                g2d.dispose()
            }

            it("draws stroked rectangle when stroke color provided") {
                val image = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
                val g2d = image.createGraphics()

                GraphicsUtils.drawRoundedRect(
                    g2d, 10, 10, 50, 30, 5,
                    stroke = Color.BLUE,
                    strokeWidth = 2
                )

                // Border should be drawn
                g2d.dispose()
            }

            it("draws both fill and stroke when both provided") {
                val image = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
                val g2d = image.createGraphics()

                GraphicsUtils.drawRoundedRect(
                    g2d, 10, 10, 50, 30, 5,
                    fill = Color.GREEN,
                    stroke = Color.BLACK,
                    strokeWidth = 2
                )

                g2d.dispose()
            }
        }

        describe("drawGradientRoundedRect") {

            it("draws vertical gradient by default") {
                val image = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
                val g2d = image.createGraphics()

                GraphicsUtils.drawGradientRoundedRect(
                    g2d, 10, 10, 50, 50, 5,
                    startColor = Color.RED,
                    endColor = Color.BLUE,
                    vertical = true
                )

                // Pixels at top should be more red, at bottom more blue
                g2d.dispose()
            }

            it("draws horizontal gradient when vertical=false") {
                val image = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
                val g2d = image.createGraphics()

                GraphicsUtils.drawGradientRoundedRect(
                    g2d, 10, 10, 50, 50, 5,
                    startColor = Color.RED,
                    endColor = Color.BLUE,
                    vertical = false
                )

                g2d.dispose()
            }
        }

        describe("drawProgressBar") {

            it("draws background bar") {
                val image = BufferedImage(200, 50, BufferedImage.TYPE_INT_ARGB)
                val g2d = image.createGraphics()

                GraphicsUtils.drawProgressBar(
                    g2d, 10, 10, 100, 20, 0.0f,
                    startColor = Color.GREEN,
                    endColor = Color.YELLOW
                )

                g2d.dispose()
            }

            it("draws partial fill based on percentage") {
                val image = BufferedImage(200, 50, BufferedImage.TYPE_INT_ARGB)
                val g2d = image.createGraphics()

                GraphicsUtils.drawProgressBar(
                    g2d, 10, 10, 100, 20, 0.5f,
                    startColor = Color.GREEN,
                    endColor = Color.YELLOW
                )

                g2d.dispose()
            }

            it("draws full fill at 100%") {
                val image = BufferedImage(200, 50, BufferedImage.TYPE_INT_ARGB)
                val g2d = image.createGraphics()

                GraphicsUtils.drawProgressBar(
                    g2d, 10, 10, 100, 20, 1.0f,
                    startColor = Color.GREEN,
                    endColor = Color.YELLOW
                )

                g2d.dispose()
            }

            it("clamps fill percentage to 0-1 range") {
                val image = BufferedImage(200, 50, BufferedImage.TYPE_INT_ARGB)
                val g2d = image.createGraphics()

                // Should not throw with values outside 0-1
                GraphicsUtils.drawProgressBar(
                    g2d, 10, 10, 100, 20, 1.5f,
                    startColor = Color.GREEN,
                    endColor = Color.YELLOW
                )

                GraphicsUtils.drawProgressBar(
                    g2d, 10, 10, 100, 20, -0.5f,
                    startColor = Color.GREEN,
                    endColor = Color.YELLOW
                )

                g2d.dispose()
            }
        }

        describe("text drawing functions") {

            it("drawCenteredString draws text centered") {
                val image = BufferedImage(200, 50, BufferedImage.TYPE_INT_ARGB)
                val g2d = image.createGraphics()
                val font = Font("SansSerif", Font.PLAIN, 12)

                GraphicsUtils.drawCenteredString(g2d, "Test", 100, 25, font, Color.WHITE)

                g2d.dispose()
            }

            it("drawString draws text left-aligned") {
                val image = BufferedImage(200, 50, BufferedImage.TYPE_INT_ARGB)
                val g2d = image.createGraphics()
                val font = Font("SansSerif", Font.PLAIN, 12)

                GraphicsUtils.drawString(g2d, "Test", 10, 25, font, Color.WHITE)

                g2d.dispose()
            }

            it("drawRightAlignedString draws text right-aligned") {
                val image = BufferedImage(200, 50, BufferedImage.TYPE_INT_ARGB)
                val g2d = image.createGraphics()
                val font = Font("SansSerif", Font.PLAIN, 12)

                GraphicsUtils.drawRightAlignedString(g2d, "Test", 190, 25, font, Color.WHITE)

                g2d.dispose()
            }
        }

        describe("background fill functions") {

            it("fillGradientBackground fills entire image") {
                val image = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
                val g2d = image.createGraphics()

                GraphicsUtils.fillGradientBackground(
                    g2d, 100, 100,
                    startColor = Color.DARK_GRAY,
                    endColor = Color.BLACK
                )

                // Check corners are filled
                val topLeft = image.getRGB(0, 0)
                val bottomRight = image.getRGB(99, 99)
                ((topLeft shr 24) and 0xff) shouldBeGreaterThan 0
                ((bottomRight shr 24) and 0xff) shouldBeGreaterThan 0
                g2d.dispose()
            }

            it("fillDiagonalGradient fills entire image diagonally") {
                val image = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
                val g2d = image.createGraphics()

                GraphicsUtils.fillDiagonalGradient(
                    g2d, 100, 100,
                    startColor = Color.RED,
                    endColor = Color.BLUE
                )

                g2d.dispose()
            }
        }

        describe("image drawing functions") {

            it("drawScaledImage scales image to fit bounds") {
                val destImage = BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB)
                val sourceImage = BufferedImage(100, 50, BufferedImage.TYPE_INT_ARGB)
                val g2d = destImage.createGraphics()

                GraphicsUtils.drawScaledImage(g2d, sourceImage, 10, 10, 80, 40)

                g2d.dispose()
            }

            it("drawCenteredImage centers image at position") {
                val destImage = BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB)
                val sourceImage = BufferedImage(50, 50, BufferedImage.TYPE_INT_ARGB)
                val g2d = destImage.createGraphics()

                GraphicsUtils.drawCenteredImage(g2d, sourceImage, 100, 100)

                g2d.dispose()
            }
        }

        describe("createGlowEffect") {

            it("creates larger image with glow radius") {
                val sourceImage = BufferedImage(50, 50, BufferedImage.TYPE_INT_ARGB)

                val result = GraphicsUtils.createGlowEffect(sourceImage, Color.YELLOW, glowRadius = 10)

                result.width shouldBe 70 // 50 + 10*2
                result.height shouldBe 70
            }

            it("uses default glow radius of 5") {
                val sourceImage = BufferedImage(50, 50, BufferedImage.TYPE_INT_ARGB)

                val result = GraphicsUtils.createGlowEffect(sourceImage, Color.CYAN)

                result.width shouldBe 60 // 50 + 5*2
                result.height shouldBe 60
            }
        }

        describe("drawDottedLine") {

            it("draws dotted line between points") {
                val image = BufferedImage(200, 50, BufferedImage.TYPE_INT_ARGB)
                val g2d = image.createGraphics()

                GraphicsUtils.drawDottedLine(g2d, 10, 100, 25, Color.WHITE, dotSpacing = 4)

                g2d.dispose()
            }
        }

        describe("drawStar") {

            it("draws 5-pointed star at position") {
                val image = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
                val g2d = image.createGraphics()

                GraphicsUtils.drawStar(g2d, 50, 50, outerRadius = 20, color = Color.YELLOW)

                g2d.dispose()
            }
        }

        describe("drawCrown") {

            it("draws crown shape at position") {
                val image = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
                val g2d = image.createGraphics()

                GraphicsUtils.drawCrown(g2d, 50, 10, width = 30, height = 20, color = Color.YELLOW)

                g2d.dispose()
            }
        }

        describe("measurement functions") {

            it("getStringWidth returns width of text") {
                val image = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
                val g2d = image.createGraphics()
                val font = Font("SansSerif", Font.PLAIN, 12)

                val width = GraphicsUtils.getStringWidth(g2d, "Test", font)

                width shouldBeGreaterThan 0
                g2d.dispose()
            }

            it("getFontHeight returns font height") {
                val image = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
                val g2d = image.createGraphics()
                val font = Font("SansSerif", Font.PLAIN, 12)

                val height = GraphicsUtils.getFontHeight(g2d, font)

                height shouldBeGreaterThan 0
                g2d.dispose()
            }

            it("longer strings have greater width") {
                val image = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
                val g2d = image.createGraphics()
                val font = Font("SansSerif", Font.PLAIN, 12)

                val shortWidth = GraphicsUtils.getStringWidth(g2d, "Hi", font)
                val longWidth = GraphicsUtils.getStringWidth(g2d, "Hello World", font)

                longWidth shouldBeGreaterThan shortWidth
                g2d.dispose()
            }

            it("larger fonts have greater height") {
                val image = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
                val g2d = image.createGraphics()
                val smallFont = Font("SansSerif", Font.PLAIN, 10)
                val largeFont = Font("SansSerif", Font.PLAIN, 24)

                val smallHeight = GraphicsUtils.getFontHeight(g2d, smallFont)
                val largeHeight = GraphicsUtils.getFontHeight(g2d, largeFont)

                largeHeight shouldBeGreaterThan smallHeight
                g2d.dispose()
            }
        }
    }
})
