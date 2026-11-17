package io.github.darinc.amssync.image

import java.awt.BasicStroke
import java.awt.Color
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.Polygon
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage

/**
 * Draws an open coffin seen from above with the player lying face up inside, head on a pillow.
 *
 * The coffin uses the classic six-sided shape: narrow at the head, widest at the shoulders,
 * tapering to the feet. All coordinates are relative to the coffin's center.
 */
object CoffinPainter {

    /** Size of one skin pixel on the card */
    const val PIXEL = 9

    // Interior outline: x positions along the coffin and half-widths at those points
    private const val HEAD_END = -170
    private const val SHOULDER = -80
    private const val FOOT_END = 170
    private const val HEAD_HALF = 50
    private const val SHOULDER_HALF = 86
    private const val FOOT_HALF = 50

    /** Thickness of the wooden rim around the lining */
    private const val RIM = 14

    /** Where the top of the head sits, relative to the coffin center */
    private const val BODY_START = -152

    /** Full outer length of the coffin */
    const val OUTER_LENGTH = (FOOT_END - HEAD_END) + RIM * 2

    /** Full outer height (width across the shoulders) of the coffin */
    const val OUTER_HEIGHT = (SHOULDER_HALF + RIM) * 2

    private val WOOD_LIGHT = Color(122, 78, 46)
    private val WOOD_DARK = Color(70, 42, 24)
    private val WOOD_EDGE = Color(42, 24, 13)
    private val GRAIN = Color(52, 30, 16, 90)
    private val LINING_LIGHT = Color(150, 28, 40)
    private val LINING_DARK = Color(88, 12, 24)
    private val QUILT = Color(60, 6, 16, 110)
    private val PILLOW = Color(236, 228, 214)
    private val PILLOW_SHADE = Color(196, 184, 166)
    private val BRASS = Color(201, 160, 72)
    private val BRASS_DARK = Color(130, 98, 36)
    private val SHADOW = Color(0, 0, 0, 120)
    private val PETAL = Color(246, 244, 236)
    private val PETAL_SHADE = Color(214, 210, 196)
    private val STAMEN = Color(240, 196, 60)
    private val STEM = Color(64, 132, 56)

    /**
     * Paint the coffin and body centered on ([centerX], [centerY]).
     *
     * @param body Front view from [SkinBodyRenderer] (16 x 32), head at the top
     */
    fun paint(g2d: Graphics2D, centerX: Int, centerY: Int, body: BufferedImage) {
        val g = g2d.create() as Graphics2D
        try {
            g.translate(centerX, centerY)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            val outer = outline(RIM)
            val inner = outline(0)

            drawShadow(g, outer)
            drawWood(g, outer)
            drawHandles(g)
            drawLining(g, inner)
            drawPillow(g)
            drawBody(g, body)
            drawLily(g)
        } finally {
            g.dispose()
        }
    }

    private fun outline(grow: Int): Polygon {
        val xs = intArrayOf(HEAD_END - grow, SHOULDER, FOOT_END + grow, FOOT_END + grow, SHOULDER, HEAD_END - grow)
        val ys = intArrayOf(
            -(HEAD_HALF + grow), -(SHOULDER_HALF + grow), -(FOOT_HALF + grow),
            FOOT_HALF + grow, SHOULDER_HALF + grow, HEAD_HALF + grow
        )
        return Polygon(xs, ys, xs.size)
    }

    private fun drawShadow(g: Graphics2D, outer: Polygon) {
        val shadow = Polygon(outer.xpoints, outer.ypoints, outer.npoints).apply { translate(6, 8) }
        g.color = SHADOW
        g.fillPolygon(shadow)
    }

    private fun drawWood(g: Graphics2D, outer: Polygon) {
        g.paint = GradientPaint(0f, -OUTER_HEIGHT / 2f, WOOD_LIGHT, 0f, OUTER_HEIGHT / 2f, WOOD_DARK)
        g.fillPolygon(outer)

        // Wood grain along the length
        val clip = g.clip
        g.clip(outer)
        g.color = GRAIN
        g.stroke = BasicStroke(1f)
        var y = -OUTER_HEIGHT / 2
        var wave = 0
        while (y < OUTER_HEIGHT / 2) {
            g.drawLine(HEAD_END - RIM, y + wave, FOOT_END + RIM, y - wave)
            y += GRAIN_SPACING
            wave = (wave + 1) % 3
        }
        g.clip = clip

        g.color = WOOD_EDGE
        g.stroke = BasicStroke(2f)
        g.drawPolygon(outer)
    }

    private fun drawHandles(g: Graphics2D) {
        listOf(-60, 60).forEach { x ->
            listOf(-1, 1).forEach { side ->
                val edge = side * (halfWidthAt(x) + RIM)
                val y = if (side < 0) edge - HANDLE_DEPTH else edge
                g.color = BRASS_DARK
                g.fillRoundRect(x - HANDLE_LENGTH / 2, y, HANDLE_LENGTH, HANDLE_DEPTH, 4, 4)
                g.color = BRASS
                g.fillRoundRect(x - HANDLE_LENGTH / 2 + 1, y + 1, HANDLE_LENGTH - 2, HANDLE_DEPTH - 3, 4, 4)
            }
        }
    }

