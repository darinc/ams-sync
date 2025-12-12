package io.github.darinc.amssync.image

import io.github.darinc.amssync.progression.Timeframe
import io.github.darinc.amssync.progression.TrendPoint
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.awt.image.BufferedImage
import java.time.Instant
import java.time.temporal.ChronoUnit

class ProgressionChartRendererTest : DescribeSpec({

    describe("ProgressionChartRenderer") {

        describe("renderChart") {

            it("creates image with correct dimensions") {
                val renderer = ProgressionChartRenderer("Test Server")
                val points = listOf(
                    TrendPoint(Instant.now().minus(7, ChronoUnit.DAYS), 100),
                    TrendPoint(Instant.now().minus(3, ChronoUnit.DAYS), 150),
                    TrendPoint(Instant.now(), 200)
                )

                val image = renderer.renderChart(
                    "TestPlayer",
                    "Power Level",
                    points,
                    Timeframe.SEVEN_DAYS
                )

                image.width shouldBe CardStyles.CHART_WIDTH
                image.height shouldBe CardStyles.CHART_HEIGHT
            }

            it("creates ARGB image type") {
                val renderer = ProgressionChartRenderer("Test Server")
                val points = listOf(
                    TrendPoint(Instant.now(), 100)
                )

                val image = renderer.renderChart(
                    "TestPlayer",
                    "Mining",
                    points,
                    Timeframe.THIRTY_DAYS
                )

                image.type shouldBe BufferedImage.TYPE_INT_ARGB
            }

            it("renders without errors with empty data points") {
                val renderer = ProgressionChartRenderer("Test Server")
                val emptyPoints = emptyList<TrendPoint>()

                val image = renderer.renderChart(
                    "TestPlayer",
                    "Power Level",
                    emptyPoints,
                    Timeframe.SEVEN_DAYS
                )

                // Should still render a valid image
                image.width shouldBe CardStyles.CHART_WIDTH
                image.height shouldBe CardStyles.CHART_HEIGHT
            }

            it("renders without errors with single data point") {
                val renderer = ProgressionChartRenderer("Test Server")
                val singlePoint = listOf(
                    TrendPoint(Instant.now(), 500)
                )

                val image = renderer.renderChart(
                    "TestPlayer",
                    "Power Level",
                    singlePoint,
                    Timeframe.SEVEN_DAYS
                )

                image.width shouldBe CardStyles.CHART_WIDTH
            }

            it("renders without errors with many data points") {
                val renderer = ProgressionChartRenderer("Test Server")
                val now = Instant.now()
                val manyPoints = (0..100).map { i ->
                    TrendPoint(now.minus((100 - i).toLong(), ChronoUnit.HOURS), 1000 + i * 10)
                }

                val image = renderer.renderChart(
                    "TestPlayer",
                    "Power Level",
                    manyPoints,
                    Timeframe.SEVEN_DAYS
                )

                image.width shouldBe CardStyles.CHART_WIDTH
            }

            it("renders with optional avatar") {
                val renderer = ProgressionChartRenderer("Test Server")
                val points = listOf(
                    TrendPoint(Instant.now().minus(1, ChronoUnit.DAYS), 100),
                    TrendPoint(Instant.now(), 200)
                )
                val avatar = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)

                val image = renderer.renderChart(
                    "TestPlayer",
                    "Power Level",
                    points,
                    Timeframe.SEVEN_DAYS,
                    avatar
                )

                image.width shouldBe CardStyles.CHART_WIDTH
            }

            it("handles different timeframes") {
                val renderer = ProgressionChartRenderer("Test Server")
                val now = Instant.now()
                val points = listOf(
                    TrendPoint(now.minus(30, ChronoUnit.DAYS), 100),
                    TrendPoint(now, 500)
                )

                // Test various timeframes
                listOf(
                    Timeframe.SEVEN_DAYS,
                    Timeframe.THIRTY_DAYS,
                    Timeframe.NINETY_DAYS,
                    Timeframe.SIX_MONTHS,
                    Timeframe.ONE_YEAR,
                    Timeframe.ALL_TIME
                ).forEach { timeframe ->
                    val image = renderer.renderChart(
                        "TestPlayer",
                        "Power Level",
                        points,
                        timeframe
                    )
                    image.width shouldBe CardStyles.CHART_WIDTH
                }
            }

            it("handles flat data (no change)") {
                val renderer = ProgressionChartRenderer("Test Server")
                val now = Instant.now()
                val flatPoints = listOf(
                    TrendPoint(now.minus(5, ChronoUnit.DAYS), 500),
                    TrendPoint(now.minus(3, ChronoUnit.DAYS), 500),
                    TrendPoint(now, 500)
                )

                val image = renderer.renderChart(
                    "TestPlayer",
                    "Mining",
                    flatPoints,
                    Timeframe.SEVEN_DAYS
                )

                image.width shouldBe CardStyles.CHART_WIDTH
            }

            it("handles very high level values") {
                val renderer = ProgressionChartRenderer("Test Server")
                val now = Instant.now()
                val highPoints = listOf(
                    TrendPoint(now.minus(7, ChronoUnit.DAYS), 50000),
                    TrendPoint(now, 100000)
                )

                val image = renderer.renderChart(
                    "TestPlayer",
                    "Power Level",
                    highPoints,
                    Timeframe.SEVEN_DAYS
                )

                image.width shouldBe CardStyles.CHART_WIDTH
            }

            it("handles zero level values") {
                val renderer = ProgressionChartRenderer("Test Server")
                val now = Instant.now()
                val zeroPoints = listOf(
                    TrendPoint(now.minus(3, ChronoUnit.DAYS), 0),
                    TrendPoint(now, 10)
                )

                val image = renderer.renderChart(
                    "TestPlayer",
                    "Alchemy",
                    zeroPoints,
                    Timeframe.SEVEN_DAYS
                )

                image.width shouldBe CardStyles.CHART_WIDTH
            }
        }

        describe("constructor") {

            it("accepts server name") {
                val renderer = ProgressionChartRenderer("My Awesome Server")
                // Should not throw
                val image = renderer.renderChart(
                    "TestPlayer",
                    "Power Level",
                    emptyList(),
                    Timeframe.SEVEN_DAYS
                )
                image.width shouldBeGreaterThan 0
            }

            it("handles empty server name") {
                val renderer = ProgressionChartRenderer("")
                val image = renderer.renderChart(
                    "TestPlayer",
                    "Power Level",
                    emptyList(),
                    Timeframe.SEVEN_DAYS
                )
                image.width shouldBeGreaterThan 0
            }
        }
    }
})
