package com.nirog.engine

import com.nirog.model.BannedActive
import com.nirog.model.OrganicStatus
import com.nirog.model.TreatmentTier
import com.nirog.model.Verdict
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RecommendationEngineTest {

    private fun ladder(r: RecommendationResult) = assertIs<RecommendationResult.Ladder>(r)

    // ── I1: exact label claim required ─────────────────────────────────────

    @Test
    fun `I1 product with active claim for exact crop and pest is surfaced`() {
        val r = recommend(catalog = catalog(listOf(product("A")), listOf(claim("A"))))
        assertEquals(listOf("A"), ladder(r).rungs.map { it.productId })
    }

    @Test
    fun `I1 product with no claim at all is rejected`() {
        val r = recommend(catalog = catalog(listOf(product("A"))))
        val nope = assertIs<RecommendationResult.NoApprovedProductFound>(r)
        assertEquals(RejectionReason.NoLabelClaim, nope.rejections.single().reason)
    }

    @Test
    fun `I1 claim registered for cotton does not apply to wheat - no fuzzy crop match`() {
        val r = recommend(catalog = catalog(listOf(product("A")), listOf(claim("A", cropId = "cotton"))))
        val nope = assertIs<RecommendationResult.NoApprovedProductFound>(r)
        assertEquals(RejectionReason.NoLabelClaim, nope.rejections.single().reason)
    }

    @Test
    fun `I1 claim for a different pest does not apply`() {
        val r = recommend(catalog = catalog(listOf(product("A")), listOf(claim("A", pestId = "aphid"))))
        assertIs<RecommendationResult.NoApprovedProductFound>(r)
    }

    @Test
    fun `I1 expired claim is rejected with its dates`() {
        val expired = claim("A", from = TODAY.minusYears(2), to = TODAY.minusDays(1))
        val r = recommend(catalog = catalog(listOf(product("A")), listOf(expired)))
        val nope = assertIs<RecommendationResult.NoApprovedProductFound>(r)
        assertIs<RejectionReason.ClaimNotEffective>(nope.rejections.single().reason)
    }

    @Test
    fun `I1 not-yet-effective claim is rejected`() {
        val future = claim("A", from = TODAY.plusDays(1))
        val r = recommend(catalog = catalog(listOf(product("A")), listOf(future)))
        assertIs<RecommendationResult.NoApprovedProductFound>(r)
    }

    // ── I2: banned actives, no override ────────────────────────────────────

    @Test
    fun `I2 banned active is excluded with the notification ref`() {
        val cat = catalog(
            listOf(product("A", active = "monocrotophos")),
            listOf(claim("A")),
            banned = listOf(BannedActive("monocrotophos", "SO 686(E)", TODAY.minusYears(1), null)),
        )
        val nope = assertIs<RecommendationResult.NoApprovedProductFound>(recommend(catalog = cat))
        val reason = assertIs<RejectionReason.Banned>(nope.rejections.single().reason)
        assertEquals("SO 686(E)", reason.notificationRef)
    }

    @Test
    fun `I2 ban scoped to another crop does not exclude`() {
        val cat = catalog(
            listOf(product("A")),
            listOf(claim("A")),
            banned = listOf(BannedActive("testazole", "SO 1(E)", TODAY.minusYears(1), "cotton")),
        )
        assertIs<RecommendationResult.Ladder>(recommend(catalog = cat))
    }

    @Test
    fun `I2 ban scoped to this crop excludes`() {
        val cat = catalog(
            listOf(product("A")),
            listOf(claim("A")),
            banned = listOf(BannedActive("testazole", "SO 1(E)", TODAY.minusYears(1), "wheat")),
        )
        assertIs<RecommendationResult.NoApprovedProductFound>(recommend(catalog = cat))
    }

    // ── I3: PHI vs days to harvest ─────────────────────────────────────────

    @Test
    fun `I3 phi longer than days to harvest is rejected with both values`() {
        val cat = catalog(listOf(product("A")), listOf(claim("A", phi = 30)))
        val r = recommend(plot = plot(harvest = TODAY.plusDays(10)), catalog = cat)
        val reason = assertIs<RejectionReason.PhiTooLong>(
            assertIs<RecommendationResult.NoApprovedProductFound>(r).rejections.single().reason,
        )
        assertEquals(30, reason.phiDays)
        assertEquals(10, reason.daysToHarvest)
    }

    @Test
    fun `I3 phi exactly equal to days remaining is allowed`() {
        val cat = catalog(listOf(product("A")), listOf(claim("A", phi = 10)))
        assertIs<RecommendationResult.Ladder>(recommend(plot = plot(harvest = TODAY.plusDays(10)), catalog = cat))
    }

    // ── I4: FRAC/IRAC rotation over the last two sprays ────────────────────

    @Test
    fun `I4 same frac group as last spray is rejected`() {
        val cat = catalog(
            listOf(product("A", frac = "11"), product("OLD", frac = "11")),
            listOf(claim("A")),
        )
        val r = recommend(history = listOf(sprayLog("OLD", daysAgo = 5)), catalog = cat)
        val nope = assertIs<RecommendationResult.NoApprovedProductFound>(r)
        val reason = nope.rejections.first { it.productId == "A" }.reason
        assertEquals(RejectionReason.ResistanceRotation("FRAC", "11"), reason)
    }

    @Test
    fun `I4 same frac group as second-to-last spray is rejected`() {
        val cat = catalog(
            listOf(product("A", frac = "11"), product("OLD1", frac = "3"), product("OLD2", frac = "11")),
            listOf(claim("A")),
        )
        val r = recommend(history = listOf(sprayLog("OLD1", 3), sprayLog("OLD2", 10)), catalog = cat)
        assertIs<RecommendationResult.NoApprovedProductFound>(r)
    }

    @Test
    fun `I4 same frac group as third-to-last spray is allowed`() {
        val cat = catalog(
            listOf(
                product("A", frac = "11"),
                product("OLD1", frac = "3"), product("OLD2", frac = "7"), product("OLD3", frac = "11"),
            ),
            listOf(claim("A")),
        )
        val r = recommend(
            history = listOf(sprayLog("OLD1", 3), sprayLog("OLD2", 10), sprayLog("OLD3", 20)),
            catalog = cat,
        )
        assertTrue(ladder(r).rungs.any { it.productId == "A" })
    }

    @Test
    fun `I4 same irac group as a recent spray is rejected`() {
        val cat = catalog(
            listOf(product("A", irac = "4A"), product("OLD", irac = "4A")),
            listOf(claim("A")),
        )
        val r = recommend(history = listOf(sprayLog("OLD", 5)), catalog = cat)
        val nope = assertIs<RecommendationResult.NoApprovedProductFound>(r)
        assertEquals(
            RejectionReason.ResistanceRotation("IRAC", "4A"),
            nope.rejections.first { it.productId == "A" }.reason,
        )
    }

    // ── I5: organic certification protection ───────────────────────────────

    @Test
    fun `I5 organic plot excludes chemical products - every non-NONE status`() {
        for (status in listOf(OrganicStatus.NPOP_CERTIFIED, OrganicStatus.PGS_REGISTERED, OrganicStatus.IN_CONVERSION)) {
            val cat = catalog(listOf(product("A", bio = false)), listOf(claim("A")))
            val r = recommend(plot = plot(organic = status), catalog = cat)
            val nope = assertIs<RecommendationResult.NoApprovedProductFound>(r, "status=$status")
            assertEquals(RejectionReason.OrganicCertification, nope.rejections.single().reason)
        }
    }

    @Test
    fun `I5 biopesticide without npop permission is still excluded on organic plot`() {
        val cat = catalog(listOf(product("A", bio = true, npop = false)), listOf(claim("A")))
        val r = recommend(plot = plot(organic = OrganicStatus.NPOP_CERTIFIED), catalog = cat)
        assertIs<RecommendationResult.NoApprovedProductFound>(r)
    }

    @Test
    fun `I5 npop-permitted biopesticide survives on organic plot`() {
        val cat = catalog(listOf(product("A", bio = true, npop = true)), listOf(claim("A")))
        val r = recommend(plot = plot(organic = OrganicStatus.NPOP_CERTIFIED), catalog = cat)
        assertIs<RecommendationResult.Ladder>(r)
    }

    @Test
    fun `I5 non-organic plot is unaffected`() {
        val cat = catalog(listOf(product("A", bio = false)), listOf(claim("A")))
        assertIs<RecommendationResult.Ladder>(recommend(plot = plot(organic = OrganicStatus.NONE), catalog = cat))
    }

    // ── I6: every exclusion is accounted for and typed ─────────────────────

    @Test
    fun `I6 rejections plus survivors equals candidate products and all reasons carry message keys`() {
        val cat = catalog(
            listOf(product("A"), product("B"), product("C", active = "bad")),
            listOf(claim("A"), claim("C")),
            banned = listOf(BannedActive("bad", "SO 2(E)", TODAY.minusYears(1), null)),
        )
        val r = ladder(recommend(catalog = cat))
        assertEquals(3, r.rungs.size + r.rejections.size)
        assertTrue(r.rejections.all { it.reason.messageKey.startsWith("reject_") })
    }

    // ── I7: ETL gate on chemical rungs ─────────────────────────────────────

    @Test
    fun `I7 chemical rung locked below ETL with actual and required values in reason`() {
        val cat = catalog(listOf(product("A")), listOf(claim("A")))
        val r = ladder(recommend(diagnosis = diagnosis(severity = 4.0), catalog = cat))
        val rung = r.rungs.single()
        assertTrue(rung.locked)
        assertTrue(rung.lockReason!!.contains("4.0") && rung.lockReason!!.contains("10.0"))
    }

    @Test
    fun `I7 chemical rung unlocked at or above ETL`() {
        val cat = catalog(listOf(product("A")), listOf(claim("A")))
        val r = ladder(recommend(diagnosis = diagnosis(severity = 10.0), catalog = cat))
        assertTrue(!r.rungs.single().locked)
    }

    @Test
    fun `I7 missing ETL data keeps chemical rung locked - conservative default`() {
        val cat = catalog(listOf(product("A")), listOf(claim("A")), etl = emptyList())
        val r = ladder(recommend(diagnosis = diagnosis(severity = 90.0), catalog = cat))
        assertTrue(r.rungs.single().locked)
    }

    @Test
    fun `I7 biological rung is never ETL-locked`() {
        val cat = catalog(listOf(product("A", bio = true, npop = true)), listOf(claim("A")), etl = emptyList())
        val r = ladder(recommend(diagnosis = diagnosis(severity = 1.0), catalog = cat))
        assertTrue(!r.rungs.single().locked)
    }

    // ── I8: abstain means escalate, never advise ───────────────────────────

    @Test
    fun `I8 abstain produces escalation even with a perfect catalog`() {
        val cat = catalog(listOf(product("A")), listOf(claim("A")))
        val r = recommend(diagnosis = diagnosis(verdict = Verdict.ABSTAIN), catalog = cat)
        val esc = assertIs<RecommendationResult.Escalate>(r)
        assertEquals("scan1", esc.scanId)
    }

    @Test
    fun `I8 ambiguous produces candidates and no treatment`() {
        val cat = catalog(listOf(product("A")), listOf(claim("A")))
        val r = recommend(diagnosis = diagnosis(verdict = Verdict.AMBIGUOUS), catalog = cat)
        assertIs<RecommendationResult.Ambiguous>(r)
    }

    // ── I9: empty result is explicit, never a fallback ─────────────────────

    @Test
    fun `I9 all products filtered returns NoApprovedProductFound with every rejection`() {
        val cat = catalog(
            listOf(product("A"), product("B", active = "bad")),
            listOf(claim("B")),
            banned = listOf(BannedActive("bad", "SO 3(E)", TODAY.minusYears(1), null)),
        )
        val nope = assertIs<RecommendationResult.NoApprovedProductFound>(recommend(catalog = cat))
        assertEquals(setOf("A", "B"), nope.rejections.map { it.productId }.toSet())
    }

    @Test
    fun `I9 empty catalog returns NoApprovedProductFound not an error`() {
        val r = recommend(catalog = catalog(emptyList(), emptyList(), emptyList(), emptyList()))
        assertIs<RecommendationResult.NoApprovedProductFound>(r)
    }

    // ── Ladder ordering ────────────────────────────────────────────────────

    @Test
    fun `ladder orders biological before chemical and cheap before expensive`() {
        val cat = catalog(
            listOf(
                product("CHEM_EXP", priceInr = 10.0),
                product("BIO", bio = true, npop = true, priceInr = 5.0),
                product("CHEM_CHEAP", priceInr = 1.0),
            ),
            listOf(claim("CHEM_EXP"), claim("BIO"), claim("CHEM_CHEAP")),
        )
        val r = ladder(recommend(catalog = cat))
        assertEquals(listOf("BIO", "CHEM_CHEAP", "CHEM_EXP"), r.rungs.map { it.productId })
        assertEquals(listOf(0, 1, 2), r.rungs.map { it.order })
        assertEquals(TreatmentTier.BIOLOGICAL, r.rungs.first().tier)
    }
}
