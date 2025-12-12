package io.github.darinc.amssync.image

import io.github.darinc.amssync.progression.Timeframe
import io.github.darinc.amssync.progression.TrendPoint
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.text.NumberFormat
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Renders line chart images for skill progression visualization.
 *
 * Creates a 600x400 PNG image showing:
 * - Header with player name, skill, and timeframe
 * - Line chart with data points
 * - X-axis with date labels
 * - Y-axis with level values
 * - Footer with server branding
 *
 * @property serverName Server name displayed in footer
 */
class ProgressionChartRenderer(
    private val serverName: String
) {
    private val numberFormat = NumberFormat.getNumberInstance(Locale.US)

    companion object {
        private const val Y_AXIS_LABEL_WIDTH = 45
        private const val X_AXIS_LABEL_HEIGHT = 25
        private const val GRID_LINES = 5
        private const val MAX_X_LABELS = 6
        private const val LINE_WIDTH = 2.5f
        private const val GLOW_WIDTH = 6f
        private const val POINT_RADIUS = 4
    }

    /**
     * Render a progression chart.
     *
     * @param playerName Player name for header
     * @param skillDisplayName Skill display name (or "Power Level")
     * @param points Data points to plot
     * @param timeframe Selected timeframe
     * @param avatar Optional player avatar for header
     * @return BufferedImage of the chart (600x400 PNG)
     */
    fun renderChart(
        playerName: String,
        skillDisplayName: String,
        points: List<TrendPoint>,
        timeframe: Timeframe,
        avatar: BufferedImage? = null
    ): BufferedImage {
        val width = CardStyles.CHART_WIDTH
        val height = CardStyles.CHART_HEIGHT

        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g2d = image.createGraphics()
        GraphicsUtils.enableAntialiasing(g2d)

        // Draw background gradient
        GraphicsUtils.fillDiagonalGradient(
            g2d, width, height,
            CardStyles.BACKGROUND_GRADIENT_START,
            CardStyles.BACKGROUND_GRADIENT_END
        )

        // Draw border
        GraphicsUtils.drawRoundedRect(
            g2d, 8, 8, width - 16, height - 16,
            CardStyles.CARD_CORNER_RADIUS,
            fill = null,
            stroke = CardStyles.BORDER_GOLD,
            strokeWidth = 2
        )

        // Draw header
        drawHeader(g2d, playerName, skillDisplayName, timeframe, avatar)

        // Calculate chart area
        val chartX = CardStyles.CHART_PADDING + Y_AXIS_LABEL_WIDTH
        val chartY = CardStyles.CHART_TITLE_HEIGHT + 10
        val chartWidth = width - chartX - CardStyles.CHART_PADDING
        val chartHeight = height - chartY - CardStyles.CHART_FOOTER_HEIGHT - X_AXIS_LABEL_HEIGHT - 10

        if (points.isNotEmpty()) {
            // Calculate data range
            val minLevel = points.minOf { it.level }
            val maxLevel = points.maxOf { it.level }
            val range = (maxLevel - minLevel).coerceAtLeast(1)
            val paddedMin = (minLevel - range * 0.1).toInt().coerceAtLeast(0)
            val paddedMax = (maxLevel + range * 0.1).toInt().coerceAtLeast(paddedMin + 10)

            // Draw grid and axes
            drawGrid(g2d, chartX, chartY, chartWidth, chartHeight, paddedMin, paddedMax)

            // Draw X-axis labels
            drawXAxisLabels(g2d, chartX, chartY + chartHeight + 5, chartWidth, points, timeframe)

            // Draw data line and points
            drawDataLine(g2d, chartX, chartY, chartWidth, chartHeight, points, paddedMin, paddedMax)
        } else {
            // No data message
            GraphicsUtils.drawCenteredString(
                g2d, "No data available",
                chartX + chartWidth / 2, chartY + chartHeight / 2,
                CardStyles.FONT_CHART_SUBTITLE, CardStyles.TEXT_GRAY
            )
        }

        // Draw footer
        drawFooter(g2d, width, height)

        g2d.dispose()
        return image
    }

    /**
     * Draw the chart header with player name, skill, and optional avatar.
     */
    private fun drawHeader(
        g2d: Graphics2D,
        playerName: String,
        skillDisplayName: String,
        timeframe: Timeframe,
        avatar: BufferedImage?
    ) {
        var textX = CardStyles.CHART_PADDING + 10

        // Draw avatar if provided
        avatar?.let {
            val avatarSize = 40
            g2d.drawImage(it, CardStyles.CHART_PADDING + 5, 15, avatarSize, avatarSize, null)
            textX += avatarSize + 10
        }

        // Draw player name
        GraphicsUtils.drawString(
            g2d, playerName, textX, 35,
            CardStyles.FONT_CHART_TITLE, CardStyles.TEXT_WHITE
        )

        // Draw skill and timeframe
        val subtitle = "$skillDisplayName - ${timeframe.displayName}"
        GraphicsUtils.drawString(
            g2d, subtitle, textX, 52,
            CardStyles.FONT_CHART_SUBTITLE, CardStyles.TEXT_GRAY
        )
    }

    /**
     * Draw horizontal grid lines and Y-axis labels.
     */
    private fun drawGrid(
        g2d: Graphics2D,
        x: Int, y: Int,
        width: Int, height: Int,
        minLevel: Int, maxLevel: Int
    ) {
        val levelRange = maxLevel - minLevel

        g2d.color = CardStyles.CHART_GRID_COLOR
        g2d.stroke = BasicStroke(1f)

        for (i in 0..GRID_LINES) {
            val lineY = y + (height * i / GRID_LINES)
            g2d.drawLine(x, lineY, x + width, lineY)

            // Y-axis label
            val levelValue = maxLevel - (levelRange * i / GRID_LINES)
            GraphicsUtils.drawRightAlignedString(
                g2d, numberFormat.format(levelValue),
                x - 8, lineY + 4,
                CardStyles.FONT_CHART_AXIS, CardStyles.CHART_AXIS_COLOR
            )
        }

        // Draw left and bottom axis lines
        g2d.color = CardStyles.CHART_AXIS_COLOR
        g2d.stroke = BasicStroke(1.5f)
        g2d.drawLine(x, y, x, y + height)  // Left axis
        g2d.drawLine(x, y + height, x + width, y + height)  // Bottom axis
    }

    /**
     * Draw X-axis date labels.
     */
    private fun drawXAxisLabels(
        g2d: Graphics2D,
        x: Int, y: Int,
        width: Int,
        points: List<TrendPoint>,
        timeframe: Timeframe
    ) {
        if (points.size < 2) {
            // Single point - just draw its date
            if (points.isNotEmpty()) {
                val formatter = getDateFormatter(timeframe)
                val label = formatter.format(points[0].timestamp.atZone(ZoneId.systemDefault()))
                GraphicsUtils.drawCenteredString(
                    g2d, label, x + width / 2, y + 15,
                    CardStyles.FONT_CHART_AXIS, CardStyles.CHART_AXIS_COLOR
                )
            }
            return
        }

        val formatter = getDateFormatter(timeframe)
        val labelCount = MAX_X_LABELS.coerceAtMost(points.size)

        for (i in 0 until labelCount) {
            val idx = if (labelCount == 1) 0 else i * (points.size - 1) / (labelCount - 1)
            val point = points[idx]
            val labelX = x + if (labelCount == 1) width / 2 else (width * i / (labelCount - 1))
            val label = formatter.format(point.timestamp.atZone(ZoneId.systemDefault()))

            GraphicsUtils.drawCenteredString(
                g2d, label, labelX, y + 15,
                CardStyles.FONT_CHART_AXIS, CardStyles.CHART_AXIS_COLOR
            )
        }
    }

    /**
     * Get appropriate date formatter based on timeframe.
     */
    private fun getDateFormatter(timeframe: Timeframe): DateTimeFormatter {
        return when {
            timeframe.days <= 7 -> DateTimeFormatter.ofPattern("MM/dd HH:mm")
            timeframe.days <= 90 -> DateTimeFormatter.ofPattern("MM/dd")
            else -> DateTimeFormatter.ofPattern("MMM yy")
        }
    }

    /**
     * Draw the data line with glow effect and points.
     */
    private fun drawDataLine(
        g2d: Graphics2D,
        chartX: Int, chartY: Int,
        chartWidth: Int, chartHeight: Int,
        points: List<TrendPoint>,
        minLevel: Int, maxLevel: Int
    ) {
        if (points.isEmpty()) return

        val levelRange = (maxLevel - minLevel).coerceAtLeast(1)

        // Calculate pixel coordinates for all points
        val xPoints = IntArray(points.size)
        val yPoints = IntArray(points.size)

        if (points.size == 1) {
            // Single point - center it
            xPoints[0] = chartX + chartWidth / 2
            yPoints[0] = chartY + chartHeight - (chartHeight * (points[0].level - minLevel) / levelRange)
        } else {
            val firstTime = points.first().timestamp
            val lastTime = points.last().timestamp
            val timeRange = Duration.between(firstTime, lastTime).toMillis().coerceAtLeast(1)

            points.forEachIndexed { i, point ->
                val timeOffset = Duration.between(firstTime, point.timestamp).toMillis()
                xPoints[i] = chartX + (chartWidth * timeOffset / timeRange).toInt()
                yPoints[i] = chartY + chartHeight - (chartHeight * (point.level - minLevel) / levelRange)
            }
        }

        if (points.size > 1) {
            // Draw glow effect
            g2d.color = CardStyles.CHART_LINE_GLOW
            g2d.stroke = BasicStroke(GLOW_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g2d.drawPolyline(xPoints, yPoints, points.size)

            // Draw main line
            g2d.color = CardStyles.CHART_LINE_COLOR
            g2d.stroke = BasicStroke(LINE_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g2d.drawPolyline(xPoints, yPoints, points.size)
        }

        // Draw points
        g2d.color = CardStyles.CHART_POINT_COLOR
        points.indices.forEach { i ->
            g2d.fillOval(
                xPoints[i] - POINT_RADIUS,
                yPoints[i] - POINT_RADIUS,
                POINT_RADIUS * 2,
                POINT_RADIUS * 2
            )
        }

        // Draw current level label at the last point
        if (points.isNotEmpty()) {
            val lastIdx = points.size - 1
            val lastLevel = points[lastIdx].level
            val labelX = xPoints[lastIdx] + 8
            val labelY = yPoints[lastIdx] + 4

            // Only draw if there's room
            if (labelX + 40 < chartX + chartWidth) {
                GraphicsUtils.drawString(
                    g2d, numberFormat.format(lastLevel),
                    labelX, labelY,
                    CardStyles.FONT_CHART_AXIS, CardStyles.CHART_LINE_COLOR
                )
            }
        }
    }

    /**
     * Draw the footer with server name and branding.
     */
    private fun drawFooter(g2d: Graphics2D, width: Int, height: Int) {
        val footerY = height - 15

        GraphicsUtils.drawString(
            g2d, serverName,
            CardStyles.CHART_PADDING, footerY,
            CardStyles.FONT_FOOTER, CardStyles.TEXT_GRAY
        )

        GraphicsUtils.drawRightAlignedString(
            g2d, CardStyles.BRANDING_TEXT,
            width - CardStyles.CHART_PADDING, footerY,
            CardStyles.FONT_FOOTER, CardStyles.TEXT_GRAY
        )
    }
}
