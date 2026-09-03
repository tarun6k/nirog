package com.nirog.data

import org.json.JSONArray
import java.time.LocalDate

/**
 * One row of the server's /v1/label-claims delta feed. `deleted` rows are
 * tombstones: the matching local claim must be removed (a revoked registration
 * must stop surfacing products — I1 depends on this).
 */
data class LabelClaimDelta(
    val entity: LabelClaimEntity,
    val deleted: Boolean,
    val updatedAt: String,
) {
    companion object {
        /** Same no-invention rule as the CSV seed: a live claim without a gazette ref is rejected. */
        fun parse(json: String): List<LabelClaimDelta> {
            val arr = JSONArray(json)
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val deleted = o.optBoolean("deleted", false)
                val ref = o.optString("sourceNotificationRef", "")
                require(deleted || ref.isNotEmpty()) { "label claim without a gazette notification ref: $o" }
                LabelClaimDelta(
                    entity = LabelClaimEntity(
                        productId = o.getString("productId"),
                        cropId = o.getString("cropId"),
                        pestId = o.getString("pestId"),
                        doseValue = o.getDouble("doseValue"),
                        doseUnit = o.getString("doseUnit"),
                        dilutionLPerHa = o.getDouble("dilutionLPerHa"),
                        phiDays = o.getInt("phiDays"),
                        sourceNotificationRef = ref,
                        effectiveFrom = LocalDate.parse(o.getString("effectiveFrom")).toEpochDay(),
                        effectiveTo = o.optString("effectiveTo", "").ifEmpty { null }
                            ?.let { LocalDate.parse(it).toEpochDay() },
                    ),
                    deleted = deleted,
                    updatedAt = o.getString("updatedAt"),
                )
            }
        }
    }
}