    private fun drawLining(g: Graphics2D, inner: Polygon) {
        g.paint = GradientPaint(0f, -SHOULDER_HALF.toFloat(), LINING_LIGHT, 0f, SHOULDER_HALF.toFloat(), LINING_DARK)
        g.fillPolygon(inner)

        // Quilted diamond pattern
        val clip = g.clip
        g.clip(inner)
        g.color = QUILT
        g.stroke = BasicStroke(1.5f)
        var d = HEAD_END - SHOULDER_HALF * 2
        while (d < FOOT_END + SHOULDER_HALF * 2) {
            g.drawLine(d, -SHOULDER_HALF, d + SHOULDER_HALF * 2, SHOULDER_HALF)
            g.drawLine(d, SHOULDER_HALF, d + SHOULDER_HALF * 2, -SHOULDER_HALF)
            d += QUILT_SPACING
        }
        g.clip = clip

        // Inner shadow where the lining meets the rim
        g.color = Color(0, 0, 0, 90)
        g.stroke = BasicStroke(4f)
        g.drawPolygon(inner)
    }

    private fun drawPillow(g: Graphics2D) {
        val x = HEAD_END + 6
        val half = HEAD_HALF - 10
        g.color = PILLOW_SHADE
        g.fillRoundRect(x, -half, PILLOW_LENGTH, half * 2, 26, 26)
        g.color = PILLOW
        g.fillRoundRect(x + 2, -half + 2, PILLOW_LENGTH - 6, half * 2 - 6, 22, 22)
    }

    /**
     * Lay the body down face up: rotate the front view so the head points left, then scale
     * with nearest-neighbor so skin pixels stay crisp.
     */
    private fun drawBody(g: Graphics2D, body: BufferedImage) {
        val width = body.width * PIXEL
        val transform = AffineTransform().apply {
            translate(BODY_START.toDouble(), width / 2.0)
            rotate(-Math.PI / 2)
            scale(PIXEL.toDouble(), PIXEL.toDouble())
        }

        // Soft shadow under the body
        val shadow = AffineTransform(transform).apply { preConcatenate(AffineTransform.getTranslateInstance(4.0, 5.0)) }
        g.drawImage(silhouette(body), shadow, null)

        val previous = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        g.drawImage(shadeArms(body), transform, null)
        if (previous != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, previous)

        drawSeams(g, transform)
    }

    /** Darken the arms slightly, like Minecraft's side lighting, so they stand apart from the torso. */
    private fun shadeArms(body: BufferedImage): BufferedImage {
        val out = BufferedImage(body.width, body.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until body.height) {
            for (x in 0 until body.width) {
                val argb = body.getRGB(x, y)
                val isArm = y in ARM_ROWS && (x < ARM_WIDTH || x >= body.width - ARM_WIDTH)
                out.setRGB(x, y, if (isArm && (argb ushr 24) != 0) darken(argb) else argb)
            }
        }
        return out
    }

    private fun darken(argb: Int): Int {
        val alpha = argb and ALPHA_MASK
        val r = ((argb shr 16 and 0xFF) * ARM_SHADE).toInt()
        val gr = ((argb shr 8 and 0xFF) * ARM_SHADE).toInt()
        val b = ((argb and 0xFF) * ARM_SHADE).toInt()
        return alpha or (r shl 16) or (gr shl 8) or b
    }

    /** Thin dark lines between the arms and torso and between the legs. */
    private fun drawSeams(g: Graphics2D, bodyTransform: AffineTransform) {
        val seams = listOf(
            doubleArrayOf(4.0, 8.0, 4.0, 20.0),
            doubleArrayOf(12.0, 8.0, 12.0, 20.0),
            doubleArrayOf(8.0, 20.0, 8.0, 32.0)
        )
        g.color = SEAM
        g.stroke = BasicStroke(2f)
        seams.forEach { seam ->
            val pts = seam.copyOf()
            bodyTransform.transform(pts, 0, pts, 0, 2)
            g.drawLine(pts[0].toInt(), pts[1].toInt(), pts[2].toInt(), pts[3].toInt())
        }
    }

    private fun silhouette(body: BufferedImage): BufferedImage {
        val out = BufferedImage(body.width, body.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until body.height) {
            for (x in 0 until body.width) {
                if ((body.getRGB(x, y) ushr 24) != 0) out.setRGB(x, y, SHADOW.rgb)
            }
        }
        return out
    }

    /** A white lily resting on the chest, drawn as pixel art to match the skin. */
    private fun drawLily(g: Graphics2D) {
        val p = LILY_PIXEL
        // Chest center: between the shoulders, a little below the neck
        val originX = BODY_START + 10 * PIXEL
        val originY = -LILY[0].length * p / 2
        LILY.forEachIndexed { row, line ->
            line.forEachIndexed { col, ch ->
                val color = when (ch) {
                    'W' -> PETAL
                    'w' -> PETAL_SHADE
                    'y' -> STAMEN
                    'g' -> STEM
                    else -> null
                } ?: return@forEachIndexed
                g.color = color
                // Lily is drawn bloom-toward-head, stem toward the feet
                g.fillRect(originX + row * p, originY + col * p, p, p)
            }
        }
    }

    private val LILY = listOf(
        ".W.W.",
        "WwyWw",
        ".WyW.",
        "..g..",
        "..g..",
        ".gg..",
        "..g..",
        "..g.."
    )

    private fun halfWidthAt(x: Int): Int = when {
        x <= SHOULDER -> HEAD_HALF + (SHOULDER_HALF - HEAD_HALF) * (x - HEAD_END) / (SHOULDER - HEAD_END)
        else -> SHOULDER_HALF - (SHOULDER_HALF - FOOT_HALF) * (x - SHOULDER) / (FOOT_END - SHOULDER)
    }

    private const val GRAIN_SPACING = 7
    private const val LILY_PIXEL = 7
    private val ARM_ROWS = 8 until 20
    private const val ARM_WIDTH = 4
    private const val ARM_SHADE = 0.8
    private const val ALPHA_MASK = -0x1000000
    private val SEAM = Color(128, 128, 128, 150)
    private const val QUILT_SPACING = 26
    private const val HANDLE_LENGTH = 34
    private const val HANDLE_DEPTH = 8
    private const val PILLOW_LENGTH = 70
}
