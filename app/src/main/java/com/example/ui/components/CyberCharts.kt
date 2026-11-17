package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.GlassBorderSubtle
import com.example.ui.theme.MetallicSilver
import com.example.ui.theme.PremiumTeal
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusError

/**
 * Hardware-accelerated Compose Canvas dual-series area & line chart
 * displaying real live Download and Upload throughput samples.
 */
@Composable
fun ThroughputWaveformChart(
    downloadSamples: List<Long>,
    uploadSamples: List<Long>,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
    ) {
        val w = size.width
        val h = size.height
        val padTop = 10.dp.toPx()
        val padBottom = 10.dp.toPx()
        val plotH = (h - padTop - padBottom).coerceAtLeast(1f)

        // Subtle horizontal telemetry guide lines
        for (i in 0..3) {
            val y = padTop + (plotH * i / 3f)
            drawLine(
                color = GlassBorderSubtle.copy(alpha = 0.55f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 0.8.dp.toPx()
            )
        }

        val maxVal = maxOf(
            downloadSamples.maxOrNull() ?: 0L,
            uploadSamples.maxOrNull() ?: 0L,
            1024L
        ).toFloat()

        fun drawSeries(samples: List<Long>, lineColor: Color, fillTop: Color) {
            if (samples.size < 2) return
            val stepX = w / (samples.size - 1).coerceAtLeast(1)
            val linePath = Path()
            val fillPath = Path()

            samples.forEachIndexed { idx, value ->
                val x = idx * stepX
                val ratio = (value.toFloat() / maxVal).coerceIn(0f, 1f)
                val y = padTop + plotH * (1f - ratio)
                if (idx == 0) {
                    linePath.moveTo(x, y)
                    fillPath.moveTo(x, padTop + plotH)
                    fillPath.lineTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }
            fillPath.lineTo((samples.size - 1) * stepX, padTop + plotH)
            fillPath.close()

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(fillTop.copy(alpha = 0.28f), Color.Transparent),
                    startY = padTop,
                    endY = padTop + plotH
                )
            )
            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        drawSeries(uploadSamples, MetallicSilver, MetallicSilver)
        drawSeries(downloadSamples, ElectricCyan, StatusConnected)
    }
}

/**
 * Clean protocol/node latency & load bar chart rendered natively via Compose Canvas.
 */
@Composable
fun LatencyDistributionBarChart(
    bars: List<Pair<String, Int>>, // label to latencyMs
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp)
    ) {
        if (bars.isEmpty()) return@Canvas
        val w = size.width
        val h = size.height
        val count = bars.size.coerceAtMost(8)
        val slotW = w / count
        val barW = (slotW * 0.48f).coerceAtMost(28.dp.toPx())
        val maxMs = (bars.take(count).maxOfOrNull { it.second.coerceAtLeast(0) } ?: 200).coerceAtLeast(100).toFloat()

        for (i in 0 until count) {
            val ms = bars[i].second
            val cx = slotW * i + slotW / 2f
            val bgTop = 8.dp.toPx()
            val bgH = (h - 16.dp.toPx()).coerceAtLeast(1f)

            drawRoundRect(
                color = ElevatedSurface,
                topLeft = Offset(cx - barW / 2f, bgTop),
                size = Size(barW, bgH),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
            )

            if (ms > 0) {
                val frac = (ms / maxMs).coerceIn(0.12f, 1f)
                val fillH = bgH * frac
                val barColor = when {
                    ms < 120 -> StatusConnected
                    ms < 250 -> ElectricCyan
                    else -> StatusError
                }
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(barColor, barColor.copy(alpha = 0.45f))),
                    topLeft = Offset(cx - barW / 2f, bgTop + (bgH - fillH)),
                    size = Size(barW, fillH),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )
            }
        }
    }
}

data class MapRegionNode(
    val code: String,
    val xFraction: Float,
    val yFraction: Float,
    val isOnline: Boolean,
    val isSelected: Boolean
)

/**
 * Stylized dot-matrix cyber world radar showing actual configured server regions.
 */
@Composable
fun CyberWorldNodeMap(
    nodes: List<MapRegionNode>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Stylized continental landmass dot clusters (Americas, Europe/Middle East, Asia)
        val continentEllipses = listOf(
            // North America
            Triple(0.22f, 0.36f, Pair(0.13f, 0.18f)),
            // South America
            Triple(0.29f, 0.68f, Pair(0.08f, 0.18f)),
            // Europe
            Triple(0.50f, 0.28f, Pair(0.10f, 0.12f)),
            // Africa
            Triple(0.51f, 0.57f, Pair(0.10f, 0.20f)),
            // Middle East & Asia
            Triple(0.70f, 0.38f, Pair(0.18f, 0.20f)),
            // Oceania
            Triple(0.83f, 0.72f, Pair(0.08f, 0.10f))
        )

        val dotGap = 9.dp.toPx()
        var y = dotGap * 0.5f
        while (y < h) {
            val ny = y / h
            var x = dotGap * 0.5f
            while (x < w) {
                val nx = x / w
                val insideContinent = continentEllipses.any { (cx, cy, radii) ->
                    val dx = (nx - cx) / radii.first
                    val dy = (ny - cy) / radii.second
                    dx * dx + dy * dy <= 1f
                }
                if (insideContinent) {
                    drawCircle(
                        color = MetallicSilver.copy(alpha = 0.22f),
                        radius = 1.5.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
                x += dotGap
            }
            y += dotGap
        }

        // Connection arcs from central Middle-East/Iran hub to active server nodes
        val hub = Offset(w * 0.58f, h * 0.42f)
        nodes.forEach { node ->
            val target = Offset(w * node.xFraction, h * node.yFraction)
            val lineColor = when {
                node.isSelected -> ElectricCyan
                node.isOnline -> PremiumTeal.copy(alpha = 0.55f)
                else -> Color(0xFF455464)
            }
            drawLine(
                color = lineColor,
                start = hub,
                end = target,
                strokeWidth = if (node.isSelected) 1.6.dp.toPx() else 0.9.dp.toPx(),
                cap = StrokeCap.Round
            )
            if (node.isSelected || node.isOnline) {
                drawCircle(
                    color = lineColor.copy(alpha = 0.28f),
                    radius = if (node.isSelected) 10.dp.toPx() else 6.dp.toPx(),
                    center = target
                )
            }
            drawCircle(
                color = if (node.isSelected) ElectricCyan else if (node.isOnline) StatusConnected else MetallicSilver,
                radius = if (node.isSelected) 4.dp.toPx() else 2.8.dp.toPx(),
                center = target
            )
        }
    }
}
