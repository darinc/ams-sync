package io.github.darinc.amssync.image

import java.awt.Color
import java.awt.image.BufferedImage

/**
 * Builds a flat, front-facing pixel body (16 x 32 skin pixels) from a Minecraft skin texture.
 *
 * Handles modern 64x64 skins (separate left limbs and outer layers) and legacy 64x32 skins
 * (left limbs mirrored from the right, hat layer only). Slim ("Alex") skins have 3-pixel arms.
 */
object SkinBodyRenderer {

    /** Width of the front view in skin pixels */
    const val BODY_WIDTH = 16

    /** Height of the front view in skin pixels */
    const val BODY_HEIGHT = 32

    private const val LIMB = 4
    private const val HEAD = 8
    private const val TORSO_HEIGHT = 12

    /** One front face: where it sits in the skin and where it goes in the front view. */
    private data class Part(
        val srcX: Int,
        val srcY: Int,
        val width: Int,
        val height: Int,
        val dstX: Int,
        val dstY: Int,
        val mirror: Boolean = false
    )

    /**
     * Render the front view of a skin.
     *
     * @param skin Skin texture (64x64 or legacy 64x32, or an integer multiple for HD skins)
     * @param slim Whether the skin uses slim arms; null detects it from the texture
     * @return 16x32 ARGB image with transparent background
     */
    fun renderFront(skin: BufferedImage, slim: Boolean? = null): BufferedImage {
        val scale = (skin.width / 64).coerceAtLeast(1)
        val legacy = skin.height * 2 == skin.width
        val isSlim = slim ?: (!legacy && detectSlim(skin, scale))
        val arm = if (isSlim) LIMB - 1 else LIMB
        val out = BufferedImage(BODY_WIDTH, BODY_HEIGHT, BufferedImage.TYPE_INT_ARGB)

        // Base layer
        val base = buildList {
            add(Part(8, 8, HEAD, HEAD, 4, 0))
            add(Part(20, 20, 8, TORSO_HEIGHT, 4, 8))
            add(Part(44, 20, arm, TORSO_HEIGHT, LIMB - arm, 8)) // right arm (viewer's left)
            add(Part(4, 20, LIMB, TORSO_HEIGHT, 4, 20)) // right leg
            if (legacy) {
                add(Part(44, 20, arm, TORSO_HEIGHT, 12, 8, mirror = true))
                add(Part(4, 20, LIMB, TORSO_HEIGHT, 8, 20, mirror = true))
            } else {
                add(Part(36, 52, arm, TORSO_HEIGHT, 12, 8)) // left arm
                add(Part(20, 52, LIMB, TORSO_HEIGHT, 8, 20)) // left leg
            }
        }
        base.forEach { copy(skin, out, it, scale, overlay = false) }

        // Outer layer (hat, jacket, sleeves, pants)
        val overlay = buildList {
            add(Part(40, 8, HEAD, HEAD, 4, 0))
            if (!legacy) {
                add(Part(20, 36, 8, TORSO_HEIGHT, 4, 8))
                add(Part(44, 36, arm, TORSO_HEIGHT, LIMB - arm, 8))
                add(Part(52, 52, arm, TORSO_HEIGHT, 12, 8))
                add(Part(4, 36, LIMB, TORSO_HEIGHT, 4, 20))
                add(Part(4, 52, LIMB, TORSO_HEIGHT, 8, 20))
            }
        }
        overlay.forEach { copy(skin, out, it, scale, overlay = true) }

        return out
    }

    /**
     * A plain burial shroud in the shape of a body, used when the skin can't be fetched.
     */
    fun renderShroud(): BufferedImage {
        val out = BufferedImage(BODY_WIDTH, BODY_HEIGHT, BufferedImage.TYPE_INT_ARGB)
        val linen = Color(214, 204, 182).rgb
        val fold = Color(176, 164, 140).rgb
        for (y in 0 until BODY_HEIGHT) {
            val (from, to) = when {
                y < HEAD -> 4 to 12
                y < HEAD + TORSO_HEIGHT -> 0 to 16
                else -> 4 to 12
            }
            for (x in from until to) {
                out.setRGB(x, y, if (y % 3 == 2) fold else linen)
            }
        }
        return out
    }

    private fun copy(skin: BufferedImage, out: BufferedImage, part: Part, scale: Int, overlay: Boolean) {
        for (y in 0 until part.height) {
            for (x in 0 until part.width) {
                val srcX = (part.srcX + if (part.mirror) part.width - 1 - x else x) * scale
                val srcY = (part.srcY + y) * scale
                if (srcX >= skin.width || srcY >= skin.height) continue
                val argb = skin.getRGB(srcX, srcY)
                val alpha = argb ushr 24
                if (overlay && alpha == 0) continue
                // Base layer is always opaque in-game
                out.setRGB(part.dstX + x, part.dstY + y, if (overlay) argb else argb or OPAQUE)
            }
        }
    }

    /** Slim skins leave the outer column of the right arm's back face transparent. */
    private fun detectSlim(skin: BufferedImage, scale: Int): Boolean =
        (skin.getRGB(SLIM_PROBE_X * scale, SLIM_PROBE_Y * scale) ushr 24) == 0

    private const val SLIM_PROBE_X = 54
    private const val SLIM_PROBE_Y = 20
    private const val OPAQUE = 0xFF shl 24
}
