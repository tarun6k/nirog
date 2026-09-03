package com.nirog.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.lifecycleScope
import com.nirog.data.DiagnosisEntity
import com.nirog.data.NirogDb
import com.nirog.data.PlotEntity
import com.nirog.data.SprayLogEntity
import com.nirog.data.toDomain
import com.nirog.engine.DoseResult
import com.nirog.feature.diary.DiaryScreen
import com.nirog.feature.treatment.DoseScreen
import kotlinx.coroutines.launch
import com.nirog.engine.Geohash
import com.nirog.engine.RecommendationResult
import com.nirog.feature.diagnosis.AnalysingScreen
import com.nirog.feature.diagnosis.AnalysisStep
import com.nirog.feature.diagnosis.DiagnosisPipeline
import com.nirog.feature.diagnosis.EscalatedScreen
import com.nirog.feature.diagnosis.ResultAmbiguousScreen
import com.nirog.feature.diagnosis.ResultConfidentScreen
import com.nirog.feature.diagnosis.pestNameHi
import com.nirog.feature.scan.GuidedCaptureScreen
import com.nirog.feature.scan.ScanStore
import com.nirog.feature.treatment.RecommendationLoader
import com.nirog.feature.treatment.TreatmentLadderScreen
import com.nirog.model.Product
import com.nirog.model.Verdict
import com.nirog.ui.NirogTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.firstOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject

