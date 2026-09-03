package com.nirog.engine

/**
 * Standard geohash encoder. Precision 5 (~4.9 x 4.9 km cell) is the ONLY
 * precision outbreak reports may use — exact coordinates never leave the device.
 */
object Geohash {
    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"

    fun encode(lat: Double, lon: Double, precision: Int = 5): String {
        var latLo = -90.0; var latHi = 90.0
        var lonLo = -180.0; var lonHi = 180.0
        val sb = StringBuilder()
        var bit = 0
        var ch = 0
        var evenBit = true
        while (sb.length < precision) {
            if (evenBit) {
                val mid = (lonLo + lonHi) / 2
                if (lon >= mid) { ch = ch shl 1 or 1; lonLo = mid } else { ch = ch shl 1; lonHi = mid }
            } else {
                val mid = (latLo + latHi) / 2
                if (lat >= mid) { ch = ch shl 1 or 1; latLo = mid } else { ch = ch shl 1; latHi = mid }
            }
            evenBit = !evenBit
            if (++bit == 5) {
                sb.append(BASE32[ch])
                bit = 0
                ch = 0
            }
        }
        return sb.toString()
    }

    /** Cell centroid (lat, lon). Mirrors the backend decoder. */
    fun decodeCentroid(geohash: String): Pair<Double, Double> {
        var latLo = -90.0; var latHi = 90.0
        var lonLo = -180.0; var lonHi = 180.0
        var evenBit = true
        for (c in geohash) {
            val idx = BASE32.indexOf(c).also { require(it >= 0) { "bad geohash char: $c" } }
            for (bit in 4 downTo 0) {
                val b = (idx shr bit) and 1
                if (evenBit) {
                    val mid = (lonLo + lonHi) / 2
                    if (b == 1) lonLo = mid else lonHi = mid
                } else {
                    val mid = (latLo + latHi) / 2
                    if (b == 1) latLo = mid else latHi = mid
                }
                evenBit = !evenBit
            }
        }
        return (latLo + latHi) / 2 to (lonLo + lonHi) / 2
    }
}

/** Pure geo math for the outbreak radar. */
object Geo {
    private const val EARTH_RADIUS_KM = 6371.0

    fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2).let { it * it } +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_KM * kotlin.math.asin(kotlin.math.sqrt(a))
    }

    /** Initial bearing from point 1 to point 2, degrees clockwise from north. */
    fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val f1 = Math.toRadians(lat1)
        val f2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = kotlin.math.sin(dLon) * kotlin.math.cos(f2)
        val x = kotlin.math.cos(f1) * kotlin.math.sin(f2) -
            kotlin.math.sin(f1) * kotlin.math.cos(f2) * kotlin.math.cos(dLon)
        return (Math.toDegrees(kotlin.math.atan2(y, x)) + 360.0) % 360.0
    }
}
