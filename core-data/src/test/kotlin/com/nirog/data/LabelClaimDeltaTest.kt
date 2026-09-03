package com.nirog.data

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LabelClaimDeltaTest {

    @Test
    fun `parses a live claim with null effectiveTo`() {
        val json = """[{
            "productId":"P1","cropId":"wheat","pestId":"yellow_rust",
            "doseValue":2.0,"doseUnit":"ML_PER_L","dilutionLPerHa":500.0,"phiDays":7,
            "sourceNotificationRef":"SO 1(E)","effectiveFrom":"2025-01-01","effectiveTo":null,
            "deleted":false,"updatedAt":"2026-09-01T10:00:00+00:00"
        }]"""
        val d = LabelClaimDelta.parse(json).single()
        assertEquals("P1", d.entity.productId)
        assertEquals(LocalDate.of(2025, 1, 1).toEpochDay(), d.entity.effectiveFrom)
        assertNull(d.entity.effectiveTo)
        assertTrue(!d.deleted)
    }

    @Test
    fun `tombstone parses as deleted`() {
        val json = """[{
            "productId":"P1","cropId":"wheat","pestId":"yellow_rust",
            "doseValue":2.0,"doseUnit":"ML_PER_L","dilutionLPerHa":500.0,"phiDays":7,
            "sourceNotificationRef":"SO 1(E)","effectiveFrom":"2025-01-01","effectiveTo":"2026-01-01",
            "deleted":true,"updatedAt":"2026-09-02T10:00:00+00:00"
        }]"""
        assertTrue(LabelClaimDelta.parse(json).single().deleted)
    }

    @Test
    fun `live claim without a gazette ref is rejected`() {
        val json = """[{
            "productId":"P1","cropId":"wheat","pestId":"yellow_rust",
            "doseValue":2.0,"doseUnit":"ML_PER_L","dilutionLPerHa":500.0,"phiDays":7,
            "sourceNotificationRef":"","effectiveFrom":"2025-01-01","effectiveTo":null,
            "deleted":false,"updatedAt":"2026-09-01T10:00:00+00:00"
        }]"""
        assertFailsWith<IllegalArgumentException> { LabelClaimDelta.parse(json) }
    }

    @Test
    fun `empty feed parses to empty list`() {
        assertEquals(emptyList(), LabelClaimDelta.parse("[]"))
    }
}