private sealed interface Screen {
    data object Home : Screen
    data object Capture : Screen
    data class Analysing(val sessionId: String) : Screen
    data class Result(val diagnosis: DiagnosisEntity, val notes: List<String>) : Screen
    data class Ladder(
        val result: RecommendationResult,
        val pestId: String,
        val severityPct: Double,
        val products: Map<String, Product>,
    ) : Screen
    data class Dose(val prep: RecommendationLoader.DosePrep, val pestId: String) : Screen
    data object Diary : Screen
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var db: NirogDb
    @Inject lateinit var scanStore: ScanStore
    @Inject lateinit var pipeline: DiagnosisPipeline

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NirogTheme { Root() } }
    }

    @Composable
    private fun Root() {
        var screen by remember { mutableStateOf<Screen>(Screen.Home) }
        var plot by remember { mutableStateOf<PlotEntity?>(null) }
        var outbreaks by remember { mutableStateOf(0) }
        var consented by remember { mutableStateOf<Boolean?>(null) }

        LaunchedEffect(Unit) {
            consented = db.farmerDao().current()?.let { it.consentVersion >= CONSENT_VERSION } ?: false
            var p = db.plotDao().plots("demo-farmer").firstOrNull()?.firstOrNull()
            if (p == null && BuildConfig.DEBUG) {
                // Debug-only sample plot so screens work before onboarding exists (artboard 12).
                p = PlotEntity(
                    id = UUID.randomUUID().toString(), farmerId = "demo-farmer", label = "रोहतक",
                    cropId = "wheat", variety = null, areaValue = 2.5, areaUnit = "ACRE",
                    sowingDate = LocalDate.now().minusDays(62).toEpochDay(),
                    lat = 28.8955, lon = 76.6066, pincode = "124001",
                    plannedHarvestDate = LocalDate.now().plusDays(60).toEpochDay(),
                    organicStatus = "NONE", state = "HARYANA",
                )
                db.plotDao().upsert(p)
            }
            plot = p
            if (p?.lat != null && p.lon != null) {
                outbreaks = db.outbreakDao().countSince(
                    Geohash.encode(p.lat!!, p.lon!!, 5), p.cropId,
                    Instant.now().minus(14, ChronoUnit.DAYS).toEpochMilli(),
                )
            }
        }

        BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

        val loader = remember { RecommendationLoader(db) }
        var dosePending by remember { mutableStateOf<Pair<String, String>?>(null) } // productId to pestId
        LaunchedEffect(dosePending) {
            dosePending?.let { (productId, pestId) ->
                val p = plot ?: return@let
                loader.prepareDose(productId, pestId, p.id)?.let { screen = Screen.Dose(it, pestId) }
                dosePending = null
            }
        }

        when (val s = screen) {
            Screen.Home -> HomeScreen(
                plot,
                outbreaks,
                onDiary = { screen = Screen.Diary },
            ) { screen = Screen.Capture }

            Screen.Capture -> when (consented) {
                true -> GuidedCaptureScreen(
                    plotId = plot?.id ?: return,
                    store = scanStore,
                ) { sessionId -> screen = Screen.Analysing(sessionId) }
                // DPDP: itemised consent (re-)prompted before any capture.
                false -> ConsentScreen {
                    lifecycleScope.launch {
                        db.farmerDao().upsert(
                            com.nirog.data.FarmerEntity(
                                id = "demo-farmer", phoneHash = "", preferredLanguage = "hi",
                                consentVersion = CONSENT_VERSION,
                                consentGrantedAt = System.currentTimeMillis(),
                            ),
                        )
                        consented = true
                    }
                }
                null -> Unit // still loading consent state
            }

            is Screen.Analysing -> {
                AnalysingScreen(AnalysisStep.WEATHER)
                LaunchedEffect(s.sessionId) {
                    val session = db.scanDao().sessionsForPlot(plot!!.id).firstOrNull()
                        ?.firstOrNull { it.id == s.sessionId } ?: return@LaunchedEffect
                    val result = pipeline.run(session)
                    com.nirog.data.Sync.enqueue(applicationContext)
                    screen = Screen.Result(result.diagnosis, result.noteKeys)
                }
            }

            is Screen.Result -> ResultScreen(s) { screen = it }

            is Screen.Ladder -> TreatmentLadderScreen(
                result = s.result,
                pestNameHi = pestNameHi(s.pestId),
                severityPct = s.severityPct,
                products = s.products,
                onDose = { rung -> rung.productId?.let { dosePending = it to s.pestId } },
            )

            is Screen.Dose -> {
                val p = plot ?: return
                DoseScreen(
                    product = s.prep.product,
                    pestNameHi = pestNameHi(s.pestId),
                    plotLine = "आपका खेत: ${p.cropId.uppercase()}, ${p.areaValue} ${p.areaUnit}",
                    plan = s.prep.plan,
                ) {
                    val plan = s.prep.plan as? DoseResult.Plan ?: return@DoseScreen
                    lifecycleScope.launch {
                        db.diaryDao().insertSprayLog(
                            SprayLogEntity(
                                id = UUID.randomUUID().toString(),
                                plotId = p.id,
                                date = LocalDate.now().toEpochDay(),
                                productId = s.prep.product.id,
                                activeIngredient = s.prep.product.activeIngredient,
                                doseActual = "${plan.productPerTank.toInt()}${plan.productUnit}/टंकी",
                                tanks = plan.tanksRequired,
                                costInr = plan.totalCostInr,
                                phiExpiryDate = plan.phiExpiry.toEpochDay(),
                                recommendationId = null,
                                farmerConfirmed = true,
                            ),
                        )
                        screen = Screen.Diary
                    }
                }
            }

            Screen.Diary -> {
                val p = plot ?: return
                val logs by db.diaryDao().sprayLogsForPlot(p.id)
                    .collectAsState(initial = emptyList())
                var names by remember { mutableStateOf(emptyMap<String, String>()) }
                LaunchedEffect(Unit) {
                    names = db.catalogDao().products().associate { it.id to it.tradeNames.split(';').first() }
                }
                DiaryScreen(
                    plotLine = "${p.cropId.uppercase()} · ${p.label}",
                    logs = logs,
                    productNames = names,
                )
            }
        }
    }

    @Composable
    private fun ResultScreen(s: Screen.Result, navigate: (Screen) -> Unit) {
        val loader = remember { RecommendationLoader(db) }
        suspend fun toLadder(diagnosis: DiagnosisEntity) {
            val plotId = plotIdOf(diagnosis) ?: return
            val result = loader.recommend(diagnosis, plotId)
            val products = db.catalogDao().products().associate { it.id to it.toDomain() }
            navigate(Screen.Ladder(result, topPest(diagnosis), diagnosis.severityPct, products))
        }
        var pending by remember { mutableStateOf<DiagnosisEntity?>(null) }
        LaunchedEffect(pending) { pending?.let { toLadder(it) } }

        when (Verdict.valueOf(s.diagnosis.verdict)) {
            Verdict.CONFIDENT -> ResultConfidentScreen(
                pestId = topPest(s.diagnosis),
                severityPct = s.diagnosis.severityPct,
                noteKeys = s.notes,
                onTreatment = { pending = s.diagnosis },
            )
            Verdict.AMBIGUOUS -> ResultAmbiguousScreen(
                candidates = s.diagnosis.toDomain().candidates,
                noteKeys = s.notes,
                onPick = { pestId ->
                    // The farmer's physical differential test is a human confirmation.
                    pending = s.diagnosis.copy(
                        candidates = "$pestId:1.0",
                        verdict = Verdict.CONFIDENT.name,
                        source = com.nirog.model.DiagnosisSource.HUMAN.name,
                    )
                },
                onEscalate = { navigate(Screen.Result(s.diagnosis.copy(verdict = Verdict.ABSTAIN.name), s.notes)) },
            )
            Verdict.ABSTAIN -> {
                var sent by remember { mutableStateOf(false) }
                EscalatedScreen(
                    noteKeys = s.notes,
                    onSendPhotos = if (sent) null else {
                        {
                            sent = true
                            lifecycleScope.launch {
                                db.escalationDao().grantConsent(s.diagnosis.scanId)
                                com.nirog.data.Sync.enqueue(applicationContext)
                            }
                        }
                    },
                ) { navigate(Screen.Home) }
            }
        }
    }

    private fun topPest(d: DiagnosisEntity): String =
        d.candidates.split(';').firstOrNull()?.substringBefore(':') ?: ""

    private suspend fun plotIdOf(d: DiagnosisEntity): String? =
        db.plotDao().plots("demo-farmer").firstOrNull()?.firstOrNull()?.id
}
