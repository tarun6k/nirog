package com.nirog.engine

import com.nirog.model.AreaUnit

sealed interface AreaResult {
    data class Hectares(val value: Double) : AreaResult
    /** Bigha size is state-specific; without a known state we refuse to guess. */
    data class UnknownBighaState(val state: String?) : AreaResult
}

object AreaConverter {
    const val HA_PER_ACRE = 0.4047
    const val HA_PER_GUNTHA = HA_PER_ACRE / 40.0

    // VERIFY BEFORE LAUNCH: bigha sizes below are commonly cited figures, not
    // gazetted values. Wrong entries here cause wrong dilution (worse than wrong
    // molecule). Keyed on state name in UPPER_SNAKE. Unknown state -> explicit error.
    val bighaHaByState: Map<String, Double> = mapOf(
        "UTTAR_PRADESH" to 0.2529,
        "RAJASTHAN" to 0.2529, // pucca bigha
        "WEST_BENGAL" to 0.1338,
        "ASSAM" to 0.1338,
        "GUJARAT" to 0.1619,
        "HIMACHAL_PRADESH" to 0.0809,
    )

    fun toHectares(value: Double, unit: AreaUnit, state: String?): AreaResult = when (unit) {
        AreaUnit.HECTARE -> AreaResult.Hectares(value)
        AreaUnit.ACRE -> AreaResult.Hectares(value * HA_PER_ACRE)
        AreaUnit.GUNTHA -> AreaResult.Hectares(value * HA_PER_GUNTHA)
        AreaUnit.BIGHA -> {
            val ha = state?.let { bighaHaByState[it.uppercase().replace(' ', '_')] }
            if (ha == null) AreaResult.UnknownBighaState(state)
            else AreaResult.Hectares(value * ha)
        }
    }
}
