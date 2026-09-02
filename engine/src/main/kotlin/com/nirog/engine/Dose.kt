package com.nirog.engine

import com.nirog.model.DoseUnit
import com.nirog.model.LabelClaim
import com.nirog.model.Plot
import com.nirog.model.Product
import java.time.LocalDate
import kotlin.math.ceil

/** Everything a farmer needs at the tank: measured per tank, not per hectare (I10). */
sealed interface DoseResult {
    data class Plan(
        val productPerTank: Double,
        /** "ml" or "g" depending on the claim's dose unit. */
        val productUnit: String,
        val tanksRequired: Int,
        val totalProduct: Double,
        val totalSprayVolumeL: Double,
        val totalCostInr: Double,
        val phiExpiry: LocalDate,
    ) : DoseResult

    data class CannotCompute(val messageKey: String, val detail: String) : DoseResult
}

object DoseCalculator {
    const val DEFAULT_TANK_CAPACITY_L = 15.0 // standard knapsack sprayer

    fun plan(
        claim: LabelClaim,
        product: Product,
        plot: Plot,
        sprayDate: LocalDate,
        tankCapacityL: Double = DEFAULT_TANK_CAPACITY_L,
    ): DoseResult {
        val areaHa = when (val a = AreaConverter.toHectares(plot.areaValue, plot.areaUnit, plot.state)) {
            is AreaResult.Hectares -> a.value
            is AreaResult.UnknownBighaState ->
                return DoseResult.CannotCompute(
                    "dose_unknown_bigha_state",
                    "No bigha size on file for state '${a.state}'",
                )
        }
        val sprayVolumeL = claim.dilutionLPerHa * areaHa
        val tanks = ceil(sprayVolumeL / tankCapacityL).toInt().coerceAtLeast(1)
        val totalProduct = when (claim.doseUnit) {
            DoseUnit.ML_PER_L, DoseUnit.G_PER_L -> claim.doseValue * sprayVolumeL
            DoseUnit.ML_PER_HA, DoseUnit.G_PER_HA -> claim.doseValue * areaHa
        }
        // ponytail: last tank is under-filled but gets the same dose as full tanks;
        // per-tank proportional split for the final partial tank if agronomists ask.
        val perTank = when (claim.doseUnit) {
            DoseUnit.ML_PER_L, DoseUnit.G_PER_L -> claim.doseValue * tankCapacityL
            DoseUnit.ML_PER_HA, DoseUnit.G_PER_HA -> totalProduct / tanks
        }
        val unit = when (claim.doseUnit) {
            DoseUnit.ML_PER_L, DoseUnit.ML_PER_HA -> "ml"
            DoseUnit.G_PER_L, DoseUnit.G_PER_HA -> "g"
        }
        return DoseResult.Plan(
            productPerTank = perTank,
            productUnit = unit,
            tanksRequired = tanks,
            totalProduct = totalProduct,
            totalSprayVolumeL = sprayVolumeL,
            totalCostInr = totalProduct * product.pricePerUnitInr,
            phiExpiry = sprayDate.plusDays(claim.phiDays.toLong()),
        )
    }
}
