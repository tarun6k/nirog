package com.nirog.engine

import com.nirog.model.DiseaseCandidate
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeohashTest {

    @Test
    fun `known vector encodes correctly`() {
        // canonical geohash test vector
        assertEquals("u4pruydqqvj", Geohash.encode(57.64911, 10.40744, 11))
    }

    @Test
    fun `precision five is about five km and is what outbreak reports use`() {
        assertEquals("u4pru", Geohash.encode(57.64911, 10.40744, 5))
        assertEquals(5, Geohash.encode(28.6139, 77.2090, 5).length)
    }

    @Test
    fun `decode returns the cell centroid near the encoded point`() {
        val (lat, lon) = Geohash.decodeCentroid("u4pru")
        // precision-5 cell is ~4.9 x 4.9 km; centroid must be within that
        assertTrue(kotlin.math.abs(lat - 57.64911) < 0.05, "lat $lat")
        assertTrue(kotlin.math.abs(lon - 10.40744) < 0.05, "lon $lon")
    }

    @Test
    fun `encode then decode round-trips into the same cell`() {
        val (lat, lon) = Geohash.decodeCentroid(Geohash.encode(28.8955, 76.6066, 5))
        assertEquals("t", Geohash.encode(lat, lon, 5).take(1))
        assertEquals(Geohash.encode(28.8955, 76.6066, 5), Geohash.encode(lat, lon, 5))
    }
}

class GeoTest {

    @Test
    fun `haversine of the same point is zero`() {
        assertTrue(Geo.haversineKm(28.0, 77.0, 28.0, 77.0) < 1e-9)
    }

    @Test
    fun `one degree of latitude is about 111 km`() {
        val d = Geo.haversineKm(28.0, 77.0, 29.0, 77.0)
        assertTrue(d > 110 && d < 112, "got $d")
    }

    @Test
    fun `bearing due east is 90 degrees`() {
        val b = Geo.bearingDeg(0.0, 0.0, 0.0, 1.0)
        assertTrue(kotlin.math.abs(b - 90.0) < 0.5, "got $b")
    }
}

class PriorAdjusterTest {

    private val candidates = listOf(
        DiseaseCandidate("yellow_rust", 0.6),
        DiseaseCandidate("aphid", 0.4),
    )

    @Test
    fun `no rules leaves priors untouched and logs nothing`() {
        val r = PriorAdjuster.adjust(candidates, mapOf(PriorMetric.RAIN_MM_14D to 50.0), emptyList())
        assertEquals(candidates, r.candidates)
        assertTrue(r.adjustments.isEmpty())
    }

    @Test
    fun `matching rule multiplies then renormalizes`() {
        val rules = listOf(
            PriorRule("yellow_rust", PriorMetric.AVG_RH_14D, RuleOp.GTE, 80.0, multiplier = 2.0, source = "TEST"),
        )
        val r = PriorAdjuster.adjust(candidates, mapOf(PriorMetric.AVG_RH_14D to 85.0), rules)
        // 0.6*2=1.2, 0.4 -> renorm: 0.75 / 0.25
        assertTrue(abs(r.candidates[0].probability - 0.75) < 1e-9)
        assertTrue(abs(r.candidates.sumOf { it.probability } - 1.0) < 1e-9)
    }

    @Test
    fun `rule whose condition fails does not apply`() {
        val rules = listOf(
            PriorRule("yellow_rust", PriorMetric.AVG_RH_14D, RuleOp.GTE, 80.0, 2.0, "TEST"),
        )
        val r = PriorAdjuster.adjust(candidates, mapOf(PriorMetric.AVG_RH_14D to 40.0), rules)
        assertEquals(candidates, r.candidates)
    }

    @Test
    fun `rule with missing metric is skipped not guessed`() {
        val rules = listOf(
            PriorRule("yellow_rust", PriorMetric.DISTRICT_PRESSURE, RuleOp.GTE, 3.0, 2.0, "TEST"),
        )
        val r = PriorAdjuster.adjust(candidates, emptyMap(), rules)
        assertEquals(candidates, r.candidates)
        assertTrue(r.adjustments.isEmpty())
    }

    @Test
    fun `every applied adjustment is logged for audit with values`() {
        val rules = listOf(
            PriorRule("yellow_rust", PriorMetric.RAIN_MM_14D, RuleOp.GTE, 20.0, 1.5, "AGRONOMIST/2026"),
        )
        val r = PriorAdjuster.adjust(candidates, mapOf(PriorMetric.RAIN_MM_14D to 42.0), rules)
        val log = r.adjustments.single()
        assertEquals("yellow_rust", log.pestId)
        assertEquals(42.0, log.actualValue)
        assertEquals(1.5, log.multiplier)
        assertEquals("AGRONOMIST/2026", log.source)
    }

    @Test
    fun `rules parse from csv and reject unknown metric`() {
        val rules = PriorAdjuster.parseRules(
            """
            pestId,metric,op,value,multiplier,source
            yellow_rust,AVG_RH_14D,GTE,80,2.0,AGRONOMIST/2026
            """.trimIndent(),
        )
        assertEquals(1, rules.size)
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            PriorAdjuster.parseRules("pestId,metric,op,value,multiplier,source\nx,VIBES,GTE,1,2,SRC")
        }
    }
}
