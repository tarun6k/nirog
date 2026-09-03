package com.nirog.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nirog.data.NearbyOutbreakEntity
import com.nirog.data.PlotEntity
import com.nirog.engine.Geo
import com.nirog.engine.Geohash
import com.nirog.feature.diagnosis.pestNameHi
import com.nirog.ui.MicButton
import com.nirog.ui.Palette
import com.nirog.ui.PaperScreen
import com.nirog.ui.blueprintCorners
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val RADAR_RANGE_KM = 10.0

/** A nearby cell resolved to distance/bearing from the farmer's plot. */
data class RadarMark(
    val diseaseId: String,
    val confirmed: Boolean,
    val count: Int,
    val distanceKm: Double,
    val bearingDeg: Double,
)

/** Pure: aggregate rows + plot position → marks within radar range, nearest first. */
fun radarMarks(plot: PlotEntity, rows: List<NearbyOutbreakEntity>): List<RadarMark> {
    val lat = plot.lat ?: return emptyList()
    val lon = plot.lon ?: return emptyList()
    val ownCell = Geohash.encode(lat, lon, 5)
    return rows
        .filter { it.geohash5 != ownCell } // the farmer's own reports aren't "nearby"
        .map { row ->
            val (cLat, cLon) = Geohash.decodeCentroid(row.geohash5)
            RadarMark(
                diseaseId = row.diseaseId,
                confirmed = row.confirmed,
                count = row.count,
                distanceKm = Geo.haversineKm(lat, lon, cLat, cLon),
                bearingDeg = Geo.bearingDeg(lat, lon, cLat, cLon),
            )
        }
        .filter { it.distanceKm <= RADAR_RANGE_KM }
        .sortedBy { it.distanceKm }
}

/** Artboard 11: drawn district map — rings, square clay = confirmed, rotated ochre = reported. */
@Composable
fun OutbreakRadarScreen(plot: PlotEntity, rows: List<NearbyOutbreakEntity>) {
    val marks = radarMarks(plot, rows)
    val hasLocation = plot.lat != null && plot.lon != null

    PaperScreen {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(
                stringResource(UiStr.radar_title),
                fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Ink,
            )
            Text(
                stringResource(UiStr.radar_sub, plot.cropId.uppercase()),
                fontSize = 16.sp, color = Palette.TextSecondary, fontWeight = FontWeight.SemiBold,
            )

            when {
                !hasLocation -> InfoCard(stringResource(UiStr.radar_no_location))
                else -> {
                    RadarMap(marks)
                    if (marks.isEmpty()) {
                        InfoCard(stringResource(UiStr.radar_empty), positive = true)
                    }
                    marks.forEach { MarkCard(it) }
                }
            }
        }
        Row(Modifier.padding(16.dp)) {
            Spacer(Modifier.weight(1f))
            MicButton()
        }
    }
}

@Composable
private fun RadarMap(marks: List<RadarMark>) {
    val yourPlotLabel = stringResource(UiStr.radar_your_plot)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .aspectRatio(1f)
            .background(Color(0xFFEEF0E4))
            .border(1.5.dp, Palette.Ink)
            .blueprintCorners(Palette.Stone),
    ) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val c = Offset(size.width / 2, size.height / 2)
            val pxPerKm = (size.width / 2 - 12.dp.toPx()) / RADAR_RANGE_KM.toFloat()
            val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 12f))

            // 5 and 10 km rings
            for (km in listOf(5.0, 10.0)) {
                drawCircle(
                    Palette.Stone, radius = (km * pxPerKm).toFloat(), center = c,
                    style = Stroke(width = 1.dp.toPx(), pathEffect = dash),
                )
            }

            // markers: square = confirmed (clay), rotated square = reported (ochre)
            marks.forEach { m ->
                val theta = Math.toRadians(m.bearingDeg - 90.0) // bearing 0 = north = up
                val r = (m.distanceKm * pxPerKm).toFloat()
                val p = Offset(c.x + (r * cos(theta)).toFloat(), c.y + (r * sin(theta)).toFloat())
                val half = 11.dp.toPx()
                translate(p.x, p.y) {
                    rotate(if (m.confirmed) 0f else 45f, pivot = Offset.Zero) {
                        drawRect(
                            if (m.confirmed) Palette.Clay else Palette.Ochre,
                            topLeft = Offset(-half, -half),
                            size = Size(half * 2, half * 2),
                        )
                    }
                }
                drawLetter(m.diseaseId.first().uppercaseChar(), p)
            }

            // the farmer's plot: green triangle + circle at the centre
            val tri = androidx.compose.ui.graphics.Path().apply {
                moveTo(c.x, c.y + 10.dp.toPx())
                lineTo(c.x - 9.dp.toPx(), c.y - 6.dp.toPx())
                lineTo(c.x + 9.dp.toPx(), c.y - 6.dp.toPx())
                close()
            }
            drawPath(tri, Palette.Green)
            drawCircle(
                Palette.Green, radius = 7.dp.toPx(),
                center = Offset(c.x, c.y - 14.dp.toPx()), style = Stroke(2.5.dp.toPx()),
            )
        }
        Text(
            yourPlotLabel,
            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Palette.GreenPressed,
            modifier = Modifier.align(Alignment.Center).padding(top = 64.dp),
        )
        Text(
            "5 · 10 किमी",
            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.Stone,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLetter(letter: Char, at: Offset) {
    drawContext.canvas.nativeCanvas.drawText(
        letter.toString(),
        at.x,
        at.y + 5.dp.toPx(),
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 13.dp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
            isAntiAlias = true
        },
    )
}

@Composable
private fun MarkCard(mark: RadarMark) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .background(Palette.Card)
            .border(1.dp, Palette.Ink)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(24.dp)
                .background(if (mark.confirmed) Palette.Clay else Palette.Ochre),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                mark.diseaseId.first().uppercase(),
                color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
            )
        }
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pestNameHi(mark.diseaseId),
                    fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Palette.Ink,
                )
                Text(
                    stringResource(
                        if (mark.confirmed) UiStr.radar_confirmed else UiStr.radar_reported,
                        mark.count,
                    ),
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
                    color = if (mark.confirmed) Palette.Clay else Palette.Ochre,
                )
            }
            Text(
                stringResource(UiStr.radar_distance, mark.distanceKm.toInt().coerceAtLeast(1)),
                fontSize = 14.sp, color = Palette.TextSecondary, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun InfoCard(text: String, positive: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .background(if (positive) Palette.TintGreen else Palette.Card)
            .border(1.dp, if (positive) Palette.Green else Palette.Hairline)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(if (positive) "✓" else "ℹ", fontSize = 16.sp, color = if (positive) Palette.GreenPressed else Palette.Stone)
        Text(
            text,
            fontSize = 15.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold,
            color = if (positive) Palette.GreenPressed else Palette.TextSecondary,
        )
    }
}

private typealias UiStr = com.nirog.ui.R.string
