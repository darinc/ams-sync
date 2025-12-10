package io.github.darinc.amssync.image

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage

/**
 * Utility functions for Graphics2D drawing operations.
 */
object GraphicsUtils {

    /**
     * Enable high-quality anti-aliasing and rendering hints.
     */
    fun enableAntialiasing(g2d: Graphics2D) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
    }

    /**
     * Draw a rounded rectangle with optional fill and stroke.
     */
    fun drawRoundedRect(
        g2d: Graphics2D,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        radius: Int,
        fill: Color? = null,
        stroke: Color? = null,
        strokeWidth: Int = 2
    ) {
        val shape = RoundRectangle2D.Float(
            x.toFloat(), y.toFloat(),
            width.toFloat(), height.toFloat(),
            radius.toFloat(), radius.toFloat()
        )

        if (fill != null) {
            g2d.color = fill
            g2d.fill(shape)
        }

        if (stroke != null) {
            g2d.color = stroke
            g2d.stroke = BasicStroke(strokeWidth.toFloat())
            g2d.draw(shape)
        }
    }

    /**
     * Draw a gradient-filled rounded rectangle.
     */
    fun drawGradientRoundedRect(
        g2d: Graphics2D,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        radius: Int,
        startColor: Color,
        endColor: Color,
        vertical: Boolean = true
    ) {
        val shape = RoundRectangle2D.Float(
            x.toFloat(), y.toFloat(),
            width.toFloat(), height.toFloat(),
            radius.toFloat(), radius.toFloat()
        )

        val gradient = if (vertical) {
            GradientPaint(x.toFloat(), y.toFloat(), startColor, x.toFloat(), (y + height).toFloat(), endColor)
        } else {
            GradientPaint(x.toFloat(), y.toFloat(), startColor, (x + width).toFloat(), y.toFloat(), endColor)
        }

        val oldPaint = g2d.paint
        g2d.paint = gradient
        g2d.fill(shape)
        g2d.paint = oldPaint
    }

    /**
     * Draw a skill progress bar with gradient fill.
     */
    fun drawProgressBar(
        g2d: Graphics2D,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        fillPercent: Float,
        startColor: Color,
        endColor: Color,
        backgroundColor: Color = CardStyles.SKILL_BAR_BG,
        radius: Int = CardStyles.SKILL_BAR_RADIUS
    ) {
        // Draw background
        drawRoundedRect(g2d, x, y, width, height, radius, fill = backgroundColor)

        // Draw fill (only if there's something to fill)
        if (fillPercent > 0f) {
            val fillWidth = (width * fillPercent.coerceIn(0f, 1f)).toInt()
            if (fillWidth > 0) {
                drawGradientRoundedRect(
                    g2d, x, y, fillWidth, height, radius,
                    startColor, endColor, vertical = false
                )
            }
        }
    }

    /**
     * Draw text centered horizontally at the given Y position.
     */
    fun drawCenteredString(
        g2d: Graphics2D,
        text: String,
        centerX: Int,
        y: Int,
        font: Font,
        color: Color
    ) {
        g2d.font = font
        g2d.color = color
        val metrics = g2d.fontMetrics
        val x = centerX - metrics.stringWidth(text) / 2
        g2d.drawString(text, x, y)
    }

    /**
     * Draw text left-aligned at the given position.
     */
    fun drawString(
        g2d: Graphics2D,
        text: String,
        x: Int,
        y: Int,
        font: Font,
        color: Color
    ) {
        g2d.font = font
        g2d.color = color
        g2d.drawString(text, x, y)
    }

    /**
     * Draw text right-aligned ending at the given X position.
     */
    fun drawRightAlignedString(
        g2d: Graphics2D,
        text: String,
        rightX: Int,
        y: Int,
        font: Font,
        color: Color
    ) {
        g2d.font = font
        g2d.color = color
        val metrics = g2d.fontMetrics
        val x = rightX - metrics.stringWidth(text)
        g2d.drawString(text, x, y)
    }

    /**
     * Fill the entire image with a vertical gradient.
     */
    fun fillGradientBackground(
        g2d: Graphics2D,
        width: Int,
        height: Int,
        startColor: Color,
        endColor: Color
    ) {
        val gradient = GradientPaint(0f, 0f, startColor, 0f, height.toFloat(), endColor)
        val oldPaint = g2d.paint
        g2d.paint = gradient
        g2d.fillRect(0, 0, width, height)
        g2d.paint = oldPaint
    }

    /**
     * Fill the entire image with a diagonal gradient (top-left to bottom-right).
     */
    fun fillDiagonalGradient(
        g2d: Graphics2D,
        width: Int,
        height: Int,
        startColor: Color,
        endColor: Color
    ) {
        val gradient = GradientPaint(
            0f, 0f, startColor,
            width.toFloat(), height.toFloat(), endColor
        )
        val oldPaint = g2d.paint
        g2d.paint = gradient
        g2d.fillRect(0, 0, width, height)
        g2d.paint = oldPaint
    }

    /**
     * Draw an image scaled to fit within the given bounds.
     */
    fun drawScaledImage(
        g2d: Graphics2D,
        image: BufferedImage,
        x: Int,
        y: Int,
        maxWidth: Int,
        maxHeight: Int
    ) {
        val scale = minOf(
            maxWidth.toDouble() / image.width,
            maxHeight.toDouble() / image.height
        )
        val scaledWidth = (image.width * scale).toInt()
        val scaledHeight = (image.height * scale).toInt()

        g2d.drawImage(image, x, y, scaledWidth, scaledHeight, null)
    }

    /**
     * Draw an image centered at the given position.
     */
    fun drawCenteredImage(
        g2d: Graphics2D,
        image: BufferedImage,
        centerX: Int,
        centerY: Int
    ) {
        val x = centerX - image.width / 2
        val y = centerY - image.height / 2
        g2d.drawImage(image, x, y, null)
    }

    /**
     * Create a simple glow effect around an image.
     */
    fun createGlowEffect(
        image: BufferedImage,
        glowColor: Color,
        glowRadius: Int = 5
    ): BufferedImage {
        val glowWidth = image.width + glowRadius * 2
        val glowHeight = image.height + glowRadius * 2
        val result = BufferedImage(glowWidth, glowHeight, BufferedImage.TYPE_INT_ARGB)
        val g2d = result.createGraphics()
        enableAntialiasing(g2d)

        // Draw glow layers (multiple passes with decreasing alpha)
        for (i in glowRadius downTo 1) {
            val alpha = (50 * i / glowRadius).coerceIn(0, 255)
            g2d.color = Color(glowColor.red, glowColor.green, glowColor.blue, alpha)
            g2d.fillOval(glowRadius - i, glowRadius - i, image.width + i * 2, image.height + i * 2)
        }

        // Draw the actual image on top
        g2d.drawImage(image, glowRadius, glowRadius, null)
        g2d.dispose()

        return result
    }

    /**
     * Draw a dotted line (for leaderboard name-score separator).
     */
    fun drawDottedLine(
        g2d: Graphics2D,
        startX: Int,
        endX: Int,
        y: Int,
        color: Color,
        dotSpacing: Int = 4
    ) {
        g2d.color = color
        var x = startX
        while (x < endX) {
            g2d.fillRect(x, y, 2, 2)
            x += dotSpacing
        }
    }

    /**
     * Draw a simple star shape (for mastery indicator).
     */
    fun drawStar(
        g2d: Graphics2D,
        centerX: Int,
        centerY: Int,
        outerRadius: Int,
        color: Color
    ) {
        val points = 5
        val innerRadius = outerRadius / 2
        val xPoints = IntArray(points * 2)
        val yPoints = IntArray(points * 2)

        for (i in 0 until points * 2) {
            val angle = Math.PI / 2 + i * Math.PI / points
            val radius = if (i % 2 == 0) outerRadius else innerRadius
            xPoints[i] = (centerX + radius * Math.cos(angle)).toInt()
            yPoints[i] = (centerY - radius * Math.sin(angle)).toInt()
        }

        g2d.color = color
        g2d.fillPolygon(xPoints, yPoints, points * 2)
    }

    /**
     * Draw a simple crown shape (for first place).
     */
    fun drawCrown(
        g2d: Graphics2D,
        centerX: Int,
        y: Int,
        width: Int,
        height: Int,
        color: Color
    ) {
        val halfWidth = width / 2
        val xPoints = intArrayOf(
            centerX - halfWidth,           // Bottom left
            centerX - halfWidth,           // Left up
            centerX - halfWidth / 2,       // Left peak base
            centerX - halfWidth + 5,       // Left valley
            centerX,                        // Center peak
            centerX + halfWidth - 5,       // Right valley
            centerX + halfWidth / 2,       // Right peak base
            centerX + halfWidth,           // Right up
            centerX + halfWidth            // Bottom right
        )
        val yPoints = intArrayOf(
            y + height,                     // Bottom left
            y + height / 3,                // Left up
            y,                              // Left peak
            y + height / 2,                // Left valley
            y,                              // Center peak
            y + height / 2,                // Right valley
            y,                              // Right peak
            y + height / 3,                // Right up
            y + height                      // Bottom right
        )

        g2d.color = color
        g2d.fillPolygon(xPoints, yPoints, xPoints.size)
    }

    /**
     * Get string width for a given font.
     */
    fun getStringWidth(g2d: Graphics2D, text: String, font: Font): Int {
        g2d.font = font
        return g2d.fontMetrics.stringWidth(text)
    }

    /**
     * Get font height (ascent + descent).
     */
    fun getFontHeight(g2d: Graphics2D, font: Font): Int {
        g2d.font = font
        val metrics = g2d.fontMetrics
        return metrics.ascent + metrics.descent
    }
}
