package io.github.darinc.amssync.image

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.awt.Color
import java.awt.image.BufferedImage

class SkinBodyRendererTest : DescribeSpec({

    fun skin(width: Int = 64, height: Int = 64) = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

    fun BufferedImage.fill(x: Int, y: Int, w: Int, h: Int, color: Color) {
        for (dy in 0 until h) for (dx in 0 until w) setRGB(x + dx, y + dy, color.rgb)
    }

    fun BufferedImage.alphaAt(x: Int, y: Int) = getRGB(x, y) ushr 24

    it("places the head, torso, arms and legs in a 16x32 front view") {
        val s = skin().apply {
            fill(8, 8, 8, 8, Color.RED) // head front
            fill(20, 20, 8, 12, Color.GREEN) // body front
            fill(44, 20, 4, 12, Color.BLUE) // right arm front
            fill(36, 52, 4, 12, Color.CYAN) // left arm front
            fill(4, 20, 4, 12, Color.YELLOW) // right leg front
            fill(20, 52, 4, 12, Color.MAGENTA) // left leg front
            fill(52, 20, 4, 12, Color.BLUE) // right arm back (makes it classic)
        }
        val body = SkinBodyRenderer.renderFront(s)

        body.width shouldBe 16
        body.height shouldBe 32
        body.getRGB(8, 4) shouldBe Color.RED.rgb
        body.getRGB(8, 14) shouldBe Color.GREEN.rgb
        body.getRGB(0, 14) shouldBe Color.BLUE.rgb
        body.getRGB(15, 14) shouldBe Color.CYAN.rgb
        body.getRGB(5, 25) shouldBe Color.YELLOW.rgb
        body.getRGB(10, 25) shouldBe Color.MAGENTA.rgb
        body.alphaAt(0, 2) shouldBe 0 // beside the head is empty
    }

    it("draws the outer layer over the base") {
        val s = skin().apply {
            fill(8, 8, 8, 8, Color.RED)
            fill(40, 8, 8, 2, Color.BLACK) // hat brim over the top two rows
        }
        val body = SkinBodyRenderer.renderFront(s, slim = false)
        body.getRGB(8, 0) shouldBe Color.BLACK.rgb
        body.getRGB(8, 5) shouldBe Color.RED.rgb
    }

    it("narrows the arms for slim skins") {
        val body = SkinBodyRenderer.renderFront(skin().apply { fill(44, 20, 3, 12, Color.BLUE) }, slim = true)
        body.alphaAt(0, 14) shouldBe 0
        body.getRGB(1, 14) shouldBe Color.BLUE.rgb
        body.alphaAt(15, 14) shouldBe 0
    }

    it("detects slim skins from the transparent arm column") {
        val slim = skin().apply { fill(44, 20, 3, 12, Color.BLUE) }
        SkinBodyRenderer.renderFront(slim).alphaAt(0, 14) shouldBe 0
    }

    it("mirrors the right limbs for legacy 64x32 skins") {
        val s = skin(height = 32).apply {
            fill(44, 20, 1, 12, Color.BLUE) // outer column of the right arm
            fill(45, 20, 3, 12, Color.WHITE)
        }
        val body = SkinBodyRenderer.renderFront(s)
        body.getRGB(0, 14) shouldBe Color.BLUE.rgb
        body.getRGB(15, 14) shouldBe Color.BLUE.rgb
    }

    it("reads HD skins at their own scale") {
        val s = skin(128, 128).apply { fill(16, 16, 16, 16, Color.RED) }
        SkinBodyRenderer.renderFront(s, slim = false).getRGB(8, 4) shouldBe Color.RED.rgb
    }

    it("draws a shroud shaped like a body") {
        val shroud = SkinBodyRenderer.renderShroud()
        shroud.alphaAt(8, 4) shouldBe 255
        shroud.alphaAt(0, 4) shouldBe 0
        shroud.alphaAt(0, 14) shouldBe 255
    }
})
