package com.nirog.data

import com.nirog.engine.Geohash
import com.nirog.engine.PriorMetric
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * Assembles the ContextSnapshot for a scan: plot facts + weather + local
 * outbreak pressure + spray history. Degrades gracefully — no network means a
 * null weather block plus a visible note key, never a crash.
 */
class ContextBuilder(
    private val weather: WeatherProvider,
    private val db: NirogDb,
) {
    data class Built(
        val entity: ContextSnapshotEntity,
        /** Metric values for PriorAdjuster — only metrics we actually have. */
        val metrics: Map<PriorMetric, Double>,
        /** Farmer-visible degradation note ("couldn't check weather"), or null. */
        val noteKey: String?,
    )

    suspend fun build(plot: PlotEntity, scanId: String, now: Instant = Instant.now()): Built {
        val today = now.atZone(ZoneOffset.UTC).toLocalDate()
        val daysSinceSowing = ChronoUnit.DAYS.between(LocalDate.ofEpochDay(plot.sowingDate), today).toInt()

        val w = if (plot.lat != null && plot.lon != null) weather.last14d(plot.lat, plot.lon) else null
        val noteKey = if (w == null) "context_no_weather" else null

        val pressure = if (plot.lat != null && plot.lon != null) {
            db.outbreakDao().countSince(
                Geohash.encode(plot.lat, plot.lon, 5),
                plot.cropId,
                now.minus(14, ChronoUnit.DAYS).toEpochMilli(),
            ).toDouble()
        } else null

        val lastSprays = db.diaryDao().lastTwoSprays(plot.id)
        val lastProduct = lastSprays.firstOrNull()?.let { db.catalogDao().product(it.productId) }

        val metrics = buildMap {
            if (w != null) {
                put(PriorMetric.RAIN_MM_14D, w.rainMmTotal)
                put(PriorMetric.AVG_RH_14D, w.avgRhPct)
                put(PriorMetric.AVG_TMAX_14D, w.avgTMaxC)
            }
            if (pressure != null) put(PriorMetric.DISTRICT_PRESSURE, pressure)
        }

        return Built(
            entity = ContextSnapshotEntity(
                scanId = scanId,
                daysSinceSowing = daysSinceSowing,
                // Growth stage needs a per-crop calendar dataset we don't have yet.
                growthStage = null,
                weather14d = w?.serialize(),
                districtPressureScore = pressure,
                lastSprayFracGroup = lastProduct?.fracGroup,
                lastSprayIracGroup = lastProduct?.iracGroup,
                capturedAt = now.toEpochMilli(),
            ),
            metrics = metrics,
            noteKey = noteKey,
        )
    }
}
