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
}
